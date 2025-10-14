package com.example.marathon

sealed class MainRoute(val path : String) {

    object History: MainRoute("history")
    object Recommend: MainRoute("recommend")
    object Friends: MainRoute("friends")
    object MyPage: MainRoute("mypage")
}
