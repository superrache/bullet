package fr.lifecontrol.app.data

import com.google.common.truth.Truth.assertThat
import fr.lifecontrol.app.data.db.AppDatabase
import fr.lifecontrol.app.data.db.MIGRATION_1_2
import fr.lifecontrol.app.data.repository.HabitDraft
import fr.lifecontrol.app.data.repository.HabitRepository
import fr.lifecontrol.domain.model.DayStatus
import fr.lifecontrol.domain.eval.DayStatusEvaluator
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Frequency
import fr.lifecontrol.domain.model.TrackingMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Le schéma ne change pas entre v1 et v2 : on applique le SQL de migration sur une base de test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MigrationTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: HabitRepository
    private val start = LocalDate.of(2026, 11, 1)

    private fun draft(templateId: String, name: String, direction: Direction = Direction.QUIT) = HabitDraft(
        templateId = templateId, name = name, emoji = "🍪", colorIndex = 1, direction = direction,
        trackingMode = TrackingMode.BINARY, unit = null, step = 1.0, startDate = start, unitCostCents = null,
        baselinePerDay = null, motivation = null, passiveMode = false, frequency = Frequency.Daily, threshold = null,
    )

    @Before
    fun setUp() {
        db = inMemoryDb()
        repo = HabitRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun snackingBecomesQuantity_withHistoryPreserved() = runTest {
        val snacking = repo.saveHabit(draft("snacking", "Grignotage"), start)
        val meat = repo.saveHabit(draft("meat", "Viande"), start)
        repo.updateEntry(snacking, start) { it.copy(done = true) }
        repo.updateEntry(snacking, start.plusDays(1)) { it.copy(done = false) }
        repo.updateEntry(meat, start) { it.copy(done = false) }

        MIGRATION_1_2.migrate(db.openHelper.writableDatabase)

        val habit = repo.getHabit(snacking)!!
        assertThat(habit.trackingMode).isEqualTo(TrackingMode.QUANTITY)
        assertThat(habit.unit).isEqualTo("grignotages")
        val entries = repo.entries.first().filter { it.habitId == snacking }.sortedBy { it.date }
        assertThat(entries.map { it.quantity }).containsExactly(0.0, 1.0).inOrder()
        val today = start.plusDays(5)
        assertThat(entries.map { DayStatusEvaluator.evaluate(habit, it.date, it, today) })
            .containsExactly(DayStatus.SUCCESS, DayStatus.FAILURE).inOrder()

        // Les autres habitudes oui/non ne sont pas touchées.
        assertThat(repo.getHabit(meat)!!.trackingMode).isEqualTo(TrackingMode.BINARY)
        assertThat(repo.entries.first().single { it.habitId == meat }.done).isFalse()
    }
}
