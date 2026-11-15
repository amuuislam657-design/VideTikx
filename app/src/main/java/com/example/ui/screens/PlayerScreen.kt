package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.local.CommentEntity
import com.example.data.local.VideoEntity
import com.example.ui.components.AvatarImage
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.EditVideoDialog
import com.example.ui.components.copyVideoDetailsToClipboard
import com.example.ui.components.formatDuration
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatUploadDate
import com.example.ui.components.shareOrCopyVideo
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.TubeCrimson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoFlow: Flow<VideoEntity?>,
    commentsFlow: Flow<List<CommentEntity>>,
    onBack: () -> Unit,
    onRecordView: (String) -> Unit,
    onToggleLike: (String) -> Unit,
    onToggleDislike: (String) -> Unit,
    onAddComment: (String, String) -> Unit,
    onDeleteComment: (String) -> Unit,
    onEditVideo: (VideoEntity, String, String, String, Boolean, Uri?) -> Unit,
    onDeleteVideo: (VideoEntity) -> Unit,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val video by videoFlow.collectAsStateWithLifecycle(initialValue = null)
    val comments by commentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var isFullscreen by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var volume by remember { mutableFloatStateOf(1f) }
    var showVolumeSlider by remember { mutableStateOf(false) }
    var commentDraft by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var expandedDesc by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // Load video into ExoPlayer when available
    val currentVideo = video
    LaunchedEffect(currentVideo?.id, currentVideo?.videoFilePath) {
        val v = currentVideo ?: return@LaunchedEffect
        val file = File(v.videoFilePath)
        if (file.exists()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            exoPlayer.play()
            onRecordView(v.id)
        }
    }

    // Sync volume
    LaunchedEffect(volume) {
        exoPlayer.volume = volume
    }

    // Poll position smoothly
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isScrubbing) {
                currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            val dur = exoPlayer.duration
            if (dur > 0) durationMs = dur
            isPlaying = exoPlayer.isPlaying
            delay(250)
        }
    }

    // Auto-hide controls while playing
    LaunchedEffect(showControls, isPlaying, showVolumeSlider) {
        if (showControls && isPlaying && !showVolumeSlider) {
            delay(3500)
            showControls = false
        }
    }

    // Handle fullscreen orientation & system bars
    LaunchedEffect(isFullscreen, currentVideo?.width, currentVideo?.height) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (isFullscreen) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            val isPortraitVideo = (currentVideo?.height ?: 0) > (currentVideo?.width ?: 1)
            activity.requestedOrientation = if (isPortraitVideo) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Pause on lifecycle stop, release player & restore orientation on dispose
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val controller = WindowCompat.getInsetsController(act.window, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onBack()
        }
    }

    if (currentVideo == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Loading video…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val effectiveDuration = (if (durationMs > 0) durationMs else currentVideo.durationMs).coerceAtLeast(1L)
    val displayPos = if (isScrubbing) scrubPositionMs else currentPosMs

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isFullscreen) Color.Black else MaterialTheme.colorScheme.background)
            .let { if (!isFullscreen) it.statusBarsPadding().navigationBarsPadding() else it }
    ) {
        // Video Surface + Interactive Controls Overlay
        val playerBoxModifier = if (isFullscreen) {
            Modifier.fillMaxSize()
        } else {
            val isPortraitVideo = currentVideo.height > currentVideo.width && currentVideo.width > 0
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (isPortraitVideo) 4f / 5f else 16f / 9f)
        }

        Box(
            modifier = playerBoxModifier
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showControls = !showControls
                    if (!showControls) showVolumeSlider = false
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        keepScreenOn = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        player = exoPlayer
                    }
                },
                update = { pv ->
                    if (pv.player !== exoPlayer) pv.player = exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Black.copy(alpha = 0.25f),
                                    Color.Black.copy(alpha = 0.78f)
                                )
                            )
                        )
                ) {
                    // Top Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (isFullscreen) isFullscreen = false else onBack()
                            },
                            modifier = Modifier.testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = currentVideo.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { isFullscreen = !isFullscreen },
                            modifier = Modifier.testTag("player_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                                tint = Color.White
                            )
                        }
                    }

                    // Center Transport Controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(26.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Replay10,
                                contentDescription = "Rewind 10 seconds",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = TubeCrimson.copy(alpha = 0.92f),
                            modifier = Modifier.size(62.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                        isPlaying = false
                                    } else {
                                        if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                            exoPlayer.seekTo(0)
                                        }
                                        exoPlayer.play()
                                        isPlaying = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("player_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                val target = (exoPlayer.currentPosition + 10_000L)
                                    .coerceAtMost(effectiveDuration)
                                exoPlayer.seekTo(target)
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Forward10,
                                contentDescription = "Forward 10 seconds",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Bottom Seekbar & Volume Controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        androidx.compose.animation.AnimatedVisibility(visible = showVolumeSlider) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.72f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (volume == 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Slider(
                                        value = volume,
                                        onValueChange = { volume = it },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = TubeCrimson,
                                            activeTrackColor = TubeCrimson,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("player_volume_slider")
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${formatDuration(displayPos)} / ${formatDuration(effectiveDuration)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { showVolumeSlider = !showVolumeSlider },
                                modifier = Modifier.testTag("player_volume_toggle")
                            ) {
                                Icon(
                                    imageVector = if (volume == 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                    contentDescription = "Volume control",
                                    tint = Color.White
                                )
                            }
                        }

                        Slider(
                            value = displayPos.toFloat().coerceIn(0f, effectiveDuration.toFloat()),
                            onValueChange = { newValue ->
                                isScrubbing = true
                                scrubPositionMs = newValue.toLong()
                            },
                            onValueChangeFinished = {
                                exoPlayer.seekTo(scrubPositionMs)
                                currentPosMs = scrubPositionMs
                                isScrubbing = false
                            },
                            valueRange = 0f..effectiveDuration.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = TubeCrimson,
                                activeTrackColor = TubeCrimson,
                                inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("player_seek_bar")
                        )
                    }
                }
            }
        }

        // Video Info + Actions + Comments (Portrait / Normal View)
        if (!isFullscreen) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = currentVideo.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${currentVideo.viewsCount} view${if (currentVideo.viewsCount == 1) "" else "s"} • ${formatUploadDate(currentVideo.createdAt)} • ${currentVideo.width}x${currentVideo.height} • ${formatFileSize(currentVideo.fileSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Creator Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AvatarImage(
                                avatarPath = currentVideo.creatorAvatarPath,
                                contentDescription = currentVideo.creatorName,
                                size = 42.dp,
                                initials = if (currentVideo.channelId != "self") currentVideo.creatorName else null
                            )
                            Column {
                                Text(
                                    text = currentVideo.creatorName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentVideo.category,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { showEditDialog = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit video")
                            }
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete video",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Action Pills Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlayerActionPill(
                            icon = Icons.Filled.ThumbUp,
                            label = currentVideo.likesCount.toString(),
                            active = currentVideo.userReaction == 1,
                            onClick = { onToggleLike(currentVideo.id) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("player_like_button")
                        )
                        PlayerActionPill(
                            icon = Icons.Filled.ThumbDown,
                            label = if (currentVideo.dislikesCount > 0) currentVideo.dislikesCount.toString() else "Dislike",
                            active = currentVideo.userReaction == -1,
                            onClick = { onToggleDislike(currentVideo.id) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("player_dislike_button")
                        )
                        PlayerActionPill(
                            icon = Icons.Filled.Share,
                            label = "Share",
                            active = false,
                            onClick = { shareOrCopyVideo(context, currentVideo, onFeedback) },
                            modifier = Modifier.weight(1f)
                        )
                        PlayerActionPill(
                            icon = Icons.Filled.ContentCopy,
                            label = "Copy",
                            active = false,
                            onClick = { copyVideoDetailsToClipboard(context, currentVideo, onFeedback) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Description Card
                if (currentVideo.description.isNotBlank()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedDesc = !expandedDesc }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Description",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentVideo.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (expandedDesc) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Comments Header + Input
                item {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Comments (${comments.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = commentDraft,
                            onValueChange = { commentDraft = it },
                            placeholder = { Text("Add a local comment…") },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("player_comment_input")
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (commentDraft.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ) {
                            IconButton(
                                onClick = {
                                    if (commentDraft.isNotBlank()) {
                                        onAddComment(currentVideo.id, commentDraft)
                                        commentDraft = ""
                                    }
                                },
                                enabled = commentDraft.isNotBlank(),
                                modifier = Modifier.testTag("player_send_comment_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Post comment",
                                    tint = if (commentDraft.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (comments.isEmpty()) {
                    item {
                        Text(
                            text = "No comments yet. Add a note or comment locally.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(comments, key = { it.id }) { comment ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            AvatarImage(
                                avatarPath = comment.authorAvatarPath,
                                contentDescription = comment.authorName,
                                size = 34.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${comment.authorName} • ${formatUploadDate(comment.createdAt)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = comment.text,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            IconButton(onClick = { onDeleteComment(comment.id) }) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete comment",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        EditVideoDialog(
            video = currentVideo,
            onDismiss = { showEditDialog = false },
            onSave = { title, desc, cat, isShort, thumbUri ->
                onEditVideo(currentVideo, title, desc, cat, isShort, thumbUri)
                showEditDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        DeleteConfirmDialog(
            video = currentVideo,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                exoPlayer.pause()
                onDeleteVideo(currentVideo)
                showDeleteDialog = false
                onBack()
            }
        )
    }
}

@Composable
private fun PlayerActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.height(44.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
