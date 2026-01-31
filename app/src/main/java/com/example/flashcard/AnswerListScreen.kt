package com.example.flashcard


import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnswerListScreen(
    questionId: String,
    navController: NavController,
    onBack: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    var question by remember { mutableStateOf<Map<String, Any>?>(null) }
    var viewingImage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(questionId) {
        try {
            val doc = db.collection("questions").document(questionId).get().await()
            question = doc.data
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Question Details") },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (question == null) {
                Text("Question not found.", modifier = Modifier.align(Alignment.Center))
            } else {
                val q = question!!
                Column(
                    modifier = Modifier.fillMaxSize(),
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
                            .height(100.dp) // Approx 4 lines
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

                    // Tag
                    Text(
                        text = "Tag: ${q["tag"] ?: "N/A"}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(Modifier.height(16.dp))

                    // Image Button
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

                    Spacer(modifier = Modifier.weight(1f))

                    // Existing Buttons
                    Button(
                        onClick = { navController.navigate("submit_answer_screen/$questionId") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Answer the Question")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { navController.navigate("other_answers_screen/$questionId") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Answers") // Updated label based on recent context
                    }
                }
            }
        }
    }
}
