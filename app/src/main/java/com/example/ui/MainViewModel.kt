package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MyTubeRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UploadUiState {
    data object Idle : UploadUiState
    data class Uploading(val progress: Float) : UploadUiState
    data class Success(val videoId: String, val isShort: Boolean) : UploadUiState
    data class Error(val message: String) : UploadUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MyTubeRepository(
        database = MyTubeDatabase.getInstance(application),
        prefs = UserPreferencesRepository(application),
        mediaStorage = LocalMediaStorage(application)
    )

    val isDarkTheme: StateFlow<Boolean> = repository.isDarkTheme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true
    )

    val userProfile: StateFlow<UserProfile> = repository.userProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserProfile()
    )

    val allVideos: StateFlow<List<VideoEntity>> = repository.allVideos.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val shortsVideos: StateFlow<List<VideoEntity>> = repository.shortsVideos.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val allChannels: StateFlow<List<ChannelEntity>> = repository.allChannels.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val commentCounts: StateFlow<Map<String, Int>> = repository.commentCounts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap()
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    val filteredHomeVideos: StateFlow<List<VideoEntity>> = combine(
        allVideos,
        _searchQuery,
        _selectedCategory
    ) { videos, query, category ->
        val trimmedQuery = query.trim().lowercase()
        videos.filter { video ->
            val matchesCategory = when (category) {
                "All" -> true
                "Shorts" -> video.isShort
                else -> video.category.equals(category, ignoreCase = true)
            }
            val matchesQuery = trimmedQuery.isEmpty() ||
                video.title.lowercase().contains(trimmedQuery) ||
                video.description.lowercase().contains(trimmedQuery) ||
                video.creatorName.lowercase().contains(trimmedQuery)
            matchesCategory && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uploadState: StateFlow<UploadUiState> = _uploadState.asStateFlow()

    private val _uploadDefaultShort = MutableStateFlow(false)
    val uploadDefaultShort: StateFlow<Boolean> = _uploadDefaultShort.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        viewModelScope.launch {
            allVideos.collect { videos ->
                _storageStats.value = repository.computeStorageStats(videos)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun toggleTheme() {
        viewModelScope.launch {
            repository.setDarkTheme(!isDarkTheme.value)
        }
    }

    fun setDarkTheme(isDark: Boolean) {
        viewModelScope.launch {
            repository.setDarkTheme(isDark)
        }
    }

    fun setUploadDefaultShort(isShort: Boolean) {
        _uploadDefaultShort.value = isShort
    }

    fun updateProfile(name: String, bio: String) {
        viewModelScope.launch {
            repository.updateProfile(name, bio)
            _statusMessage.value = "Profile saved locally"
        }
    }

    fun updateProfileAvatar(uri: Uri) {
        viewModelScope.launch {
            try {
                repository.updateProfileAvatar(uri)
                _statusMessage.value = "Profile picture updated"
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Could not update profile picture"
            }
        }
    }

    suspend fun inspectVideoUri(uri: Uri): ExtractedVideoMetadata {
        return repository.inspectVideo(uri)
    }

    fun publishVideo(
        videoUri: Uri,
        thumbnailUri: Uri?,
        title: String,
        description: String,
        category: String,
        isShort: Boolean,
        channelId: String
    ) {
        if (title.isBlank()) {
            _uploadState.value = UploadUiState.Error("Please enter a video title.")
            return
        }
        viewModelScope.launch {
            _uploadState.value = UploadUiState.Uploading(0.02f)
            try {
                val created = repository.publishVideo(
                    videoUri = videoUri,
                    thumbnailUri = thumbnailUri,
                    title = title,
                    description = description,
                    category = category,
                    isShort = isShort,
                    channelId = channelId,
                    onProgress = { progress ->
                        _uploadState.value = UploadUiState.Uploading(progress)
                    }
                )
                _uploadState.value = UploadUiState.Success(created.id, created.isShort)
            } catch (e: Exception) {
                _uploadState.value = UploadUiState.Error(
                    e.message ?: "Failed to save video offline."
                )
            }
        }
    }

    fun resetUploadState() {
        _uploadState.value = UploadUiState.Idle
    }

    fun updateVideoDetails(
        videoId: String,
        title: String,
        description: String,
        category: String,
        isShort: Boolean,
        newThumbnailUri: Uri?
    ) {
        viewModelScope.launch {
            repository.updateVideoDetails(
                videoId = videoId,
                title = title,
                description = description,
                category = category,
                isShort = isShort,
                newThumbnailUri = newThumbnailUri
            )
            _statusMessage.value = "Video updated"
        }
    }

    fun deleteVideo(video: VideoEntity) {
        viewModelScope.launch {
            repository.deleteVideo(video)
            _statusMessage.value = "Deleted \"${video.title}\""
        }
    }

    fun observeVideo(videoId: String): Flow<VideoEntity?> = repository.observeVideo(videoId)

    fun observeComments(videoId: String): Flow<List<CommentEntity>> =
        repository.observeComments(videoId)

    fun recordView(videoId: String) {
        viewModelScope.launch {
            repository.incrementViews(videoId)
        }
    }

    fun toggleLike(videoId: String) {
        viewModelScope.launch {
            repository.toggleLike(videoId)
        }
    }

    fun toggleDislike(videoId: String) {
        viewModelScope.launch {
            repository.toggleDislike(videoId)
        }
    }

    fun addComment(videoId: String, text: String) {
        viewModelScope.launch {
            repository.addComment(videoId, text)
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            repository.deleteComment(commentId)
        }
    }

    fun toggleChannelSubscription(channelId: String, subscribe: Boolean) {
        viewModelScope.launch {
            repository.toggleChannelSubscription(channelId, subscribe)
        }
    }

    fun createChannel(name: String, handle: String, bio: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createChannel(name, handle, bio)
            _statusMessage.value = "Channel \"$name\" created"
        }
    }

    fun cleanupOrphanFiles() {
        viewModelScope.launch {
            val cleaned = repository.cleanupOrphanFiles()
            _storageStats.value = repository.computeStorageStats(allVideos.value)
            _statusMessage.value = if (cleaned > 0) {
                "Freed $cleaned unused cache file(s)"
            } else {
                "Offline storage is already clean"
            }
        }
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }
}
