package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.MainViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ShortsScreen
import com.example.ui.screens.SubscriptionsScreen
import com.example.ui.screens.UploadScreen
import com.example.ui.theme.MyTubeTheme
import com.example.ui.theme.TubeCrimson
import kotlinx.coroutines.launch

private object MyTubeRoutes {
    const val HOME = "home"
    const val SHORTS = "shorts"
    const val UPLOAD = "upload"
    const val SUBSCRIPTIONS = "subscriptions"
    const val PROFILE = "profile"
    const val PLAYER = "player/{videoId}"

    fun playerRoute(videoId: String) = "player/$videoId"
}

private data class BottomTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val isUploadCenter: Boolean = false
)

private val bottomTabs = listOf(
    BottomTab(
        route = MyTubeRoutes.HOME,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    BottomTab(
        route = MyTubeRoutes.SHORTS,
        labelRes = R.string.nav_shorts,
        selectedIcon = Icons.Filled.Bolt,
        unselectedIcon = Icons.Outlined.Bolt
    ),
    BottomTab(
        route = MyTubeRoutes.UPLOAD,
        labelRes = R.string.nav_upload,
        selectedIcon = Icons.Filled.Add,
        unselectedIcon = Icons.Filled.Add,
        isUploadCenter = true
    ),
    BottomTab(
        route = MyTubeRoutes.SUBSCRIPTIONS,
        labelRes = R.string.nav_subscriptions,
        selectedIcon = Icons.Filled.Subscriptions,
        unselectedIcon = Icons.Outlined.Subscriptions
    ),
    BottomTab(
        route = MyTubeRoutes.PROFILE,
        labelRes = R.string.nav_profile,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()

            MyTubeTheme(darkTheme = isDarkTheme) {
                MyTubeApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MyTubeApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val allVideos by viewModel.allVideos.collectAsStateWithLifecycle()
    val filteredHomeVideos by viewModel.filteredHomeVideos.collectAsStateWithLifecycle()
    val shortsVideos by viewModel.shortsVideos.collectAsStateWithLifecycle()
    val channels by viewModel.allChannels.collectAsStateWithLifecycle()
    val commentCounts by viewModel.commentCounts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()
    val uploadDefaultShort by viewModel.uploadDefaultShort.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeStatusMessage()
    }

    val showToast: (String) -> Unit = { msg ->
        coroutineScope.launch {
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isPlayerRoute = currentDestination?.route?.startsWith("player/") == true

    val navigateToTab: (String) -> Unit = { route ->
        if (route != MyTubeRoutes.UPLOAD) {
            viewModel.setUploadDefaultShort(false)
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp && !isPlayerRoute

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    bottomTabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        val label = stringResource(tab.labelRes)
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateToTab(tab.route) },
                            icon = {
                                if (tab.isUploadCenter) {
                                    Surface(
                                        shape = CircleShape,
                                        color = TubeCrimson,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.Add,
                                                contentDescription = label,
                                                tint = Color.White
                                            )
                                        }
                                    }
                                } else {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                }
                            },
                            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("nav_${tab.route}")
                        )
                    }
                }
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (!isPlayerRoute && !useNavRail) {
                        MyTubeBottomBar(
                            currentRoute = currentDestination?.route,
                            onSelectTab = navigateToTab
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = MyTubeRoutes.HOME,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    composable(MyTubeRoutes.HOME) {
                        HomeScreen(
                            videos = filteredHomeVideos,
                            totalLibraryCount = allVideos.size,
                            searchQuery = searchQuery,
                            selectedCategory = selectedCategory,
                            isDarkTheme = isDarkTheme,
                            onSearchQueryChange = viewModel::setSearchQuery,
                            onCategorySelected = viewModel::setSelectedCategory,
                            onToggleTheme = viewModel::toggleTheme,
                            onPlayVideo = { video ->
                                navController.navigate(MyTubeRoutes.playerRoute(video.id))
                            },
                            onNavigateToUpload = {
                                viewModel.setUploadDefaultShort(false)
                                navigateToTab(MyTubeRoutes.UPLOAD)
                            },
                            onEditVideo = { video, title, desc, cat, isShort, thumbUri ->
                                viewModel.updateVideoDetails(video.id, title, desc, cat, isShort, thumbUri)
                            },
                            onDeleteVideo = viewModel::deleteVideo,
                            onFeedback = showToast,
                            modifier = Modifier.statusBarsPadding()
                        )
                    }

                    composable(MyTubeRoutes.SHORTS) {
                        ShortsScreen(
                            shorts = shortsVideos,
                            channels = channels,
                            commentCounts = commentCounts,
                            observeComments = viewModel::observeComments,
                            onToggleLike = viewModel::toggleLike,
                            onToggleDislike = viewModel::toggleDislike,
                            onAddComment = viewModel::addComment,
                            onDeleteComment = viewModel::deleteComment,
                            onToggleSubscribe = viewModel::toggleChannelSubscription,
                            onRecordView = viewModel::recordView,
                            onUploadShortClick = {
                                viewModel.setUploadDefaultShort(true)
                                navigateToTab(MyTubeRoutes.UPLOAD)
                            },
                            onFeedback = showToast
                        )
                    }

                    composable(MyTubeRoutes.UPLOAD) {
                        UploadScreen(
                            channels = channels,
                            uploadState = uploadState,
                            defaultIsShort = uploadDefaultShort,
                            onInspectVideo = viewModel::inspectVideoUri,
                            onPublish = viewModel::publishVideo,
                            onUploadSuccessConsumed = { publishedShort ->
                                showToast(
                                    if (publishedShort) "Short published offline!" else "Video published to Home!"
                                )
                                navigateToTab(
                                    if (publishedShort) MyTubeRoutes.SHORTS else MyTubeRoutes.HOME
                                )
                            },
                            onResetUploadState = viewModel::resetUploadState,
                            modifier = Modifier.statusBarsPadding()
                        )
                    }

                    composable(MyTubeRoutes.SUBSCRIPTIONS) {
                        SubscriptionsScreen(
                            channels = channels,
                            videos = allVideos,
                            userProfile = userProfile,
                            onToggleSubscribe = viewModel::toggleChannelSubscription,
                            onCreateChannel = viewModel::createChannel,
                            onPlayVideo = { video ->
                                navController.navigate(MyTubeRoutes.playerRoute(video.id))
                            },
                            onEditVideo = { video, title, desc, cat, isShort, thumbUri ->
                                viewModel.updateVideoDetails(video.id, title, desc, cat, isShort, thumbUri)
                            },
                            onDeleteVideo = viewModel::deleteVideo,
                            onUploadClick = {
                                viewModel.setUploadDefaultShort(false)
                                navigateToTab(MyTubeRoutes.UPLOAD)
                            },
                            onFeedback = showToast,
                            modifier = Modifier.statusBarsPadding()
                        )
                    }

                    composable(MyTubeRoutes.PROFILE) {
                        ProfileScreen(
                            userProfile = userProfile,
                            allVideos = allVideos,
                            storageStats = storageStats,
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = viewModel::setDarkTheme,
                            onUpdateProfile = viewModel::updateProfile,
                            onUpdateAvatar = viewModel::updateProfileAvatar,
                            onCleanupOrphans = viewModel::cleanupOrphanFiles,
                            onPlayVideo = { video ->
                                navController.navigate(MyTubeRoutes.playerRoute(video.id))
                            },
                            onEditVideo = { video, title, desc, cat, isShort, thumbUri ->
                                viewModel.updateVideoDetails(video.id, title, desc, cat, isShort, thumbUri)
                            },
                            onDeleteVideo = viewModel::deleteVideo,
                            modifier = Modifier.statusBarsPadding()
                        )
                    }

                    composable(
                        route = MyTubeRoutes.PLAYER,
                        arguments = listOf(navArgument("videoId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val videoId = backStackEntry.arguments?.getString("videoId") ?: ""
                        PlayerScreen(
                            videoFlow = viewModel.observeVideo(videoId),
                            commentsFlow = viewModel.observeComments(videoId),
                            onBack = { navController.popBackStack() },
                            onRecordView = viewModel::recordView,
                            onToggleLike = viewModel::toggleLike,
                            onToggleDislike = viewModel::toggleDislike,
                            onAddComment = viewModel::addComment,
                            onDeleteComment = viewModel::deleteComment,
                            onEditVideo = { video, title, desc, cat, isShort, thumbUri ->
                                viewModel.updateVideoDetails(video.id, title, desc, cat, isShort, thumbUri)
                            },
                            onDeleteVideo = viewModel::deleteVideo,
                            onFeedback = showToast
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MyTubeBottomBar(
    currentRoute: String?,
    onSelectTab: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        windowInsets = WindowInsets.navigationBars
    ) {
        bottomTabs.forEach { tab ->
            val selected = currentRoute == tab.route
            val label = stringResource(tab.labelRes)
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectTab(tab.route) },
                icon = {
                    if (tab.isUploadCenter) {
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(TubeCrimson)
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = label,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = label
                        )
                    }
                },
                label = {
                    if (!tab.isUploadCenter) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                alwaysShowLabel = !tab.isUploadCenter,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = if (tab.isUploadCenter) Color.Transparent else MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.testTag("nav_${tab.route}")
            )
        }
    }
}
