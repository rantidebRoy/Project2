package com.example.flashcard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlin.random.Random

// --- Data Models ---
data class Topic(
    val id: String = "",
    val name: String = ""
)

data class Flashcard(
    val id: String = "",
    val question: String = "",
    val answer: String = "",
    val imageUrl: String = ""
)

// --- ViewModel ---
class FlashcardViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance().reference

    var topics by mutableStateOf<List<Topic>>(emptyList())
        private set

    var flashcards by mutableStateOf<List<Flashcard>>(emptyList())
        private set

    var currentTopic by mutableStateOf<Topic?>(null)
        private set

    fun loadTopics() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("topics")
            .get()
            .addOnSuccessListener { result ->
                topics = result.documents.map { doc ->
                    Topic(doc.id, doc.getString("name") ?: "")
                }
            }
    }

    fun addTopic(name: String, onDone: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val topic = hashMapOf("name" to name)
        db.collection("users").document(uid).collection("topics")
            .add(topic)
            .addOnSuccessListener {
                loadTopics()
                onDone()
            }
    }

    /**
     * Loads flashcards for the given topic and sets currentTopic.
     */
    fun loadFlashcards(topic: Topic) {
        val uid = auth.currentUser?.uid ?: return
        currentTopic = topic
        db.collection("users").document(uid)
            .collection("topics").document(topic.id)
            .collection("flashcards")
            .get()
            .addOnSuccessListener { result ->
                flashcards = result.documents.map { doc ->
                    Flashcard(
                        id = doc.id,
                        question = doc.getString("question") ?: "",
                        answer = doc.getString("answer") ?: "",
                        imageUrl = doc.getString("imageUrl") ?: ""
                    )
                }
            }
    }

    /**
     * Adds a flashcard. If imageUri != null, upload to Firebase Storage first,
     * then save the document with the image URL.
     */
    fun addFlashcard(question: String, answer: String, imageUri: Uri?, onDone: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val topic = currentTopic ?: return

        if (imageUri != null) {
            // Path: flashcards/{uid}/{topicId}/{timestamp}.jpg
            val imageRef = storage.child("flashcards/$uid/${topic.id}/${System.currentTimeMillis()}.jpg")

            val uploadTask = imageRef.putFile(imageUri)
            uploadTask
                .addOnSuccessListener { taskSnapshot ->
                    // Get download URL
                    imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        saveFlashcardToFirestore(question, answer, downloadUri.toString(), topic, onDone)
                    }.addOnFailureListener { e ->
                        // If getting URL fails, fallback to saving without image or inform user.
                        saveFlashcardToFirestore(question, answer, "", topic, onDone)
                    }
                }
                .addOnFailureListener {
                    // Upload failed: save without image (or you may choose to surface error)
                    saveFlashcardToFirestore(question, answer, "", topic, onDone)
                }
        } else {
            // No image - save directly
            saveFlashcardToFirestore(question, answer, "", topic, onDone)
        }
    }

    private fun saveFlashcardToFirestore(question: String, answer: String, imageUrl: String, topic: Topic, onDone: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return

        val flashcard = hashMapOf(
            "question" to question,
            "answer" to answer,
            "imageUrl" to imageUrl
        )

        db.collection("users").document(uid)
            .collection("topics").document(topic.id)
            .collection("flashcards")
            .add(flashcard)
            .addOnSuccessListener {
                loadFlashcards(topic)
                onDone()
            }
    }

    /**
     * Deletes both the Firestore document and the image from Storage (if present).
     */
    fun deleteFlashcard(flashcardId: String, imageUrl: String = "") {
        val uid = auth.currentUser?.uid ?: return
        val topic = currentTopic ?: return

        val docRef = db.collection("users").document(uid)
            .collection("topics").document(topic.id)
            .collection("flashcards").document(flashcardId)

        if (imageUrl.isNotBlank()) {
            // Delete storage file first (best-effort), then delete document
            try {
                val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl)
                storageRef.delete().addOnCompleteListener {
                    // Regardless of success/failure of delete in storage, remove document
                    docRef.delete().addOnSuccessListener {
                        loadFlashcards(topic)
                    }
                }.addOnFailureListener {
                    // If storage delete fails, still attempt to delete doc
                    docRef.delete().addOnSuccessListener {
                        loadFlashcards(topic)
                    }
                }
            } catch (e: IllegalArgumentException) {
                // Malformed URL or non-storage URL: just delete the doc
                docRef.delete().addOnSuccessListener {
                    loadFlashcards(topic)
                }
            }
        } else {
            // No image - just delete doc
            docRef.delete().addOnSuccessListener {
                loadFlashcards(topic)
            }
        }
    }
}

