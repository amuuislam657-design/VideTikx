package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ChannelEntity
import com.example.data.local.UserProfile
import com.example.data.local.VideoEntity
import com.example.ui.components.AvatarImage
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.EditVideoDialog
import com.example.ui.components.VideoCard
import com.example.ui.components.shareOrCopyVideo

@Composable
fun SubscriptionsScreen(
    channels: List<ChannelEntity>,
    videos: List<VideoEntity>,
    userProfile: UserProfile,
    onToggleSubscribe: (String, Boolean) -> Unit,
    onCreateChannel: (name: String, handle: String, bio: String) -> Unit,
    onPlayVideo: (VideoEntity) -> Unit,
    onEditVideo: (VideoEntity, String, String, String, Boolean, android.net.Uri?) -> Unit,
    onDeleteVideo: (VideoEntity) -> Unit,
    onUploadClick: () -> Unit,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedChannelId by remember { mutableStateOf<String?>(null) }
    var showCreateChannelDialog by remember { mutableStateOf(false) }
    var editingVideo by remember { mutableStateOf<VideoEntity?>(null) }
    var deletingVideo by remember { mutableStateOf<VideoEntity?>(null) }

    val subscribedChannelIds = remember(channels) {
        channels.filter { it.isSubscribed }.map { it.id }.toSet()
    }

    val feedVideos = remember(videos, selectedChannelId, subscribedChannelIds) {
        if (selectedChannelId != null) {
            videos.filter { it.channelId == selectedChannelId }
        } else {
            videos.filter { it.channelId in subscribedChannelIds }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Subscriptions",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Follow local channels & organize collections",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { showCreateChannelDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("create_channel_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Channel")
                }
            }
        }

        // Subscribed Channels Horizontal Avatar Strip
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    val isAllSelected = selectedChannelId == null
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedChannelId = null }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isAllSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Subscriptions,
                                contentDescription = "All subscribed channels",
                                tint = if (isAllSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "All",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                items(channels, key = { it.id }) { channel ->
                    val isSelected = selectedChannelId == channel.id
                    val displayName = if (channel.isDefaultSelf) userProfile.name else channel.name
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedChannelId = if (isSelected) null else channel.id
                            }
                            .padding(4.dp)
                    ) {
                        AvatarImage(
                            avatarPath = if (channel.isDefaultSelf) userProfile.avatarPath else null,
                            contentDescription = displayName,
                            size = 56.dp,
                            accentColor = Color(channel.accentColorHex),
                            initials = if (!channel.isDefaultSelf) displayName else null,
                            modifier = if (isSelected) {
                                Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Local Channels Management Cards
        item {
            Text(
                text = "Local Creator Channels",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        items(channels, key = { "card_${it.id}" }) { channel ->
            val displayName = if (channel.isDefaultSelf) userProfile.name else channel.name
            val count = videos.count { it.channelId == channel.id }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable {
                        selectedChannelId = if (selectedChannelId == channel.id) null else channel.id
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarImage(
                        avatarPath = if (channel.isDefaultSelf) userProfile.avatarPath else null,
                        contentDescription = displayName,
                        size = 46.dp,
                        accentColor = Color(channel.accentColorHex),
                        initials = if (!channel.isDefaultSelf) displayName else null
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${channel.handle} • $count video${if (count == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = channel.bio,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (!channel.isDefaultSelf) {
                        if (channel.isSubscribed) {
                            OutlinedButton(
                                onClick = { onToggleSubscribe(channel.id, false) },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("subscribe_toggle_${channel.id}")
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Subscribed", style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Button(
                                onClick = { onToggleSubscribe(channel.id, true) },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("subscribe_toggle_${channel.id}")
                            ) {
                                Text("Subscribe", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "You",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Subscribed Videos Feed Section
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (selectedChannelId == null) {
                    "Feed from Subscribed Channels (${feedVideos.size})"
                } else {
                    val ch = channels.find { it.id == selectedChannelId }
                    "Videos in ${ch?.name ?: "Channel"} (${feedVideos.size})"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (feedVideos.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No videos in this subscription view yet",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Upload a video and assign it to any of your followed channels.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(onClick = onUploadClick, shape = RoundedCornerShape(12.dp)) {
                            Text("Upload Video")
                        }
                    }
                }
            }
        } else {
            items(feedVideos, key = { "sub_${it.id}" }) { video ->
                VideoCard(
                    video = video,
                    onPlay = { onPlayVideo(video) },
                    onEdit = { editingVideo = video },
                    onDelete = { deletingVideo = video },
                    onShare = { shareOrCopyVideo(context, video, onFeedback) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }

    if (showCreateChannelDialog) {
        CreateChannelDialog(
            onDismiss = { showCreateChannelDialog = false },
            onCreate = { name, handle, bio ->
                onCreateChannel(name, handle, bio)
                showCreateChannelDialog = false
            }
        )
    }

    editingVideo?.let { video ->
        EditVideoDialog(
            video = video,
            onDismiss = { editingVideo = null },
            onSave = { title, desc, cat, isShort, thumbUri ->
                onEditVideo(video, title, desc, cat, isShort, thumbUri)
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
private fun CreateChannelDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, handle: String, bio: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Local Channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Channel Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = handle,
                    onValueChange = { handle = it },
                    label = { Text("Handle (e.g. @studio)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, handle, bio) },
                enabled = name.isNotBlank()
            ) {
                Text("Create & Follow")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
