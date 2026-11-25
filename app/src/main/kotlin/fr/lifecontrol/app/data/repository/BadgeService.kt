package fr.lifecontrol.app.data.repository

import fr.lifecontrol.app.data.content.BadgeMeta
import fr.lifecontrol.app.data.content.CatalogRepository
import fr.lifecontrol.app.data.db.AppDatabase
import fr.lifecontrol.app.data.db.BadgeUnlockEntity
import fr.lifecontrol.app.data.db.ContentReadEntity
import fr.lifecontrol.app.di.ApplicationScope
import fr.lifecontrol.domain.badge.BadgeEngine
import fr.lifecontrol.domain.badge.BadgeProgress
import fr.lifecontrol.domain.badge.BadgeRules
import fr.lifecontrol.domain.badge.BadgeSnapshot
import fr.lifecontrol.domain.badge.BadgeUnit
import fr.lifecontrol.domain.model.Habit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Un badge tel qu'affiché : progression + niveaux définitivement acquis (RG-11). */
data class BadgeView(
    val badgeId: String,
    val meta: BadgeMeta,
    val habitId: String?,
    val habitName: String?,
    val habitEmoji: String?,
    /** Unité de l'habitude, pour les badges exprimés en unités (pages, verres…). */
    val habitUnit: String? = null,
    val current: Long,
    val tiers: List<Long>,
    val unit: BadgeUnit,
    /** Niveaux acquis (le maximum entre la progression et les déblocages enregistrés). */
    val level: Int,
    val unlockDates: Map<Int, Long>,
) {
    val key: String get() = "$badgeId/${habitId.orEmpty()}"
    val isUnlocked: Boolean get() = level > 0
    val nextTarget: Long? get() = tiers.getOrNull(level)
    val isComplete: Boolean get() = level >= tiers.size
    val fraction: Float
        get() = nextTarget?.let { (current.toFloat() / it).coerceIn(0f, 1f) } ?: 1f
}

/** Moteur de badges branché sur le journal (§7.3) : calcule, persiste et annonce les déblocages. */
@Singleton
class BadgeService @Inject constructor(
    journalRepository: JournalRepository,
    db: AppDatabase,
    private val catalogRepository: CatalogRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    private val badgeDao = db.badgeDao()

    private val _badges = MutableStateFlow<List<BadgeView>>(emptyList())
    val badges: StateFlow<List<BadgeView>> = _badges.asStateFlow()

    private val unlockChannel = Channel<List<BadgeView>>(Channel.BUFFERED)

    /** Nouveaux déblocages, livrés une seule fois (animation F-18). */
    val unlockEvents: Flow<List<BadgeView>> = unlockChannel.receiveAsFlow()

    init {
        scope.launch {
            combine(journalRepository.journal.filterNotNull(), badgeDao.observeContentRead()) { journal, read ->
                journal to read.size
            }.collectLatest { (journal, articlesRead) ->
                val progress = withContext(Dispatchers.Default) {
                    BadgeEngine.evaluate(BadgeSnapshot(journal.today, journal.histories.values.toList(), articlesRead))
                }
                // Enregistrement et annonce ne doivent pas être séparés par une annulation
                // (sinon le déblocage serait enregistré sans jamais être annoncé).
                withContext(NonCancellable) {
                    val newlyUnlocked = persistUnlocks(progress, journal.habits)
                    val views = buildViews(progress, badgeDao.getUnlocks(), journal.habits)
                    _badges.value = views
                    if (newlyUnlocked.isNotEmpty()) {
                        unlockChannel.send(views.filter { it.key in newlyUnlocked })
                    }
                }
            }
        }
    }

    private suspend fun persistUnlocks(progress: List<BadgeProgress>, habits: List<Habit>): Set<String> {
        val existing = badgeDao.getUnlocks()
            .groupBy { "${it.badgeId}/${it.habitId.orEmpty()}" }
            .mapValues { (_, list) -> list.maxOf { it.level } }
        val now = System.currentTimeMillis()
        val toInsert = mutableListOf<BadgeUnlockEntity>()
        val keys = mutableSetOf<String>()
        for (p in progress) {
            val key = "${p.badgeId}/${p.habitId.orEmpty()}"
            val already = existing[key] ?: 0
            if (p.reachedLevel > already) {
                val habit = habits.firstOrNull { it.id == p.habitId }
                for (level in already + 1..p.reachedLevel) {
                    toInsert += BadgeUnlockEntity(UUID.randomUUID().toString(), p.badgeId, p.habitId, habit?.name, level, now)
                }
                keys += key
            }
        }
        if (toInsert.isNotEmpty()) badgeDao.insertUnlocks(toInsert)
        return keys
    }

    private fun buildViews(progress: List<BadgeProgress>, unlocks: List<BadgeUnlockEntity>, habits: List<Habit>): List<BadgeView> {
        val unlocksByKey = unlocks.groupBy { "${it.badgeId}/${it.habitId.orEmpty()}" }
        val ruleOrder = BadgeRules.all.mapIndexed { i, r -> r.id to i }.toMap()
        val views = progress.mapNotNull { p ->
            val meta = catalogRepository.badge(p.badgeId) ?: return@mapNotNull null
            val key = "${p.badgeId}/${p.habitId.orEmpty()}"
            val persisted = unlocksByKey[key].orEmpty()
            val habit = habits.firstOrNull { it.id == p.habitId }
            BadgeView(
                badgeId = p.badgeId,
                meta = meta,
                habitId = p.habitId,
                habitName = habit?.name,
                habitEmoji = habit?.emoji,
                habitUnit = habit?.unit,
                current = p.current,
                tiers = p.tiers,
                unit = p.unit,
                level = maxOf(p.reachedLevel, persisted.maxOfOrNull { it.level } ?: 0),
                unlockDates = persisted.associate { it.level to it.unlockedAt },
            )
        }
        // Badges acquis pour des habitudes supprimées : toujours visibles (RG-11).
        val known = views.map { it.key }.toSet()
        val orphans = unlocksByKey.filterKeys { it !in known }.mapNotNull { (_, list) ->
            val first = list.first()
            val meta = catalogRepository.badge(first.badgeId) ?: return@mapNotNull null
            val level = list.maxOf { it.level }
            BadgeView(
                badgeId = first.badgeId, meta = meta, habitId = first.habitId, habitName = first.habitName,
                habitEmoji = null, current = 0, tiers = List(level) { 0L }, unit = BadgeUnit.COUNT,
                level = level, unlockDates = list.associate { it.level to it.unlockedAt },
            )
        }
        return (views + orphans).sortedWith(compareBy({ ruleOrder[it.badgeId] ?: Int.MAX_VALUE }, { it.habitName.orEmpty() }))
    }

    suspend fun markArticleRead(articleId: String) {
        badgeDao.insertContentRead(listOf(ContentReadEntity(articleId, System.currentTimeMillis())))
    }
}
