# Materialdrain

Materialdrain is a modern Android client for Pixeldrain, built with Jetpack Compose and Material 3. It provides a native interface for uploading, managing, and viewing your Pixeldrain files.

## Features

*   **File Upload:**
    *   Upload files from your device.
    *   Directly paste and upload text snippets.
    *   Progress tracking for uploads.
    *   Preview for selected image, text, and audio files before uploading.
*   **File Management:**
    *   View a list of your uploaded files.
    *   Sort files by name, size, or upload date (ascending/descending).
    *   Filter files by name.
    *   View detailed information for each file (name, size, MIME type, upload date, views, downloads, SHA256 hash).
    *   Download files directly.
    *   Delete files (if `can_edit` permission is available).
*   **User Interface:**
    *   Clean, modern UI based on Material 3 components and theming (including dynamic color support).
    *   Tab-based navigation for Upload, Files, Lists (placeholder), and Settings.
    *   Responsive screen layouts.
    *   Informative dialogs for errors, confirmations, and API key input.
*   **Settings:**
    *   Configure your Pixeldrain API key for authenticated access.

## Tech Stack & Key Libraries

*   **Kotlin:** Primary programming language.
*   **Jetpack Compose:** Modern declarative UI toolkit for Android.
*   **Material 3:** Latest design system for UI components and theming.
*   **Ktor Client:** For making HTTP requests to the Pixeldrain API (uploads, file info, user files).
    *   Content negotiation with `kotlinx.serialization`.
    *   Streaming for file uploads.
*   **ViewModel:** Part of Android Jetpack's Architecture Components for managing UI-related data.
*   **Coroutines & Flow:** For asynchronous operations and reactive data streams.
*   **Coil:** For image loading (thumbnails).
*   **Android Core KTX:** Kotlin extensions for a more idiomatic Kotlin experience.
*   **Splash Screen API:** For a smooth app startup experience.

## Setup

1.  Clone the repository:
    ```bash
    git clone https://github.com/fidesosu/Materialdrain.git
    ```
2.  Open the project in Android Studio (latest stable version recommended).
3.  The project uses Gradle for dependency management. Sync the project with Gradle files.
4.  To use features that require authentication (like viewing your files or uploading to your account), you will need to obtain a Pixeldrain API key and enter it in the app's Settings screen.

## Future Work (Potential)

*   Implement the "Lists" feature for creating and managing Pixeldrain lists.
*   More robust error handling and user feedback.
