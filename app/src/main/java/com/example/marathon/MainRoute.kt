package com.example.marathon

sealed class MainRoute(val path: String) {
    object Run : MainRoute("run")
    object HistoryList : MainRoute("history_list")
    object Settings : MainRoute("settings")
    object RunDetail : MainRoute("run_detail/{sessionId}") {
        fun createRoute(sessionId: String) = "run_detail/$sessionId"
    }
}
