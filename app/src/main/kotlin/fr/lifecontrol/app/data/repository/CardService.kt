package fr.lifecontrol.app.data.repository

import fr.lifecontrol.app.data.cards.CardGenerator
import fr.lifecontrol.app.data.cards.CardImageStore
import fr.lifecontrol.app.data.content.CardRarity
import fr.lifecontrol.app.data.db.AppDatabase
import fr.lifecontrol.app.data.db.CardEntity
import fr.lifecontrol.app.di.ApplicationScope
import fr.lifecontrol.domain.model.DayStatus
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Frequency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** Statut d'une habitude pour un jour, avec ce qu'il faut pour le compter. */
data class HabitDay(val status: DayStatus, val weekly: Boolean = false, val passive: Boolean = false)

/** Bilan d'une journée pour la carte du jour. */
data class DayScore(val successes: Int, val total: Int, val complete: Boolean) {
    val rate: Double get() = if (total == 0) 0.0 else successes.toDouble() / total
}

object CardRules {
    /**
     * Compte les habitudes suivies ce jour-là.
     *  - « N fois par semaine » non réalisée aujourd'hui : ni comptée ni bloquante (pas un écart, RG-06) ;
     *  - mode passif (arrêter / limiter) pas encore saisi : compté comme tenu, comme il le sera en fin de journée ;
     *  - toute autre habitude en attente rend la journée incomplète ;
     *  - jour passé non renseigné : compté comme non tenu.
     */
    fun score(days: List<HabitDay>): DayScore {
        var successes = 0
        var total = 0
        var complete = true
        for (d in days) when (d.status) {
            DayStatus.SUCCESS -> { successes++; total++ }
            DayStatus.FAILURE, DayStatus.MISSING -> total++
            DayStatus.PENDING -> when {
                d.weekly -> Unit
                d.passive -> { successes++; total++ }
                else -> { total++; complete = false }
            }
            DayStatus.NEUTRAL, DayStatus.FUTURE -> Unit
        }
        return DayScore(successes, total, complete && total > 0)
    }
}

/**
 * Carte du jour : une carte par jour, quoi qu'il arrive, dès que toutes les habitudes du jour sont renseignées
 * (ou le lendemain, avec le bilan final, si la journée est restée incomplète). Sa rareté dépend du taux de réussite.
 * Elle est générée en direct depuis Wikipédia ; hors connexion, elle reste « à récupérer » et sera générée plus tard.
 */
