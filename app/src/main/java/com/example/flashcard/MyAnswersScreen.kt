package com.example.flashcard

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
fun MyAnswersScreen(
    questionId: String,
    onBack: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var myAnswers by remember { mutableStateOf<List<Pair<String, Map<String, Any>>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    var userFirestoreId by remember { mutableStateOf<String?>(null) }

    // --- Load current user's Firestore id safely ---
    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (uid.isNullOrEmpty()) {
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
            loading = false
            return@LaunchedEffect
        }

        try {
            val userDocRef = db.collection("users").document(uid)
            val userDoc = userDocRef.get().await()
            if (userDoc.exists()) {
                userFirestoreId = userDoc.getString("id")
                if (userFirestoreId.isNullOrEmpty()) {
                    Toast.makeText(context, "User ID not found in Firestore", Toast.LENGTH_SHORT).show()
                    loading = false
                }
            } else {
                Toast.makeText(context, "User document not found", Toast.LENGTH_SHORT).show()
                loading = false
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to fetch user ID", Toast.LENGTH_SHORT).show()
            loading = false
        }
    }

    // --- Load answers only after userFirestoreId is available ---
    LaunchedEffect(userFirestoreId) {
        if (userFirestoreId.isNullOrEmpty()) return@LaunchedEffect
        coroutineScope.launch {
            try {
                val snapshot = db.collection("questions")
                    .document(questionId)
                    .collection("answers")
                    .get()
                    .await()

                myAnswers = snapshot.documents
                    .filter { it.getString("answerer_id") == userFirestoreId }
                    .map { it.id to it.data!! }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load answers", Toast.LENGTH_SHORT).show()
            } finally {
                loading = false
            }
        }
    }

    // --- Delete answer ---
    fun deleteAnswer(answerDocId: String) {
        coroutineScope.launch {
            try {
                db.collection("questions")
                    .document(questionId)
                    .collection("answers")
                    .document(answerDocId)
                    .delete()
                    .await()

                myAnswers = myAnswers.filterNot { it.first == answerDocId }
                Toast.makeText(context, "Answer deleted", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to delete answer", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Answers") },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (myAnswers.isEmpty()) {
                Text(
                    "You have not answered this question.",
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                LazyColumn {
                    items(myAnswers) { (docId, ans) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        ans["answer_body"]?.toString() ?: "(Empty)",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                IconButton(onClick = { deleteAnswer(docId) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete Answer",
                                        tint = MaterialTheme.colorScheme.error
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
