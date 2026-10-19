package com.brian.solwidget.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTabTest {
    @Test
    fun blankOrUnknownNamesStayOnLive() {
        assertEquals(HomeTab.LIVE, homeTabStored(null))
        assertEquals(HomeTab.LIVE, homeTabStored(""))
        assertEquals(HomeTab.LIVE, homeTabStored("live"))
    }

    @Test
    fun historyNameRestoresHistory() {
        assertEquals(HomeTab.HISTORY, homeTabStored("history"))
        assertEquals("history", HomeTab.HISTORY.storedName())
        assertEquals("live", HomeTab.LIVE.storedName())
    }
}
