package fr.lifecontrol.app.data

import com.google.common.truth.Truth.assertThat
import fr.lifecontrol.app.data.content.CatalogRepository
import fr.lifecontrol.app.data.content.HabitTemplate
import fr.lifecontrol.app.data.content.LEGACY_TEMPLATE_IDS
import fr.lifecontrol.domain.model.Direction
import fr.lifecontrol.domain.model.TrackingMode
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CatalogTest {

    private val repo = CatalogRepository(context(), Json { ignoreUnknownKeys = true })

    @Test
    fun everyVariantIsAValidSetup() {
        for (template in repo.catalog.templates) {
            if (template.variants.isEmpty()) continue
            assertThat(template.variants.map { it.id }).containsNoDuplicates()
            // La première variante reprend les réglages par défaut du modèle (utilisés par l'accueil).
            assertThat(template.withVariant(template.variants.first())).isEqualTo(template)
            template.variants.forEach { assertValid(template.withVariant(it)) }
        }
    }

    private fun assertValid(t: HabitTemplate) {
        if (t.direction == Direction.LIMIT) assertThat(t.mode).isNotEqualTo(TrackingMode.BINARY)
        if (t.direction != Direction.QUIT && t.mode != TrackingMode.BINARY) assertThat(t.threshold).isNotNull()
        if (t.mode == TrackingMode.QUANTITY) assertThat(t.unit).isNotEmpty()
    }

    @Test
    fun meatAndVegetarianAreOneTemplate() {
        assertThat(repo.catalog.templates.map { it.id }).doesNotContain("meat")
        LEGACY_TEMPLATE_IDS.values.forEach { assertThat(repo.template(it)).isNotNull() }
        // Les habitudes créées avec l'ancien modèle retrouvent leur fiche.
        assertThat(repo.template("meat")!!.id).isEqualTo("vegetarian")

        val veg = repo.template("vegetarian")!!
        val meals = veg.withVariant(veg.variant("veggie_meals"))
        assertThat(meals.direction).isEqualTo(Direction.START)
        assertThat(meals.mode).isEqualTo(TrackingMode.QUANTITY)
        assertThat(meals.threshold).isEqualTo(1.0)
        val meat = veg.withVariant(veg.variant("meat_meals"))
        assertThat(meat.direction).isEqualTo(Direction.LIMIT)
        assertThat(meat.unit).isEqualTo("repas avec viande")
    }
}
