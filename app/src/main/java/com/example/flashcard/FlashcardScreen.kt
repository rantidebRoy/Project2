package com.example.flashcard

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random
import kotlinx.coroutines.launch

// --- Data Models ---
data class Topic(
    val id: String = "",
    val name: String = ""
)

data class Flashcard(
    val id: String = "",
    val question: String = "",
    val answer: String = ""
)

// --- ViewModel ---
class FlashcardViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    var topics by mutableStateOf<List<Topic>>(emptyList())
        private set

    var flashcards by mutableStateOf<List<Flashcard>>(emptyList())
        private set

    var currentTopic by mutableStateOf<Topic?>(null)
        private set

    // ---------- Topics ----------
    fun loadTopics() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("topics")
            .get()
            .addOnSuccessListener { result ->
                topics = result.documents.map { doc ->
                    val fieldId = doc.get("id")?.toString()
                    Topic(
                        id = fieldId ?: "",
                        name = doc.getString("name") ?: ""
                    )
                }
            }
    }

    // --- Add Topic With Incremental ID Based on User's Own ID Field ---
    fun addTopic(name: String, onDone: () -> Unit, onFailure: (Exception) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return

        val userRef = db.collection("users").document(uid)
        val topicsRef = userRef.collection("topics")

        userRef.get().addOnSuccessListener { userDoc ->
            val userId = userDoc.get("id")?.toString() ?: return@addOnSuccessListener

            topicsRef.get().addOnSuccessListener { result ->
                val existingNumbers = result.documents.mapNotNull { doc ->
                    val fieldId = doc.get("id")?.toString() ?: doc.id
                    if (fieldId.startsWith("${userId}f")) {
                        fieldId.substringAfter("f").toIntOrNull()
                    } else null
                }

                val nextNumber = (existingNumbers.maxOrNull() ?: 0) + 1
                val newTopicId = "${userId}f$nextNumber"

                val data = hashMapOf(
                    "id" to newTopicId,
                    "name" to name
                )

                topicsRef.document()
                    .set(data)
                    .addOnSuccessListener {
                        loadTopics()
                        onDone()
                    }
                    .addOnFailureListener { e -> onFailure(e) }
            }.addOnFailureListener { e -> onFailure(e) }
        }.addOnFailureListener { e -> onFailure(e) }
    }

    fun deleteTopic(topicId: String, onDone: () -> Unit, onFailure: (Exception) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return

        val topicsRef = db.collection("users").document(uid).collection("topics")

        topicsRef.whereEqualTo("id", topicId).get()
            .addOnSuccessListener { result ->
                for (doc in result.documents) {
                    doc.reference.delete()
                }
                loadTopics()
                onDone()
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // ---------- Flashcards ----------
    fun loadFlashcards(topic: Topic) {
        val uid = auth.currentUser?.uid ?: return
        currentTopic = topic

        db.collection("users").document(uid)
            .collection("topics")
            .whereEqualTo("id", topic.id)
            .get()
            .addOnSuccessListener { topicDocs ->
                val topicDoc = topicDocs.documents.firstOrNull() ?: return@addOnSuccessListener

                topicDoc.reference.collection("flashcards")
                    .get()
                    .addOnSuccessListener { result ->
                        flashcards = result.documents.map { doc ->
                            Flashcard(
                                id = doc.id,
                                question = doc.get("question")?.toString() ?: "",
                                answer = doc.get("answer")?.toString() ?: ""
                            )
                        }
                    }
            }
    }

    fun addFlashcard(question: String, answer: String, onDone: () -> Unit, onFailure: (Exception) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return
        val topic = currentTopic ?: return

        db.collection("users").document(uid)
            .collection("topics").whereEqualTo("id", topic.id)
            .get()
            .addOnSuccessListener { topicDocs ->
                val topicDoc = topicDocs.documents.firstOrNull() ?: return@addOnSuccessListener

                val flashcard = hashMapOf("question" to question, "answer" to answer)

                topicDoc.reference.collection("flashcards")
                    .add(flashcard)
                    .addOnSuccessListener {
                        loadFlashcards(topic)
                        onDone()
                    }
                    .addOnFailureListener { e -> onFailure(e) }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun deleteFlashcard(flashcardId: String, onDone: () -> Unit = {}, onFailure: (Exception) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return
        val topic = currentTopic ?: return

        db.collection("users").document(uid)
            .collection("topics").whereEqualTo("id", topic.id)
            .get()
            .addOnSuccessListener { topicDocs ->
                val topicDoc = topicDocs.documents.firstOrNull() ?: return@addOnSuccessListener

                topicDoc.reference.collection("flashcards")
                    .document(flashcardId)
                    .delete()
                    .addOnSuccessListener {
                        loadFlashcards(topic)
                        onDone()
                    }
                    .addOnFailureListener { e -> onFailure(e) }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // ---------- Share Topic ----------
    fun shareTopic(onDone: () -> Unit, onFailure: (Exception) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return
        val topic = currentTopic ?: return

        val userTopicsRef = db.collection("users").document(uid).collection("topics")

        userTopicsRef.whereEqualTo("id", topic.id).get()
            .addOnSuccessListener { topicDocs ->
                val topicDoc = topicDocs.documents.firstOrNull()
                if (topicDoc == null) {
                    onDone()
                    return@addOnSuccessListener
                }

                val globalRef = db.collection("global_flashcards").document(topic.id)

                // delete existing global flashcards & topic doc if any
                globalRef.collection("flashcards").get()
                    .addOnSuccessListener { oldFlashcards ->
                        val batchDelete = db.batch()
                        for (doc in oldFlashcards.documents) {
                            batchDelete.delete(doc.reference)
                        }
                        batchDelete.delete(globalRef)

                        batchDelete.commit()
                            .addOnSuccessListener {
                                topicDoc.reference.collection("flashcards").get()
                                    .addOnSuccessListener { newFlashcards ->
                                        val newTopicData = mapOf(
                                            "id" to topic.id,
                                            "name" to topic.name,
                                            "sharedBy" to uid,
                                            "timestamp" to System.currentTimeMillis()
                                        )

                                        globalRef.set(newTopicData)
                                            .addOnSuccessListener {
                                                val batchAdd = db.batch()
                                                for (fDoc in newFlashcards.documents) {
                                                    val data = mapOf(
                                                        "question" to (fDoc.get("question")?.toString() ?: ""),
                                                        "answer" to (fDoc.get("answer")?.toString() ?: "")
                                                    )
                                                    val newFlashRef = globalRef.collection("flashcards").document()
                                                    batchAdd.set(newFlashRef, data)
                                                }

                                                batchAdd.commit()
                                                    .addOnSuccessListener {
                                                        // Write root-level topic_tag name only, avoid duplicates
                                                        val tagData = mapOf("name" to topic.name)
                                                        db.collection("topic_tag")
                                                            .whereEqualTo("name", topic.name)
                                                            .get()
                                                            .addOnSuccessListener { existing ->
                                                                if (existing.isEmpty) {
                                                                    db.collection("topic_tag").document()
                                                                        .set(tagData)
                                                                        .addOnSuccessListener { onDone() }
                                                                        .addOnFailureListener { e -> onFailure(e) }
                                                                } else {
                                                                    onDone()
                                                                }
                                                            }
                                                            .addOnFailureListener { e -> onFailure(e) }
                                                    }
                                                    .addOnFailureListener { e -> onFailure(e) }
                                            }
                                            .addOnFailureListener { e -> onFailure(e) }
                                    }
                                    .addOnFailureListener { e -> onFailure(e) }
                            }
                            .addOnFailureListener { e -> onFailure(e) }
                    }
                    .addOnFailureListener { e -> onFailure(e) }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // ---------- Importing logic ----------
    fun importGlobalTopicIntoUser(globalTopicId: String, onDone: () -> Unit, onFailure: (Exception) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(uid)
        val topicsRef = userRef.collection("topics")
        val globalRef = db.collection("global_flashcards").document(globalTopicId)

        globalRef.get().addOnSuccessListener { gDoc ->
            if (!gDoc.exists()) {
                onDone()
                return@addOnSuccessListener
            }
            val globalName = gDoc.get("name")?.toString() ?: ""
            globalRef.collection("flashcards").get()
                .addOnSuccessListener { flashDocs ->
                    userRef.get().addOnSuccessListener { userDoc ->
                        val userId = userDoc.get("id")?.toString() ?: return@addOnSuccessListener

                        topicsRef.get().addOnSuccessListener { existingTopics ->
                            val existingNumbers = existingTopics.documents.mapNotNull { doc ->
                                val fieldId = doc.get("id")?.toString() ?: doc.id
                                if (fieldId.startsWith("${userId}f")) {
                                    fieldId.substringAfter("f").toIntOrNull()
                                } else null
                            }
                            val nextNumber = (existingNumbers.maxOrNull() ?: 0) + 1
                            val newTopicId = "${userId}f$nextNumber"

                            val topicData = mapOf("id" to newTopicId, "name" to globalName)

                            topicsRef.document().set(topicData)
                                .addOnSuccessListener {
                                    // locate created document
                                    topicsRef.whereEqualTo("id", newTopicId).get()
                                        .addOnSuccessListener { newTopicDocs ->
                                            val newTopicDoc = newTopicDocs.documents.firstOrNull()
                                            if (newTopicDoc == null) {
                                                onFailure(Exception("Failed to locate newly created topic document"))
                                                return@addOnSuccessListener
                                            }

                                            val batch = db.batch()
                                            for (fDoc in flashDocs.documents) {
                                                val data = mapOf(
                                                    "question" to (fDoc.get("question")?.toString() ?: ""),
                                                    "answer" to (fDoc.get("answer")?.toString() ?: "")
                                                )
                                                val newFlashRef = newTopicDoc.reference.collection("flashcards").document()
                                                batch.set(newFlashRef, data)
                                            }

                                            batch.commit()
                                                .addOnSuccessListener {
                                                    loadTopics()
                                                    onDone()
                                                }
                                                .addOnFailureListener { e -> onFailure(e) }
                                        }
                                        .addOnFailureListener { e -> onFailure(e) }
                                }
                                .addOnFailureListener { e -> onFailure(e) }
                        }.addOnFailureListener { e -> onFailure(e) }
                    }.addOnFailureListener { e -> onFailure(e) }
                }
                .addOnFailureListener { e -> onFailure(e) }
        }.addOnFailureListener { e -> onFailure(e) }
    }

    // ---------- Delete global topic (helper) ----------
    fun deleteGlobalTopic(globalTopicId: String, onDone: () -> Unit, onFailure: (Exception) -> Unit = {}) {
        val db = FirebaseFirestore.getInstance()
        val ref = db.collection("global_flashcards").document(globalTopicId)
        ref.collection("flashcards").get()
            .addOnSuccessListener { fs ->
                val batch = db.batch()
                for (fd in fs.documents) batch.delete(fd.reference)
                batch.delete(ref)
                batch.commit()
                    .addOnSuccessListener { onDone() }
                    .addOnFailureListener { e -> onFailure(e) }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }
}

// ----------------------------------------------------------------------------------
// UI
// ----------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(viewModel: FlashcardViewModel, onBack: () -> Unit) {
    var currentView by remember { mutableStateOf("topics") }
    var shuffledFlashcards by remember { mutableStateOf<List<Flashcard>>(emptyList()) }
    var currentShuffleIndex by remember { mutableStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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
                            "import" -> "Import Topics"
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
                            "import" -> currentView = "topics"
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    onAddTopic = {
                        // navigate to add topic - but handle success snackbar here
                        currentView = "addTopic"
                    },
                    onImportTopics = { currentView = "import" }
                )

                // --- Add Topic ---
                "addTopic" -> AddTopicScreen(
                    onAdd = { name ->
                        viewModel.addTopic(name,
                            onDone = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Topic '$name' added")
                                }
                                currentView = "topics"
                            },
                            onFailure = { e ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Failed to add topic: ${e.message}")
                                }
                            }
                        )
                    },
                    onCancel = { currentView = "topics" }
                )

                // --- Topic Options ---
                "topicOptions" -> TopicOptionsScreen(
                    viewModel = viewModel,
                    onSelectShuffle = {
                        shuffledFlashcards = viewModel.flashcards.shuffled(Random(System.currentTimeMillis()))
                        currentShuffleIndex = 0
                        showAnswer = false
                        currentView = "shuffle"
                    },
                    onSelectList = { currentView = "list" },
                    onBack = { currentView = "topics" },
                    onShareTopic = {
                        viewModel.shareTopic(
                            onDone = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Topic shared")
                                }
                                currentView = "topics"
                            },
                            onFailure = { e ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Share failed: ${e.message}")
                                }
                            }
                        )
                    },
                    onDeleteTopic = {
                        viewModel.currentTopic?.id?.let { id ->
                            viewModel.deleteTopic(id, onDone = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Topic deleted")
                                }
                                currentView = "topics"
                            }, onFailure = { e ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Delete failed: ${e.message}")
                                }
                            })
                        }
                    }
                )

                // --- Shuffle ---
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
                    onDone = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Flashcard added")
                        }
                        currentView = "list"
                    },
                    onCancel = { currentView = "list" }
                )

                // --- Import Topics ---
                "import" -> ImportTopicsScreen(
                    viewModel = viewModel,
                    onBack = { currentView = "topics" },
                    onImportSuccess = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Topic imported")
                        }
                        viewModel.loadTopics()
                    },
                    onGlobalDeleteSuccess = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Global topic deleted")
                        }
                    }
                )
            }
        }
    }
}

