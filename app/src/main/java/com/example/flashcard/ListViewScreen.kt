package com.example.flashcard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@Composable
fun ListViewScreen(navController: NavController, topic: String) {
    var flashcards by remember { mutableStateOf<List<Flashcard>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        flashcards = FirestoreRepository.getFlashcards(topic)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("$topic - Flashcards", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        LazyColumn {
            items(flashcards.size) { i ->
                val card = flashcards[i]
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Q: ${card.question}")
                        Text("A: ${card.answer}")
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    FirestoreRepository.deleteFlashcard(topic, card.id)
                                    flashcards = FirestoreRepository.getFlashcards(topic)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
