package com.example.flashcard

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitAnswerScreen(
    navController: NavController,
    questionId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val userUid = FirebaseAuth.getInstance().currentUser?.uid

    var questionTitle by remember { mutableStateOf<String?>(null) }
    var questionBody by remember { mutableStateOf<String?>(null) }
    var questionTag by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var answerBody by remember { mutableStateOf("") }

    // Fetch the question details
    LaunchedEffect(questionId) {
        db.collection("questions").document(questionId).get()
            .addOnSuccessListener { doc ->
                questionTitle = doc.getString("title")
                questionBody = doc.getString("body")
                questionTag = doc.getString("tag")
                isLoading = false
            }
            .addOnFailureListener {
                isLoading = false
                Toast.makeText(context, "Failed to load question", Toast.LENGTH_SHORT).show()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Submit Answer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (answerBody.isNotBlank() && userUid != null) {
                                db.collection("users").document(userUid).get()
                                    .addOnSuccessListener { userDoc ->
                                        val userId = userDoc.getLong("id")
                                        val answerData = mapOf(
                                            "answer_body" to answerBody.trim(),
                                            "answerer_id" to userId,
                                            "timestamp" to FieldValue.serverTimestamp()
                                        )
                                        db.collection("questions")
                                            .document(questionId)
                                            .collection("answers")
                                            .add(answerData)
                                            .addOnSuccessListener {
                                                Toast.makeText(context, "Answer submitted!", Toast.LENGTH_SHORT).show()
                                                navController.popBackStack() // Go back to search results
                                            }
                                            .addOnFailureListener { e ->
                                                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                            } else {
                                Toast.makeText(context, "Write an answer first", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Submit")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Show question info
                Text(
                    text = questionTitle ?: "No title",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = questionBody ?: "No body",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tag: ${questionTag ?: "N/A"}",
                    style = MaterialTheme.typography.labelMedium
                )

                Spacer(Modifier.height(32.dp))

                OutlinedTextField(
                    value = answerBody,
                    onValueChange = { answerBody = it },
                    label = { Text("Write your answer here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }
    }
}
