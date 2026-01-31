package com.example.flashcard

data class CheckedEvent(
    val title: String = "",
    val description: String = "",
    val date: String = "",
    val hour: Int = 0,
    val minute: Int = 0,
    val isChecked: Boolean = false,
    val timestamp: Long = 0L,
    val docId: String = ""
)
