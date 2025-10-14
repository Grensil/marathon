package com.example.marathon

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.history.HistoryScreen
import com.example.recommend.RecommendScreen

@Composable
fun MainScreen() {
    val navController = rememberNavController()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(modifier = Modifier.height(60.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            navController.navigate(MainRoute.History.path)
                        }, contentAlignment = Alignment.Center
                ) {
                    Image(painter = painterResource(R.drawable.icon_run), contentDescription = null)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            navController.navigate(MainRoute.Recommend.path)
                        }, contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.icon_recommend),
                        contentDescription = null
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            navController.navigate(MainRoute.Friends.path)
                        }, contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.icon_friends),
                        contentDescription = null
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            navController.navigate(MainRoute.MyPage.path)
                        }, contentAlignment = Alignment.Center
                ) {
                    Image(painter = painterResource(R.drawable.icon_my), contentDescription = null)
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(
                    bottom = innerPadding.calculateBottomPadding(),
                    top = innerPadding.calculateTopPadding()
                )
        ) {
            MainNavGraph(
                navController = navController
            )
        }
    }
}


@Composable
fun MainNavGraph(navController: NavHostController) {

    NavHost(
        navController = navController, startDestination = MainRoute.History
    ) {
        composable(route = MainRoute.History.path) {
            HistoryScreen()
        }

        composable(route = MainRoute.Recommend.path) {
            RecommendScreen()
        }
    }
}