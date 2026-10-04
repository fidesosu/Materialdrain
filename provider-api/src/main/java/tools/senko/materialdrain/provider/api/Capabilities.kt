package tools.senko.materialdrain.provider.api

/** What a [StorageProvider] can do. The UI reads these instead of guessing from which ops are non-null. */
enum class ProviderCapability {
    UPLOAD, DOWNLOAD, DELETE, FILE_INFO,
    BROWSE, MKDIR, RENAME,
    // A flat "give me everything on the account" call - distinct from BROWSE, which is hierarchical
    // (folders, mkdir, rename). Named to not read next to LISTS as a one-letter typo of it.
    ENUMERATE,
    SEARCH, SHARE_LINK, PERMISSIONS,
    USER_QUOTA, RICH_FILE_STATS, LISTS,
    // Several files at once as one zip archive (Pixeldrain: comma separated ids). See FileStoreOps.downloadArchive
    ARCHIVE_DOWNLOAD,
    // Looking inside an archive (zip, 7z, rar...) without downloading it. See ArchiveOps
    ARCHIVE_BROWSE
}

enum class ProviderKind { PIXELDRAIN, WEBDAV, S3, GENERIC_REST }
