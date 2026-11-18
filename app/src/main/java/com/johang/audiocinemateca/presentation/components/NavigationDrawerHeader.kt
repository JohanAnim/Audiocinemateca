package com.johang.audiocinemateca.presentation.components

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.domain.model.SemVer
import com.johang.audiocinemateca.domain.model.UpdateChannels
import com.johang.audiocinemateca.util.accessibleHeading
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class DrawerDayStage {
    DAWN,       // Amanecer (05:00 - 06:59)
    MORNING,    // Mañana (07:00 - 11:59)
    NOON,       // Mediodía (12:00 - 13:59)
    AFTERNOON,  // Tarde (14:00 - 16:59)
    SUNSET,     // Atardecer (17:00 - 18:59)
    NIGHT,      // Noche (19:00 - 23:59)
    LATE_NIGHT  // Madrugada (00:00 - 04:59)
}

data class DrawerHeaderMeta(
    val stage: DrawerDayStage,
    val stageLabel: String,
    val greeting: String,
    val formattedTime: String,
    val regionName: String,
    val versionSummary: String,
    val isAuraMessage: Boolean
)

internal fun resolveDrawerDayStage(hourOfDay: Int): DrawerDayStage {
    return when (hourOfDay) {
        in 5..6 -> DrawerDayStage.DAWN
        in 7..11 -> DrawerDayStage.MORNING
        in 12..13 -> DrawerDayStage.NOON
        in 14..16 -> DrawerDayStage.AFTERNOON
        in 17..18 -> DrawerDayStage.SUNSET
        in 19..23 -> DrawerDayStage.NIGHT
        else -> DrawerDayStage.LATE_NIGHT
    }
}

internal fun resolveDayMomentMessage(
    stage: DrawerDayStage,
    isAuraConfigured: Boolean,
    variantSeed: Int = 0
): String {
    if (!isAuraConfigured) {
        return when (stage) {
            DrawerDayStage.DAWN -> "Amanece el cine en tu oído"
            DrawerDayStage.MORNING -> "Mañana brillante de historias"
            DrawerDayStage.NOON -> "Mediodía luminoso en cartelera"
            DrawerDayStage.AFTERNOON -> "Cielo abierto para disfrutar"
            DrawerDayStage.SUNSET -> "Cielo dorado de función"
            DrawerDayStage.NIGHT -> "Noche estrellada de cine"
            DrawerDayStage.LATE_NIGHT -> "Silencio de madrugada y buen cine"
        }
    }

    val auraOptions = when (stage) {
        DrawerDayStage.DAWN -> listOf(
            "¿El gallo aún bosteza y tú ya buscas función? Qué crack, vamos por una historia épica.",
            "Amanece y hasta el sonido despierta despacio… arranquemos el día con un estreno suave.",
            "Primer café en mente y claqueta lista: hoy puede empezar como película ganadora.",
            "Si el sol apenas se estira en el horizonte, nosotros ya tenemos butaca en primera fila.",
            "Madrugar tiene premio: silencio puro para disfrutar cada detalle de la audiodescripción."
        )
        DrawerDayStage.MORNING -> listOf(
            "El día viene cargando escenas nuevas: dime qué emoción le ponemos a esta mañana.",
            "¡Arriba ese telón! ¿Arrancamos con un misterio que despierte más que un espresso?",
            "Mañana con luz fresca y audífonos listos… tengo joyas escondidas en el catálogo.",
            "Si el tráfico o la rutina aburren, aquí tengo un viaje directo a otro universo.",
            "Prometo no hacer spoilers… pero hoy huele a maratón de buena serie."
        )
        DrawerDayStage.NOON -> listOf(
            "El reloj marcó mediodía: pausa oficial para escuchar un capítulo mientras descansas.",
            "Mitad del día y la energía pide historia corta, ligera o un documental sorprendente.",
            "¿Almorzando o en un respiro? Te guardo una dosis rápida de comedia o aventura.",
            "Justo a mitad de jornada viene bien desconectar un rato con una narración brillante.",
            "¡Corte comercial del mediodía! Dime cuánto tiempo tienes y elegimos la función exacta."
        )
        DrawerDayStage.AFTERNOON -> listOf(
            "Tarde perfecta para cambiar el ruido de fuera por una buena banda sonora y acción.",
            "¿A qué mundo te apetece escaparte esta tarde? Yo ya tengo el proyector encendido.",
            "Si la tarde pide sofá, manta o paseo con audífonos, tengo la historia ideal.",
            "Un poco de suspenso o una risa inesperada le dan la vuelta a cualquier tarde.",
            "Aquí no vendemos palomitas… pero casi: pídeme cualquier género y armamos plan."
        )
        DrawerDayStage.SUNSET -> listOf(
            "El cielo se tiñe de cine dorado: la transición exacta para pasar a modo película.",
            "Fin de jornada a la vista… merece celebrarse con una obra que te atrape desde el minuto uno.",
            "Cuando cae el sol, las voces cobran otra magia. ¿Exploramos los estrenos de hoy?",
            "Cierre de tarde con aroma a función estelar: solo falta que elijas quién protagoniza.",
            "Las luces bajan poquito a poco afuera; aquí dentro empieza lo mejor del catálogo."
        )
        DrawerDayStage.NIGHT -> listOf(
            "Ponte cómodo, sube el audio y apaga el mundo: esta noche toca función especial.",
            "La noche pide historias que te hagan olvidar el reloj. ¿Suspenso, drama o risas?",
            "Cartelera nocturna abierta: dime cómo terminó tu día y te receto la peli exacta.",
            "Bajo la luna todo suena más intenso… ¿nos lanzamos con un clásico o una sorpresa?",
            "Modo cine nocturno activado: prometo elegir algo que no quieras pausar ni un segundo."
        )
        DrawerDayStage.LATE_NIGHT -> listOf(
            "¿El insomnio nos reunió otra vez? Ven, tengo historias suaves para hacerte compañía.",
            "Madrugada tranquila, cero interrupciones y todo el catálogo solo para nosotros dos.",
            "Mientras medio planeta sueña, tú y yo podemos viajar por galaxias en voz baja.",
            "Club oficial de trasnochadores del cine: ¿una de misterio suave o un relato sereno?",
            "A esta hora cada susurro de la narración suena mágico. Aquí sigo despierta contigo."
        )
    }
    val safeIdx = ((variantSeed % auraOptions.size) + auraOptions.size) % auraOptions.size
    return auraOptions[safeIdx]
}

