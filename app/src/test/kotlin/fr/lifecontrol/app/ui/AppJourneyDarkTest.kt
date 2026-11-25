package fr.lifecontrol.app.ui

import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Même parcours que [AppJourneyTest], en mode sombre (captures préfixées `dark_`). */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = HiltTestApplication::class, qualifiers = "fr-rFR-w400dp-h860dp-night-xhdpi")
class AppJourneyDarkTest : AppJourneyTest() {
    override val shotPrefix = "dark_"
}
