package com.example.flashcard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun ShuffleScreen(navController: NavController, topic: String) {
    var flashcards by remember {
        mutableStateOf(listOf(
            "Q1: What is 2+2?" to "A1: 4",
            "Q2: Define gravity" to "A2: Force of attraction"
        ))
    }

    var index by remember { mutableStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }

    if (flashcards.isEmpty()) {
        Text("No flashcards available.")
        return
    }

    val (question, answer) = flashcards[index]

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Topic: $topic", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(32.dp))

        Text(if (showAnswer) answer else question, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))

        if (!showAnswer) {
            Button(onClick = { showAnswer = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Show Answer")
            }
        } else {
            if (index < flashcards.lastIndex) {
                Button(onClick = {
                    showAnswer = false
                    index++
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Next Question")
                }
            } else {
                Button(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                    Text("End Session")
                }
            }
        }
    }
}
