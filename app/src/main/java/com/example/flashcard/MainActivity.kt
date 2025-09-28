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
private const val AUTH_ROUTE = "auth_screen"
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

    // Determine the starting destination: AuthScreen if not logged in, MainApp otherwise
    val startDestination = if (sessionManager.isLoggedIn()) MAIN_APP_ROUTE else AUTH_ROUTE

    // ViewModel for authentication, injected with the session manager
    val loginViewModel: LoginViewModel = viewModel(
        factory = LoginViewModelFactory(sessionManager)
    )

    // ViewModel for the main flashcard logic (assuming this is your main data model)
    // FlashcardViewModel is expected to be defined in FlashcardViewModel.kt
    val flashcardViewModel: FlashcardViewModel = viewModel()

    // ViewModel for the timer
    // TimerModel is expected to be defined in TimerModel.kt
    val timerModel: TimerModel = viewModel()

    // Define navigation graph
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // --- 1. Authentication Screen ---
        composable(AUTH_ROUTE) {
            // AuthScreen is expected to be defined in AuthScreen.kt
            AuthScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    // This callback is triggered on successful sign-in/up
                    // The ViewModel already saves the state, so we just navigate
                    navController.navigate(MAIN_APP_ROUTE) {
                        popUpTo(AUTH_ROUTE) { inclusive = true } // Remove auth screen from back stack
                    }
                }
            )
        }

        // --- 2. Main Application Flow ---
        composable(MAIN_APP_ROUTE) {
            // Placeholder for the main screen which will house sub-navigation or main features
            MainApplicationScreen(
                flashcardViewModel = flashcardViewModel,
                timerModel = timerModel,
                loginViewModel = loginViewModel, // Pass for logout functionality
                onNavigateToTimer = { navController.navigate(TIMER_ROUTE) },
                onNavigateToFlashcard = {navController.navigate(FLASHCARD_ROUTE)},
                onLogout = {
                    // Clear session and navigate back to auth screen
                    loginViewModel.logout()
                    navController.navigate(AUTH_ROUTE) {
                        popUpTo(MAIN_APP_ROUTE) { inclusive = true } // Clear main app from back stack
                    }
                }
            )
        }

        // --- 3. Timer Screen ---
        composable(TIMER_ROUTE) {
            // TimerScreen is expected to be defined in TimerScreen.kt
            TimerScreen(
                viewModel = timerModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(FLASHCARD_ROUTE) {
            // TimerScreen is expected to be defined in TimerScreen.kt
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
