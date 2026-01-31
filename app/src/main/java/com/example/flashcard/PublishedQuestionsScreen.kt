package com.example.flashcard

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Close
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishedQuestionsScreen(
    onBack: () -> Unit,
    onOpenAnswers: (String) -> Unit    // ⭐ ADDED
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser
    val coroutineScope = rememberCoroutineScope()

    var userId by remember { mutableStateOf<Long?>(null) }
    var publishedQuestions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedQuestion by remember { mutableStateOf<Map<String, Any>?>(null) }
    var viewingImage by remember { mutableStateOf<String?>(null) }

    // --- Fetch user's sequential id ---
    LaunchedEffect(user?.uid) {
        val uid = user?.uid
        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { snapshot ->
                    userId = snapshot.getLong("id")
                    if (userId != null) {
                        db.collection("questions")
                            .whereEqualTo("owner_id", userId)
                            .get()
                            .addOnSuccessListener { querySnapshot ->
                                publishedQuestions = querySnapshot.documents.mapNotNull {
                                    it.data?.plus("docId" to it.id)
                                }
                                isLoading = false
                            }
                            .addOnFailureListener {
                                isLoading = false
                                Toast.makeText(context, "Failed to load questions", Toast.LENGTH_SHORT).show()
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
                title = { Text("Published Questions") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedQuestion != null) {
                            selectedQuestion = null
                        } else {
                            onBack()
                        }
                    }) {
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

            when {
                selectedQuestion != null -> {
                    val q = selectedQuestion!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Title
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = q["title"]?.toString() ?: "(No Title)",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Spacer(Modifier.height(16.dp))

                        // Body
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = q["body"]?.toString() ?: "(No Body)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(Modifier.height(16.dp))

                        // Image
                        val imgUrl = q["imageUrl"]?.toString()
                        if (!imgUrl.isNullOrBlank()) {
                            Button(
                                onClick = { viewingImage = imgUrl },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Show Image")
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        // Previous Answers
                        Button(
                            onClick = { onOpenAnswers(q["docId"].toString()) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Answers")
                        }
                    }
                }

                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                publishedQuestions.isEmpty() -> {
                    Text(
                        text = "You haven't published any questions yet.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        items(
                            items = publishedQuestions,
                            key = { it["docId"].toString() }
                        ) { question ->

                            val docId = question["docId"].toString()

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clickable {
                                        selectedQuestion = question
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {

                                        Column {
                                            Text(
                                                text = question["title"]?.toString() ?: "(No Title)",
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = question["body"]?.toString() ?: "(No Body)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 4,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Tag: ${question["tag"] ?: "N/A"}",
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    try {
                                                        // Delete all answers first
                                                        val answersSnapshot = db.collection("questions")
                                                            .document(docId)
                                                            .collection("answers")
                                                            .get()
                                                            .await()

                                                        for (answerDoc in answersSnapshot.documents) {
                                                            db.collection("questions")
                                                                .document(docId)
                                                                .collection("answers")
                                                                .document(answerDoc.id)
                                                                .delete()
                                                                .await()
                                                        }

                                                        // Delete question
                                                        db.collection("questions").document(docId).delete().await()

                                                        publishedQuestions =
                                                            publishedQuestions.filter { it["docId"] != docId }

                                                        Toast.makeText(context, "Question deleted", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Question")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
