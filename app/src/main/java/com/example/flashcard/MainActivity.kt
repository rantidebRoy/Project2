package com.example.flashcard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// Import necessary dependencies for ViewModels used in the composables
import androidx.compose.material3.ExperimentalMaterial3Api



// Define destination routes as strings for navigation
//private const val AUTH_ROUTE = "auth_screen"
//private const val MAIN_APP_ROUTE = "main_app"
//private const val TIMER_ROUTE = "timer_screen"
//
//private const val FLASHCARD_ROUTE = "flashcard_screen"

private const val WELCOME_ROUTE = "welcome_screen" // NEW ROUTE
private const val LOGIN_ROUTE = "login_screen"     // NEW ROUTE
private const val SIGNUP_ROUTE = "signup_screen"   // NEW ROUTE
private const val MAIN_APP_ROUTE = "main_app"
private const val TIMER_ROUTE = "timer_screen"
private const val FLASHCARD_ROUTE = "flashcard_screen"

// The mock definitions for Flashcard, FlashcardViewModel, TimerModel, TimerScreen,
// and AuthScreen have been removed to resolve the redeclaration errors.
// These classes and functions must be defined in their own respective files.

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize SessionManager with the application context
        val sessionManager = SessionManager(applicationContext)

        setContent {
            // Apply your application's theme
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Pass the session manager to the root composable
                    FlashcardAppNavigation(sessionManager)
                }
            }
        }
    }
}

/**
 * Custom ViewModelFactory to create LoginViewModel with a SessionManager dependency.
 * This is necessary because LoginViewModel now requires SessionManager in its constructor.
 */
class LoginViewModelFactory(private val sessionManager: SessionManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // We now rely on the actual LoginViewModel class definition
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


/**
 * The main navigation Composable for the entire application.
 * Determines the starting screen based on the user's login state.
 */
@Composable
fun FlashcardAppNavigation(sessionManager: SessionManager) {
    val navController = rememberNavController()

    // --- 1. Define Destinations and ViewModels ---

    // Determine the starting destination:
    // If logged in, go to MainApp. If not, go to the Welcome screen.
    val startDestination = if (sessionManager.isLoggedIn()) MAIN_APP_ROUTE else WELCOME_ROUTE

    // ViewModel for authentication, injected with the session manager
    val loginViewModel: LoginViewModel = viewModel(
        factory = LoginViewModelFactory(sessionManager)
    )

    // ViewModels for the main application features
    val flashcardViewModel: FlashcardViewModel = viewModel()
    val timerModel: TimerModel = viewModel()

    // --- 2. Define Navigation Graph ---

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {

        // --- A. WELCOME Screen (New Entry Point) ---
        composable(WELCOME_ROUTE) {
            WelcomeScreen(
                onNavigateToLogin = { navController.navigate(LOGIN_ROUTE) },
                onNavigateToSignup = { navController.navigate(SIGNUP_ROUTE) }
            )
        }

        // --- B. LOGIN Screen ---
        composable(LOGIN_ROUTE) {
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    // Navigate to MainApp and clear the entire authentication stack
                    navController.navigate(MAIN_APP_ROUTE) {
                        popUpTo(WELCOME_ROUTE) { inclusive = true }
                    }
                },
                onNavigateToSignup = {
                    // Navigate to Signup, replacing the current Login screen in the stack
                    navController.navigate(SIGNUP_ROUTE) {
                        popUpTo(LOGIN_ROUTE) { inclusive = true }
                    }
                }
            )
        }

        // --- C. SIGNUP Screen ---
        composable(SIGNUP_ROUTE) {
            SignupScreen(
                viewModel = loginViewModel,
                onSignupSuccess = {
                    // On successful signup (which often logs the user in), navigate to MainApp
                    navController.navigate(MAIN_APP_ROUTE) {
                        popUpTo(WELCOME_ROUTE) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    // Navigate back to Login, replacing the current Signup screen in the stack
                    navController.navigate(LOGIN_ROUTE) {
                        popUpTo(SIGNUP_ROUTE) { inclusive = true }
                    }
                }
            )
        }

        // -----------------------------------------------------------------
        // --- D. Main Application Flow (LOGGED-IN ROUTES) ---
        // -----------------------------------------------------------------

        composable(MAIN_APP_ROUTE) {
            MainApplicationScreen(
                flashcardViewModel = flashcardViewModel,
                timerModel = timerModel,
                loginViewModel = loginViewModel,
                onNavigateToTimer = { navController.navigate(TIMER_ROUTE) },
                onNavigateToFlashcard = { navController.navigate(FLASHCARD_ROUTE) },
                onLogout = {
                    // Clear session and navigate back to the entry point (Welcome)
                    loginViewModel.logout()
                    navController.navigate(WELCOME_ROUTE) {
                        popUpTo(MAIN_APP_ROUTE) { inclusive = true }
                    }
                }
            )
        }

        composable(TIMER_ROUTE) {
            TimerScreen(
                viewModel = timerModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(FLASHCARD_ROUTE) {
            FlashcardScreen(
                viewModel = flashcardViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Placeholder for the main screen where flashcards and timer features are accessed.
 * You will build out the actual UI for the home screen here.
 */
@Composable
fun MainApplicationScreen(
    flashcardViewModel: FlashcardViewModel,
    timerModel: TimerModel,
    loginViewModel: LoginViewModel,
    onNavigateToFlashcard: () -> Unit,
    onNavigateToTimer: () -> Unit,
    onLogout: () -> Unit
) {
    // This is where you would place your main app UI (e.g., a tab bar, or a dashboard)
    // For now, we'll show a simple screen to confirm login success.
    // Replace FlashcardScreen with your main app dashboard later.
    HomePage(
        onNavigateToTimer = onNavigateToTimer,
        onNavigateToFlashcard = onNavigateToFlashcard,
        onLogout = onLogout // Pass logout to a button on this screen
    )

}

/**
 * Placeholder for your Flashcard Review Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(
    onNavigateToTimer: () -> Unit,
    onNavigateToFlashcard: () -> Unit,
    onLogout: () -> Unit
)
{
    // A simple scaffold to hold the buttons for testing navigation
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study Buddy") },
                actions = {
                    Button(onClick = onLogout) {
                        Text("Logout")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Welcome! You are logged in.",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onNavigateToTimer) {
                Text("Go to Timer")
            }
            Button(onClick = onNavigateToFlashcard) {
                Text("Go to Flashcard")
            }

        }
    }
}
