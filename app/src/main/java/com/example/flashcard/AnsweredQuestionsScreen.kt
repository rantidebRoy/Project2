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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnsweredQuestionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser
    var userId by remember { mutableStateOf<Long?>(null) }
    var answeredList by remember { mutableStateOf<List<Pair<Map<String, Any>, Map<String, Any>>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Step 1: Get user sequential ID
    LaunchedEffect(user?.uid) {
        val uid = user?.uid
        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { snapshot ->
                    val fetchedId = snapshot.getLong("id")
                    if (fetchedId != null) {
                        userId = fetchedId
                        // Step 2: Fetch answers by this user
                        db.collection("answers")
                            .whereEqualTo("owner_id", fetchedId)
                            .get()
                            .addOnSuccessListener { answerSnapshot ->
                                val tempList = mutableListOf<Pair<Map<String, Any>, Map<String, Any>>>()
                                val answers = answerSnapshot.documents.mapNotNull { it.data }

                                if (answers.isEmpty()) {
                                    isLoading = false
                                    return@addOnSuccessListener
                                }

                                // Step 3: For each answer, fetch corresponding question
                                var remaining = answers.size
                                for (answer in answers) {
                                    val questionId = answer["question_id"]?.toString()
                                    if (questionId != null) {
                                        db.collection("questions")
                                            .document(questionId)
                                            .get()
                                            .addOnSuccessListener { questionDoc ->
                                                val question = questionDoc.data
                                                if (question != null) {
                                                    tempList.add(question to answer)
                                                }
                                                remaining--
                                                if (remaining == 0) {
                                                    answeredList = tempList
                                                    isLoading = false
                                                }
                                            }
                                            .addOnFailureListener {
                                                remaining--
                                                if (remaining == 0) {
                                                    answeredList = tempList
                                                    isLoading = false
                                                }
                                            }
                                    } else {
                                        remaining--
                                        if (remaining == 0) {
                                            answeredList = tempList
                                            isLoading = false
                                        }
                                    }
                                }
                            }
                            .addOnFailureListener {
                                isLoading = false
                                Toast.makeText(context, "Failed to load answers", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        isLoading = false
                        Toast.makeText(context, "User ID not found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    isLoading = false
                    Toast.makeText(context, "Failed to fetch user info", Toast.LENGTH_SHORT).show()
                }
        } else {
            isLoading = false
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
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
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                answeredList.isEmpty() -> {
                    Text(
                        text = "You haven’t answered any questions yet.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        items(answeredList) { (question, answer) ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = question["title"]?.toString() ?: "(No Title)",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = question["body"]?.toString() ?: "(No Description)",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Your Answer:",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = answer["body"]?.toString() ?: "(No Answer Provided)",
                                        style = MaterialTheme.typography.bodyLarge
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


