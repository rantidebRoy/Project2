package com.example.flashcard

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.net.Uri
import coil.compose.AsyncImage
import com.example.flashcard.CloudinaryHelper
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Close

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
    var questionImageUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var answerBody by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> imageUri = uri }

    // Load question details
    LaunchedEffect(questionId) {
        db.collection("questions").document(questionId).get()
            .addOnSuccessListener { doc ->
                questionTitle = doc.getString("title")
                questionBody = doc.getString("body")
                questionTag = doc.getString("tag")
                questionImageUrl = doc.getString("imageUrl")
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
                    IconButton(onClick = { navController.popBackStack() }) { // Navigate back to previous screen in stack
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                    } else {
                        TextButton(
                            onClick = {
                                if (answerBody.isBlank() || userUid == null) {
                                    Toast.makeText(context, "Write an answer first", Toast.LENGTH_SHORT).show()
                                    return@TextButton
                                }

                                isUploading = true

                                fun saveAnswer(imageUrl: String?) {
                                    db.collection("users").document(userUid).get()
                                        .addOnSuccessListener { userDoc ->
                                            val appUserId = userDoc.getLong("id")

                                            val answerData = hashMapOf(
                                                "answer_body" to answerBody.trim(),
                                                "answerer_id" to appUserId,
                                                "timestamp" to FieldValue.serverTimestamp(),
                                                "imageUrl" to imageUrl
                                            )

                                            db.collection("questions")
                                                .document(questionId)
                                                .collection("answers")
                                                .add(answerData)
                                                .addOnSuccessListener {
                                                    db.collection("users")
                                                        .document(userUid)
                                                        .update("answered_questions", FieldValue.arrayUnion(questionId))

                                                    isLoading = false
                                                    isUploading = false
                                                    Toast.makeText(context, "Answer submitted!", Toast.LENGTH_SHORT).show()
                                                    navController.popBackStack()
                                                }
                                                .addOnFailureListener { e ->
                                                    isLoading = false
                                                    isUploading = false
                                                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                        }
                                        .addOnFailureListener {
                                            isUploading = false
                                            Toast.makeText(context, "Failed to get user: ${it.message}", Toast.LENGTH_SHORT).show()
                                        }
                                }

                                if (imageUri != null) {
                                    CloudinaryHelper.upload(
                                        uri = imageUri!!,
                                        context = context,
                                        onSuccess = { url -> saveAnswer(url) },
                                        onError = { error ->
                                            isUploading = false
                                            Toast.makeText(context, "Image upload failed: $error", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    saveAnswer(null)
                                }
                            }
                        ) {
                            Text("Submit")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        var viewingImage by remember { mutableStateOf<String?>(null) }

        if (viewingImage != null) {
            Dialog(onDismissRequest = { viewingImage = null }) {
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                val state = rememberTransformableState { zoomChange, panChange, _ ->
                    scale = (scale * zoomChange).coerceAtLeast(1f)
                    offset += panChange
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable { viewingImage = null }
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
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
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Question Title (2 lines ~ 60dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = questionTitle ?: "No title",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Spacer(Modifier.height(8.dp))

                // Question Body (4 lines ~ 100dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = questionBody ?: "No body",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (!questionImageUrl.isNullOrBlank()) {
                    Button(onClick = { viewingImage = questionImageUrl }) {
                        Text("View Question Image")
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Answer Input
                OutlinedTextField(
                    value = answerBody,
                    onValueChange = { answerBody = it },
                    label = { Text("Write your answer here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                Spacer(Modifier.height(16.dp))

                Button(onClick = {
                    imageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) {
                    Text(if (imageUri == null) "Add Answer Image" else "Change Answer Image")
                }

                if (imageUri != null) {
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Selected Image",
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
            }
        }
    }
}
