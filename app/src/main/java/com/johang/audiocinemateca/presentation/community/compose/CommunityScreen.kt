package com.johang.audiocinemateca.presentation.community.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.presentation.community.AnnouncementsViewModel
import com.johang.audiocinemateca.presentation.community.CommunityViewModel
import com.johang.audiocinemateca.presentation.community.GlobalChatViewModel

@Composable
fun CommunityScreen(
    globalChatViewModel: GlobalChatViewModel,
    announcementsViewModel: AnnouncementsViewModel,
    communityViewModel: CommunityViewModel,
    initialTabIndex: Int = 0,
    onVoiceClick: () -> Unit = {},
    onNavigateToPlayer: (CatalogItem, Int, Int) -> Unit = { _, _, _ -> },
    onNavigateToContentDetail: (String, String) -> Unit = { _, _ -> },
    onNavigateToCommunitySettings: () -> Unit = {}
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(initialTabIndex.coerceIn(0, 2)) }
    LaunchedEffect(initialTabIndex) {
        selectedTabIndex = initialTabIndex.coerceIn(0, 2)
    }
    val tabTitles = listOf("Global", "Anuncios", "Comentarios")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                isTraversalGroup = true
            }
    ) {
        // Tab Row
        PrimaryTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics {
                isTraversalGroup = true
                traversalIndex = 1f
                collectionInfo = CollectionInfo(rowCount = 1, columnCount = tabTitles.size)
            }
        ) {
            tabTitles.forEachIndexed { index, title ->
                val isSelected = selectedTabIndex == index
                Tab(
                    selected = isSelected,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.clearAndSetSemantics {
                        role = Role.Tab
                        selected = isSelected
                        stateDescription = "${index + 1} de ${tabTitles.size}"
                        collectionItemInfo = CollectionItemInfo(
                            rowIndex = 0,
                            rowSpan = 1,
                            columnIndex = index,
                            columnSpan = 1
                        )
                        contentDescription = title
                        onClick {
                            selectedTabIndex = index
                            true
                        }
                    }
                )
            }
        }

        // Tab Contents
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = if (selectedTabIndex == 0) -1f else 2f
                }
        ) {
            when (selectedTabIndex) {
                0 -> GlobalChatTab(
                    viewModel = globalChatViewModel,
                    onVoiceClick = onVoiceClick,
                    onNavigateToPlayer = onNavigateToPlayer,
                    onNavigateToCommunitySettings = onNavigateToCommunitySettings
                )
                1 -> AnnouncementsTab(
                    viewModel = announcementsViewModel
                )
                2 -> CommunityCommentsTab(
                    viewModel = communityViewModel,
                    onNavigateToPlayer = onNavigateToPlayer
                )
            }
        }
    }
}
