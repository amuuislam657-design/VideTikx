package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.StorageStats
import com.example.data.local.UserProfile
import com.example.data.local.VideoEntity
import com.example.ui.components.AvatarImage
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.EditVideoDialog
import com.example.ui.components.formatDuration
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatUploadDate
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.TubeCrimson
import com.example.ui.theme.WarningAmber
import java.io.File

@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    allVideos: List<VideoEntity>,
    storageStats: StorageStats,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onUpdateProfile: (name: String, bio: String) -> Unit,
    onUpdateAvatar: (Uri) -> Unit,
    onCleanupOrphans: () -> Unit,
    onPlayVideo: (VideoEntity) -> Unit,
    onEditVideo: (VideoEntity, String, String, String, Boolean, Uri?) -> Unit,
    onDeleteVideo: (VideoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Videos, 1 = Shorts
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editingVideo by remember { mutableStateOf<VideoEntity?>(null) }
    var deletingVideo by remember { mutableStateOf<VideoEntity?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onUpdateAvatar(uri)
    }

    val standardVideos = remember(allVideos) { allVideos.filter { !it.isShort } }
    val shorts = remember(allVideos) { allVideos.filter { it.isShort } }
    val displayedVideos = if (selectedTab == 0) standardVideos else shorts
    val totalViews = remember(allVideos) { allVideos.sumOf { it.viewsCount } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile Card
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clickable {
                                    avatarPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .testTag("profile_avatar_picker")
                        ) {
                            AvatarImage(
                                avatarPath = userProfile.avatarPath,
                                contentDescription = "Profile picture",
                                size = 76.dp
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.CameraAlt,
                                        contentDescription = "Change profile picture",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = userProfile.bio.ifBlank { "No bio added yet" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { showEditProfileDialog = true },
                            modifier = Modifier.testTag("edit_profile_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit profile",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProfileStatTile(
                            label = "Videos",
                            value = standardVideos.size.toString(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("profile_video_count")
                        )
                        ProfileStatTile(
                            label = "Shorts",
                            value = shorts.size.toString(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("profile_shorts_count")
                        )
                        ProfileStatTile(
                            label = "Total Views",
                            value = totalViews.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Theme Settings Card
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Dark Mode",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (isDarkTheme) "Obsidian dark interface active" else "Clean editorial light mode active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = onToggleTheme,
                        modifier = Modifier.testTag("profile_theme_switch")
                    )
                }
            }
        }

        // Offline Storage Manager Card
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Offline Storage",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = formatFileSize(storageStats.totalBytes),
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("profile_total_storage")
                        )
                    }

                    // Segmented storage bar
                    val totalSafe = storageStats.totalBytes.coerceAtLeast(1L).toFloat()
                    val videoRatio = (storageStats.videosBytes / totalSafe).coerceIn(0f, 1f)
                    val thumbRatio = ((storageStats.thumbnailsBytes + storageStats.avatarsBytes) / totalSafe)
                        .coerceIn(0f, 1f - videoRatio)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        if (videoRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(videoRatio.coerceAtLeast(0.02f))
                                    .fillMaxHeight()
                                    .background(TubeCrimson)
                            )
                        }
                        if (thumbRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(thumbRatio.coerceAtLeast(0.02f))
                                    .fillMaxHeight()
                                    .background(WarningAmber)
                            )
                        }
                        val remaining = (1f - videoRatio - thumbRatio).coerceAtLeast(0f)
                        if (remaining > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .weight(remaining)
                                    .fillMaxHeight()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Videos: ${formatFileSize(storageStats.videosBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Thumbnails & Data: ${formatFileSize(storageStats.thumbnailsBytes + storageStats.avatarsBytes + storageStats.databaseBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = onCleanupOrphans,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cleanup_storage_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (storageStats.orphanFilesCount > 0) {
                                "Clean ${storageStats.orphanFilesCount} Unused Cache File(s)"
                            } else {
                                "Verify & Clean Cache"
                            }
                        )
                    }
                }
            }
        }

        // My Videos / My Shorts Tab Toggle
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProfileContentTab(
                        selected = selectedTab == 0,
                        label = "Videos (${standardVideos.size})",
                        icon = Icons.Filled.Movie,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("profile_tab_videos")
                    )
                    ProfileContentTab(
                        selected = selectedTab == 1,
                        label = "Shorts (${shorts.size})",
                        icon = Icons.Filled.Bolt,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("profile_tab_shorts")
                    )
                }
            }
        }

        if (displayedVideos.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedTab == 0) {
                                "No standard videos uploaded yet."
                            } else {
                                "No Shorts uploaded yet."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(displayedVideos, key = { "prof_${it.id}" }) { video ->
                ProfileVideoRow(
                    video = video,
                    onPlay = { onPlayVideo(video) },
                    onEdit = { editingVideo = video },
                    onDelete = { deletingVideo = video }
                )
            }
        }
    }

    if (showEditProfileDialog) {
        EditProfileDialog(
            initialName = userProfile.name,
            initialBio = userProfile.bio,
            onDismiss = { showEditProfileDialog = false },
            onSave = { name, bio ->
                onUpdateProfile(name, bio)
                showEditProfileDialog = false
            }
        )
    }

    editingVideo?.let { video ->
        EditVideoDialog(
            video = video,
            onDismiss = { editingVideo = null },
            onSave = { title, desc, category, isShort, thumbUri ->
                onEditVideo(video, title, desc, category, isShort, thumbUri)
                editingVideo = null
            }
        )
    }

    deletingVideo?.let { video ->
        DeleteConfirmDialog(
            video = video,
            onDismiss = { deletingVideo = null },
            onConfirm = {
                onDeleteVideo(video)
                deletingVideo = null
            }
        )
    }
}

@Composable
private fun ProfileStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMono),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProfileContentTab(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = modifier.height(42.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProfileVideoRow(
    video: VideoEntity,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(124.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                AsyncImage(
                    model = File(video.thumbnailFilePath),
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = formatDuration(video.durationMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${formatUploadDate(video.createdAt)} • ${formatFileSize(video.fileSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${video.viewsCount} view${if (video.viewsCount == 1) "" else "s"} • ${video.category}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("profile_edit_video_${video.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit video",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("profile_delete_video_${video.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete video",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun EditProfileDialog(
    initialName: String,
    initialBio: String,
    onDismiss: () -> Unit,
    onSave: (name: String, bio: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var bio by remember { mutableStateOf(initialBio) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Creator Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_name_input")
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_bio_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, bio) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_profile_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
