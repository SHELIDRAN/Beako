package org.nekomanga.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.nekomanga.desktop.platform.SelfTest

class SelfTestTest {

    @Test
    fun everyRuntimeCheckPassesOnTheBuildJdk() {
        val results = SelfTest.run()
        assertTrue(results.size >= 7)
        results.forEach { (name, error) -> assertEquals(null, error?.toString(), name) }

        val report = Files.createTempFile("beako-self-test", ".txt").toFile()
        assertEquals(0, SelfTest.runAndReport(report))
        assertTrue(report.readLines().all { it.startsWith("PASS ") })
    }
}
