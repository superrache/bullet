package fr.lifecontrol.app.ui

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.google.common.truth.Truth.assertThat
import fr.lifecontrol.app.widget.DayProgress
import fr.lifecontrol.app.widget.ProgressWidget
import fr.lifecontrol.app.widget.ProgressWidgetUpdater
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import fr.lifecontrol.app.MainActivity
import fr.lifecontrol.app.data.repository.HabitDraft
import fr.lifecontrol.app.data.repository.HabitRepository
import fr.lifecontrol.app.data.repository.SettingsRepository
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Frequency
import fr.lifecontrol.domain.model.TrackingMode
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import javax.inject.Inject

/**
 * Parcours de bout en bout sur l'activité réelle (Hilt, Room, DataStore, catalogue) sous Robolectric,
 * avec captures d'écran Roborazzi (`./gradlew recordRoborazziDebug`).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = HiltTestApplication::class, qualifiers = "fr-rFR-w400dp-h860dp-xhdpi")
open class AppJourneyTest {

    /** Préfixe des captures, pour distinguer les variantes (mode sombre…). */
    protected open val shotPrefix = ""

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var habitRepository: HabitRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var cardService: fr.lifecontrol.app.data.repository.CardService
    @Inject lateinit var widgetUpdater: ProgressWidgetUpdater

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        hilt.inject()
        // Captures reproductibles : tirage des cartes déterministe (Wikipédia simulé, cf. FakeWikimedia).
        cardService.random = kotlin.random.Random(7)
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/$shotPrefix$name.png")
    }

    /**
     * Ferme les annonces : badges débloqués par l'historique, puis cartes du jour (tirées en différé,
     * révélées une à une) jusqu'à ce qu'aucune ne revienne.
     */
    private fun dismissDialogs() {
        while (compose.onAllNodesWithText("Fermer").fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodesWithText("Fermer").onFirst().performClick()
            compose.waitForIdle()
        }
        while (runCatching { waitForText("Plus tard", timeout = 3_000) }.isSuccess) {
            compose.onAllNodesWithText("Plus tard").onFirst().performClick()
            compose.waitForIdle()
        }
    }

    /** Rend le widget d'écran d'accueil (taille 2 × 2) et le capture. */
    private fun widgetShot(name: String): DayProgress {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val progress = runBlocking { widgetUpdater.current() }!!
        val view = ProgressWidget.render(context, progress).apply(context, android.widget.FrameLayout(context))
        val density = context.resources.displayMetrics.density
        val width = (140 * density).toInt()
        val height = (140 * density).toInt()
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        bitmap.captureRoboImage("build/outputs/roborazzi/$shotPrefix$name.png")
        return progress
    }

    private fun waitForText(text: String, timeout: Long = 10_000) {
        compose.waitUntil(timeout) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun onboardingThenFirstEntries() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Ton journal")
            shot("01_onboarding_intro")
            compose.onNodeWithText("Passer").performClick()
            waitForText("Par quoi veux-tu commencer")
            compose.onNodeWithText("Cigarette").performClick()
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Lecture"))
            compose.onNodeWithText("Lecture").performClick()
            shot("02_onboarding_choose")
            compose.onNodeWithText("Suivant").performClick()
            waitForText("Derniers réglages")
            compose.onNodeWithText("Prénom (facultatif)").performTextInput("Camille")
            shot("03_onboarding_config")
            compose.onNodeWithText("C'est parti !").performClick()

            waitForText("Bonjour Camille")
            waitForText("Lecture")
            shot("04_today_empty_day")

            // Lecture : +5 pages, quatre fois → objectif de 20 pages atteint, premier badge débloqué.
            repeat(4) {
                compose.onAllNodesWithContentDescription("Augmenter")[1].performClick()
                compose.waitForIdle()
            }
            waitForText("Nouveau badge débloqué")
            shot("05_badge_unlocked")
            compose.onNodeWithText("Fermer").performClick()

            // Cigarette : « 0 ? » note une journée sans tabac en un geste.
            compose.onNodeWithContentDescription("Noter zéro aujourd'hui").performClick()
            waitForText("2 / 2 objectifs tenus")
            repeat(5) {
                if (compose.onAllNodesWithText("Fermer").fetchSemanticsNodes().isNotEmpty()) {
                    compose.onAllNodesWithText("Fermer").onFirst().performClick()
                    compose.waitForIdle()
                }
            }
            // Journée entièrement renseignée, réussie à 100 % : carte du jour légendaire, tirée en direct.
            waitForText("Lire la carte")
            shot("06a_card_of_the_day")
            compose.onNodeWithText("Plus tard").performClick()
            shot("06_today_after_entries")
            // Widget : mêmes chiffres que l'écran « Aujourd'hui », sans aucun nom d'habitude.
            assertThat(widgetShot("06b_widget")).isEqualTo(DayProgress(2, 2))

            // Détail d'une entrée : note, humeur (F-08).
            compose.onNodeWithText("Lecture").performClick()
            waitForText("Humeur")
            shot("07_entry_sheet")
        }
    }

    private fun hasScrollAxis(vertical: Boolean) = androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
        if (vertical) androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange
        else androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange,
    )

    /** « Manger végétarien » : le choix de la façon de suivre pré-remplit sens, mode, unité et seuil. */
    @Test
    fun addHabitWithTrackingVariant() {
        runBlocking { settingsRepository.update { it.copy(onboardingDone = true, firstName = "Alex") } }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Ajouter une habitude")
            compose.onNodeWithText("Ajouter une habitude").performClick()
            compose.onNode(hasScrollToNodeAction() and hasScrollAxis(vertical = true))
                .performScrollToNode(hasText("Manger végétarien"))
            compose.onNodeWithText("Manger végétarien").performClick()
            waitForText("Comment veux-tu suivre ?")
            compose.onNodeWithText("Compter mes repas avec viande").performClick()
            waitForText("Moins de viande")
            waitForText("Maximum par jour")
            shot("08_edit_variants")
            compose.onNodeWithText("Compter mes repas végétariens").performClick()
            waitForText("Repas végétariens")
            waitForText("Minimum par jour")
        }
    }

    @Test
    fun screensWithHistory() {
        seedHistory()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Bonjour Alex")
            waitForText("Sport")
            // Ferme les annonces de badges débloqués par l'historique.
            dismissDialogs()
            shot("10_today")
            widgetShot("10b_widget")

            compose.onNodeWithText("Calendrier").performClick()
            waitForText("Toutes")
            shot("11_calendar_month")
            compose.onNodeWithText("Vue d'ensemble").performClick()
            waitForText("Carte de chaleur")
            compose.onNodeWithText("Trimestre").performClick()
            shot("12_overview_quarter")
            compose.onNodeWithText("Pixels par habitude").performScrollTo()
            shot("12b_overview_pixels")
            compose.onNodeWithText("Trimestre").performScrollTo()
            compose.onNodeWithText("🚭 Cigarette").performClick()
            waitForText("Économisés")
            shot("13_overview_cigarette")

            compose.onNodeWithText("Récompenses").performClick()
            waitForText("obtenus sur")
            shot("14_badges")

            // Carte à collectionner, tirée avec le bouton de débogage.
            cardService.debugDraw(fr.lifecontrol.app.data.content.CardRarity.RARE)
            waitForText("Lire la carte")
            shot("14b_card_reveal")
            compose.onNodeWithText("Lire la carte").performClick()
            waitForText("Lire sur Wikipédia")
            shot("14c_card_detail")
            compose.onNodeWithText("Fermer").performClick()
            compose.onNodeWithText("Cartes", substring = true).performClick()
            waitForText("Une carte par jour")
            shot("14d_cards")

            compose.onNodeWithText("Découvrir").performClick()
            waitForText("Besoin d'aide")
            shot("15_discover")
            compose.onAllNodesWithText("Cigarette").onFirst().performClick()
            waitForText("Ce qui se passe quand tu arrêtes")
            shot("16_article")
        }
    }

    private fun seedHistory() = runBlocking {
        val today = LocalDate.now().let { if (java.time.LocalTime.now().hour < 4) it.minusDays(1) else it }
        val start = today.minusDays(75)
        settingsRepository.update { it.copy(onboardingDone = true, firstName = "Alex") }
        val smoking = habitRepository.saveHabit(
            HabitDraft(
                templateId = "smoking", name = "Cigarette", emoji = "🚭", colorIndex = 11, direction = Direction.QUIT,
                trackingMode = TrackingMode.QUANTITY, unit = "cigarettes", step = 1.0, startDate = start,
                unitCostCents = 65, baselinePerDay = 10.0, motivation = "Retrouver mon souffle", passiveMode = false,
                frequency = Frequency.Daily, threshold = null,
            ),
            today,
        )
        val sport = habitRepository.saveHabit(
            HabitDraft(
                templateId = "sport", name = "Sport", emoji = "🏃", colorIndex = 2, direction = Direction.START,
                trackingMode = TrackingMode.DURATION, unit = null, step = 10.0, startDate = start,
                unitCostCents = null, baselinePerDay = null, motivation = null, passiveMode = false,
                frequency = Frequency.TimesPerWeek(3), threshold = 30.0,
            ),
            today,
        )
        val water = habitRepository.saveHabit(
            HabitDraft(
                templateId = "water", name = "Boire de l'eau", emoji = "💧", colorIndex = 4, direction = Direction.START,
                trackingMode = TrackingMode.QUANTITY, unit = "verres", step = 1.0, startDate = start,
                unitCostCents = null, baselinePerDay = null, motivation = null, passiveMode = false,
                frequency = Frequency.Daily, threshold = 6.0,
            ),
            today,
        )
        var day = start
        var i = 0
        while (day.isBefore(today)) {
            val cig = when {
                i < 10 -> (8 - i / 2).toDouble()
                i % 13 == 0 -> 2.0
                else -> 0.0
            }
            habitRepository.updateEntry(smoking, day) {
                it.copy(quantity = cig, triggerId = if (cig > 0) listOf("stress", "party", "boredom")[i % 3] else null)
            }
            if (i % 7 in setOf(0, 2, 4) && i % 11 != 0) habitRepository.updateEntry(sport, day) { it.copy(quantity = 30.0 + (i % 3) * 10) }
            if (i % 9 != 0) habitRepository.updateEntry(water, day) { it.copy(quantity = if (i % 5 == 0) 4.0 else 7.0) }
            day = day.plusDays(1)
            i++
        }
        habitRepository.updateEntry(water, today) { it.copy(quantity = 3.0) }
    }
}

