package com.example.flashcard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
fun AuthScreen(
    // ViewModel is provided by Compose/Hilt if configured correctly.
    // Use the `SessionManager`'s dependency injection in the parent Activity/Application.
    viewModel: LoginViewModel = viewModel(),
    onLoginSuccess: () -> Unit
) {
    // Correctly observe the state variables directly from the ViewModel.
    // Since the ViewModel properties are defined using 'by mutableStateOf',
    // Compose automatically recomposes when they change.
    val isLoading = viewModel.authState == AuthState.LOADING
    val isInputValid = viewModel.email.isNotBlank() && viewModel.password.isNotBlank()
    val errorMessage = viewModel.errorMessage

    // Use LaunchedEffect to handle navigation on successful authentication
    LaunchedEffect(viewModel.authState) {
        if (viewModel.authState == AuthState.SUCCESS) {
            onLoginSuccess()
            // Reset the state *after* successful navigation to clean up for future use
            viewModel.resetState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Study Buddy Login", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))

        // Email Field - Direct update to ViewModel state
        OutlinedTextField(
            value = viewModel.email,
            onValueChange = { viewModel.email = it }, // State is updated here
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Password Field - Direct update to ViewModel state
        OutlinedTextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it }, // State is updated here
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Login Button
        Button(
            onClick = { viewModel.login() },
            modifier = Modifier.fillMaxWidth(),
            enabled = isInputValid && !isLoading
        ) {
            Text("Login")
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Sign Up Button
        TextButton(
            onClick = { viewModel.signUp() },
            modifier = Modifier.fillMaxWidth(),
            enabled = isInputValid && !isLoading
        ) {
            Text("Don't have an account? Sign Up")
        }

        // Loading Indicator
        if (isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }

        // Error Message
        // Check if the error message is not null or blank before displaying
        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            // Show a button to reset the error state
            TextButton(onClick = { viewModel.resetState() }) {
                Text("Dismiss")
            }
        }
    }
}
