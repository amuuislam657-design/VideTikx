package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY createdAt DESC")
    fun observeAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE isShort = 1 ORDER BY createdAt DESC")
    fun observeShorts(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE id = :videoId LIMIT 1")
    fun observeVideoById(videoId: String): Flow<VideoEntity?>

    @Query("SELECT * FROM videos WHERE id = :videoId LIMIT 1")
    suspend fun getVideoById(videoId: String): VideoEntity?

    @Query("SELECT videoFilePath FROM videos")
    suspend fun getAllVideoPaths(): List<String>

    @Query("SELECT thumbnailFilePath FROM videos")
    suspend fun getAllThumbnailPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity)

    @Update
    suspend fun updateVideo(video: VideoEntity)

    @Query("DELETE FROM videos WHERE id = :videoId")
    suspend fun deleteVideoById(videoId: String)

    @Query("UPDATE videos SET viewsCount = viewsCount + 1 WHERE id = :videoId")
    suspend fun incrementViews(videoId: String)

    @Query("UPDATE videos SET creatorName = :name, creatorAvatarPath = :avatarPath WHERE channelId = 'self'")
    suspend fun syncSelfCreatorInfo(name: String, avatarPath: String?)

    @Transaction
    suspend fun toggleLike(videoId: String) {
        val video = getVideoById(videoId) ?: return
        val updated = when (video.userReaction) {
            1 -> video.copy(
                userReaction = 0,
                likesCount = (video.likesCount - 1).coerceAtLeast(0)
            )
            -1 -> video.copy(
                userReaction = 1,
                likesCount = video.likesCount + 1,
                dislikesCount = (video.dislikesCount - 1).coerceAtLeast(0)
            )
            else -> video.copy(
                userReaction = 1,
                likesCount = video.likesCount + 1
            )
        }
        updateVideo(updated)
    }

    @Transaction
    suspend fun toggleDislike(videoId: String) {
        val video = getVideoById(videoId) ?: return
        val updated = when (video.userReaction) {
            -1 -> video.copy(
                userReaction = 0,
                dislikesCount = (video.dislikesCount - 1).coerceAtLeast(0)
            )
            1 -> video.copy(
                userReaction = -1,
                dislikesCount = video.dislikesCount + 1,
                likesCount = (video.likesCount - 1).coerceAtLeast(0)
            )
            else -> video.copy(
                userReaction = -1,
                dislikesCount = video.dislikesCount + 1
            )
        }
        updateVideo(updated)
    }
}

@Dao
interface CommentDao {
    @Query("SELECT * FROM comments WHERE videoId = :videoId ORDER BY createdAt DESC")
    fun observeCommentsForVideo(videoId: String): Flow<List<CommentEntity>>

    @Query("SELECT videoId, COUNT(*) AS count FROM comments GROUP BY videoId")
    fun observeCommentCounts(): Flow<List<VideoCommentCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)

    @Query("DELETE FROM comments WHERE id = :commentId")
    suspend fun deleteCommentById(commentId: String)
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY isDefaultSelf DESC, createdAt ASC")
    fun observeAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :channelId LIMIT 1")
    suspend fun getChannelById(channelId: String): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("UPDATE channels SET isSubscribed = :subscribed WHERE id = :channelId")
    suspend fun setSubscribed(channelId: String, subscribed: Boolean)

    @Query("UPDATE channels SET name = :name WHERE id = 'self'")
    suspend fun updateSelfChannelName(name: String)

    @Query("DELETE FROM channels WHERE id = :channelId AND isDefaultSelf = 0")
    suspend fun deleteChannel(channelId: String)
}
