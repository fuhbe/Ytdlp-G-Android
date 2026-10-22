package com.fuhbe.ytdlpg.simple

import org.junit.Assert.*
import org.junit.Test

class PolicyTest {
    @Test fun acceptsDistinctYoutubeLinksAndRejectsOtherHostsAndSchemes() {
        assertEquals(2, Policy.links("https://youtu.be/123456789ab\nhttps://www.youtube.com/playlist?list=PL123\nhttps://youtu.be/123456789ab").size)
        for (bad in listOf("javascript:alert(1)", "https://youtube.com.evil.org/watch?v=x", "http://youtu.be/123", "https://a@youtube.com/watch?v=x")) {
            assertThrows(IllegalArgumentException::class.java) { Policy.links(bad) }
        }
    }
    @Test fun zipIsNeverEnabledForOneFileAndSelectionChangesClearIt() {
        assertFalse(Policy.choice(Choice(zip = true), 1).zip)
        assertFalse(Policy.choice(Choice(zip = true), 0).zip)
        assertTrue(Policy.choice(Choice(zip = true), 2).zip)
    }
    @Test fun offersOnlyRealHeightsUpTo4kAndUsesAnExplicitFallback() {
        assertEquals(listOf(2160, 1080, 720, 144), Policy.heights(listOf(4320, 2160, 1080, 720, 1080, 0, 144)))
        assertTrue(Policy.selector(1080).contains("height<=1080"))
        assertTrue(Policy.selector(0).contains("height<=2160"))
        assertFalse(Policy.selector(720).contains("mp3"))
    }
    @Test fun accessDenialAndDiskErrorsAreActionable() {
        assertTrue(Policy.readableError("HTTP Error 403: Forbidden").contains("403"))
        assertTrue(Policy.readableError("ENOSPC").contains("Armazenamento"))
    }
}
