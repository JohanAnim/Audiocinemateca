package com.johang.audiocinemateca.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.presentation.home.components.FeaturedBannerSection
import com.johang.audiocinemateca.presentation.home.components.HomeToolbar
import com.johang.audiocinemateca.presentation.home.components.RecommendationsSection
import com.johang.audiocinemateca.presentation.home.components.Top10RankingSection
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToExplore: () -> Unit,
    onNavigateToDetail: (itemId: String, itemType: String) -> Unit,
    onPlayContent: (itemId: String, itemType: String) -> Unit,
    onResumeContentWithPosition: (itemId: String, itemType: String, partIndex: Int, episodeIndex: Int) -> Unit = { id, type, _, _ -> onPlayContent(id, type) },
    onMarkAsWatched: (item: com.johang.audiocinemateca.presentation.home.components.ContinueListeningItem) -> Unit,
    onOpenDrawer: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSurpriseMeClick: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    androidx.compose.runtime.LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            listState.scrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF020617)
                    )
                )
            )
    ) {
        HomeToolbar(
            onMenuClick = onOpenDrawer,
            onSearchClick = onSearchClick,
            onSurpriseMeClick = onSurpriseMeClick
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF6366F1)
                )
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    if (!uiState.isLoggedIn) {
                        item(key = "guest_section") {
                            GuestHomeSection(
                                onNavigateToExplore = onNavigateToExplore,
                                onNavigateToProfile = onNavigateToProfile
                            )
                        }
                    } else {
                        item(key = "banner_section") {
                            FeaturedBannerSection(
                                banner = uiState.featuredBanner,
                                isBannerLoading = uiState.isBannerLoading,
                                playText = uiState.bannerPlayText,
                                onPlayClick = { clickedBanner ->
                                    if (clickedBanner.itemId.isNotEmpty()) {
                                        onPlayContent(clickedBanner.itemId, clickedBanner.itemType)
                                    } else {
                                        onNavigateToExplore()
                                    }
                                },
                                onTitleClick = { clickedBanner ->
                                    if (clickedBanner.itemId.isNotEmpty()) {
                                        onNavigateToDetail(clickedBanner.itemId, clickedBanner.itemType)
                                    } else {
                                        onNavigateToExplore()
                                    }
                                },
                                onFavoriteToggle = {
                                    viewModel.toggleFavoriteBanner()
                                }
                            )
                        }

                        if (uiState.continueListeningList.isNotEmpty()) {
                            item(key = "continue_listening_section") {
                                com.johang.audiocinemateca.presentation.home.components.ContinueListeningSection(
                                    itemsList = uiState.continueListeningList,
                                    onResumeContent = onResumeContentWithPosition,
                                    onMarkAsWatched = onMarkAsWatched,
                                    onNavigateToDetail = onNavigateToDetail
                                )
                            }
                        }

                        item(key = "recommendations_section") {
                            RecommendationsSection(
                                userName = uiState.userName,
                                recommendations = uiState.recommendations,
                                isLoading = uiState.isRecommendationsLoading,
                                onNavigateToDetail = onNavigateToDetail,
                                onPlayContent = onPlayContent,
                                onRefreshRecommendations = {
                                    viewModel.loadRecommendations(forceRefresh = true)
                                }
                            )
                        }

                        item(key = "top10_section") {
                            Top10RankingSection(
                                top10List = uiState.top10Ranking,
                                isLoading = uiState.isTop10Loading,
                                onNavigateToDetail = onNavigateToDetail,
                                onPlayContent = onPlayContent
                            )
                        }

                        if (uiState.curatedCollections.isNotEmpty()) {
                            item(key = "curated_collections_section") {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(28.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    uiState.curatedCollections.forEach { collection ->
                                        com.johang.audiocinemateca.presentation.home.components.CuratedCollectionSection(
                                            collection = collection,
                                            onNavigateToDetail = onNavigateToDetail,
                                            onPlayContent = onPlayContent
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "footer_section") {
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Has llegado al final",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Puedes seguir explorando en la pestaña Explorar",
                                        fontSize = 14.sp,
                                        color = Color(0xFF94A3B8),
                                        textAlign = TextAlign.Center,
                                        lineHeight = 20.sp
                                    )
                                    Spacer(modifier = Modifier.height(18.dp))
                                    Button(
                                        onClick = onNavigateToExplore,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF6366F1),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                    ) {
                                        Text(
                                            text = "Explorar catálogo completo",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GuestHomeSection(
    onNavigateToExplore: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(initialOffsetY = { 40 })
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Card 1: Bienvenida e Introducción a Audiocinemateca
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1B4B)
                ),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.5.dp, Color(0xFF6366F1).copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = Color(0xFF312E81),
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_catalog),
                                contentDescription = null,
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "¡Te damos la bienvenida a Audiocinemateca!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 30.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Audiocinemateca es tu plataforma ideal para escuchar películas, series, cortometrajes y documentales audiodescritos en español (audesc), donde un narrador relata lo que sucede en pantalla. Diseñada para llevar el entretenimiento al siguiente nivel de una forma súper genial, práctica y 100% accesible.",
                        fontSize = 15.sp,
                        color = Color(0xFFE0E7FF),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            }

            // Card 2: Modo Local y Pestaña Explorar
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0F2942)
                ),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.5.dp, Color(0xFF0284C7).copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(26.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFF0369A1),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_catalog),
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Disfruta al instante sin cuenta",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Puedes ir directamente a la pestaña 'Explorar' en cualquier momento. Allí tienes acceso completo e ilimitado a todo el catálogo sin necesidad de registrarte ni iniciar sesión.",
                        fontSize = 14.sp,
                        color = Color(0xFFBAE6FD),
                        lineHeight = 21.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFF0C4A6E),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "💡 Consejo: Si deseas usar la app 100% de forma local, puedes ir a Ajustes y establecer 'Explorar' como tu pantalla de inicio predeterminada.",
                            fontSize = 13.sp,
                            color = Color(0xFF7DD3FC),
                            lineHeight = 19.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onNavigateToExplore,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .audiocinematecaAccessibility(
                                label = "Explorar catálogo completo ahora",
                                role = Role.Button,
                                onClickAction = onNavigateToExplore
                            )
                    ) {
                        Text(
                            text = "Explorar catálogo ahora ➔",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Card 3: Extiende las Funcionalidades en la Nube
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.5.dp, Color(0xFF475569).copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(26.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFF334155),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_account),
                                    contentDescription = null,
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Conecta tu cuenta en la nube",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Al iniciar sesión con tu cuenta de Google o correo electrónico, extiendes la experiencia con potentes ventajas:\n\n" +
                                "• Sincronización en la nube de tus favoritos, listas y avance de reproducción en cualquier dispositivo.\n" +
                                "• Participación activa en la Comunidad y el Chat Global en vivo.\n" +
                                "• Valora y comenta tus audiodescripciones favoritas.\n" +
                                "• Sugerencias personalizadas con Aura, tu asistente IA de voz.",
                        fontSize = 14.sp,
                        color = Color(0xFFCBD5E1),
                        lineHeight = 21.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onNavigateToProfile,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .audiocinematecaAccessibility(
                                label = "Mejorar cuenta e iniciar sesión",
                                role = Role.Button,
                                onClickAction = onNavigateToProfile
                            )
                    ) {
                        Text(
                            text = "Mejorar cuenta e Iniciar sesión ➔",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
