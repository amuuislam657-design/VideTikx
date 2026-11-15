package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ExtractedVideoMetadata(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val displayName: String
) {
    val isPortrait: Boolean get() = height > width && width > 0
}

class LocalMediaStorage(private val context: Context) {

    private val videosDir: File
        get() = File(context.filesDir, "videos").apply { if (!exists()) mkdirs() }

    private val thumbnailsDir: File
        get() = File(context.filesDir, "thumbnails").apply { if (!exists()) mkdirs() }

    private val avatarsDir: File
        get() = File(context.filesDir, "avatars").apply { if (!exists()) mkdirs() }

    suspend fun inspectVideoUri(uri: Uri): ExtractedVideoMetadata = withContext(Dispatchers.IO) {
        var durationMs = 0L
        var width = 1920
        var height = 1080
        var fileSizeBytes = 0L
        var displayName = "video.mp4"

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) fileSizeBytes = cursor.getLong(sizeIdx)
            }
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0

            if (rawW > 0 && rawH > 0) {
                if (rotation == 90 || rotation == 270) {
                    width = rawH
                    height = rawW
                } else {
                    width = rawW
                    height = rawH
                }
            }
        } catch (_: Exception) {
            // Keep safe defaults if container metadata isn't readable before copy
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        ExtractedVideoMetadata(
            durationMs = durationMs,
            width = width,
            height = height,
            fileSizeBytes = fileSizeBytes,
            displayName = displayName
        )
    }

    suspend fun saveVideoFile(
        uri: Uri,
        onProgress: (Float) -> Unit
    ): Pair<String, Long> = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri)
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?.takeIf { it.isNotBlank() }
            ?: guessExtensionFromName(uri)
            ?: "mp4"

        val totalBytes = queryFileSize(uri)
        val destFile = File(videosDir, "vid_${UUID.randomUUID()}.$ext")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(64 * 1024)
                var copied = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    copied += read
                    if (totalBytes > 0) {
                        onProgress((copied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.98f))
                    }
                }
                output.flush()
            }
        } ?: throw IllegalStateException("Unable to open selected video file.")

        if (destFile.length() == 0L) {
            destFile.delete()
            throw IllegalStateException("Selected video file is empty.")
        }

        onProgress(1f)
        destFile.absolutePath to destFile.length()
    }

    suspend fun saveThumbnailFromUriOrVideo(
        customThumbnailUri: Uri?,
        savedVideoPath: String
    ): String = withContext(Dispatchers.IO) {
        val destFile = File(thumbnailsDir, "thumb_${UUID.randomUUID()}.jpg")

        if (customThumbnailUri != null) {
            val decoded = context.contentResolver.openInputStream(customThumbnailUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            if (decoded != null) {
                FileOutputStream(destFile).use { out ->
                    decoded.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
                decoded.recycle()
                return@withContext destFile.absolutePath
            }
        }

        // Auto-extract frame from video if no custom thumbnail or decode failed
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(savedVideoPath)
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L)
            if (frame != null) {
                FileOutputStream(destFile).use { out ->
                    frame.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                frame.recycle()
                return@withContext destFile.absolutePath
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        // Fallback dark frame bitmap so path is always valid
        val fallback = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        fallback.eraseColor(android.graphics.Color.rgb(20, 20, 26))
        FileOutputStream(destFile).use { out ->
            fallback.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        fallback.recycle()
        destFile.absolutePath
    }

    suspend fun saveProfileAvatar(uri: Uri, oldAvatarPath: String?): String = withContext(Dispatchers.IO) {
        val destFile = File(avatarsDir, "avatar_${System.currentTimeMillis()}.jpg")
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: throw IllegalStateException("Unable to read selected image.")

        FileOutputStream(destFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()

        if (!oldAvatarPath.isNullOrBlank() && oldAvatarPath != destFile.absolutePath) {
            runCatching { File(oldAvatarPath).delete() }
        }
        destFile.absolutePath
    }

    suspend fun extractMetadataFromFile(videoPath: String): Pair<Long, Pair<Int, Int>> =
        withContext(Dispatchers.IO) {
            var durationMs = 0L
            var width = 1920
            var height = 1080
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(videoPath)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0
                if (rawW > 0 && rawH > 0) {
                    if (rotation == 90 || rotation == 270) {
                        width = rawH
                        height = rawW
                    } else {
                        width = rawW
                        height = rawH
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
            durationMs to (width to height)
        }

    suspend fun deleteFileIfExists(path: String?) = withContext(Dispatchers.IO) {
        if (!path.isNullOrBlank()) {
            runCatching {
                val file = File(path)
                if (file.exists()) file.delete()
            }
        }
    }

    suspend fun calculateStorageStats(
        referencedVideoPaths: Set<String>,
        referencedThumbnailPaths: Set<String>,
        videoCount: Int,
        shortsCount: Int
    ): StorageStats = withContext(Dispatchers.IO) {
        val videoFiles = videosDir.listFiles().orEmpty()
        val thumbFiles = thumbnailsDir.listFiles().orEmpty()
        val avatarFiles = avatarsDir.listFiles().orEmpty()

        val videosBytes = videoFiles.sumOf { it.length() }
        val thumbnailsBytes = thumbFiles.sumOf { it.length() }
        val avatarsBytes = avatarFiles.sumOf { it.length() }
        val dbFile = context.getDatabasePath(MyTubeDatabase.DATABASE_NAME)
        val dbBytes = if (dbFile.exists()) dbFile.length() else 0L

        val orphanVideos = videoFiles.count { it.absolutePath !in referencedVideoPaths }
        val orphanThumbs = thumbFiles.count { it.absolutePath !in referencedThumbnailPaths }

        StorageStats(
            videosBytes = videosBytes,
            thumbnailsBytes = thumbnailsBytes,
            avatarsBytes = avatarsBytes,
            databaseBytes = dbBytes,
            videoCount = videoCount,
            shortsCount = shortsCount,
            orphanFilesCount = orphanVideos + orphanThumbs
        )
    }

    suspend fun cleanupOrphanFiles(
        referencedVideoPaths: Set<String>,
        referencedThumbnailPaths: Set<String>
    ): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        videosDir.listFiles().orEmpty().forEach { file ->
            if (file.absolutePath !in referencedVideoPaths && file.delete()) {
                deleted++
            }
        }
        thumbnailsDir.listFiles().orEmpty().forEach { file ->
            if (file.absolutePath !in referencedThumbnailPaths && file.delete()) {
                deleted++
            }
        }
        deleted
    }

    private fun queryFileSize(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && idx >= 0 && !cursor.isNull(idx)) {
                return cursor.getLong(idx)
            }
        }
        return -1L
    }

    private fun guessExtensionFromName(uri: Uri): String? {
        val last = uri.lastPathSegment ?: return null
        val dot = last.lastIndexOf('.')
        if (dot in 0 until last.length - 1) {
            return last.substring(dot + 1).lowercase().take(5)
        }
        return null
    }
}
