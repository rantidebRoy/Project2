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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.Close

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherAnswersListScreen(
    questionId: String,
    onBack: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var otherAnswers by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedAnswer by remember { mutableStateOf<Map<String, Any>?>(null) }
    var viewingImage by remember { mutableStateOf<String?>(null) }

    // Logged-in user's UID
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // Logged-in user's sequential ID
    var mySequentialId by remember { mutableStateOf<Int?>(null) }

    // Fetch CURRENT USER'S sequential ID
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { snapshot ->
                    mySequentialId = snapshot.getLong("id")?.toInt()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to fetch user ID", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Fetch all answers
    LaunchedEffect(questionId) {
        coroutineScope.launch {
            try {
                val snapshot = db.collection("questions")
                    .document(questionId)
                    .collection("answers")
                    .get()
                    .await()

                otherAnswers = snapshot.documents.mapNotNull { d ->
                    d.data?.plus("id" to d.id) // ⭐ include Firestore doc ID ⭐
                }

            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load answers", Toast.LENGTH_SHORT).show()
            } finally {
                loading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Answers") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedAnswer != null) selectedAnswer = null else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->



        if (viewingImage != null) {
            Dialog(onDismissRequest = { viewingImage = null }) {
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                val state = rememberTransformableState { zoomChange, panChange, _ ->
                    scale = (scale * zoomChange).coerceAtLeast(1f)
                    offset += panChange
                }
                Box(Modifier.fillMaxSize().clickable { viewingImage = null }) {
                    Box(
                        Modifier.fillMaxSize()
                            .transformable(state = state)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = viewingImage,
                            contentDescription = "Full Image",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    IconButton(
                        onClick = { viewingImage = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }

        if (selectedAnswer != null) {
            val ans = selectedAnswer!!
            val answererId = (ans["answerer_id"] as? Long)?.toInt()
                ?: ans["answerer_id"]?.toString()?.toIntOrNull()
            val displayName = if (answererId != null && answererId == mySequentialId) "me" else answererId?.toString() ?: "Unknown"

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                Text("Answer by: $displayName", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(ans["answer_body"]?.toString() ?: "(Empty)")
                }
                Spacer(Modifier.height(16.dp))

                val imgUrl = ans["imageUrl"]?.toString()
                if (!imgUrl.isNullOrBlank()) {
                    Button(
                        onClick = { viewingImage = imgUrl },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Show Image")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Click an option to view to answer in detail",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .align(Alignment.CenterHorizontally)
                )

            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (otherAnswers.isEmpty()) {
                Text("No answers yet.", modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {

                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(otherAnswers) { ans ->

                        val answererId = (ans["answerer_id"] as? Long)?.toInt()
                            ?: ans["answerer_id"]?.toString()?.toIntOrNull()

                        val answerId = ans["id"]?.toString() ?: ""

                        val isMine = answererId != null && answererId == mySequentialId

                        val displayName = if (isMine) "me" else answererId?.toString() ?: "Unknown"

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .clickable { selectedAnswer = ans }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Answer by: $displayName",
                                        style = MaterialTheme.typography.titleSmall
                                    )

                                    // ⭐ SHOW DELETE ONLY FOR MY OWN ANSWERS ⭐
                                    if (isMine) {
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    try {
                                                        db.collection("questions")
                                                            .document(questionId)
                                                            .collection("answers")
                                                            .document(answerId)
                                                            .delete()
                                                            .await()

                                                        // Update UI instantly
                                                        otherAnswers =
                                                            otherAnswers.filter { it["id"] != answerId }

                                                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color.Black
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = ans["answer_body"]?.toString() ?: "(Empty)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // ⭐ SHOW YOUR SEQUENTIAL ID ⭐
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Your ID: ${mySequentialId ?: "Loading..."}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            }
        }
    }
}
