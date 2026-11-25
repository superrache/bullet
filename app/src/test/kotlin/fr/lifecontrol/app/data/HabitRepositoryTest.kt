package fr.lifecontrol.app.data

import com.google.common.truth.Truth.assertThat
import fr.lifecontrol.app.data.db.AppDatabase
import fr.lifecontrol.app.data.repository.DuplicateHabitException
import fr.lifecontrol.app.data.repository.HabitDraft
import fr.lifecontrol.app.data.repository.HabitRepository
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
class HabitRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: HabitRepository
    private val start = LocalDate.of(2026, 11, 1)

    private fun draft(threshold: Double = 2.0, id: String? = null) = HabitDraft(
        id = id, templateId = "alcohol", name = " Alcool ", emoji = "🍷", colorIndex = 3,
        direction = Direction.LIMIT, trackingMode = TrackingMode.QUANTITY, unit = "verres", step = 1.0,
        startDate = start, unitCostCents = 400, baselinePerDay = 3.0, motivation = "  ", passiveMode = false,
        frequency = Frequency.Daily, threshold = threshold,
    )

    @Before
    fun setUp() {
        db = inMemoryDb()
        repo = HabitRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun createHabit_trimsAndStoresGoal() = runTest {
        val id = repo.saveHabit(draft(), start)
        val habit = repo.getHabit(id)!!
        assertThat(habit.name).isEqualTo("Alcool")
        assertThat(habit.motivation).isNull()
        assertThat(habit.goals).hasSize(1)
        assertThat(habit.currentGoal.threshold).isEqualTo(2.0)
    }

    @Test
    fun RG08_changingGoalCreatesVersionFromToday() = runTest {
        val id = repo.saveHabit(draft(), start)
        val today = start.plusDays(10)
        repo.saveHabit(draft(threshold = 1.0, id = id), today)
        val habit = repo.getHabit(id)!!
        assertThat(habit.goals.map { it.validFrom }).containsExactly(start, today).inOrder()
        assertThat(habit.goalAt(start.plusDays(5)).threshold).isEqualTo(2.0)
        assertThat(habit.goalAt(today).threshold).isEqualTo(1.0)

        // Une seconde modification le même jour remplace la version du jour.
        repo.saveHabit(draft(threshold = 0.5, id = id), today)
        assertThat(repo.getHabit(id)!!.goals).hasSize(2)
        assertThat(repo.getHabit(id)!!.currentGoal.threshold).isEqualTo(0.5)
    }

    @Test
    fun unchangedGoalKeepsHistory() = runTest {
        val id = repo.saveHabit(draft(), start)
        repo.saveHabit(draft(id = id).copy(name = "Vin"), start.plusDays(3))
        val habit = repo.getHabit(id)!!
        assertThat(habit.name).isEqualTo("Vin")
        assertThat(habit.goals).hasSize(1)
    }

    @Test
    fun RG01_updateEntryUpsertsAndDeletesWhenEmpty() = runTest {
        val id = repo.saveHabit(draft(), start)
        repo.updateEntry(id, start) { it.copy(quantity = 1.0) }
        repo.updateEntry(id, start) { it.copy(quantity = (it.quantity ?: 0.0) + 1, note = "soirée") }
        val entries = repo.entries.first()
        assertThat(entries).hasSize(1)
        assertThat(entries.single().quantity).isEqualTo(2.0)

        repo.updateEntry(id, start) { it.copy(quantity = null, note = null) }
        assertThat(repo.entries.first()).isEmpty()
    }

    @Test
    fun deletingHabitCascadesEntries() = runTest {
        val id = repo.saveHabit(draft(), start)
        repo.updateEntry(id, start) { it.copy(quantity = 1.0) }
        repo.deleteHabit(id)
        assertThat(repo.entries.first()).isEmpty()
        assertThat(repo.habits.first()).isEmpty()
    }

    @Test
    fun moveReordersActiveHabits() = runTest {
        val a = repo.saveHabit(draft().copy(name = "A", templateId = null), start)
        val b = repo.saveHabit(draft().copy(name = "B", templateId = null), start)
        repo.move(b, -1)
        assertThat(repo.habits.first().map { it.id }).containsExactly(b, a).inOrder()
    }

    @Test
    fun defaultTriggersAreSeeded() = runTest {
        assertThat(repo.triggers.first().map { it.id }).containsAtLeast("stress", "party", "boredom")
    }

    @Test
    fun duplicateName_isRejected_ignoringCaseAndAccents() = runTest {
        repo.saveHabit(draft().copy(templateId = null, name = "Café"), start)
        val e = assertThrows(DuplicateHabitException::class.java) {
            kotlinx.coroutines.runBlocking { repo.saveHabit(draft().copy(templateId = null, name = "  cafe "), start) }
        }
        assertThat(e.existingName).isEqualTo("Café")
    }

    @Test
    fun sameTemplate_isRejectedOnCreation() = runTest {
        repo.saveHabit(draft(), start)
        assertThat(repo.findDuplicate("Vin", "alcohol", null)?.name).isEqualTo("Alcool")
    }

    @Test
    fun editingHabit_isNotItsOwnDuplicate() = runTest {
        val id = repo.saveHabit(draft(), start)
        repo.saveHabit(draft(threshold = 1.0, id = id), start)
        assertThat(repo.getHabit(id)!!.currentGoal.threshold).isEqualTo(1.0)
    }

    @Test
    fun renamingToExistingName_isRejected() = runTest {
        repo.saveHabit(draft(), start)
        val other = repo.saveHabit(draft().copy(templateId = null, name = "Sport"), start)
        assertThat(repo.findDuplicate("ALCOOL", null, other)?.name).isEqualTo("Alcool")
    }

    @Test
    fun deleteHabit_removesIt() = runTest {
        val id = repo.saveHabit(draft(), start)
        repo.deleteHabit(id)
        assertThat(repo.getHabit(id)).isNull()
        assertThat(repo.findDuplicate("Alcool", "alcohol", null)).isNull()
    }
}
