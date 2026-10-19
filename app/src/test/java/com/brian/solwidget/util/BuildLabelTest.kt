package com.brian.solwidget.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildLabelTest {

    @Test
    fun exactReleaseTagShowsSemver() {
        val label = BuildLabel.resolve("v2.0.0\n", dirty = false, shortHash = "6e23c0b", commit = "abc")
        assertEquals("2.0.0", label.text)
        assertEquals("2.0.0", label.release)
        assertTrue(label.isRelease)
    }

    @Test
    fun commitsAfterAReleaseStayAHash() {
        val label = BuildLabel.resolve("", dirty = false, shortHash = "6e23c0b", commit = "abc")
        assertEquals("6e23c0b", label.text)
        assertEquals("", label.release)
        assertFalse(label.isRelease)
    }

    @Test
    fun prereleaseTagStaysAHash() {
        val label = BuildLabel.resolve("v2.1.0-rc.1", dirty = false, shortHash = "abcdef0", commit = "abc")
        assertEquals("abcdef0", label.text)
        assertNull(BuildLabel.highestRelease("v2.1.0-rc.1"))
    }

    @Test
    fun editedReleaseTreeStaysAHash() {
        val label = BuildLabel.resolve("v2.0.0", dirty = true, shortHash = "6e23c0b", commit = "abc")
        assertEquals("6e23c0b", label.text)
        assertEquals("", label.release)
    }

    @Test
    fun highestStableTagWins() {
        assertEquals(Semver(2, 1, 0), BuildLabel.highestRelease("v2.0.0\nv2.1.0-beta.1\n2.1.0"))
    }
}