@Singleton
class CardService @Inject constructor(
    journalRepository: JournalRepository,
    db: AppDatabase,
    private val generator: CardGenerator,
    private val images: CardImageStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val cardDao = db.cardDao()
    private val awardMutex = Mutex()
    private val generateMutex = Mutex()

    /** Source d'aléa (remplaçable dans les tests). */
    var random: Random = Random.Default

    val cards: StateFlow<List<CardEntity>> = cardDao.observeAll()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _generating = MutableStateFlow<Set<String>>(emptySet())
    /** Cartes en cours de génération (animation de tirage). */
    val generating: StateFlow<Set<String>> = _generating.asStateFlow()

    private val _failed = MutableStateFlow<Set<String>>(emptySet())
    /** Cartes dont la dernière génération a échoué (hors connexion…). */
    val failed: StateFlow<Set<String>> = _failed.asStateFlow()

    private val revealChannel = Channel<String>(Channel.BUFFERED)
    /** Identifiants des cartes à révéler (le tirage s'affiche pendant la génération). */
    val reveals: Flow<String> = revealChannel.receiveAsFlow()

    init {
        scope.launch {
            journalRepository.journal.filterNotNull().collectLatest { journal ->
                fun scoreOf(day: LocalDate) = CardRules.score(
                    journal.habits.filter { it.isActiveOn(day) }.map { h ->
                        HabitDay(
                            status = journal.history(h.id)?.status(day) ?: DayStatus.NEUTRAL,
                            weekly = h.goalAt(day).frequency is Frequency.TimesPerWeek,
                            passive = h.passiveMode && h.direction != Direction.START,
                        )
                    },
                )
                val yesterday = journal.today.minusDays(1)
                val yesterdayScore = scoreOf(yesterday)
                val todayScore = scoreOf(journal.today)
                withContext(NonCancellable) {
                    // La veille : bilan définitif, même si elle n'a pas été complétée.
                    if (yesterdayScore.total > 0) award(yesterday, yesterdayScore.rate)
                    if (todayScore.complete) award(journal.today, todayScore.rate)
                }
            }
        }
        scope.launch {
            images.keepOnly(cardDao.getAll().map { it.id }.toSet())
            retryPending()
        }
    }

    private suspend fun award(day: LocalDate, rate: Double) = awardMutex.withLock {
        val card = CardEntity(
            id = CardEntity.dailyId(day.toString()),
            day = day.toString(),
            source = "day",
            rarity = CardRarity.forSuccessRate(rate).name,
            successRate = rate,
            createdAt = System.currentTimeMillis(),
        )
        if (cardDao.insert(card) != -1L) {
            revealChannel.send(card.id)
            scope.launch { generate(card.id) }
        }
    }

    /** Génère les cartes restées à récupérer (au démarrage, au retour dans l'application, ou à la demande). */
    fun retryPending() {
        scope.launch {
            cardDao.getAll().filter { !it.isReady }.forEach { generate(it.id, revealWhenReady = it.id in _failed.value) }
        }
    }

    fun retry(cardId: String) {
        scope.launch { generate(cardId, revealWhenReady = false) }
    }

    private suspend fun generate(cardId: String, revealWhenReady: Boolean = false) {
        if (cardId in _generating.value) return
        _generating.update { it + cardId }
        _failed.update { it - cardId }
        try {
            generateMutex.withLock {
                val card = cardDao.get(cardId) ?: return
                if (card.isReady) return
                val owned = cardDao.getAll().filter { it.isReady }.flatMap { listOfNotNull(it.qid, it.title) }.toSet()
                val g = generator.generate(CardRarity.parse(card.rarity), owned, random)
                images.save(cardId, g.imageBytes)
                cardDao.update(
                    card.copy(
                        status = CardEntity.STATUS_READY, qid = g.qid, title = g.title, themeId = g.themeId,
                        themeLabel = g.themeLabel, summary = g.summary, text = g.text, url = g.url, views = g.views,
                        imageUrl = g.imageUrl, imageArtist = g.imageArtist, imageLicense = g.imageLicense,
                        imageLicenseUrl = g.imageLicenseUrl, imagePage = g.imagePage,
                    ),
                )
            }
            if (revealWhenReady) revealChannel.send(cardId)
        } catch (e: IOException) {
            _failed.update { it + cardId }
        } finally {
            _generating.update { it - cardId }
        }
    }

    /** Image d'une carte ; retéléchargée si absente (après un import, par exemple). */
    suspend fun image(card: CardEntity, maxWidth: Int): android.graphics.Bitmap? {
        images.load(card.id, maxWidth)?.let { return it }
        val url = card.imageUrl ?: return null
        return try {
            images.save(card.id, generator.fetchImage(url))
            images.load(card.id, maxWidth)
        } catch (e: IOException) {
            null
        }
    }

    /** Débogage : une carte de la rareté voulue, tout de suite (hors carte du jour). */
    fun debugDraw(rarity: CardRarity) {
        scope.launch {
            val card = CardEntity(
                id = "debug-" + UUID.randomUUID(),
                day = LocalDate.now().toString(),
                source = "debug",
                rarity = rarity.name,
                successRate = -1.0,
                createdAt = System.currentTimeMillis(),
            )
            cardDao.insert(card)
            revealChannel.send(card.id)
            generate(card.id)
        }
    }

    /** Débogage : oublie toutes les cartes, y compris celle du jour (qui pourra être regagnée). */
    fun debugReset() {
        scope.launch {
            awardMutex.withLock {
                cardDao.clear()
                images.keepOnly(emptySet())
            }
        }
    }
}
