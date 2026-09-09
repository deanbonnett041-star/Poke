package com.aicardgrader.app.nav

object Routes {
    const val HOME = "home"
    const val CAPTURE_FRONT = "capture_front"
    const val CAPTURE_BACK = "capture_back"
    const val ANALYZING = "analyzing"
    const val RESULTS = "results"
    const val HISTORY = "history"
    const val HISTORY_DETAIL_PATTERN = "history_detail/{id}"
    fun historyDetail(id: Long) = "history_detail/$id"
    const val ABOUT = "about"
    const val SETTINGS = "settings"
}
