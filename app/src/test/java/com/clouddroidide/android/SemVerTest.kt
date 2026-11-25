package com.clouddroidide.android

import com.clouddroidide.android.updater.SemVer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {
    @Test
    fun `newer tag triggers update`() {
        assertTrue(SemVer.isNewer("v1.0.1", "1.0.0"))
        assertTrue(SemVer.isNewer("1.2.0", "1.1.9"))
        assertTrue(SemVer.isNewer("2.0.0", "1.9.9"))
    }

    @Test
    fun `same or older tag does not trigger update`() {
        assertFalse(SemVer.isNewer("v1.0.0", "1.0.0"))
        assertFalse(SemVer.isNewer("1.0.0", "1.0.1"))
        assertFalse(SemVer.isNewer("v1.0.0-rc1", "1.0.0"))
    }
}
