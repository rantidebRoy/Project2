# Study Buddy 🎓

Study Buddy is a comprehensive, all-in-one Android application designed to help students manage their studies efficiently. Built with modern Android development practices using **Jetpack Compose** and **Firebase**, it combines essential study tools like flashcards, timers, and a collaborative Q&A platform to enhance the learning experience.

## 🚀 Key Features

-   **🗂️ Interactive Flashcards:**
    -   Create and organize study materials into custom topics.
    -   Practice with an interactive study mode, including shuffling for better retention.
    -   Quickly add new cards and topics to keep your study sessions up-to-date.

-   **⏱️ Focus Timer:**
    -   Improve concentration using a built-in study timer.
    -   Designed to help you stay focused during deep study sessions.

-   **📅 Study Scheduler:**
    -   Keep track of upcoming exams, assignments, and study sessions.
    -   Receive background notifications for scheduled events so you never miss a deadline.

-   **🤝 Collaborative Q&A:**
    -   A community-driven platform to ask study-related questions.
    -   Interact with fellow students by submitting answers and sharing knowledge.

-   **👤 User Profile & Auth:**
    -   Secure authentication using **Firebase Auth** (Login, Signup, Password Reset).
    -   Personalized profile management to track your study progress.

## 🛠️ Technology Stack

-   **Language:** [Kotlin](https://kotlinlang.org/)
-   **UI Framework:** [Jetpack Compose](https://developer.android.com/jetcompose) (Modern, declarative UI toolkit)
-   **Backend:** [Firebase](https://firebase.google.com/)
    -   **Firestore:** Real-time NoSQL database for flashcards, events, and Q&A.
    -   **Authentication:** Secure user login and management.
-   **Architecture:** MVVM (Model-View-ViewModel) for clean, testable code.
-   **Image Loading:** [Coil](https://coil-kt.github.io/coil/) (Image loading library for Android).
-   **Media Storage:** [Cloudinary](https://cloudinary.com/documentation/android_integration) (Integrated for media handling).
-   **Background Tasks:** [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) for notification scheduling.
-   **Dependency Management:** Gradle Version Catalog.

## 📥 Installation

1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/your-username/Study-Buddy.git
    ```
2.  **Open in Android Studio:**
    -   Select "Open" and navigate to the project directory.
3.  **Firebase Setup:**
    -   Create a new Firebase project at [Firebase Console](https://console.firebase.google.com/).
    -   Add an Android app with the package name `com.example.flashcard`.
    -   Download the `google-services.json` file and place it in the `app/` directory.
    -   Enable **Email/Password Authentication** and **Cloud Firestore**.
4.  **Build & Run:**
    -   Sync the project with Gradle files.
    -   Run the app on an emulator or a physical device.

## 📂 Project Structure

-   `MainActivity.kt`: Entry point and navigation graph.
-   `ui/theme/`: Material 3 design tokens and theme configuration.
-   Components:
    -   `FlashcardScreen.kt`: Manage and study flashcards.
    -   `SchedulerScreen.kt`: Event management and scheduling.
    -   `QnAScreen.kt`: Collaborative learning platform.
    -   `TimerModel.kt`: Business logic for the study timer.
-   `MidnightReceiver.kt` & `MidnightWorker.kt`: Background workers for scheduled management.

## 🧪 Testing

The project includes unit and instrumentation tests to ensure reliability:
-   JUnit for unit testing logic.
-   Espresso & Compose Test Library for UI testing.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

*Happy Studying!* 📖✨
