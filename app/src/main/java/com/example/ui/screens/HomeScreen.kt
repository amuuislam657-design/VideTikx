package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.VideoCategories
import com.example.data.local.VideoEntity
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.EditVideoDialog
import com.example.ui.components.MyTubeBrandHeader
import com.example.ui.components.VideoCard
import com.example.ui.components.shareOrCopyVideo

@Composable
fun HomeScreen(
    videos: List<VideoEntity>,
    totalLibraryCount: Int,
    searchQuery: String,
    selectedCategory: String,
    isDarkTheme: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onToggleTheme: () -> Unit,
    onPlayVideo: (VideoEntity) -> Unit,
    onNavigateToUpload: () -> Unit,
    onEditVideo: (VideoEntity, String, String, String, Boolean, android.net.Uri?) -> Unit,
    onDeleteVideo: (VideoEntity) -> Unit,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchExpanded by remember { mutableStateOf(false) }
    var editingVideo by remember { mutableStateOf<VideoEntity?>(null) }
    var deletingVideo by remember { mutableStateOf<VideoEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        MyTubeBrandHeader(
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onSearchClick = { searchExpanded = !searchExpanded }
        )

        // Always-accessible Search Bar when expanded or query active
        AnimatedVisibility(visible = searchExpanded || searchQuery.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(stringResource(R.string.search_videos_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty() || searchExpanded) {
                        IconButton(
                            onClick = {
                                if (searchQuery.isNotEmpty()) {
                                    onSearchQueryChange("")
                                } else {
                                    searchExpanded = false
                                }
                            },
                            modifier = Modifier.testTag("clear_search_button")
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("home_search_input")
            )
        }

        // Category Chips Row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(VideoCategories.ALL_FILTERS) { category ->
                val selected = selectedCategory == category
                FilterChip(
                    selected = selected,
                    onClick = { onCategorySelected(category) },
                    label = {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = if (selected) null else FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = false,
                        borderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.testTag("category_chip_${category.lowercase()}")
                )
            }
        }

        if (videos.isEmpty()) {
            EmptyHomeState(
                hasAnyVideos = totalLibraryCount > 0,
                onClearFilters = {
                    onSearchQueryChange("")
                    onCategorySelected("All")
                },
                onUploadClick = onNavigateToUpload,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 6.dp,
                    bottom = 24.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("home_video_grid")
            ) {
                items(videos, key = { it.id }) { video ->
                    VideoCard(
                        video = video,
                        onPlay = { onPlayVideo(video) },
                        onEdit = { editingVideo = video },
                        onDelete = { deletingVideo = video },
                        onShare = { shareOrCopyVideo(context, video, onFeedback) }
                    )
                }
            }
        }
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
private fun EmptyHomeState(
    hasAnyVideos: Boolean,
    onClearFilters: () -> Unit,
    onUploadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 460.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_empty_studio),
                    contentDescription = "Offline studio illustration",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = if (hasAnyVideos) "No matching videos" else stringResource(R.string.empty_library_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (hasAnyVideos) {
                        "Try another title or switch back to All categories."
                    } else {
                        stringResource(R.string.empty_library_subtitle)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (hasAnyVideos) {
                    OutlinedButton(
                        onClick = onClearFilters,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("clear_filters_button")
                    ) {
                        Text("Show All Videos")
                    }
                } else {
                    Button(
                        onClick = onUploadClick,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("empty_upload_cta")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.upload_first_video))
                    }
                }
            }
        }
    }
}
