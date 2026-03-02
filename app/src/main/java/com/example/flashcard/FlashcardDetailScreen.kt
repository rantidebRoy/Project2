package com.example.flashcard

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun FlashcardDetailScreen(navController: NavController, topicId: String, flashcardId: String) {
    val db = FirebaseFirestore.getInstance()
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var flashcard by remember { mutableStateOf<Flashcard?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(flashcardId) {
        if (uid == null) return@LaunchedEffect
        db.collection("users")
            .document(uid)
            .collection("topics")
            .document(topicId)
            .collection("flashcards")
            .document(flashcardId)
            .get()
            .addOnSuccessListener { doc ->
                flashcard = Flashcard(
                    id = doc.id,
                    question = doc.getString("question") ?: "",
                    answer = doc.getString("answer") ?: ""
                )
                isLoading = false
            }
            .addOnFailureListener {
                isLoading = false
            }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        flashcard?.let { card ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Q: ${card.question}", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Text("A: ${card.answer}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (uid == null) return@Button
                        db.collection("users")
                            .document(uid)
                            .collection("topics")
                            .document(topicId)
                            .collection("flashcards")
                            .document(flashcardId)
                            .delete()
                            .addOnSuccessListener {
                                Toast.makeText(context, "Flashcard deleted", Toast.LENGTH_SHORT).show()
                                navController.popBackStack() // go back to list
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Flashcard")
                }

                Spacer(Modifier.height(12.dp))
                Button(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
                }
            }
        }
    }
}