// --- FlashcardScreen Composable ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(viewModel: FlashcardViewModel, onBack: () -> Unit) {
    var currentView by remember { mutableStateOf("topics") }
    var shuffledFlashcards by remember { mutableStateOf<List<Flashcard>>(emptyList()) }
    var currentShuffleIndex by remember { mutableStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentView) {
                            "topics" -> "Topics"
                            "addTopic" -> "Add Topic"
                            "topicOptions" -> viewModel.currentTopic?.name ?: ""
                            "shuffle" -> viewModel.currentTopic?.name ?: ""
                            "list" -> viewModel.currentTopic?.name ?: ""
                            "addFlashcard" -> "Add Flashcard"
                            else -> ""
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (currentView) {
                            "topics" -> onBack()
                            "addTopic" -> currentView = "topics"
                            "topicOptions" -> currentView = "topics"
                            "shuffle", "list" -> currentView = "topicOptions"
                            "addFlashcard" -> currentView = "list"
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (currentView) {
                // --- Topic List ---
                "topics" -> TopicListScreen(
                    viewModel = viewModel,
                    onTopicSelected = {
                        viewModel.loadFlashcards(it)
                        currentView = "topicOptions"
                    },
                    onAddTopic = { currentView = "addTopic" }
                )

                // --- Add Topic ---
                "addTopic" -> AddTopicScreen(
                    onAdd = { name -> viewModel.addTopic(name) { currentView = "topics" } },
                    onCancel = { currentView = "topics" }
                )

                // --- Topic Options ---
                "topicOptions" -> TopicOptionsScreen(
                    onSelectShuffle = {
                        shuffledFlashcards = viewModel.flashcards.shuffled(Random(System.currentTimeMillis()))
                        currentShuffleIndex = 0
                        showAnswer = false
                        currentView = "shuffle"
                    },
                    onSelectList = { currentView = "list" },
                    onBack = { currentView = "topics" }
                )

                // --- Shuffle Flashcards ---
                "shuffle" -> {
                    if (shuffledFlashcards.isEmpty()) {
                        Text("No flashcards in this topic.", Modifier.padding(24.dp))
                    } else {
                        val currentCard = shuffledFlashcards.getOrNull(currentShuffleIndex)
                        currentCard?.let { card ->
                            ShuffleFlashcardScreen(
                                flashcard = card,
                                showAnswer = showAnswer,
                                onShowAnswer = { showAnswer = true },
                                onNext = {
                                    currentShuffleIndex++
                                    showAnswer = false
                                    if (currentShuffleIndex >= shuffledFlashcards.size) {
                                        currentView = "topicOptions"
                                    }
                                },
                                onEndSession = { currentView = "topicOptions" }
                            )
                        }
                    }
                }

                // --- List View ---
                "list" -> FlashcardListWithDeleteScreen(
                    viewModel = viewModel,
                    onBack = { currentView = "topicOptions" },
                    onAddFlashcard = { currentView = "addFlashcard" }
                )

                // --- Add Flashcard ---
                "addFlashcard" -> AddFlashcardScreen(
                    viewModel = viewModel,
                    onDone = { currentView = "list" },
                    onCancel = { currentView = "list" }
                )
            }
        }
    }
}

// --- Topic List & Add Topic Screens ---
@Composable
fun TopicListScreen(viewModel: FlashcardViewModel, onTopicSelected: (Topic) -> Unit, onAddTopic: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.loadTopics() }
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Your Topics", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (viewModel.topics.isEmpty()) Text("No topics yet.")
        else viewModel.topics.forEach { topic ->
            Card(modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onTopicSelected(topic) },
                elevation = CardDefaults.cardElevation(4.dp)) {
                Box(Modifier.padding(16.dp)) { Text(topic.name, fontSize = 20.sp) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddTopic, modifier = Modifier.fillMaxWidth()) { Text("Add New Topic") }
    }
}

