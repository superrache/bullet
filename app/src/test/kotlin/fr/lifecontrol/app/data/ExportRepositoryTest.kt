package fr.lifecontrol.app.data

import com.google.common.truth.Truth.assertThat
import fr.lifecontrol.app.data.db.AppDatabase
import fr.lifecontrol.app.data.repository.ExportRepository
import fr.lifecontrol.app.data.repository.HabitDraft
import fr.lifecontrol.app.data.repository.HabitRepository
import fr.lifecontrol.app.data.repository.ImportException
import fr.lifecontrol.app.data.repository.ImportMode
import fr.lifecontrol.app.di.AppModule
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.Frequency
import fr.lifecontrol.domain.model.TrackingMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ExportRepositoryTest {

    private lateinit var db: AppDatabase
    private val json = AppModule.json()
    private val start = LocalDate.of(2026, 11, 1)

    @Before
    fun setUp() {
        db = inMemoryDb()
    }

    @After
    fun tearDown() = db.close()

    private fun draft(name: String) = HabitDraft(
        name = name, emoji = "🏃", colorIndex = 2, direction = Direction.START, trackingMode = TrackingMode.DURATION,
        unit = null, step = 10.0, startDate = start, unitCostCents = null, baselinePerDay = null, motivation = "Santé",
        passiveMode = false, frequency = Frequency.TimesPerWeek(3), threshold = 30.0,
    )

    @Test
    fun exportThenReplaceRestoresEverything() = runTest {
        val repo = HabitRepository(db)
        val export = ExportRepository(db, settingsRepository(backgroundScope), json)
        val id = repo.saveHabit(draft("Sport"), start)
        repo.updateEntry(id, start) { it.copy(quantity = 45.0, note = "Footing", triggerId = "stress") }
        val text = export.export()

        export.deleteAll()
        assertThat(repo.habits.first()).isEmpty()

        export.import(export.parse(text), ImportMode.REPLACE)
        val habit = repo.habits.first().single()
        assertThat(habit.name).isEqualTo("Sport")
        assertThat(habit.currentGoal.frequency).isEqualTo(Frequency.TimesPerWeek(3))
        val entry = repo.entries.first().single()
        assertThat(entry.quantity).isEqualTo(45.0)
        assertThat(entry.note).isEqualTo("Footing")
        assertThat(entry.triggerId).isEqualTo("stress")
    }

    @Test
    fun mergeKeepsMostRecentEntry() = runTest {
        val repo = HabitRepository(db)
        val export = ExportRepository(db, settingsRepository(backgroundScope), json)
        val id = repo.saveHabit(draft("Sport"), start)
        repo.updateEntry(id, start) { it.copy(quantity = 20.0) }
        val older = export.parse(export.export())

        Thread.sleep(5)
        repo.updateEntry(id, start) { it.copy(quantity = 60.0) }
        repo.updateEntry(id, start.plusDays(1)) { it.copy(quantity = 15.0) }

        export.import(older, ImportMode.MERGE)
        val entries = repo.entries.first().associateBy { it.date }
        assertThat(entries[start]!!.quantity).isEqualTo(60.0)
        assertThat(entries[start.plusDays(1)]!!.quantity).isEqualTo(15.0)
    }

    @Test
    fun invalidFileIsRejected() = runTest {
        val export = ExportRepository(db, settingsRepository(backgroundScope), json)
        assertThrows(ImportException::class.java) { export.parse("{pas du json") }
        assertThrows(ImportException::class.java) {
            export.parse("""{"schemaVersion":99,"exportedAt":"x","habits":[],"goals":[],"entries":[],"triggers":[],"reminders":[],"badgeUnlocks":[]}""")
        }
    }
}
