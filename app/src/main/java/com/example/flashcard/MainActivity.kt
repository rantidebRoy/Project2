package com.example.flashcard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.flashcard.ui.theme.FlashcardTheme

// --------------------------
//        ROUTES
// --------------------------
private const val SPLASH_ROUTE = "splash"
private const val INTRO_ROUTE = "intro"

private const val LOGIN_ROUTE = "login_screen"
private const val SIGNUP_ROUTE = "signup_screen"
private const val RESET_PASSWORD_ROUTE = "reset_password_screen"
private const val MAIN_ROUTE = "main_app"
private const val TIMER_ROUTE = "timer_screen"
private const val FLASHCARD_ROUTE = "flashcard_screen"
private const val PROFILE_ROUTE = "profile_screen"
private const val SCHEDULER_ROUTE = "scheduler_screen"
private const val QNA_ROUTE = "qna_screen"
const val PUBLISH_QUESTION_ROUTE = "publish_question_screen"

private const val HELP_ROUTE = "help_screen"
private const val ABOUT_ROUTE = "about_screen"

// --------------------------
//       MainActivity
// --------------------------
class MainActivity : ComponentActivity() {

    // Permission Launcher for Android 13+ Notification Permission
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Optionally handle the result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()

        // --- START THE MIDNIGHT CYCLE ---
        MidnightReceiver.scheduleMidnightAlarm(this)

        // Check/Ask for Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            FlashcardTheme {
                AppNavigationWithNotifications()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "scheduler_channel",
                "Scheduler Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

// --------------------------
//   ViewModel Factory
// --------------------------
class LoginViewModelFactory(private val sessionManager: SessionManager) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// --------------------------
// Navigation Graph
// --------------------------
@Composable
fun AppNavigationWithNotifications() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }

    val loginViewModel: LoginViewModel =
        viewModel(factory = LoginViewModelFactory(sessionManager))
    val flashcardViewModel: FlashcardViewModel = viewModel()
    val timerModel: TimerModel = viewModel()

    val startDestination = SPLASH_ROUTE

    // Background Notification Checker
    LaunchedEffect(Unit) { startNotificationChecker(context) }

    NavHost(navController = navController, startDestination = startDestination) {

        // --- Splash ---
        composable(SPLASH_ROUTE) {
            SplashScreen(navController = navController, sessionManager = sessionManager)
        }

        // --- Intro ---
        composable(INTRO_ROUTE) {
            IntroScreen(navController)
        }

        // --- Login ---
        composable(LOGIN_ROUTE) {
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    sessionManager.saveSession(FirebaseAuth.getInstance().currentUser?.uid ?: "")
                    navController.navigate(MAIN_ROUTE) {
                        popUpTo(LOGIN_ROUTE) { inclusive = true }
                    }
                },
                onNavigateToSignup = { navController.navigate(SIGNUP_ROUTE) },
                onNavigateToReset = { navController.navigate(RESET_PASSWORD_ROUTE) }
            )
        }

        composable(RESET_PASSWORD_ROUTE) {
            ResetPasswordScreen(onBack = { navController.popBackStack() })
        }

        composable(SIGNUP_ROUTE) {
            SignupScreen(
                onSignup = { name, email, password ->
                    registerUser(context, name, email, password) {
                        sessionManager.saveSession(FirebaseAuth.getInstance().currentUser?.uid ?: "")
                        navController.navigate(MAIN_ROUTE) {
                            popUpTo(SIGNUP_ROUTE) { inclusive = true }
                        }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(LOGIN_ROUTE) {
                        popUpTo(SIGNUP_ROUTE) { inclusive = true }
                    }
                }
            )
        }

        // --- Main App Screen --- (pass nav lambdas for new screens too)
        composable(MAIN_ROUTE) {
            MainScreen(
                onNavigateToTimer = { navController.navigate(TIMER_ROUTE) },
                onNavigateToFlashcard = { navController.navigate(FLASHCARD_ROUTE) },
                onNavigateToProfile = { navController.navigate(PROFILE_ROUTE) },
                onNavigateToScheduler = { navController.navigate(SCHEDULER_ROUTE) },
                onNavigateToQnA = { navController.navigate(QNA_ROUTE) },
                onNavigateToHelp = { navController.navigate(HELP_ROUTE) },
                onNavigateToAbout = { navController.navigate(ABOUT_ROUTE) },
                onLogout = {
                    FirebaseAuth.getInstance().signOut()
                    sessionManager.clearSession()
                    Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                    navController.navigate(LOGIN_ROUTE) {
                        popUpTo(MAIN_ROUTE) { inclusive = true }
                    }
                }
            )
        }

        // --- Timer ---
        composable(TIMER_ROUTE) {
            TimerScreen(
                viewModel = timerModel,
                onBack = { navController.popBackStack() },
                context = context
            )
        }

        // --- Flashcard ---
        composable(FLASHCARD_ROUTE) {
            FlashcardScreen(viewModel = flashcardViewModel, onBack = { navController.popBackStack() })
        }

        composable("topicDetail/{topic}") { entry ->
            TopicDetailScreen(navController, entry.arguments?.getString("topic") ?: "")
        }

        composable("flashcardDetail/{topicId}/{flashcardId}") { entry ->
            FlashcardDetailScreen(
                navController,
                entry.arguments?.getString("topicId") ?: "",
                entry.arguments?.getString("flashcardId") ?: ""
            )
        }

        // --- Profile, Scheduler, QnA, Publish ---
        composable(PROFILE_ROUTE) { ProfileScreen(onBack = { navController.popBackStack() }) }
        composable(SCHEDULER_ROUTE) { SchedulerScreen(onBack = { navController.popBackStack() }) }
        composable(QNA_ROUTE) { QnAScreen(parentNavController = navController) }
        composable(PUBLISH_QUESTION_ROUTE) { PublishQuestionScreen(onBack = { navController.popBackStack() }) }

        // --- Help & About ---
        composable(HELP_ROUTE) { HelpScreen(onBack = { navController.popBackStack() }) }
        composable(ABOUT_ROUTE) { AboutScreen(onBack = { navController.popBackStack() }) }
    }
}