internal fun resolveRegionDisplayName(
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault()
): String {
    val spanishLocale = Locale.forLanguageTag("es")
    val rawCityKey = timeZone.id
        .substringAfterLast('/', "")
        .replace('_', ' ')
        .trim()

    val cityAccents = mapOf(
        "Bogota" to "Bogotá",
        "Mexico City" to "Ciudad de México",
        "Cordoba" to "Córdoba",
        "Asuncion" to "Asunción",
        "San Jose" to "San José",
        "Panama" to "Panamá",
        "Cancun" to "Cancún",
        "Merida" to "Mérida",
        "Sao Paulo" to "São Paulo",
        "New York" to "Nueva York",
        "Los Angeles" to "Los Ángeles"
    )

    val tzRegionFallback = mapOf(
        "America/Bogota" to "CO",
        "America/Lima" to "PE",
        "America/Mexico_City" to "MX",
        "America/Cancun" to "MX",
        "America/Monterrey" to "MX",
        "America/Tijuana" to "MX",
        "America/Argentina/Buenos_Aires" to "AR",
        "America/Buenos_Aires" to "AR",
        "America/Santiago" to "CL",
        "America/Caracas" to "VE",
        "America/Guayaquil" to "EC",
        "America/La_Paz" to "BO",
        "America/Asuncion" to "PY",
        "America/Montevideo" to "UY",
        "America/Panama" to "PA",
        "America/Costa_Rica" to "CR",
        "America/Guatemala" to "GT",
        "America/Havana" to "CU",
        "America/Santo_Domingo" to "DO",
        "America/Puerto_Rico" to "PR",
        "America/New_York" to "US",
        "America/Chicago" to "US",
        "America/Denver" to "US",
        "America/Los_Angeles" to "US",
        "Europe/Madrid" to "ES",
        "Atlantic/Canary" to "ES"
    )

    val formattedCity = rawCityKey
        .takeIf {
            it.isNotBlank() &&
                !it.equals("GMT", ignoreCase = true) &&
                !it.equals("UTC", ignoreCase = true) &&
                !it.startsWith("GMT+", ignoreCase = true) &&
                !it.startsWith("GMT-", ignoreCase = true)
        }
        ?.let { cityAccents[it] ?: it }

    // Determinar el país directamente a partir de la zona horaria (no del idioma del sistema, como es-US)
    val icuCountryCode = runCatching {
        android.icu.util.TimeZone.getRegion(timeZone.id)
    }.getOrNull()?.takeIf { it.length == 2 && it != "001" && it != "ZZ" }
        ?: tzRegionFallback[timeZone.id]

    val country = icuCountryCode?.let { code ->
        Locale.Builder().setRegion(code).build().getDisplayCountry(spanishLocale).trim()
    }?.takeIf { it.isNotBlank() }
        ?: locale.getDisplayCountry(spanishLocale).trim()

    return when {
        formattedCity != null && country.isNotBlank() && !formattedCity.equals(country, ignoreCase = true) ->
            "$formattedCity, $country"
        formattedCity != null -> formattedCity
        country.isNotBlank() -> country
        else -> timeZone.getDisplayName(false, TimeZone.SHORT, spanishLocale)
    }
}

