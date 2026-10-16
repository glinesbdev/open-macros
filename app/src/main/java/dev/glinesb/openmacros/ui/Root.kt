package dev.glinesb.openmacros.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import dev.glinesb.openmacros.R
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.glinesb.openmacros.container
import dev.glinesb.openmacros.domain.Meal
import dev.glinesb.openmacros.ui.food.CustomFoodScreen
import dev.glinesb.openmacros.ui.food.FoodDetailScreen
import dev.glinesb.openmacros.ui.food.QuickAddScreen
import dev.glinesb.openmacros.ui.food.SearchScreen
import dev.glinesb.openmacros.ui.goals.ProfileScreen
import dev.glinesb.openmacros.ui.photo.PhotoLogScreen
import dev.glinesb.openmacros.ui.scan.ScanScreen
import dev.glinesb.openmacros.ui.settings.SettingsScreen
import dev.glinesb.openmacros.ui.today.TodayScreen
import dev.glinesb.openmacros.ui.training.ExerciseDetailScreen
import dev.glinesb.openmacros.ui.training.RecommenderScreen
import dev.glinesb.openmacros.ui.training.RoutineEditorScreen
import dev.glinesb.openmacros.ui.training.TrainingScreen
import dev.glinesb.openmacros.ui.training.WorkoutDetailScreen
import dev.glinesb.openmacros.ui.training.WorkoutScreen
import dev.glinesb.openmacros.ui.weight.WeightScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector? = null, val iconRes: Int = 0)

