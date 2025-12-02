package com.example.flashcard

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnsweredQuestionsScreen(
    onBack: () -> Unit,
    navigateToAnswers: (String) -> Unit
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser

    var answeredQuestions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var questionIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(user?.uid) {
        if (user?.uid == null) {
            isLoading = false
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
            return@LaunchedEffect
        }

        try {
            val userDoc = db.collection("users")
                .document(user.uid)
                .get()
                .await()

            val ids = userDoc.get("answered_questions") as? List<String> ?: emptyList()
            questionIds = ids

            if (ids.isEmpty()) {
                answeredQuestions = emptyList()
                isLoading = false
                return@LaunchedEffect
            }

            // Fetch question data
            val tempList = mutableListOf<Map<String, Any>>()
            for (id in ids) {
                val qSnap = db.collection("questions")
                    .document(id)
                    .get()
                    .await()

                qSnap.data?.let { data ->
                    val questionWithId = data.toMutableMap()
                    questionWithId["id"] = qSnap.id  // ✅ IMPORTANT FIX
                    tempList.add(questionWithId)
                }
            }

            answeredQuestions = tempList
            isLoading = false

        } catch (e: Exception) {
            isLoading = false
            Toast.makeText(context, "Failed to load answered questions", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }



    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text("Answered Questions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                answeredQuestions.isEmpty() -> Text(
                    "You haven’t answered any questions yet.",
                    modifier = Modifier.align(Alignment.Center)
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    itemsIndexed(answeredQuestions) { index, question ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable {
                                    val questionId = question["id"]?.toString()
                                    if (questionId != null) {
                                        navigateToAnswers(questionId)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Invalid question data",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    text = question["title"]?.toString() ?: "(No Title)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = question["body"]?.toString() ?: "(No Body)",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
