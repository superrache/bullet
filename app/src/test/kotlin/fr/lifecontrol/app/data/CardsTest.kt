package fr.lifecontrol.app.data

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import fr.lifecontrol.app.data.cards.CardGenerator
import fr.lifecontrol.app.data.cards.FakeWikimedia
import fr.lifecontrol.app.data.content.CardRarity
import fr.lifecontrol.app.data.content.CardTheme
import fr.lifecontrol.app.data.content.CardThemeRepository
import fr.lifecontrol.app.data.db.AppDatabase
import fr.lifecontrol.app.data.db.CardEntity
import fr.lifecontrol.app.data.db.MIGRATION_2_4
import fr.lifecontrol.app.data.db.MIGRATION_3_4
import fr.lifecontrol.app.data.repository.CardRules
import fr.lifecontrol.app.data.repository.HabitDay
import fr.lifecontrol.domain.model.DayStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CardsTest {

    private fun themes(vararg themes: CardTheme) =
        object : CardThemeRepository(ApplicationProvider.getApplicationContext(), Json) {
            override val themes = themes.toList()
        }

    private val history = CardTheme("history", "🏛️", "Histoire", (1..12).map { "Sujet ${"x".repeat(it)}" })

    // --- Bilan du jour et rareté ---

    @Test
    fun rarete_selonLeTauxDeReussite() {
        assertThat(CardRarity.forSuccessRate(1.0)).isEqualTo(CardRarity.LEGENDARY)
        assertThat(CardRarity.forSuccessRate(0.75)).isEqualTo(CardRarity.RARE)
        assertThat(CardRarity.forSuccessRate(0.5)).isEqualTo(CardRarity.RARE)
        assertThat(CardRarity.forSuccessRate(0.49)).isEqualTo(CardRarity.COMMON)
        assertThat(CardRarity.forSuccessRate(0.0)).isEqualTo(CardRarity.COMMON)
    }

    @Test
    fun journeeComplete_quandPlusRienNAttend() {
        val score = CardRules.score(listOf(HabitDay(DayStatus.SUCCESS), HabitDay(DayStatus.FAILURE), HabitDay(DayStatus.NEUTRAL)))
        assertThat(score.complete).isTrue()
        assertThat(score.rate).isEqualTo(0.5)

        assertThat(CardRules.score(listOf(HabitDay(DayStatus.SUCCESS), HabitDay(DayStatus.PENDING))).complete).isFalse()
        assertThat(CardRules.score(emptyList()).complete).isFalse()
    }

    @Test
    fun habitudesHebdomadairesEtPassives_neBloquentPas() {
        val score = CardRules.score(
            listOf(
                HabitDay(DayStatus.SUCCESS),
                HabitDay(DayStatus.PENDING, weekly = true),
                HabitDay(DayStatus.PENDING, passive = true),
            ),
        )
        assertThat(score.complete).isTrue()
        assertThat(score.total).isEqualTo(2)
        assertThat(score.rate).isEqualTo(1.0)
    }

    @Test
    fun jourPasseNonRenseigne_compteCommeNonTenu() {
        val score = CardRules.score(listOf(HabitDay(DayStatus.SUCCESS), HabitDay(DayStatus.MISSING)))
        assertThat(score.rate).isEqualTo(0.5)
    }

    // --- Génération en direct ---

    @Test
    fun legendaire_piocheLesArticlesLesPlusConsultes() = runTest {
        // Consultations = longueur du titre : « Sujet xxxxxxxxxxxx » est le plus consulté.
        val generator = CardGenerator(FakeWikimedia(), themes(history))
        val legendary = (1..5).map { generator.generate(CardRarity.LEGENDARY, emptySet(), Random(it)).title }
        assertThat(legendary.toSet()).containsAnyOf("Sujet xxxxxxxxxxxx", "Sujet xxxxxxxxxxx")
        assertThat(legendary.all { it.length >= "Sujet xxxxxxxxxxx".length }).isTrue()

        val common = (1..5).map { generator.generate(CardRarity.COMMON, emptySet(), Random(it)).title }
        assertThat(common.all { it.length <= "Sujet xxxxxx".length }).isTrue()
    }

    @Test
    fun carteComplete_avecCreditsEtImage() = runTest {
        val card = CardGenerator(FakeWikimedia(), themes(history)).generate(CardRarity.RARE, emptySet(), Random(3))
        assertThat(card.themeLabel).isEqualTo("🏛️ Histoire")
        assertThat(card.qid).startsWith("Q")
        assertThat(card.imageArtist).isEqualTo("Photographe & co")
        assertThat(card.imageLicense).isEqualTo("CC BY-SA 4.0")
        assertThat(card.views).isGreaterThan(0L)
        assertThat(card.imageBytes).isNotEmpty()
    }

    @Test
    fun jamaisDeuxFoisLaMemeCarte_niImageNonLibre() = runTest {
        val fake = FakeWikimedia(nonFreeImages = setOf("Sujet xxxxxxxxxxxx"))
        val generator = CardGenerator(fake, themes(history))
        val owned = setOf("Sujet xxxxxxxxxxx")
        repeat(5) {
            val title = generator.generate(CardRarity.LEGENDARY, owned, Random(it)).title
            assertThat(title).isNotEqualTo("Sujet xxxxxxxxxxxx")
            assertThat(title).isNotIn(owned)
        }
    }

    @Test
    fun themeEpuise_continueAvecLesArticlesLies() = runTest {
        val generator = CardGenerator(FakeWikimedia(), themes(history))
        val card = generator.generate(CardRarity.COMMON, history.titles.toSet(), Random(1))
        assertThat(card.title).startsWith("Article lié")
        assertThat(card.themeId).isEqualTo("history")
    }

    @Test
    fun horsConnexion_erreurReseau() = runTest {
        val generator = CardGenerator(FakeWikimedia(offline = true), themes(history))
        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { generator.generate(CardRarity.RARE, emptySet(), Random(1)) }
        }
    }

    @Test
    fun ordreSelonRarete() {
        val sorted = (0 until 12).toList()
        assertThat(CardGenerator.orderForRarity(sorted, CardRarity.LEGENDARY, Random(1)).take(2)).containsExactly(0, 1)
        assertThat(CardGenerator.orderForRarity(sorted, CardRarity.RARE, Random(1)).take(4)).containsExactly(2, 3, 4, 5)
        assertThat(CardGenerator.orderForRarity(sorted, CardRarity.COMMON, Random(1)).take(6)).containsExactly(6, 7, 8, 9, 10, 11)
        assertThat(CardGenerator.orderForRarity(listOf(42), CardRarity.RARE, Random(1))).containsExactly(42)
    }

    @Test
    fun texteCoupeAUneFinDePhrase() {
        val text = "Mot ".repeat(100) + "Fin. " + "Mot ".repeat(200)
        assertThat(CardGenerator.trimSentences(text, 700)).endsWith("Mot Fin.")
        // Pas de fin de phrase assez loin : coupe sur un mot.
        assertThat(CardGenerator.trimSentences("Court. " + "Mot ".repeat(200), 700)).endsWith("Mot…")
        assertThat(CardGenerator.trimSentences("Court [1] texte.", 700)).isEqualTo("Court texte.")
    }

    // --- Stockage ---

    @Test
    fun migration3to4_etUneCarteParJour() = runTest {
        val db: AppDatabase = inMemoryDb()
        val sql = db.openHelper.writableDatabase
        sql.execSQL("DROP TABLE card")
        sql.execSQL("CREATE TABLE card_unlock (card_id TEXT NOT NULL PRIMARY KEY)")
        MIGRATION_3_4.migrate(sql)
        val card = CardEntity(CardEntity.dailyId("2026-11-25"), "2026-11-25", "day", "RARE", 0.5, createdAt = 1L)
        assertThat(db.cardDao().insert(card)).isNotEqualTo(-1L)
        assertThat(db.cardDao().insert(card.copy(rarity = "LEGENDARY"))).isEqualTo(-1L)
        assertThat(db.cardDao().getAll().single().rarity).isEqualTo("RARE")
        db.close()
    }

    @Test
    fun migration2to4_creeSeulementLaTableDesCartes() = runTest {
        val db: AppDatabase = inMemoryDb()
        val sql = db.openHelper.writableDatabase
        sql.execSQL("DROP TABLE card")
        MIGRATION_2_4.migrate(sql)
        db.cardDao().insert(CardEntity(CardEntity.dailyId("2026-11-25"), "2026-11-25", "day", "RARE", 0.5, createdAt = 1L))
        assertThat(db.cardDao().getAll()).hasSize(1)
        val tables = sql.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('card_unlock', 'card_roll')")
        assertThat(tables.count).isEqualTo(0)
        tables.close()
        db.close()
    }
}