internal fun resolveVersionChannelLabel(
    appVersion: String,
    updateChannel: String
): String {
    val parsed = SemVer.parseOrNull(appVersion)
    val isBetaBuild = parsed?.isPrerelease == true ||
        appVersion.contains("beta", ignoreCase = true) ||
        appVersion.contains("alpha", ignoreCase = true) ||
        appVersion.contains("rc", ignoreCase = true)
    val isBetaChannel = UpdateChannels.normalize(updateChannel) == UpdateChannels.BETA

    val channelDescription = when {
        isBetaBuild -> "Versión Beta"
        isBetaChannel -> "Canal Beta"
        else -> "Lanzamiento oficial"
    }
    return "Versión $appVersion • $channelDescription"
}

private fun buildDrawerHeaderMeta(
    context: Context,
    timeMillis: Long,
    username: String,
    appVersion: String,
    updateChannel: String,
    isAuraConfigured: Boolean,
    variantSeed: Int
): DrawerHeaderMeta {
    val calendar = Calendar.getInstance().apply { timeInMillis = timeMillis }
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val stage = resolveDrawerDayStage(hour)

    val stageGreeting = when (stage) {
        DrawerDayStage.DAWN, DrawerDayStage.MORNING -> "Buenos días"
        DrawerDayStage.NOON, DrawerDayStage.AFTERNOON, DrawerDayStage.SUNSET -> "Buenas tardes"
        DrawerDayStage.NIGHT, DrawerDayStage.LATE_NIGHT -> "Buenas noches"
    }
    val stageLabel = resolveDayMomentMessage(
        stage = stage,
        isAuraConfigured = isAuraConfigured,
        variantSeed = variantSeed
    )

    val cleanName = username
        .trim()
        .takeIf { it.isNotBlank() && !it.equals("Audiocinemateca", ignoreCase = true) }

    val greeting = if (cleanName != null) {
        "¡$stageGreeting, $cleanName!"
    } else {
        "¡$stageGreeting!"
    }

    val is24h = runCatching { DateFormat.is24HourFormat(context) }.getOrDefault(false)
    val timePattern = if (is24h) "HH:mm" else "h:mm a"
    val formattedTime = SimpleDateFormat(timePattern, Locale.forLanguageTag("es"))
        .format(Date(timeMillis))
        .trim()

    val regionName = resolveRegionDisplayName()
    val versionSummary = resolveVersionChannelLabel(appVersion, updateChannel)

    return DrawerHeaderMeta(
        stage = stage,
        stageLabel = stageLabel,
        greeting = greeting,
        formattedTime = formattedTime,
        regionName = regionName,
        versionSummary = versionSummary,
        isAuraMessage = isAuraConfigured
    )
}

