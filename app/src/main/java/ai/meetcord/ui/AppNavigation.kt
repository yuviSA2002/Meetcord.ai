package ai.meetcord.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.launch

sealed class Screen(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("Home", Icons.Filled.Home)
    object Meetings : Screen("Meetings", Icons.Filled.History)
    object Settings : Screen("Settings", Icons.Filled.Settings)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainPagerScreen(onNavigateToDetail: (Int) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val items = listOf(Screen.Home, Screen.Meetings, Screen.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF000000),
                contentColor = Color.White
            ) {
                items.forEachIndexed { index, screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = pagerState.currentPage == index,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFFFFFFF),
                            unselectedIconColor = Color(0xFF666666),
                            selectedTextColor = Color(0xFFFFFFFF),
                            unselectedTextColor = Color(0xFF666666),
                            indicatorColor = Color(0xFF111111)
                        ),
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(innerPadding)
        ) { page ->
            when (page) {
                0 -> HomeScreen()
                1 -> MeetingsScreen(onNavigateToDetail = onNavigateToDetail)
                2 -> SettingsScreen()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main_pager"
    ) {
        composable("main_pager") {
            MainPagerScreen(
                onNavigateToDetail = { id ->
                    navController.navigate("meeting_detail/$id")
                }
            )
        }
        composable(
            route = "meeting_detail/{meetingId}",
            arguments = listOf(navArgument("meetingId") { type = NavType.IntType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("meetingId") ?: 0
            MeetingDetailScreen(
                meetingId = id,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
