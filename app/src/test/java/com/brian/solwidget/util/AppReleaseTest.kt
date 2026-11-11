package com.brian.solwidget.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReleaseTest {
    @Test
    fun theSameReleaseIsNotNewer() {
        assertFalse(isNewerRelease("2.4.0", "2.4.0"))
        assertFalse(isNewerRelease("2.4.0+", "2.4.0"))
        assertFalse(isNewerRelease("v2.4.0", "2.4.0"))
    }

    @Test
    fun aHigherNumberIsNewer() {
        assertTrue(isNewerRelease("2.4.0", "2.5.0"))
        assertTrue(isNewerRelease("2.4.0", "2.4.1"))
        assertTrue(isNewerRelease("2.4.0", "2.10.0"))
        assertTrue(isNewerRelease("2.4", "2.4.1"))
    }

    @Test
    fun aLowerOrUnreadableNameIsNotNewer() {
        assertFalse(isNewerRelease("2.5.0", "2.4.0"))
        assertFalse(isNewerRelease("2.4.0", "latest"))
        assertFalse(isNewerRelease("", "2.5.0"))
    }

    @Test
    fun aReleaseCheckIsDueADayAfterTheLastOne() {
        assertFalse(isReleaseCheckDue(1_000L, 1_000L + MILLIS_PER_DAY - 1))
        assertTrue(isReleaseCheckDue(1_000L, 1_000L + MILLIS_PER_DAY))
        assertTrue(isReleaseCheckDue(0L, MILLIS_PER_DAY))
    }

    @Test
    fun aPromptShowsOnceUntilThatVersionIsDismissed() {
        assertTrue(shouldPromptForUpdate("2.5.0", dismissedVersion = null, promptedVersion = null))
        assertFalse(shouldPromptForUpdate("2.5.0", dismissedVersion = "2.5.0", promptedVersion = null))
        assertFalse(shouldPromptForUpdate("2.5.0", dismissedVersion = null, promptedVersion = "2.5.0"))
        assertTrue(shouldPromptForUpdate("2.5.1", dismissedVersion = "2.5.0", promptedVersion = "2.5.0"))
        assertFalse(shouldPromptForUpdate(" ", dismissedVersion = null, promptedVersion = null))
    }

    @Test
    fun anOfferRequiresANewerHttpsRelease() {
        val offer = releaseOffer("v2.5.0", "https://example.com/app.apk", "2.4.0+")
        assertEquals("2.5.0", offer?.versionName)
        assertEquals("https://example.com/app.apk", offer?.apkUrl)
        assertNull(releaseOffer("2.5.0", "https://example.com/app.apk", "2.5.0+"))
        assertNull(releaseOffer("2.5.0", "http://example.com/app.apk", "2.4.0"))
        assertNull(releaseOffer("2.5.0", null, "2.4.0"))
    }

    @Test
    fun onlyANewerWaywardSunPackageCanInstall() {
        assertTrue(isInstallableUpgrade("com.brian.solwidget", 9L, 8L))
        assertFalse(isInstallableUpgrade("com.brian.solwidget", 8L, 8L))
        assertFalse(isInstallableUpgrade("com.brian.solwidget", 7L, 8L))
        assertFalse(isInstallableUpgrade("com.brian.dialbuddy", 99L, 8L))
        assertFalse(isInstallableUpgrade(null, 99L, 8L))
    }

    @Test
    fun anUpdateArchiveStartsWithZipMagic() {
        assertTrue(hasZipMagic(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4)))
        assertFalse(hasZipMagic(byteArrayOf('P'.code.toByte())))
        assertFalse(hasZipMagic(byteArrayOf('M'.code.toByte(), 'Z'.code.toByte())))
    }

}