// --- Topic List Screen (scrollable list + fixed bottom buttons) ---
@Composable
fun TopicListScreen(
    viewModel: FlashcardViewModel,
    onTopicSelected: (Topic) -> Unit,
    onAddTopic: () -> Unit,
    onImportTopics: () -> Unit
) {
    LaunchedEffect(Unit) { viewModel.loadTopics() }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Your Topics",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, top = 24.dp)
        )

        Spacer(Modifier.height(12.dp))

        // Scrollable list area, takes available space
        Box(modifier = Modifier.weight(1f).padding(horizontal = 24.dp)) {
            if (viewModel.topics.isEmpty()) {
                Text("No topics yet.", modifier = Modifier.align(Alignment.TopStart))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(viewModel.topics) { topic ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { onTopicSelected(topic) },
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Box(Modifier.padding(16.dp)) {
                                Text(topic.name, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
        }

        // Fixed bottom buttons
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Button(onClick = onAddTopic, modifier = Modifier.fillMaxWidth()) {
                Text("Add New Topic")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onImportTopics, modifier = Modifier.fillMaxWidth()) {
                Text("Import Topics")
            }
        }
    }
}

// --- Add Topic Screen ---
@Composable
fun AddTopicScreen(onAdd: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Add New Topic", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Topic Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onAdd(name) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save Topic") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

// --- Import Topics Screen ---
@Composable
fun ImportTopicsScreen(
    viewModel: FlashcardViewModel,
    onBack: () -> Unit,
    onImportSuccess: () -> Unit,
    onGlobalDeleteSuccess: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) } // names
    var results by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // Pair(name, globalDocId)
    var loading by remember { mutableStateOf(false) }
    val db = FirebaseFirestore.getInstance()
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid

    // realtime prefix search for suggestions (max 3)
    LaunchedEffect(query) {
        if (query.isBlank()) { suggestions = emptyList(); return@LaunchedEffect }
        val start = query
        val end = query + '\uf8ff'
        db.collection("topic_tag")
            .whereGreaterThanOrEqualTo("name", start)
            .whereLessThanOrEqualTo("name", end)
            .limit(10)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { it.get("name")?.toString() }
                suggestions = list.distinct().take(3)
            }
            .addOnFailureListener { suggestions = emptyList() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Import Topics", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search topics") },
            modifier = Modifier.fillMaxWidth()
        )

        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth().heightIn(max = (48.dp * suggestions.size))) {
                LazyColumn {
                    items(suggestions) { name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    query = name
                                    suggestions = emptyList()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(onClick = {
            if (query.isBlank()) { results = emptyList(); return@Button }
            loading = true
            val start = query
            val end = query + '\uf8ff'
            db.collection("topic_tag")
                .whereGreaterThanOrEqualTo("name", start)
                .whereLessThanOrEqualTo("name", end)
                .get()
                .addOnSuccessListener { snap ->
                    val names = snap.documents.mapNotNull { it.get("name")?.toString() }.distinct()
                    if (names.isEmpty()) {
                        results = emptyList(); loading = false; return@addOnSuccessListener
                    }
                    val chunks = names.chunked(10)
                    val tmpResults = mutableListOf<Pair<String, String>>()
                    var processed = 0
                    for (chunk in chunks) {
                        db.collection("global_flashcards")
                            .whereIn("name", chunk)
                            .get()
                            .addOnSuccessListener { gSnap ->
                                for (gDoc in gSnap.documents) {
                                    val gname = gDoc.get("name")?.toString() ?: ""
                                    val gid = gDoc.id
                                    tmpResults.add(Pair(gname, gid))
                                }
                                processed++
                                if (processed == chunks.size) {
                                    results = tmpResults
                                    loading = false
                                }
                            }
                            .addOnFailureListener {
                                processed++
                                if (processed == chunks.size) {
                                    results = tmpResults
                                    loading = false
                                }
                            }
                    }
                }
                .addOnFailureListener {
                    results = emptyList(); loading = false
                }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Search")
        }

        Spacer(Modifier.height(12.dp))

        if (loading) {
            Text("Loading...", modifier = Modifier.padding(8.dp))
        }

        // Results scrollable area
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(results) { (name, gid) ->
                var count by remember { mutableStateOf<Int?>(null) }
                var sharedBy by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(gid) {
                    val docRef = db.collection("global_flashcards").document(gid)
                    docRef.get()
                        .addOnSuccessListener { doc -> sharedBy = doc.get("sharedBy")?.toString() }
                        .addOnFailureListener { sharedBy = null }
                    docRef.collection("flashcards").get()
                        .addOnSuccessListener { fSnap -> count = fSnap.size() }
                        .addOnFailureListener { count = 0 }
                }

                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                    elevation = CardDefaults.cardElevation(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("ID: $gid", fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Flashcards: ${count ?: "..."}", fontSize = 12.sp)
                        }

                        // If this topic was shared by the current user: show both delete and add
                        if (sharedBy != null && sharedBy == currentUid) {
                            IconButton(onClick = {
                                viewModel.deleteGlobalTopic(gid, onDone = {
                                    // locally remove from results
                                    results = results.filterNot { it.second == gid }
                                    onGlobalDeleteSuccess()
                                }, onFailure = {
                                    // ignore or show error snackbar (hosted by caller)
                                })
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete global topic")
                            }
                            IconButton(onClick = {
                                viewModel.importGlobalTopicIntoUser(gid, onDone = {
                                    onImportSuccess()
                                }, onFailure = {
                                    // optional error handling
                                })
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Import")
                            }
                        } else {
                            IconButton(onClick = {
                                viewModel.importGlobalTopicIntoUser(gid, onDone = {
                                    onImportSuccess()
                                }, onFailure = {
                                    // optional error
                                })
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Import")
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Topic Options ---
@Composable
fun TopicOptionsScreen(
    viewModel: FlashcardViewModel,
    onSelectShuffle: () -> Unit,
    onSelectList: () -> Unit,
    onBack: () -> Unit,
    onShareTopic: () -> Unit,
    onDeleteTopic: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = onSelectShuffle, modifier = Modifier.fillMaxWidth()) { Text("Shuffle All") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSelectList, modifier = Modifier.fillMaxWidth()) { Text("List View") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onShareTopic, modifier = Modifier.fillMaxWidth()) { Text("Share Topic") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onDeleteTopic, modifier = Modifier.fillMaxWidth()) { Text("Delete Topic") }
    }
}

// --- Shuffle Flashcard Screen ---
// --- Shuffle Flashcard Screen ---
// --- Shuffle Flashcard Screen ---
@Composable
fun ShuffleFlashcardScreen(
    flashcard: Flashcard,
    showAnswer: Boolean,
    onShowAnswer: () -> Unit,
    onNext: () -> Unit,
    onEndSession: () -> Unit
) {
    val questionScroll = rememberScrollState()
    val answerScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // Question Box (scrollable, 3 lines visible)
        Text("Question:", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp) // ~3 lines
                .border(1.dp, MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.medium)
                .padding(8.dp)
                .verticalScroll(questionScroll)
        ) {
            Text(
                text = flashcard.question,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // ANSWER (scrollable, only if shown)
        if (showAnswer) {
            Spacer(Modifier.height(24.dp))
            Text("Answer:", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp) // ~3 lines
                    .border(1.dp, MaterialTheme.colorScheme.secondary, shape = MaterialTheme.shapes.medium)
                    .padding(8.dp)
                    .verticalScroll(answerScroll)
            ) {
                Text(
                    text = flashcard.answer,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Buttons
        if (!showAnswer) {
            Button(onClick = onShowAnswer, modifier = Modifier.fillMaxWidth()) {
                Text("Show Answer")
            }
        }

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
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        Text(
            "Flashcards in ${viewModel.currentTopic?.name}",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(viewModel.flashcards) { card ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(4.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(modifier = Modifier.align(Alignment.CenterStart)) {
                            Text("Q: ${card.question}", fontWeight = FontWeight.Bold)
                            Text("A: ${card.answer}")
                        }

                        IconButton(onClick = { viewModel.deleteFlashcard(card.id) },
                            modifier = Modifier.align(Alignment.TopEnd)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Flashcard", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = onAddFlashcard, modifier = Modifier.fillMaxWidth()) { Text("Add New Flashcard") }
        Spacer(Modifier.height(8.dp))
    }
}

// --- Add Flashcard Screen ---
@Composable
fun AddFlashcardScreen(viewModel: FlashcardViewModel, onDone: () -> Unit, onCancel: () -> Unit) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("Add Flashcard", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = answer, onValueChange = { answer = it }, label = { Text("Answer") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { viewModel.addFlashcard(question, answer, onDone = { onDone() }, onFailure = {}) },
            enabled = question.isNotBlank() && answer.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save Flashcard") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}
