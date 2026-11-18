package com.johang.audiocinemateca.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class NavigationDrawerHeaderTest {

    @Test
    fun resolveDrawerDayStage_coversAllDayPeriods() {
        assertEquals(DrawerDayStage.DAWN, resolveDrawerDayStage(5))
        assertEquals(DrawerDayStage.DAWN, resolveDrawerDayStage(6))
        assertEquals(DrawerDayStage.MORNING, resolveDrawerDayStage(9))
        assertEquals(DrawerDayStage.NOON, resolveDrawerDayStage(12))
        assertEquals(DrawerDayStage.NOON, resolveDrawerDayStage(13))
        assertEquals(DrawerDayStage.AFTERNOON, resolveDrawerDayStage(15))
        assertEquals(DrawerDayStage.SUNSET, resolveDrawerDayStage(18))
        assertEquals(DrawerDayStage.NIGHT, resolveDrawerDayStage(21))
        assertEquals(DrawerDayStage.LATE_NIGHT, resolveDrawerDayStage(2))
    }

    @Test
    fun resolveDayMomentMessage_adaptsToAuraConfiguration() {
        val auraNoon = resolveDayMomentMessage(DrawerDayStage.NOON, isAuraConfigured = true, variantSeed = 0)
        assertTrue(auraNoon.contains("mediodía", ignoreCase = true))

        val lateNightVariants = (0..4).map { seed ->
            resolveDayMomentMessage(DrawerDayStage.LATE_NIGHT, isAuraConfigured = true, variantSeed = seed)
        }
        assertEquals(5, lateNightVariants.toSet().size)
        assertTrue(lateNightVariants.all { it.isNotBlank() })

        val genericNight = resolveDayMomentMessage(DrawerDayStage.NIGHT, isAuraConfigured = false)
        assertEquals("Noche estrellada de cine", genericNight)
    }

    @Test
    fun resolveVersionChannelLabel_distinguishesOfficialAndBeta() {
        assertEquals(
            "Versión 3.3.2 • Lanzamiento oficial",
            resolveVersionChannelLabel("3.3.2", "stable")
        )
        assertEquals(
            "Versión 3.5.0 • Canal Beta",
            resolveVersionChannelLabel("3.5.0", "beta")
        )
        assertEquals(
            "Versión 3.5.0-beta.1 • Versión Beta",
            resolveVersionChannelLabel("3.5.0-beta.1", "stable")
        )
    }

    @Test
    fun resolveRegionDisplayName_formatsCityAndCountry() {
        val lima = resolveRegionDisplayName(
            timeZone = TimeZone.getTimeZone("America/Lima"),
            locale = Locale.forLanguageTag("es-US")
        )
        assertEquals("Lima, Perú", lima)

        val bogotaWithUsLocale = resolveRegionDisplayName(
            timeZone = TimeZone.getTimeZone("America/Bogota"),
            locale = Locale.forLanguageTag("es-US")
        )
        assertEquals("Bogotá, Colombia", bogotaWithUsLocale)
    }
}
