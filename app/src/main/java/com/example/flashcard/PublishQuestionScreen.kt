package com.example.flashcard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.net.Uri
import coil.compose.AsyncImage
import com.example.flashcard.CloudinaryHelper
import kotlinx.coroutines.tasks.await


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishQuestionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }

    var sequentialId by remember { mutableStateOf<Int?>(null) }

    var suggestions by remember { mutableStateOf(listOf<String>()) }
    var showDropdown by remember { mutableStateOf(false) }

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> imageUri = uri }

    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { snapshot ->
                    sequentialId = snapshot.getLong("id")?.toInt()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to fetch user ID", Toast.LENGTH_SHORT).show()
                }
        }
    }

    LaunchedEffect(tag) {
        if (tag.isBlank()) {
            suggestions = emptyList()
            showDropdown = false
            return@LaunchedEffect
        }

        val termLower = tag.lowercase().trim()
        val result = db.collection("qna_tag")
            .whereGreaterThanOrEqualTo("name_lowercase", termLower)
            .whereLessThanOrEqualTo("name_lowercase", termLower + "\uf8ff")
            .limit(10)
            .get()
            .await()

        suggestions = result.documents.mapNotNull { it.getString("name") }
        showDropdown = suggestions.isNotEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        TopAppBar(
            title = { Text("Publish Question") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                } else {
                    TextButton(
                        onClick = {
                            if (title.isNotBlank() && body.isNotBlank() && tag.isNotBlank()) {
                                if (sequentialId == null) {
                                    Toast.makeText(context, "User ID not loaded", Toast.LENGTH_SHORT).show()
                                    return@TextButton
                                }

                                isUploading = true

                                fun saveQuestion(imageUrl: String?) {
                                    val tagLower = tag.lowercase().trim()
                                    db.collection("qna_tag")
                                        .whereEqualTo("name_lowercase", tagLower)
                                        .get()
                                        .addOnSuccessListener { snapshot ->
                                            if (snapshot.isEmpty) {
                                                val newTag = hashMapOf(
                                                    "name" to tag,
                                                    "name_lowercase" to tagLower
                                                )
                                                db.collection("qna_tag").add(newTag)
                                            }

                                            val question = hashMapOf(
                                                "title" to title,
                                                "body" to body,
                                                "tag" to tag,
                                                "tag_lowercase" to tagLower,
                                                "owner_id" to sequentialId,
                                                "imageUrl" to imageUrl
                                            )

                                            db.collection("questions")
                                                .add(question)
                                                .addOnSuccessListener {
                                                    isUploading = false
                                                    Toast.makeText(context, "Question Published!", Toast.LENGTH_SHORT).show()
                                                    onBack()
                                                }
                                                .addOnFailureListener { e ->
                                                    isUploading = false
                                                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                        }
                                        .addOnFailureListener { e ->
                                            isUploading = false
                                            Toast.makeText(context, "Failed to check tags: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                }

                                if (imageUri != null) {
                                    CloudinaryHelper.upload(
                                        uri = imageUri!!,
                                        context = context,
                                        onSuccess = { url -> saveQuestion(url) },
                                        onError = { error ->
                                            isUploading = false
                                            Toast.makeText(context, "Image upload failed: $error", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    saveQuestion(null)
                                }

                            } else {
                                Toast.makeText(context, "Fill all fields", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Publish")
                    }
                }
            }
        )

        // Content area with padding
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),   // padding only below app bar
        ) {

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Question Title") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Question Body") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            Spacer(Modifier.height(16.dp))

            Spacer(Modifier.height(16.dp))

            Button(onClick = { 
                imageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                ) 
            }) {
                Text(if (imageUri == null) "Add Image (Optional)" else "Change Image")
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

            Spacer(Modifier.height(16.dp))

            Box {
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("Tag") },
                    modifier = Modifier.fillMaxWidth()
                )

                DropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false },
                    modifier = Modifier
                        .background(Color.White)
                        .heightIn(max = 144.dp)
                ) {
                    suggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion) },
                            onClick = {
                                tag = suggestion
                                showDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (sequentialId == null) {
                Text(
                    text = "Fetching user ID...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text("Your User ID: $sequentialId", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