@Composable
fun NavigationDrawerHeader(
    username: String,
    appVersion: String,
    updateChannel: String,
    isAuraConfigured: Boolean,
    isDrawerOpen: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var openSeed by remember { androidx.compose.runtime.mutableIntStateOf(kotlin.random.Random.nextInt(5)) }

    LaunchedEffect(isDrawerOpen) {
        nowMillis = System.currentTimeMillis()
        if (isDrawerOpen) {
            val prevMod = openSeed % 5
            var nextSeed = kotlin.random.Random.nextInt(5)
            if (nextSeed == prevMod) {
                nextSeed = (nextSeed + 1 + kotlin.random.Random.nextInt(4)) % 5
            }
            openSeed = nextSeed
            while (true) {
                delay(15_000L)
                nowMillis = System.currentTimeMillis()
            }
        }
    }

    val meta = remember(nowMillis, username, appVersion, updateChannel, isAuraConfigured, openSeed) {
        buildDrawerHeaderMeta(
            context = context,
            timeMillis = nowMillis,
            username = username,
            appVersion = appVersion,
            updateChannel = updateChannel,
            isAuraConfigured = isAuraConfigured,
            variantSeed = openSeed
        )
    }

    val skyGradient = remember(meta.stage) {
        when (meta.stage) {
            DrawerDayStage.DAWN -> listOf(
                Color(0xFF171338),
                Color(0xFF432371),
                Color(0xFF9A3412)
            )
            DrawerDayStage.MORNING -> listOf(
                Color(0xFF0A2540),
                Color(0xFF0F4C81),
                Color(0xFF0284C7)
            )
            DrawerDayStage.NOON -> listOf(
                Color(0xFF0B3B69),
                Color(0xFF0284C7),
                Color(0xFF38BDF8)
            )
            DrawerDayStage.AFTERNOON -> listOf(
                Color(0xFF0B1F4B),
                Color(0xFF1D4ED8),
                Color(0xFF0EA5E9)
            )
            DrawerDayStage.SUNSET -> listOf(
                Color(0xFF151238),
                Color(0xFF581C87),
                Color(0xFFB45309)
            )
            DrawerDayStage.NIGHT -> listOf(
                Color(0xFF020617),
                Color(0xFF0F172A),
                Color(0xFF1E1B4B)
            )
            DrawerDayStage.LATE_NIGHT -> listOf(
                Color(0xFF01040F),
                Color(0xFF090D24),
                Color(0xFF17113A)
            )
        }
    }

    Surface(
        color = Color(0xFF0F172A),
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                isTraversalGroup = true
                traversalIndex = 1f
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(colors = skyGradient))
        ) {
            // Escena celestial animada decorativa acorde al momento del día
            TimeOfDaySkyAnimation(
                stage = meta.stage,
                modifier = Modifier
                    .matchParentSize()
                    .clearAndSetSemantics {}
            )

            // Velo sutil de contraste en la zona inferior/izquierda para garantizar lectura impecable
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.14f),
                                Color.Black.copy(alpha = 0.38f)
                            )
                        )
                    )
                    .clearAndSetSemantics {}
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Título principal en grande arriba del todo
                Text(
                    text = "Audiocinemateca",
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 0.3.sp,
                    modifier = Modifier.accessibleHeading("Audiocinemateca")
                )

                // 2. Logo insignia abajo del título con descripción profesional accesible + mensaje de Aura o escena
                val logoDescription =
                    "Insignia oficial de Audiocinemateca: un marco de pantalla de cine iluminado con el triángulo de reproducción en el centro, rodeado por audífonos modernos y ondas sonoras concéntricas en azul índigo y cian sobre fondo noche, representando el entretenimiento audiovisual audiodescrito."

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF0C1630),
                        border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.28f)),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(64.dp)
                            .clearAndSetSemantics {
                                contentDescription = logoDescription
                                role = Role.Image
                            }
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_audiocinemateca_brand),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.28f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                        modifier = Modifier
                            .weight(1f)
                            .clearAndSetSemantics {
                                contentDescription = meta.stageLabel
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            if (meta.isAuraMessage) {
                                Image(
                                    painter = painterResource(id = R.drawable.aura_avatar),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                )
                            }
                            Text(
                                text = meta.stageLabel,
                                style = MaterialTheme.typography.labelSmall,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.96f)
                            )
                        }
                    }
                }

                // 3. Saludo personalizado según la etapa del día
                Text(
                    text = meta.greeting,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // 4. Hora actual y región del usuario (un único nodo semántico para evitar doble lectura)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics {
                            contentDescription = "Son las ${meta.formattedTime}, hora de ${meta.regionName}"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF7DD3FC),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Son las ${meta.formattedTime} • Hora de ${meta.regionName}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }

                // 5. Versión de la app y canal (Oficial o Beta)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.28f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                ) {
                    Text(
                        text = meta.versionSummary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeOfDaySkyAnimation(
    stage: DrawerDayStage,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "drawer_sky_transition")

    val slowFloat by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "celestial_float"
    )

    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "celestial_pulse"
    )

    val slowOrbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sun_rays_orbit"
    )

    Canvas(modifier = modifier) {
        when (stage) {
            DrawerDayStage.DAWN -> {
                val center = Offset(size.width * 0.84f, size.height * 0.44f + slowFloat * 4.dp.toPx())
                drawSunWithGlow(
                    center = center,
                    coreColor = Color(0xFFFDE68A),
                    glowColor = Color(0xFFF59E0B),
                    radius = 18.dp.toPx(),
                    pulse = pulse,
                    orbitDegrees = slowOrbit
                )
            }
            DrawerDayStage.MORNING -> {
                val center = Offset(size.width * 0.84f, size.height * 0.24f + slowFloat * 3.dp.toPx())
                drawSunWithGlow(
                    center = center,
                    coreColor = Color(0xFFFEF08A),
                    glowColor = Color(0xFF38BDF8),
                    radius = 18.dp.toPx(),
                    pulse = pulse,
                    orbitDegrees = slowOrbit
                )
                drawSoftCloud(
                    origin = Offset(size.width * 0.56f + slowFloat * 8.dp.toPx(), size.height * 0.22f),
                    scale = 0.85f,
                    alpha = 0.18f
                )
                drawSoftCloud(
                    origin = Offset(size.width * 0.72f - slowFloat * 6.dp.toPx(), size.height * 0.62f),
                    scale = 0.7f,
                    alpha = 0.14f
                )
            }
            DrawerDayStage.NOON -> {
                val center = Offset(size.width * 0.84f, size.height * 0.16f + slowFloat * 2.5.dp.toPx())
                drawSunWithGlow(
                    center = center,
                    coreColor = Color(0xFFFFFBEB),
                    glowColor = Color(0xFF7DD3FC),
                    radius = 20.dp.toPx(),
                    pulse = pulse,
                    orbitDegrees = slowOrbit
                )
            }
            DrawerDayStage.AFTERNOON -> {
                val center = Offset(size.width * 0.85f, size.height * 0.22f + slowFloat * 3.dp.toPx())
                drawSunWithGlow(
                    center = center,
                    coreColor = Color(0xFFFEF9C3),
                    glowColor = Color(0xFF60A5FA),
                    radius = 19.dp.toPx(),
                    pulse = pulse,
                    orbitDegrees = slowOrbit
                )
                drawSoftCloud(
                    origin = Offset(size.width * 0.52f + slowFloat * 10.dp.toPx(), size.height * 0.26f),
                    scale = 0.9f,
                    alpha = 0.16f
                )
            }
            DrawerDayStage.SUNSET -> {
                val center = Offset(size.width * 0.83f, size.height * 0.56f + slowFloat * 4.dp.toPx())
                drawSunWithGlow(
                    center = center,
                    coreColor = Color(0xFFFDBA74),
                    glowColor = Color(0xFFEA580C),
                    radius = 20.dp.toPx(),
                    pulse = pulse,
                    orbitDegrees = slowOrbit
                )
                drawTwinklingStars(pulse = pulse, alphaScale = 0.45f)
            }
            DrawerDayStage.NIGHT -> {
                drawTwinklingStars(pulse = pulse, alphaScale = 0.95f)
                val moonCenter = Offset(size.width * 0.84f, size.height * 0.25f + slowFloat * 4.dp.toPx())
                drawCrescentMoon(
                    center = moonCenter,
                    radius = 17.dp.toPx(),
                    pulse = pulse,
                    shadowBg = Color(0xFF0A1228)
                )
            }
            DrawerDayStage.LATE_NIGHT -> {
                drawTwinklingStars(pulse = pulse, alphaScale = 1f)
                val moonCenter = Offset(size.width * 0.86f, size.height * 0.34f + slowFloat * 3.5.dp.toPx())
                drawCrescentMoon(
                    center = moonCenter,
                    radius = 15.dp.toPx(),
                    pulse = pulse,
                    shadowBg = Color(0xFF070B1E)
                )
            }
        }
    }
}

