# Materialdrain

Materialdrain is a modern Android client for Pixeldrain, built with Jetpack Compose and Material 3. It offers a native interface for uploading, managing, and viewing your Pixeldrain files.

## Features

*   **File Uploads:**
    *   Upload files directly from your device.
    *   Paste and upload text snippets.
    *   Track upload progress with byte counts.
    *   **Comprehensive Previews:**
        *   Interactive previews for images, GIFs, videos (with playback), and audio (with playback controls) before uploading.
        *   Text content preview for common text-based file types.
        *   Icon representation for APKs (app icon) and PDFs.
        *   Display of relevant metadata during selection (e.g., audio tags, video duration).
*   **File Management:**
    *   View your list of uploaded files, with thumbnails for media.
    *   Sort files by name, size, or upload date (ascending/descending).
    *   Filter files by name.
    *   **Detailed File Information & Previews:**
        *   Access comprehensive details for each file (name, size, MIME type, upload date, views, downloads, SHA256 hash).
        *   View full-screen, interactive previews for images, GIFs, and videos.
        *   Preview content of text-based files directly.
    *   Download files to your device with progress indication.
    *   Delete files (requires `can_edit` permission).
*   **User Interface:**
    *   Clean, contemporary UI leveraging Material 3 components and theming, including dynamic color support.
    *   Intuitive tab-based navigation: Upload, Files, Lists (placeholder), and Settings.
    *   Responsive layouts for various screen sizes.
    *   Clear dialogs for errors, confirmations, and API key input.
*   **Settings:**
    *   Configure your Pixeldrain API key for authenticated account access.

## Tech Stack & Key Libraries

Materialdrain utilizes a modern Android development stack:

*   **Kotlin:** The primary programming language.
*   **Jetpack Compose:** For building a declarative UI.
*   **Material 3:** The latest design system for UI components and theming.
*   **Ktor Client:** For handling HTTP requests to the Pixeldrain API, featuring:
    *   Content negotiation with `kotlinx.serialization`.
    *   Efficient streaming for file uploads.
*   **ViewModel:** From Android Jetpack's Architecture Components, for managing UI-related data.
*   **Coroutines & Flow:** For asynchronous operations and reactive data streams.
*   **Coil:** For optimized image loading (e.g., thumbnails).
*   **Android Core KTX:** Kotlin extensions for enhanced Android development.
*   **Splash Screen API:** For a smooth application startup experience.

## Setup

1.  Clone the repository:
    ```bash
    git clone https://github.com/fidesosu/Materialdrain.git
    ```
2.  Open the project in Android Studio (current stable version recommended).
3.  Sync the project with its Gradle files.
4.  To use authenticated features (like managing your files or uploading to your account), obtain a Pixeldrain API key and enter it in the app's Settings screen.

## Future Work

*   Implement the "Lists" feature for creating and managing Pixeldrain lists.
*   Enhance error handling and user feedback mechanisms.
