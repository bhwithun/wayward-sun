package com.brian.solwidget.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckTest {

    private val hashBuild = BuildLabel(text = "6e23c0b", release = "", commit = "a".repeat(40))
    private val releaseBuild = BuildLabel(text = "2.0.0", release = "2.0.0", commit = "b".repeat(40))
    private val remote = RemoteRelease(tag = "v2.1.0", pageUrl = "https://github.com/bhwithun/wayward-sun/releases/tag/v2.1.0")

    @Test
    fun releaseBuildOpensANewerRelease() {
        val decision = decideUpdate(releaseBuild, remote, CommitRelation.UNKNOWN)
        assertEquals("Release 2.1.0 is available.", decision.message)
        assertEquals(remote.pageUrl, decision.openUrl)
    }

    @Test
    fun releaseBuildOnTheSameVersionStaysPut() {
        val same = remote.copy(tag = "v2.0.0")
        val decision = decideUpdate(releaseBuild, same, CommitRelation.UNKNOWN)
        assertEquals("You're on release 2.0.0.", decision.message)
        assertNull(decision.openUrl)
    }

    @Test
    fun hashBuildBehindTheReleaseOffersIt() {
        val decision = decideUpdate(hashBuild, remote, CommitRelation.LOCAL_BEHIND)
        assertEquals("Release 2.1.0 is available.", decision.message)
        assertEquals(remote.pageUrl, decision.openUrl)
    }

    @Test
    fun hashBuildAheadOfTheReleaseDoesNotOfferIt() {
        val decision = decideUpdate(hashBuild, remote, CommitRelation.LOCAL_AHEAD)
        assertEquals("This build is ahead of release 2.1.0.", decision.message)
        assertNull(decision.openUrl)
    }

    @Test
    fun missingReleaseIsReported() {
        val decision = decideUpdate(hashBuild, null, CommitRelation.UNKNOWN)
        assertEquals("No release has been published.", decision.message)
    }

    @Test
    fun checkerUsesTheReleaseSource() {
        val source = object : ReleaseSource {
            override fun latest() = remote
            override fun relation(commit: String, tag: String) = CommitRelation.SAME
        }
        val decision = UpdateChecker.check(hashBuild, source)
        assertEquals("This build matches release 2.1.0.", decision.message)
    }

    @Test
    fun githubJsonFieldsDecode() {
        val body = """{"tag_name":"v2.0.0","html_url":"https://github.com/bhwithun/wayward-sun/releases/tag/v2.0.0","status":"ahead"}"""
        assertEquals("v2.0.0", jsonString(body, "tag_name"))
        assertEquals("ahead", jsonString(body, "status"))
    }
}