private fun DrawScope.drawSunWithGlow(
    center: Offset,
    coreColor: Color,
    glowColor: Color,
    radius: Float,
    pulse: Float,
    orbitDegrees: Float
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glowColor.copy(alpha = 0.35f + pulse * 0.15f), Color.Transparent),
            center = center,
            radius = radius * (2.6f + pulse * 0.35f)
        ),
        radius = radius * 3.0f,
        center = center
    )

    // Rayos sutiles girando suavemente
    val rayCount = 8
    val innerR = radius * 1.25f
    val outerR = radius * (1.62f + pulse * 0.14f)
    for (i in 0 until rayCount) {
        val angleRad = ((i * (360f / rayCount)) + orbitDegrees) * (PI / 180.0)
        val sx = center.x + (innerR * cos(angleRad)).toFloat()
        val sy = center.y + (innerR * sin(angleRad)).toFloat()
        val ex = center.x + (outerR * cos(angleRad)).toFloat()
        val ey = center.y + (outerR * sin(angleRad)).toFloat()
        drawLine(
            color = coreColor.copy(alpha = 0.52f),
            start = Offset(sx, sy),
            end = Offset(ex, ey),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    drawCircle(
        color = coreColor.copy(alpha = 0.92f),
        radius = radius,
        center = center
    )
}

private fun DrawScope.drawSoftCloud(
    origin: Offset,
    scale: Float,
    alpha: Float
) {
    val r = 14.dp.toPx() * scale
    val color = Color.White.copy(alpha = alpha)
    drawCircle(color = color, radius = r, center = origin)
    drawCircle(color = color, radius = r * 1.25f, center = Offset(origin.x + r * 1.0f, origin.y - r * 0.28f))
    drawCircle(color = color, radius = r * 0.92f, center = Offset(origin.x + r * 2.0f, origin.y + r * 0.05f))
}

private fun DrawScope.drawTwinklingStars(
    pulse: Float,
    alphaScale: Float
) {
    val stars = listOf(
        Triple(0.56f, 0.16f, 0f),
        Triple(0.66f, 0.32f, 0.35f),
        Triple(0.73f, 0.12f, 0.7f),
        Triple(0.92f, 0.44f, 0.2f),
        Triple(0.61f, 0.54f, 0.55f),
        Triple(0.77f, 0.68f, 0.85f),
        Triple(0.91f, 0.78f, 0.45f)
    )
    stars.forEach { (fx, fy, phaseOffset) ->
        val wave = sin((pulse + phaseOffset) * PI).toFloat().let { if (it < 0f) -it else it }
        val starAlpha = ((0.25f + wave * 0.7f) * alphaScale).coerceIn(0f, 1f)
        val cx = size.width * fx
        val cy = size.height * fy
        val r = (1.6f + wave * 1.4f).dp.toPx()
        drawCircle(
            color = Color(0xFFE0F2FE).copy(alpha = starAlpha),
            radius = r,
            center = Offset(cx, cy)
        )
    }
}

private fun DrawScope.drawCrescentMoon(
    center: Offset,
    radius: Float,
    pulse: Float,
    shadowBg: Color
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF7DD3FC).copy(alpha = 0.25f + pulse * 0.1f), Color.Transparent),
            center = center,
            radius = radius * 2.7f
        ),
        radius = radius * 2.7f,
        center = center
    )

    // Media luna usando Path combinado con recorte desplazado
    val outer = Path().apply {
        addOval(
            androidx.compose.ui.geometry.Rect(
                left = center.x - radius,
                top = center.y - radius,
                right = center.x + radius,
                bottom = center.y + radius
            )
        )
    }
    drawPath(path = outer, color = Color(0xFFE0F2FE))
    drawCircle(
        color = shadowBg.copy(alpha = 0.96f),
        radius = radius * 0.86f,
        center = Offset(center.x + radius * 0.42f, center.y - radius * 0.24f)
    )
}
