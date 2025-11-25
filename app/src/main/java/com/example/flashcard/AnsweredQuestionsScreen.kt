package com.example.flashcard

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnsweredQuestionsScreen(onBack: () -> Unit) {

    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser

    var answeredQuestions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(user?.uid) {
        if (user?.uid == null) {
            isLoading = false
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
            return@LaunchedEffect
        }

        coroutineScope.launch {
            try {
                // 🔥 1️⃣ Fetch user document
                val userDoc = db.collection("users")
                    .document(user.uid)
                    .get()
                    .await()

                // 🔥 2️⃣ Get answered_questions field (list of question IDs)
                val ids = userDoc.get("answered_questions") as? List<String> ?: emptyList()

                if (ids.isEmpty()) {
                    answeredQuestions = emptyList()
                    isLoading = false
                    return@launch
                }

                // 🔥 3️⃣ Fetch each question by ID
                val tempList = mutableListOf<Map<String, Any>>()

                for (id in ids) {
                    val qSnap = db.collection("questions")
                        .document(id)
                        .get()
                        .await()

                    qSnap.data?.let { data ->
                        tempList.add(data + ("id" to id))
                    }
                }

                answeredQuestions = tempList
                isLoading = false

            } catch (e: Exception) {
                isLoading = false
                Toast.makeText(context, "Failed to load answered questions", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
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
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                answeredQuestions.isEmpty() -> {
                    Text(
                        "You haven’t answered any questions yet.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        items(answeredQuestions) { question ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        question["title"]?.toString() ?: "(No Title)",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        question["body"]?.toString() ?: "(No Body)",
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
}