// --------------------------
//     Splash Screen
// --------------------------
@Composable
fun SplashScreen(navController: NavController, sessionManager: SessionManager) {
    LaunchedEffect(Unit) {
        delay(2000)
        if (sessionManager.isLoggedIn()) {
            navController.navigate(MAIN_ROUTE) {
                popUpTo(SPLASH_ROUTE) { inclusive = true }
            }
        } else {
            navController.navigate(INTRO_ROUTE) {
                popUpTo(SPLASH_ROUTE) { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "App Logo",
            modifier = Modifier.size(250.dp)
        )
    }
}

// --------------------------
//     Intro Screen
// --------------------------

@Composable
fun IntroScreen(navController: NavController) {
    val pages = onboardingPages
    var currentPage by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        // Top bar: app name + Skip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Study Buddy",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            TextButton(
                onClick = {
                    // Go directly to Sign Up / Login page
                    navController.navigate(SIGNUP_ROUTE) {
                        popUpTo(INTRO_ROUTE) { inclusive = true }
                    }
                }
            ) {
                Text("Skip")
            }
        }

        // Center: current image
        Image(
            painter = painterResource(id = pages[currentPage].imageRes),
            contentDescription = "Intro image ${currentPage + 1}",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Dots indicator (optional but nice)
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            pages.forEachIndexed { index, _ ->
                val isSelected = index == currentPage
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(if (isSelected) 10.dp else 8.dp)
                        .background(
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom buttons: Next / Get Started
        Button(
            onClick = {
                if (currentPage < pages.lastIndex) {
                    currentPage += 1
                } else {
                    // Last page -> go to Sign Up / Login screen
                    navController.navigate(SIGNUP_ROUTE) {
                        popUpTo(INTRO_ROUTE) { inclusive = true }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text(
                text = if (currentPage < pages.lastIndex) "Next" else "Get Started",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Optional: small text button to go to Login directly
        TextButton(
            onClick = {
                navController.navigate(LOGIN_ROUTE) {
                    popUpTo(INTRO_ROUTE) { inclusive = true }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Already have an account? Log In")
        }
    }
}



// -------------------------------------------------------------
// Existing Screens — unchanged except routing updated
// -------------------------------------------------------------
// ---------------- MAIN SCREEN ----------------

@Composable
fun MainScreen(
    onNavigateToTimer: () -> Unit,
    onNavigateToFlashcard: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToScheduler: () -> Unit,
    onNavigateToQnA: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onLogout: () -> Unit
) {
    // Added Help and About cards
    val cardItems = listOf(
        CardItem("Profile", Icons.Default.Person, onNavigateToProfile),
        CardItem("Timer", Icons.Default.Timer, onNavigateToTimer),
        CardItem("Flashcards", Icons.Default.MenuBook, onNavigateToFlashcard),
        CardItem("Scheduler", Icons.Default.CalendarToday, onNavigateToScheduler),
        CardItem("Q&A", Icons.Default.QuestionAnswer, onNavigateToQnA),
        CardItem("Help", Icons.Default.Help, onNavigateToHelp),
        CardItem("About", Icons.Default.Info, onNavigateToAbout)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(20.dp)
    ) {
        // Top Title
        Text(
            text = "Study Buddy",
            style = MaterialTheme.typography.headlineMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(cardItems) { item ->
                FeatureCard(item)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Logout Button
        Button(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Logout", color = Color.White)
        }
    }
}


// ---------------- CARD ITEM MODEL ----------------

data class CardItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

data class OnboardingPage(
    val imageRes: Int
)
private val onboardingPages = listOf(
    OnboardingPage(R.drawable.flashcards),
    OnboardingPage(R.drawable.timer),
    OnboardingPage(R.drawable.scheduler),
    OnboardingPage(R.drawable.qna)
)



// ---------------- CARD UI ----------------

@Composable
fun FeatureCard(item: CardItem) {
    Card(
        modifier = Modifier
            .height(150.dp)
            .fillMaxWidth()
            .clickable { item.onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = item.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


// ---------------- Profile Screen ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val userId = FirebaseAuth.getInstance().currentUser?.uid

    var username by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf<String?>(null) }
    var id by remember { mutableStateOf<Number?>(null) }

    LaunchedEffect(userId) {
        if (userId != null) {
            FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .get()
                .addOnSuccessListener { snapshot ->
                    username = snapshot.getString("name")
                    email = snapshot.getString("email")
                    id = snapshot.getLong("id")
                }.addOnFailureListener {
                    Toast.makeText(context, "Failed to fetch user data", Toast.LENGTH_SHORT).show()
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // --- Profile Icon ---
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(110.dp),
                tonalElevation = 4.dp
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(20.dp))

            // --- Profile Card ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    ProfileItem(label = "Name", value = username)
                    ProfileItem(label = "Email", value = email)
                    ProfileItem(label = "User ID", value = id?.toString())
                }
            }
        }
    }
}

@Composable
fun ProfileItem(label: String, value: String?) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value ?: "Loading...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


// ------------------------------
// Firebase Registration
// ------------------------------
fun registerUser(
    context: Context,
    name: String,
    email: String,
    password: String,
    onSuccess: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    auth.createUserWithEmailAndPassword(email, password)
        .addOnSuccessListener { result ->

            val firebaseUser = result.user ?: return@addOnSuccessListener

            val counterRef = db.collection("counters").document("users")
            val userRef = db.collection("users").document(firebaseUser.uid)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(counterRef)
                val currentCount = snapshot.getLong("userCount") ?: 9999
                val newId = currentCount + 1

                transaction.set(counterRef, mapOf("userCount" to newId))
                transaction.set(userRef, mapOf("id" to newId, "name" to name, "email" to email))

                newId
            }.addOnSuccessListener {
                Toast.makeText(context, "Signup success!", Toast.LENGTH_LONG).show()
                onSuccess()
            }.addOnFailureListener {
                Toast.makeText(context, "Transaction failed", Toast.LENGTH_LONG).show()
            }

        }.addOnFailureListener { e ->
            Toast.makeText(context, "Signup failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
}

// ------------------------------
// Background Notification Checker
// ------------------------------
fun startNotificationChecker(context: Context) {
    val db = FirebaseFirestore.getInstance()
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    CoroutineScope(Dispatchers.IO).launch {
        while (true) {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val todayStr = "today"

            try {
                val snapshot = db.collection("users")
                    .document(userId).collection("events")
                    .whereEqualTo("date", todayStr)
                    .get().await()

                snapshot.documents.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    val eventHour = (data["hour"] as? Long)?.toInt() ?: 0
                    val eventMinute = (data["minute"] as? Long)?.toInt() ?: 0
                    val title = data["title"] as? String ?: "Event"
                    val desc = data["description"] as? String ?: ""

                    if (eventHour == hour && eventMinute == minute) {

                        val builder = androidx.core.app.NotificationCompat.Builder(
                            context, "scheduler_channel"
                        )
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setContentTitle(title)
                            .setContentText(desc)
                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true)

                        notificationManager.notify(doc.id.hashCode(), builder.build())
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }

            delay(60000)
        }
    }
}

// ------------------------------
// Help Screen
// ------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val scroll = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        TopAppBar(
            title = { Text("Help - How to use Study Buddy") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scroll)
        ) {
            Text("Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Study Buddy is built to help you memorize and focus. Below are the app's features and how to use them.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(16.dp))
            Text("1) Flashcards", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "• Open Flashcards from the Home screen.\n" +
                        "• You will see a random card (question). Tap 'Show Answer' to reveal it.\n" +
                        "• 'Next Card' picks another random question.\n" +
                        "• 'Add New Card' lets you create a new question-answer pair. Note: currently saved in-memory; to persist across restarts we will connect a database (Room/Firebase) later."
            )
            Spacer(Modifier.height(12.dp))

            Text("2) Timer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "• Set minutes and seconds, then tap 'Start'.\n" +
                        "• Use Pause to pause the countdown and Resume to continue.\n" +
                        "• Use Restart to set a new time.\n" +
                        "• The timer continues in the background when you navigate away — return to see the current state."
            )
            Spacer(Modifier.height(12.dp))

            Text("3) Scheduler", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "• Scheduler lets you save events for the next day with a title, description, hour & minute.\n" +
                        "• When the scheduled time arrives, the app shows a notification.\n" +
                        "• Planned improvements: persist events using Room and schedule notifications with AlarmManager/WorkManager."
            )
            Spacer(Modifier.height(12.dp))

            Text("4) Q&A", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "• Q&A allows users to post questions and answer others' questions.\n" +
                        "• Use the 'Publish Question' option to add a new question.\n" +
                        "• Future work: backend (MongoDB) integration so questions and answers are stored remotely."
            )
            Spacer(Modifier.height(12.dp))

            Text("5) Profile & Authentication", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "• Sign Up and Login handled with Firebase Authentication.\n" +
                        "• Profile page shows name, email and user ID fetched from Firestore.\n" +
                        "• If you sign up, the app assigns you an incremental ID stored in Firestore 'users' collection."
            )
            Spacer(Modifier.height(12.dp))

            Text("6) Notifications & Permissions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "• The app asks for notification permission on Android 13+.\n" +
                        "• Scheduler notifications are sent via NotificationManager.\n" +
                        "• If notifications aren't showing, check system-level app notification settings and ensure permission is granted."
            )
            Spacer(Modifier.height(12.dp))

            Text("Tips & Troubleshooting", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "• If a feature isn't available or data doesn't persist, check if you're logged in and if Firestore connectivity is configured.\n" +
                        "• For persistent flashcards/events we will add Room or Firebase storage in the next milestone.\n" +
                        "• If notifications fail, verify channel creation (Android O+) and notification permission on Android 13+."
            )

            Spacer(Modifier.height(24.dp))
            Text("Need more help?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Contact the dev team or open an issue in the project repo for bug reports and feature requests.")
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------
// About Screen (includes names and ~250 word project description)
// ------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val scroll = rememberScrollState()

    // ~250-word project description (approx.)
//

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        TopAppBar(
            title = { Text("About Us") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scroll)
        ) {
            Text("Team Members", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("• 2022331023 - Rantideb Roy")
            Text("• 2022331057 - Banasree Pramanik")
            Text("• 2022331059 - Supto Das")
            Spacer(Modifier.height(16.dp))



            Spacer(Modifier.height(24.dp))
            Text("Contact & Repo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("Repository: https://github.com/rantidebRoy/Project2")
            Spacer(Modifier.height(12.dp))
            Text("This project is a progress-stage prototype — see project milestones for upcoming features like Room persistence, repetitive timers, and a full Q&A backend.")
            Spacer(Modifier.height(24.dp))
        }
    }
}