private val tabs = listOf(
    Tab(Routes.TODAY, "Diary", iconRes = R.drawable.ic_today),
    Tab(Routes.WEIGHT, "Weight", iconRes = R.drawable.ic_monitor_weight),
    Tab(Routes.TRAINING, "Training", iconRes = R.drawable.ic_fitness_center),
    Tab(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
)

object Routes {
    const val ONBOARDING = "onboarding"
    const val GOALS = "goals"
    const val TODAY = "today"
    const val WEIGHT = "weight"
    const val SETTINGS = "settings"
    const val TRAINING = "training"
    const val RECOMMEND = "training/recommend"
    fun routine(id: Long? = null) = "training/routine?id=${id ?: -1}"
    fun workout(id: Long) = "training/workout/$id"
    fun workoutDetail(id: Long) = "training/history/$id"
    fun exercise(id: Long) = "training/exercise/$id"
    fun add(day: Long, meal: Meal) = "add/$day/${meal.name}"
    fun scan(day: Long, meal: Meal) = "scan/$day/${meal.name}"
    fun quickAdd(day: Long, meal: Meal) = "quickadd/$day/${meal.name}"
    fun photo(day: Long, meal: Meal) = "photo/$day/${meal.name}"
    fun food(id: Long, day: Long, meal: Meal, entryId: Long = -1) = "food/$id?day=$day&meal=${meal.name}&entryId=$entryId"
    fun custom(day: Long = -1, meal: Meal? = null, barcode: String? = null, foodId: Long = -1) =
        "custom?day=$day&meal=${meal?.name.orEmpty()}&barcode=${barcode.orEmpty()}&foodId=$foodId"
}

@Composable
fun OpenMacrosRoot() {
    val container = LocalContext.current.container
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    // Outer scaffold only owns the bottom bar; each screen's own Scaffold/TopAppBar handles the
    // status bar. Insets used by the bar are consumed so inner screens don't pad twice.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = { nav.switchTab(tab.route) },
                        icon = { Icon(tab.icon?.let { rememberVectorPainter(it) } ?: painterResource(tab.iconRes), contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.TODAY, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable(Routes.ONBOARDING) {
                ProfileScreen(isOnboarding = true, onDone = { nav.popBackStack() }, onBack = {})
            }
            composable(Routes.GOALS) { ProfileScreen(isOnboarding = false, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() }) }
            composable(Routes.TODAY) {
                // Today is always the graph's start (keeps tab back-stack handling simple); first
                // launch pushes onboarding on top of it.
                LaunchedEffect(Unit) {
                    if (container.settings.state.value.profile == null) nav.navigate(Routes.ONBOARDING) { launchSingleTop = true }
                }
                TodayScreen(
                    onAddFood = { day, meal -> nav.navigate(Routes.add(day, meal)) },
                    onEditEntry = { e -> e.foodId?.let { nav.navigate(Routes.food(it, e.epochDay, Meal.valueOf(e.meal), e.id)) } },
                )
            }
            composable(Routes.WEIGHT) { WeightScreen() }
            composable(Routes.TRAINING) {
                TrainingScreen(
                    onOpenWorkout = { nav.navigate(Routes.workout(it)) },
                    onOpenWorkoutDetail = { nav.navigate(Routes.workoutDetail(it)) },
                    onEditRoutine = { nav.navigate(Routes.routine(it)) },
                    onRecommend = { nav.navigate(Routes.RECOMMEND) },
                    onOpenExercise = { nav.navigate(Routes.exercise(it)) },
                )
            }
            composable(Routes.RECOMMEND) {
                RecommenderScreen(
                    onBack = { nav.popBackStack() },
                    onCreated = { id -> nav.navigate(Routes.routine(id)) { popUpTo(Routes.RECOMMEND) { inclusive = true } } },
                )
            }
            composable("training/routine?id={id}", listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { e ->
                RoutineEditorScreen(
                    routineId = e.arguments!!.getLong("id").takeIf { it > 0 },
                    onBack = { nav.popBackStack() },
                    onSaved = { nav.popBackStack() },
                )
            }
            composable("training/workout/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                WorkoutScreen(
                    workoutId = e.arguments!!.getLong("id"),
                    onBack = { nav.popBackStack() },
                    onFinished = { id -> nav.navigate(Routes.workoutDetail(id)) { popUpTo("training/workout/{id}") { inclusive = true } } },
                    onOpenExercise = { nav.navigate(Routes.exercise(it)) },
                )
            }
            composable("training/history/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                WorkoutDetailScreen(
                    workoutId = e.arguments!!.getLong("id"),
                    onBack = { nav.popBackStack() },
                    onOpenExercise = { nav.navigate(Routes.exercise(it)) },
                )
            }
            composable("training/exercise/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                ExerciseDetailScreen(
                    exerciseId = e.arguments!!.getLong("id"),
                    onBack = { nav.popBackStack() },
                    onOpenWorkout = { nav.navigate(Routes.workoutDetail(it)) },
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen(onEditProfile = { nav.navigate(Routes.GOALS) }) }

            val dayMeal = listOf(
                navArgument("day") { type = NavType.LongType },
                navArgument("meal") { type = NavType.StringType },
            )
            composable("add/{day}/{meal}", dayMeal) { e ->
                val day = e.arguments!!.getLong("day"); val meal = Meal.valueOf(e.arguments!!.getString("meal")!!)
                SearchScreen(
                    meal = meal,
                    onBack = { nav.popBackStack() },
                    onOpenFood = { id -> nav.navigate(Routes.food(id, day, meal)) },
                    onScan = { nav.navigate(Routes.scan(day, meal)) },
                    onPhoto = { nav.navigate(Routes.photo(day, meal)) },
                    onOpenSettings = { nav.switchTab(Routes.SETTINGS) },
                    onQuickAdd = { nav.navigate(Routes.quickAdd(day, meal)) },
                    onCreateFood = { nav.navigate(Routes.custom(day, meal)) },
                    onEditFood = { id -> nav.navigate(Routes.custom(foodId = id)) },
                )
            }
            composable("scan/{day}/{meal}", dayMeal) { e ->
                val day = e.arguments!!.getLong("day"); val meal = Meal.valueOf(e.arguments!!.getString("meal")!!)
                ScanScreen(
                    onBack = { nav.popBackStack() },
                    onFound = { id -> nav.navigate(Routes.food(id, day, meal)) { popUpTo("scan/{day}/{meal}") { inclusive = true } } },
                    onCreate = { code -> nav.navigate(Routes.custom(day, meal, code)) { popUpTo("scan/{day}/{meal}") { inclusive = true } } },
                )
            }
            composable("photo/{day}/{meal}", dayMeal) { e ->
                PhotoLogScreen(
                    day = e.arguments!!.getLong("day"),
                    initialMeal = Meal.valueOf(e.arguments!!.getString("meal")!!),
                    onBack = { nav.popBackStack() },
                    onLogged = { nav.backToToday() },
                )
            }
            composable("quickadd/{day}/{meal}", dayMeal) { e ->
                QuickAddScreen(
                    day = e.arguments!!.getLong("day"),
                    initialMeal = Meal.valueOf(e.arguments!!.getString("meal")!!),
                    onBack = { nav.popBackStack() },
                    onSaved = { nav.backToToday() },
                )
            }
            composable(
                "food/{foodId}?day={day}&meal={meal}&entryId={entryId}",
                listOf(
                    navArgument("foodId") { type = NavType.LongType },
                    navArgument("day") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("meal") { type = NavType.StringType; defaultValue = Meal.SNACKS.name },
                    navArgument("entryId") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { e ->
                val a = e.arguments!!
                FoodDetailScreen(
                    foodId = a.getLong("foodId"),
                    day = a.getLong("day"),
                    initialMeal = Meal.valueOf(a.getString("meal")!!),
                    entryId = a.getLong("entryId").takeIf { it > 0 },
                    onBack = { nav.popBackStack() },
                    onSaved = { nav.backToToday() },
                    onEditFood = { id -> nav.navigate(Routes.custom(foodId = id)) },
                )
            }
            composable(
                "custom?day={day}&meal={meal}&barcode={barcode}&foodId={foodId}",
                listOf(
                    navArgument("day") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("meal") { type = NavType.StringType; defaultValue = "" },
                    navArgument("barcode") { type = NavType.StringType; defaultValue = "" },
                    navArgument("foodId") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { e ->
                val a = e.arguments!!
                val day = a.getLong("day")
                val meal = a.getString("meal")?.takeIf { it.isNotEmpty() }?.let { Meal.valueOf(it) }
                CustomFoodScreen(
                    foodId = a.getLong("foodId").takeIf { it > 0 },
                    initialBarcode = a.getString("barcode").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onSaved = { id ->
                        if (day >= 0 && meal != null) {
                            nav.navigate(Routes.food(id, day, meal)) {
                                popUpTo("custom?day={day}&meal={meal}&barcode={barcode}&foodId={foodId}") { inclusive = true }
                            }
                        } else nav.popBackStack()
                    },
                    onDeleted = { if (!nav.popBackStack("add/{day}/{meal}", inclusive = false)) nav.backToToday() },
                )
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private fun NavHostController.backToToday() {
    if (!popBackStack(Routes.TODAY, inclusive = false)) navigate(Routes.TODAY)
}
