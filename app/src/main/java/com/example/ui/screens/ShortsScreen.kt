package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.ChannelEntity
import com.example.data.local.CommentEntity
import com.example.data.local.VideoEntity
import com.example.ui.components.AvatarImage
import com.example.ui.components.copyVideoDetailsToClipboard
import com.example.ui.components.formatUploadDate
import com.example.ui.components.shareOrCopyVideo
import com.example.ui.theme.TubeCrimson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.io.File

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
fun ShortsScreen(
    shorts: List<VideoEntity>,
    channels: List<ChannelEntity>,
    commentCounts: Map<String, Int>,
    observeComments: (String) -> Flow<List<CommentEntity>>,
    onToggleLike: (String) -> Unit,
    onToggleDislike: (String) -> Unit,
    onAddComment: (String, String) -> Unit,
    onDeleteComment: (String) -> Unit,
    onToggleSubscribe: (String, Boolean) -> Unit,
    onRecordView: (String) -> Unit,
    onUploadShortClick: () -> Unit,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    if (shorts.isEmpty()) {
        EmptyShortsState(
            onUploadShortClick = onUploadShortClick,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { shorts.size })
    var isMuted by remember { mutableStateOf(false) }
    var isUserPaused by remember { mutableStateOf(false) }
    var showTapIndicator by remember { mutableStateOf(false) }
    var activeCommentsVideo by remember { mutableStateOf<VideoEntity?>(null) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }

    // Pause/resume with lifecycle
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> if (!isUserPaused) exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    // Sync mute state
    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    // Auto-play visible page
    val currentShort = shorts.getOrNull(pagerState.settledPage) ?: shorts.first()
    LaunchedEffect(currentShort.id, currentShort.videoFilePath) {
        isUserPaused = false
        val file = File(currentShort.videoFilePath)
        if (file.exists()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            exoPlayer.play()
            onRecordView(currentShort.id)
        }
    }

    LaunchedEffect(showTapIndicator) {
        if (showTapIndicator) {
            delay(750)
            showTapIndicator = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .testTag("shorts_vertical_pager")
        ) { page ->
            val shortVideo = shorts[page]
            val isCurrentPage = page == pagerState.settledPage
            val channel = channels.find { it.id == shortVideo.channelId }
            val commentCount = commentCounts[shortVideo.id] ?: 0

            ShortPageItem(
                video = shortVideo,
                channel = channel,
                commentCount = commentCount,
                isCurrentPage = isCurrentPage,
                exoPlayer = exoPlayer,
                isPaused = isUserPaused,
                isMuted = isMuted,
                showTapIndicator = showTapIndicator && isCurrentPage,
                onTogglePlayPause = {
                    if (isCurrentPage) {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                            isUserPaused = true
                        } else {
                            exoPlayer.play()
                            isUserPaused = false
                        }
                        showTapIndicator = true
                    }
                },
                onToggleMute = { isMuted = !isMuted },
                onLike = { onToggleLike(shortVideo.id) },
                onDislike = { onToggleDislike(shortVideo.id) },
                onOpenComments = { activeCommentsVideo = shortVideo },
                onShare = { shareOrCopyVideo(context, shortVideo, onFeedback) },
                onCopyInfo = { copyVideoDetailsToClipboard(context, shortVideo, onFeedback) },
                onToggleSubscribe = {
                    if (channel != null && !channel.isDefaultSelf) {
                        onToggleSubscribe(channel.id, !channel.isSubscribed)
                    }
                }
            )
        }

        // Top Shorts Bar Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = TubeCrimson,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Shorts",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                onClick = onUploadShortClick,
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                modifier = Modifier.testTag("shorts_add_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Create Short",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
        }
    }

    // Comments Modal Bottom Sheet
    activeCommentsVideo?.let { video ->
        val comments by observeComments(video.id).collectAsStateWithLifecycle(initialValue = emptyList())
        ShortsCommentsBottomSheet(
            video = video,
            comments = comments,
            onDismiss = { activeCommentsVideo = null },
            onSendComment = { text -> onAddComment(video.id, text) },
            onDeleteComment = onDeleteComment
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ShortPageItem(
    video: VideoEntity,
    channel: ChannelEntity?,
    commentCount: Int,
    isCurrentPage: Boolean,
    exoPlayer: ExoPlayer,
    isPaused: Boolean,
    isMuted: Boolean,
    showTapIndicator: Boolean,
    onTogglePlayPause: () -> Unit,
    onToggleMute: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
    onCopyInfo: () -> Unit,
    onToggleSubscribe: () -> Unit
) {
    var expandedDesc by remember(video.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTogglePlayPause
            )
    ) {
        if (isCurrentPage) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        keepScreenOn = true
                        resizeMode = if (video.height >= video.width) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        player = exoPlayer
                    }
                },
                update = { view ->
                    view.resizeMode = if (video.height >= video.width) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    if (view.player !== exoPlayer) {
                        view.player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = File(video.thumbnailFilePath),
                contentDescription = video.title,
                contentScale = if (video.height >= video.width) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top & Bottom Scrims
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.78f)
                        )
                    )
                )
        )

        // Play / Pause center indicator
        AnimatedVisibility(
            visible = showTapIndicator || (isCurrentPage && isPaused),
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 1.1f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.62f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (isPaused) "Play" else "Pause",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }

        // Right Side Action Rail
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ShortRailAction(
                icon = Icons.Filled.ThumbUp,
                label = video.likesCount.toString(),
                active = video.userReaction == 1,
                onClick = onLike,
                testTag = "short_like_${video.id}"
            )
            ShortRailAction(
                icon = Icons.Filled.ThumbDown,
                label = if (video.dislikesCount > 0) video.dislikesCount.toString() else "Dislike",
                active = video.userReaction == -1,
                onClick = onDislike,
                testTag = "short_dislike_${video.id}"
            )
            ShortRailAction(
                icon = Icons.Filled.ChatBubble,
                label = commentCount.toString(),
                active = false,
                onClick = onOpenComments,
                testTag = "short_comments_${video.id}"
            )
            ShortRailAction(
                icon = Icons.Filled.Share,
                label = "Share",
                active = false,
                onClick = onShare,
                testTag = "short_share_${video.id}"
            )
            ShortRailAction(
                icon = Icons.Filled.ContentCopy,
                label = "Copy",
                active = false,
                onClick = onCopyInfo,
                testTag = "short_copy_${video.id}"
            )
            ShortRailAction(
                icon = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                label = if (isMuted) "Muted" else "Sound",
                active = isMuted,
                onClick = onToggleMute,
                testTag = "short_mute_${video.id}"
            )
        }

        // Bottom Left Metadata & Creator Overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 84.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AvatarImage(
                    avatarPath = video.creatorAvatarPath,
                    contentDescription = video.creatorName,
                    size = 38.dp,
                    accentColor = channel?.let { Color(it.accentColorHex) },
                    initials = if (video.channelId != "self") video.creatorName else null
                )
                Text(
                    text = video.creatorName,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                if (channel != null && !channel.isDefaultSelf) {
                    Surface(
                        onClick = onToggleSubscribe,
                        shape = RoundedCornerShape(16.dp),
                        color = if (channel.isSubscribed) Color.White.copy(alpha = 0.22f) else TubeCrimson
                    ) {
                        Text(
                            text = if (channel.isSubscribed) "Subscribed" else "Subscribe",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (video.description.isNotBlank()) {
                Text(
                    text = video.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = if (expandedDesc) 6 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expandedDesc = !expandedDesc }
                )
            }
        }
    }
}

@Composable
private fun ShortRailAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag(testTag)
    ) {
        Surface(
            shape = CircleShape,
            color = if (active) TubeCrimson else Color.Black.copy(alpha = 0.45f),
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortsCommentsBottomSheet(
    video: VideoEntity,
    comments: List<CommentEntity>,
    onDismiss: () -> Unit,
    onSendComment: (String) -> Unit,
    onDeleteComment: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var commentDraft by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Comments (${comments.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()

            if (comments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No local comments yet.\nStart the conversation offline!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
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

            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

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
                        .testTag("shorts_comment_input")
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
                                onSendComment(commentDraft)
                                commentDraft = ""
                            }
                        },
                        enabled = commentDraft.isNotBlank(),
                        modifier = Modifier.testTag("shorts_send_comment_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send comment",
                            tint = if (commentDraft.isNotBlank()) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyShortsState(
    onUploadShortClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.empty_shorts_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.empty_shorts_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onUploadShortClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("empty_upload_short_cta")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.upload_short))
                }
            }
        }
    }
}
