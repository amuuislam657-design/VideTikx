package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

object VideoCategories {
    val ALL_FILTERS = listOf(
        "All",
        "Shorts",
        "Music",
        "Gaming",
        "Tech",
        "Vlogs",
        "Education",
        "Cinema",
        "Other"
    )

    val UPLOAD_OPTIONS = listOf(
        "Vlogs",
        "Music",
        "Gaming",
        "Tech",
        "Education",
        "Cinema",
        "Other"
    )
}

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val category: String,
    val isShort: Boolean,
    val videoFilePath: String,
    val thumbnailFilePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val width: Int,
    val height: Int,
    val channelId: String,
    val creatorName: String,
    val creatorAvatarPath: String? = null,
    val viewsCount: Int = 0,
    val likesCount: Int = 0,
    val dislikesCount: Int = 0,
    /** 0 = none, 1 = liked, -1 = disliked */
    val userReaction: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "comments",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["videoId"])]
)
data class CommentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val videoId: String,
    val authorName: String,
    val authorAvatarPath: String? = null,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val handle: String,
    val bio: String,
    val accentColorHex: Long,
    val isSubscribed: Boolean = true,
    val isDefaultSelf: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class VideoCommentCount(
    val videoId: String,
    val count: Int
)

data class StorageStats(
    val videosBytes: Long = 0L,
    val thumbnailsBytes: Long = 0L,
    val avatarsBytes: Long = 0L,
    val databaseBytes: Long = 0L,
    val videoCount: Int = 0,
    val shortsCount: Int = 0,
    val orphanFilesCount: Int = 0
) {
    val totalBytes: Long
        get() = videosBytes + thumbnailsBytes + avatarsBytes + databaseBytes
}
