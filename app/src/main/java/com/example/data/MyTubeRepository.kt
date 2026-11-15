package com.example.data

import android.net.Uri
import com.example.data.local.ChannelEntity
import com.example.data.local.CommentEntity
import com.example.data.local.ExtractedVideoMetadata
import com.example.data.local.LocalMediaStorage
import com.example.data.local.MyTubeDatabase
import com.example.data.local.StorageStats
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserProfile
import com.example.data.local.VideoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

class MyTubeRepository(
    private val database: MyTubeDatabase,
    private val prefs: UserPreferencesRepository,
    private val mediaStorage: LocalMediaStorage
) {
    private val videoDao = database.videoDao()
    private val commentDao = database.commentDao()
    private val channelDao = database.channelDao()

    val isDarkTheme: Flow<Boolean> = prefs.isDarkTheme
    val userProfile: Flow<UserProfile> = prefs.userProfile
    val allVideos: Flow<List<VideoEntity>> = videoDao.observeAllVideos()
    val shortsVideos: Flow<List<VideoEntity>> = videoDao.observeShorts()
    val allChannels: Flow<List<ChannelEntity>> = channelDao.observeAllChannels()
    val commentCounts: Flow<Map<String, Int>> = commentDao.observeCommentCounts()
        .map { list -> list.associate { it.videoId to it.count } }

    fun observeVideo(videoId: String): Flow<VideoEntity?> = videoDao.observeVideoById(videoId)

    fun observeComments(videoId: String): Flow<List<CommentEntity>> =
        commentDao.observeCommentsForVideo(videoId)

    suspend fun setDarkTheme(isDark: Boolean) {
        prefs.setDarkTheme(isDark)
    }

    suspend fun updateProfile(name: String, bio: String) {
        prefs.updateProfile(name, bio)
        val profile = prefs.userProfile.first()
        channelDao.updateSelfChannelName(profile.name)
        videoDao.syncSelfCreatorInfo(profile.name, profile.avatarPath)
    }

    suspend fun updateProfileAvatar(uri: Uri) {
        val current = prefs.userProfile.first()
        val savedPath = mediaStorage.saveProfileAvatar(uri, current.avatarPath)
        prefs.updateProfileAvatar(savedPath)
        videoDao.syncSelfCreatorInfo(current.name, savedPath)
    }

    suspend fun inspectVideo(uri: Uri): ExtractedVideoMetadata = mediaStorage.inspectVideoUri(uri)

    suspend fun publishVideo(
        videoUri: Uri,
        thumbnailUri: Uri?,
        title: String,
        description: String,
        category: String,
        isShort: Boolean,
        channelId: String,
        onProgress: (Float) -> Unit
    ): VideoEntity {
        val (savedVideoPath, fileSizeBytes) = mediaStorage.saveVideoFile(videoUri) { copyProgress ->
            onProgress(copyProgress * 0.85f)
        }
        onProgress(0.90f)

        val (durationMs, dimensions) = mediaStorage.extractMetadataFromFile(savedVideoPath)
        val savedThumbPath = mediaStorage.saveThumbnailFromUriOrVideo(thumbnailUri, savedVideoPath)
        onProgress(0.96f)

        val profile = prefs.userProfile.first()
        val channel = channelDao.getChannelById(channelId)
        val creatorName = if (channelId == "self" || channel == null) profile.name else channel.name
        val avatarPath = if (channelId == "self" || channel == null) profile.avatarPath else null

        val entity = VideoEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            description = description.trim(),
            category = category,
            isShort = isShort,
            videoFilePath = savedVideoPath,
            thumbnailFilePath = savedThumbPath,
            durationMs = durationMs,
            fileSizeBytes = fileSizeBytes,
            width = dimensions.first,
            height = dimensions.second,
            channelId = channel?.id ?: "self",
            creatorName = creatorName,
            creatorAvatarPath = avatarPath
        )

        videoDao.insertVideo(entity)
        onProgress(1f)
        return entity
    }

    suspend fun updateVideoDetails(
        videoId: String,
        title: String,
        description: String,
        category: String,
        isShort: Boolean,
        newThumbnailUri: Uri?
    ) {
        val existing = videoDao.getVideoById(videoId) ?: return
        var thumbPath = existing.thumbnailFilePath
        if (newThumbnailUri != null) {
            val replacement = mediaStorage.saveThumbnailFromUriOrVideo(
                customThumbnailUri = newThumbnailUri,
                savedVideoPath = existing.videoFilePath
            )
            mediaStorage.deleteFileIfExists(existing.thumbnailFilePath)
            thumbPath = replacement
        }
        videoDao.updateVideo(
            existing.copy(
                title = title.trim().ifBlank { existing.title },
                description = description.trim(),
                category = category,
                isShort = isShort,
                thumbnailFilePath = thumbPath
            )
        )
    }

    suspend fun deleteVideo(video: VideoEntity) {
        videoDao.deleteVideoById(video.id)
        mediaStorage.deleteFileIfExists(video.videoFilePath)
        mediaStorage.deleteFileIfExists(video.thumbnailFilePath)
    }

    suspend fun incrementViews(videoId: String) {
        videoDao.incrementViews(videoId)
    }

    suspend fun toggleLike(videoId: String) {
        videoDao.toggleLike(videoId)
    }

    suspend fun toggleDislike(videoId: String) {
        videoDao.toggleDislike(videoId)
    }

    suspend fun addComment(videoId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val profile = prefs.userProfile.first()
        commentDao.insertComment(
            CommentEntity(
                videoId = videoId,
                authorName = profile.name,
                authorAvatarPath = profile.avatarPath,
                text = trimmed
            )
        )
    }

    suspend fun deleteComment(commentId: String) {
        commentDao.deleteCommentById(commentId)
    }

    suspend fun toggleChannelSubscription(channelId: String, subscribe: Boolean) {
        channelDao.setSubscribed(channelId, subscribe)
    }

    suspend fun createChannel(name: String, handle: String, bio: String): ChannelEntity {
        val palette = listOf(0xFFFF2E4C, 0xFF6366F1, 0xFF10B981, 0xFFF59E0B, 0xFF06B6D4, 0xFFEC4899)
        val cleanName = name.trim()
        val cleanHandle = handle.trim().let {
            when {
                it.isBlank() -> "@" + cleanName.lowercase().replace(Regex("[^a-z0-9]"), "")
                it.startsWith("@") -> it
                else -> "@$it"
            }
        }
        val channel = ChannelEntity(
            name = cleanName,
            handle = cleanHandle,
            bio = bio.trim().ifBlank { "Offline local creator channel" },
            accentColorHex = palette.random(),
            isSubscribed = true,
            isDefaultSelf = false
        )
        channelDao.insertChannel(channel)
        return channel
    }

    suspend fun computeStorageStats(videos: List<VideoEntity>): StorageStats {
        val videoPaths = videoDao.getAllVideoPaths().toSet()
        val thumbPaths = videoDao.getAllThumbnailPaths().toSet()
        val shortsCount = videos.count { it.isShort }
        val standardCount = videos.size - shortsCount
        return mediaStorage.calculateStorageStats(
            referencedVideoPaths = videoPaths,
            referencedThumbnailPaths = thumbPaths,
            videoCount = standardCount,
            shortsCount = shortsCount
        )
    }

    suspend fun cleanupOrphanFiles(): Int {
        val videoPaths = videoDao.getAllVideoPaths().toSet()
        val thumbPaths = videoDao.getAllThumbnailPaths().toSet()
        return mediaStorage.cleanupOrphanFiles(videoPaths, thumbPaths)
    }
}
