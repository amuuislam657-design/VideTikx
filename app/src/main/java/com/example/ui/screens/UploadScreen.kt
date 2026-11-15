package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.local.ChannelEntity
import com.example.data.local.ExtractedVideoMetadata
import com.example.data.local.VideoCategories
import com.example.ui.UploadUiState
import com.example.ui.components.formatDuration
import com.example.ui.components.formatFileSize
import com.example.ui.theme.JetBrainsMono
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
fun UploadScreen(
    channels: List<ChannelEntity>,
    uploadState: UploadUiState,
    defaultIsShort: Boolean,
    onInspectVideo: suspend (Uri) -> ExtractedVideoMetadata,
    onPublish: (
        videoUri: Uri,
        thumbnailUri: Uri?,
        title: String,
        description: String,
        category: String,
        isShort: Boolean,
        channelId: String
    ) -> Unit,
    onUploadSuccessConsumed: (isShort: Boolean) -> Unit,
    onResetUploadState: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedThumbUri by remember { mutableStateOf<Uri?>(null) }
    var videoMetadata by remember { mutableStateOf<ExtractedVideoMetadata?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(VideoCategories.UPLOAD_OPTIONS.first()) }
    var isShort by remember(defaultIsShort) { mutableStateOf(defaultIsShort) }
    var selectedChannelId by remember { mutableStateOf("self") }
    var isPreviewPlaying by remember { mutableStateOf(false) }

    // Inspect video metadata when selected
    LaunchedEffect(selectedVideoUri) {
        val uri = selectedVideoUri
        if (uri != null) {
            val meta = onInspectVideo(uri)
            videoMetadata = meta
            if (title.isBlank()) {
                title = meta.displayName.substringBeforeLast('.').replace('_', ' ').take(80)
            }
            if (meta.isPortrait && meta.durationMs in 1..180_000L) {
                isShort = true
            }
        } else {
            videoMetadata = null
        }
    }

    // Handle upload success -> reset form and navigate
    LaunchedEffect(uploadState) {
        if (uploadState is UploadUiState.Success) {
            val publishedShort = uploadState.isShort
            selectedVideoUri = null
            selectedThumbUri = null
            videoMetadata = null
            title = ""
            description = ""
            onResetUploadState()
            onUploadSuccessConsumed(publishedShort)
        }
    }

    val documentVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
            onResetUploadState()
        }
    }

    val galleryVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
            onResetUploadState()
        }
    }

    val thumbnailPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedThumbUri = uri
        }
    }

    // Preview ExoPlayer
    val previewPlayer = remember(selectedVideoUri) {
        selectedVideoUri?.let { uri ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = false
            }
        }
    }

    DisposableEffect(previewPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPreviewPlaying = isPlaying
            }
        }
        previewPlayer?.addListener(listener)
        onDispose {
            previewPlayer?.removeListener(listener)
            previewPlayer?.release()
        }
    }

    val isUploading = uploadState is UploadUiState.Uploading

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Upload to MY TUBE",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Saved locally on your device • 100% offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Format toggle: Standard Video vs Short
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FormatModeTab(
                        selected = !isShort,
                        title = "Standard Video",
                        icon = Icons.Filled.Movie,
                        onClick = { if (!isUploading) isShort = false },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("upload_mode_video")
                    )
                    FormatModeTab(
                        selected = isShort,
                        title = "Short Clip",
                        icon = Icons.Filled.Bolt,
                        onClick = { if (!isUploading) isShort = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("upload_mode_short")
                    )
                }
            }

            // Video Selector & Live Preview Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedVideoUri == null || previewPlayer == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable(enabled = !isUploading) {
                                    documentVideoPicker.launch(arrayOf("video/*"))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.CloudUpload,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Select a video to preview",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Supports MP4, WebM, MKV, MOV & device formats",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // Video Preview Player
                        val previewAspect = if (isShort || videoMetadata?.isPortrait == true) {
                            9f / 12f
                        } else {
                            16f / 9f
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(previewAspect)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = previewPlayer
                                        useController = false
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        layoutParams = FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                },
                                update = { view ->
                                    if (view.player !== previewPlayer) {
                                        view.player = previewPlayer
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(56.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (previewPlayer.isPlaying) {
                                            previewPlayer.pause()
                                        } else {
                                            previewPlayer.play()
                                        }
                                    },
                                    modifier = Modifier.testTag("preview_play_pause_button")
                                ) {
                                    Icon(
                                        imageVector = if (isPreviewPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPreviewPlaying) "Pause preview" else "Play preview",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }

                        // Extracted metadata badges
                        videoMetadata?.let { meta ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetadataBadge(label = formatDuration(meta.durationMs))
                                MetadataBadge(label = "${meta.width}x${meta.height}")
                                if (meta.fileSizeBytes > 0) {
                                    MetadataBadge(label = formatFileSize(meta.fileSizeBytes))
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { documentVideoPicker.launch(arrayOf("video/*")) },
                            enabled = !isUploading,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_video_files_button")
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Files")
                        }
                        OutlinedButton(
                            onClick = {
                                galleryVideoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            enabled = !isUploading,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_video_gallery_button")
                        ) {
                            Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery")
                        }
                    }
                }
            }

            // Custom Thumbnail Section
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(116.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedThumbUri != null) {
                            AsyncImage(
                                model = selectedThumbUri,
                                contentDescription = "Custom thumbnail preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Custom Thumbnail",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedThumbUri != null) {
                                "Custom image selected"
                            } else {
                                "Optional • Auto-captures video frame if omitted"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    thumbnailPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isUploading,
                                modifier = Modifier.testTag("pick_thumbnail_button")
                            ) {
                                Text(if (selectedThumbUri == null) "Choose Image" else "Change")
                            }
                            if (selectedThumbUri != null) {
                                TextButton(
                                    onClick = { selectedThumbUri = null },
                                    enabled = !isUploading
                                ) {
                                    Text("Reset")
                                }
                            }
                        }
                    }
                }
            }

            // Video Details Form
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Video Title *") },
                        placeholder = { Text("Enter a descriptive title") },
                        singleLine = true,
                        enabled = !isUploading,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_title_input")
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        placeholder = { Text("Add notes, timestamps, or story behind this video") },
                        minLines = 3,
                        maxLines = 5,
                        enabled = !isUploading,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_description_input")
                    )

                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VideoCategories.UPLOAD_OPTIONS.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { if (!isUploading) selectedCategory = category },
                                label = { Text(category) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }

                    if (channels.isNotEmpty()) {
                        Text(
                            text = "Publish To Channel",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            channels.forEach { channel ->
                                FilterChip(
                                    selected = selectedChannelId == channel.id,
                                    onClick = { if (!isUploading) selectedChannelId = channel.id },
                                    label = { Text(channel.name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Progress & Error States
            if (uploadState is UploadUiState.Uploading) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Saving offline to device…",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${(uploadState.progress * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { uploadState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }

            if (uploadState is UploadUiState.Error) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = uploadState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // Publish Button
            Button(
                onClick = {
                    val uri = selectedVideoUri ?: return@Button
                    previewPlayer?.pause()
                    onPublish(
                        uri,
                        selectedThumbUri,
                        title,
                        description,
                        selectedCategory,
                        isShort,
                        selectedChannelId
                    )
                },
                enabled = selectedVideoUri != null && title.isNotBlank() && !isUploading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("publish_video_button")
            ) {
                Text(
                    text = if (isUploading) {
                        "Publishing…"
                    } else if (isShort) {
                        "Publish Short Offline"
                    } else {
                        "Publish Video Offline"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FormatModeTab(
    selected: Boolean,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = modifier.height(46.dp)
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
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MetadataBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