@Composable
fun AddTopicScreen(onAdd: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Add New Topic", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Topic Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onAdd(name) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save Topic") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

// --- Topic Options ---
@Composable
fun TopicOptionsScreen(onSelectShuffle: () -> Unit, onSelectList: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Button(onClick = onSelectShuffle, modifier = Modifier.fillMaxWidth()) { Text("Shuffle All") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSelectList, modifier = Modifier.fillMaxWidth()) { Text("List View") }
        Spacer(Modifier.height(12.dp))
    }
}

// --- Shuffle Flashcard Screen ---
@Composable
fun ShuffleFlashcardScreen(
    flashcard: Flashcard,
    showAnswer: Boolean,
    onShowAnswer: () -> Unit,
    onNext: () -> Unit,
    onEndSession: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {

        // Show image first (if any)
        if (flashcard.imageUrl.isNotBlank()) {
            AsyncImage(
                model = flashcard.imageUrl,
                contentDescription = "Flashcard Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(8.dp)
            )
            Spacer(Modifier.height(12.dp))
        }

        if (flashcard.question.isNotBlank()) {
            Text("Q: ${flashcard.question}", style = MaterialTheme.typography.headlineSmall)
        } else if (flashcard.imageUrl.isBlank()) {
            // no question and no image (shouldn't normally happen) — show placeholder
            Text("Empty flashcard", style = MaterialTheme.typography.headlineSmall)
        }

        if (showAnswer && flashcard.answer.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text("A: ${flashcard.answer}", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(24.dp))
        if (!showAnswer) Button(onClick = onShowAnswer, modifier = Modifier.fillMaxWidth()) { Text("Show Answer") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Next") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onEndSession, modifier = Modifier.fillMaxWidth()) { Text("End Session") }
    }
}

// --- List View with Delete ---
@Composable
fun FlashcardListWithDeleteScreen(
    viewModel: FlashcardViewModel,
    onBack: () -> Unit,
    onAddFlashcard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            "Flashcards in ${viewModel.currentTopic?.name}",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(viewModel.flashcards) { card ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {

                        Column(
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            if (card.imageUrl.isNotBlank()) {
                                // show image preview
                                AsyncImage(
                                    model = card.imageUrl,
                                    contentDescription = "Flashcard Image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                            }

                            if (card.question.isNotBlank()) {
                                Text("Q: ${card.question}", fontWeight = FontWeight.Bold)
                            }
                            if (card.answer.isNotBlank()) {
                                Text("A: ${card.answer}")
                            }
                        }

                        IconButton(
                            onClick = { viewModel.deleteFlashcard(card.id, card.imageUrl) },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Flashcard",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onAddFlashcard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add New Flashcard")
        }

        Spacer(Modifier.height(8.dp))
    }
}

// --- Add Flashcard Screen (with image picker) ---
@Composable
fun AddFlashcardScreen(viewModel: FlashcardViewModel, onDone: () -> Unit, onCancel: () -> Unit) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        imageUri = uri
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("Add Flashcard", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = answer, onValueChange = { answer = it }, label = { Text("Answer") }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(16.dp))

        Button(onClick = { launcher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text("Pick Image (Optional)")
        }

        Spacer(Modifier.height(12.dp))

        imageUri?.let {
            AsyncImage(
                model = it,
                contentDescription = "Selected image preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text("Image selected. Will be uploaded to Firebase Storage.")
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                if (isUploading) return@Button
                isUploading = true
                // Call ViewModel to add flashcard (handles upload internally)
                viewModel.addFlashcard(question, answer, imageUri) {
                    // onDone called after Firestore write completes
                    isUploading = false
                    onDone()
                }
            },
            enabled = question.isNotBlank() || imageUri != null,  // allow image-only if you prefer; adjust if you require question
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isUploading) {
                Text("Saving...")
            } else {
                Text("Save Flashcard")
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}
