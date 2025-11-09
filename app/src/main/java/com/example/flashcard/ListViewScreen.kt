package com.example.flashcard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class Flashcard(
    val id: String,
    val question: String,
    val answer: String
)

@Composable
fun ListViewScreen(navController: NavController, topicId: String) {
    val db = FirebaseFirestore.getInstance()
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var flashcards by remember { mutableStateOf<List<Flashcard>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Load flashcards from Firestore
    LaunchedEffect(topicId) {
        if (uid == null) return@LaunchedEffect
        db.collection("users")
            .document(uid)
            .collection("topics")
            .document(topicId)
            .collection("flashcards")
            .get()
            .addOnSuccessListener { snapshot ->
                flashcards = snapshot.documents.map { doc ->
                    Flashcard(
                        id = doc.id,
                        question = doc.getString("question") ?: "",
                        answer = doc.getString("answer") ?: ""
                    )
                }
                isLoading = false
            }
            .addOnFailureListener {
                isLoading = false
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Flashcards", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (flashcards.isEmpty()) {
            Text("No flashcards found for this topic.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(flashcards, key = { it.id }) { card ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // Question text
                            Text(
                                text = card.question,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .align(Alignment.CenterStart),
                                style = MaterialTheme.typography.bodyLarge
                            )

                            // Trash icon on top-right
                            IconButton(
                                onClick = {
                                    uid?.let { userId ->
                                        db.collection("users")
                                            .document(userId)
                                            .collection("topics")
                                            .document(topicId)
                                            .collection("flashcards")
                                            .document(card.id)
                                            .delete()
                                            .addOnSuccessListener {
                                                flashcards = flashcards.filter { it.id != card.id }
                                            }
                                    }
                                },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Flashcard",
                                    tint = Color.Red
                                )
                            }

                            // Clickable to navigate to flashcard details
                            Spacer(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable {
                                        navController.navigate("flashcardDetail/$topicId/${card.id}")
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
