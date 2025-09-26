# Important
I apologize if you try to build and run the app. Currently the github repo probably doesn't have all the necessary files needed to build it from source. I'll try to get them all in near future. 

In the mean time please use the built apps in the Releases page.

---
# Materialdrain

Materialdrain is a modern Android client for Pixeldrain, built with Jetpack Compose and Material 3. It offers a native interface for uploading, managing, and viewing your Pixeldrain files.

## Features

### ✔️ Available Now

*   **File Upload & Management:**
    *   Upload single files from your device.
    *   Upload text snippets as files.
    *   Preview selected files (images, audio, video, text, PDF metadata, APK metadata) *before* uploading.
    *   View real-time upload progress (bytes and percentage).
    *   Securely set your Pixeldrain API Key via the Settings screen.
      
*   **File Listing & Detailed View ("Files" Screen):**
    *   List all your uploaded files with key details.
    *   Sort files by Name, Size, or Upload Date (ascending/descending).
    *   Filter files by name.
    *   Pull-to-refresh the file list.
    *   Open a detailed view for any file, showing:
        *   Comprehensive metadata.
        *   Rich media previews (images, GIFs, video with ExoPlayer, audio).
        *   Text content preview for supported file types.
    *   Download your files with progress updates.
    *   Delete files from your account (with a confirmation dialog).
      
*   **Filesystem View (Beta - "Filesystem" Screen):**
    *   Browse your Pixeldrain account storage in a familiar folder/file hierarchy.
    *   Navigate into and out of directories.
    *   View basic file and folder information (name, size, type, modification date).
    *   Display thumbnails for images and some video files directly in the list.
    *   Pull-to-refresh the current directory view.
      
*   **General User Experience:**
    *   Intuitive bottom navigation for main app sections (Upload, Files, Lists, Filesystem).
    *   Contextual Top App Bar with screen titles and relevant actions (e.g., Settings, Back).
    *   Extended Floating Action Buttons (FABs) for primary actions on each screen.
    *   Informative Snackbars for operation status (e.g., upload/download/delete success/failure, settings saved).
    *   User-friendly dialogs for confirmations, errors, and input (e.g., API key missing, delete confirmation).
    *   Smooth animated transitions between screens.
    *   Support for system dark/light themes (via Material 3).
    *   Video playback caching (via ExoPlayer).
    *   Initial app splash screen.

### ⏳ In Development / Planned

*   **Lists Screen Functionality:**
    *   View and manage Pixeldrain lists.
    *   Create new lists and add files to them.
*   **Filesystem View Enhancements:**
    *   Download files/folders directly from the filesystem view.
    *   Full preview capabilities for files within the filesystem view.
    *   File/folder manipulation (e.g., rename, move, delete) within the filesystem view.
    *   Create new folders.
    *   Upload files directly to a specific path within the filesystem.
*   **Upload Enhancements:**
    *   Support for uploading multiple files simultaneously.

### ❌ Not Currently Planned (or Low Priority)

*   Nothing has come to mind that I definitely won't add

## 🛠️ Development Status

Every feature listed under "Available Now" or "In Development / Planned" in this README is intended for implementation and will almost definitely be implemented as long as pixeldrain remains a functional service. 

The app also needs some optimizations concerning the download and upload speed (currently 8-10MB/s, it's usable, but it can definitely be increased.)
