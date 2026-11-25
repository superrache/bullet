package fr.lifecontrol.app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import fr.lifecontrol.app.R
import fr.lifecontrol.app.ui.badges.BadgesScreen
import fr.lifecontrol.app.ui.calendar.CalendarScreen
import fr.lifecontrol.app.ui.discover.ArticleScreen
import fr.lifecontrol.app.ui.discover.CatalogScreen
import fr.lifecontrol.app.ui.discover.HelpScreen
import fr.lifecontrol.app.ui.habit.HabitDetailScreen
import fr.lifecontrol.app.ui.habit.HabitEditScreen
import fr.lifecontrol.app.ui.onboarding.OnboardingScreen
import fr.lifecontrol.app.ui.settings.SettingsScreen
import fr.lifecontrol.app.ui.today.TodayScreen
import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute
@Serializable data object HomeRoute
@Serializable data class DayRoute(val date: String)
@Serializable data class HabitDetailRoute(val habitId: String)
@Serializable data class HabitEditRoute(val habitId: String? = null, val templateId: String? = null)
@Serializable data object AddHabitRoute
@Serializable data class ArticleRoute(val templateId: String)
@Serializable data object HelpRoute
@Serializable data object SettingsRoute

private enum class Tab(val label: Int, val icon: ImageVector) {
    TODAY(R.string.tab_today, Icons.Rounded.Today),
    CALENDAR(R.string.tab_calendar, Icons.Rounded.CalendarMonth),
    BADGES(R.string.tab_badges, Icons.Rounded.EmojiEvents),
    DISCOVER(R.string.tab_discover, Icons.Rounded.Explore),
}

@Composable
fun AppNavigation(onboardingDone: Boolean, navController: NavHostController = rememberNavController()) {
    LaunchedEffect(onboardingDone) {
        val current = navController.currentDestination
        if (!onboardingDone && current != null && current.route?.contains("OnboardingRoute") != true) {
            navController.navigate(OnboardingRoute) { popUpTo(0) { inclusive = true } }
        }
    }

    NavHost(navController, startDestination = if (onboardingDone) HomeRoute else OnboardingRoute) {
        composable<OnboardingRoute> {
            OnboardingScreen(onFinished = {
                navController.navigate(HomeRoute) { popUpTo(0) { inclusive = true } }
            })
        }
        composable<HomeRoute> { HomeScreen(navController) }
        composable<DayRoute> {
            TodayScreen(
                onOpenHabit = { navController.navigate(HabitDetailRoute(it)) },
                onAddHabit = { navController.navigate(AddHabitRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenArticle = { navController.navigate(ArticleRoute(it)) },
                onOpenHelp = { navController.navigate(HelpRoute) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<HabitDetailRoute> {
            HabitDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(HabitEditRoute(habitId = it)) },
                onOpenDay = { navController.navigate(DayRoute(it.toString())) },
                onOpenArticle = { navController.navigate(ArticleRoute(it)) },
                onOpenHelp = { navController.navigate(HelpRoute) },
            )
        }
        composable<HabitEditRoute> { entry ->
            val route = entry.toRoute<HabitEditRoute>()
            HabitEditScreen(
                onBack = { navController.popBackStack() },
                onOpenHabit = { id -> navController.navigate(HabitDetailRoute(id)) { popUpTo<HomeRoute>() } },
                onSaved = { id ->
                    if (route.habitId != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(HabitDetailRoute(id)) { popUpTo<HomeRoute>() }
                    }
                },
            )
        }
        composable<AddHabitRoute> {
            CatalogScreen(
                pickMode = true,
                onOpenTemplate = { navController.navigate(HabitEditRoute(templateId = it)) },
                onCreateCustom = { navController.navigate(HabitEditRoute()) },
                onOpenHelp = { navController.navigate(HelpRoute) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<ArticleRoute> {
            ArticleScreen(
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate(HabitEditRoute(templateId = it)) },
            )
        }
        composable<HelpRoute> { HelpScreen(onBack = { navController.popBackStack() }) }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() }, onOpenHelp = { navController.navigate(HelpRoute) })
        }
    }
}

@Composable
private fun HomeScreen(navController: NavHostController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { padding ->
        val inner = PaddingValues(bottom = padding.calculateBottomPadding())
        when (Tab.entries[tab]) {
            Tab.TODAY -> TodayScreen(
                onOpenHabit = { navController.navigate(HabitDetailRoute(it)) },
                onAddHabit = { navController.navigate(AddHabitRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenArticle = { navController.navigate(ArticleRoute(it)) },
                onOpenHelp = { navController.navigate(HelpRoute) },
                contentPadding = inner,
            )
            Tab.CALENDAR -> CalendarScreen(
                onOpenDay = { navController.navigate(DayRoute(it.toString())) },
                onOpenHabit = { navController.navigate(HabitDetailRoute(it)) },
                contentPadding = inner,
            )
            Tab.BADGES -> BadgesScreen(contentPadding = inner)
            Tab.DISCOVER -> CatalogScreen(
                pickMode = false,
                onOpenTemplate = { navController.navigate(ArticleRoute(it)) },
                onCreateCustom = { navController.navigate(HabitEditRoute()) },
                onOpenHelp = { navController.navigate(HelpRoute) },
                contentPadding = inner,
            )
        }
    }
}
