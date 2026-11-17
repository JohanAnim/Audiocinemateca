package com.johang.audiocinemateca.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {

    @Test
    fun `beta prerelease is newer than previous stable release`() {
        assertTrue(SemVer.isNewer("3.4.0-beta.1", "3.3.2"))
        assertTrue(SemVer.isNewer("v3.4.0-beta.1", "3.3.2"))
    }

    @Test
    fun `stable release is newer than its beta prereleases`() {
        assertTrue(SemVer.isNewer("3.4.0", "3.4.0-beta.1"))
        assertTrue(SemVer.isNewer("3.4.0", "3.4.0-rc.2"))
        assertFalse(SemVer.isNewer("3.4.0-beta.2", "3.4.0"))
    }

    @Test
    fun `beta numeric identifiers compare numerically`() {
        assertTrue(SemVer.isNewer("3.4.0-beta.2", "3.4.0-beta.1"))
        assertTrue(SemVer.isNewer("3.4.0-beta.10", "3.4.0-beta.2"))
        assertTrue(SemVer.isNewer("3.4.0-rc.1", "3.4.0-beta.9"))
        assertFalse(SemVer.isNewer("3.4.0-beta.1", "3.4.0-beta.1"))
    }

    @Test
    fun `downgrade from beta to older stable is not offered as newer`() {
        assertFalse(SemVer.isNewer("3.3.2", "3.4.0-beta.1"))
    }

    @Test
    fun `channel helpers normalize unknown values to stable`() {
        assertEquals(UpdateChannels.STABLE, UpdateChannels.normalize(null))
        assertEquals(UpdateChannels.STABLE, UpdateChannels.normalize("unknown"))
        assertEquals(UpdateChannels.BETA, UpdateChannels.normalize("beta"))
        assertEquals(UpdateChannels.BETA, UpdateChannels.normalize("BETA"))
        assertEquals("Beta", UpdateChannels.displayName("beta"))
        assertEquals("Estable", UpdateChannels.displayName("stable"))
    }
}
