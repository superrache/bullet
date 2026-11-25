package fr.lifecontrol.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import fr.lifecontrol.app.ui.theme.BulletTheme
import fr.lifecontrol.app.ui.today.HabitCard
import fr.lifecontrol.app.ui.today.HabitCardState
import fr.lifecontrol.domain.model.DayStatus
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Frequency
import fr.lifecontrol.domain.model.Goal
import fr.lifecontrol.domain.model.Habit
import fr.lifecontrol.domain.model.Streak
import fr.lifecontrol.domain.model.StreakUnit
import fr.lifecontrol.domain.model.TrackingMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class HabitCardTest {

    @get:Rule
    val compose = createComposeRule()

    private fun habit(direction: Direction, mode: TrackingMode, threshold: Double? = null) = Habit(
        id = "h", templateId = null, name = "Cigarette", emoji = "🚭", colorIndex = 0, direction = direction,
        trackingMode = mode, unit = "cigarettes", startDate = LocalDate.of(2026, 11, 1), unitCostCents = null,
        baselinePerDay = null, motivation = null, passiveMode = false, sortOrder = 0, archivedAt = null,
        goals = listOf(Goal(LocalDate.of(2026, 11, 1), Frequency.Daily, threshold)),
    )

    @Test
    fun quitBinary_showsHeldAndSlippedButtons() {
        var value: Boolean? = null
        compose.setContent {
            BulletTheme {
                HabitCard(
                    state = HabitCardState(habit(Direction.QUIT, TrackingMode.BINARY), DayStatus.PENDING, null, Streak(4, 4, StreakUnit.DAYS), null),
                    reorderMode = false, onOpen = {}, onDone = { value = it }, onIncrement = {}, onMove = {},
                )
            }
        }
        compose.onNodeWithText("🔥 4 jours").assertExists()
        compose.onNodeWithText("Craqué").performClick()
        assertThat(value).isFalse()
        compose.onNodeWithText("Tenu 💪").performClick()
        assertThat(value).isTrue()
    }

    @Test
    fun quantity_incrementUsesStep() {
        var delta = 0.0
        compose.setContent {
            BulletTheme {
                HabitCard(
                    state = HabitCardState(habit(Direction.LIMIT, TrackingMode.QUANTITY, 2.0), DayStatus.PENDING, null, Streak(0, 0, StreakUnit.DAYS), null),
                    reorderMode = false, onOpen = {}, onDone = {}, onIncrement = { delta += it }, onMove = {},
                )
            }
        }
        compose.onNodeWithText("≤ 2 cigarettes · tous les jours").assertExists()
        compose.onNodeWithContentDescription("Augmenter").performClick()
        assertThat(delta).isEqualTo(1.0)
    }
}
