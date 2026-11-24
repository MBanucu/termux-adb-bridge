package io.github.mbanucu.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TermuxContractTest {

    @Test
    public void namesAreTheDocumentedTermuxOnes() {
        assertEquals("com.termux", TermuxContract.PACKAGE);
        assertEquals("com.termux.app.RunCommandService", TermuxContract.RUN_COMMAND_SERVICE);
        assertEquals("com.termux.RUN_COMMAND", TermuxContract.ACTION_RUN_COMMAND);
        assertEquals("com.termux.permission.RUN_COMMAND",
                TermuxContract.PERMISSION_RUN_COMMAND);
        assertEquals("/data/data/com.termux/files/home", TermuxContract.DEFAULT_HOME);
        assertEquals("/data/data/com.termux/files/usr/bin/sh", TermuxContract.DEFAULT_SH);
    }

    @Test
    public void minorVersionComesFromTheSecondComponent() {
        assertEquals(118, TermuxContract.minorOf("0.118.1"));
        assertEquals(109, TermuxContract.minorOf("0.109"));
        assertEquals(109, TermuxContract.minorOf("0.109.0-beta"));
        assertEquals(-1, TermuxContract.minorOf(null));
        assertEquals(-1, TermuxContract.minorOf("118"));
        assertEquals(-1, TermuxContract.minorOf("0.x"));
        assertEquals(-1, TermuxContract.minorOf(""));
    }

    @Test
    public void resultSupportStartsAt0109AndUnknownIsAssumedSupported() {
        assertTrue(TermuxContract.supportsResult("0.118.1"));
        assertTrue(TermuxContract.supportsResult("0.109"));
        assertFalse(TermuxContract.supportsResult("0.108.0"));
        assertFalse(TermuxContract.supportsResult("0.100"));
        // Unknown versions are assumed modern: an ignored extra is harmless.
        assertTrue(TermuxContract.supportsResult("unknown"));
        assertTrue(TermuxContract.supportsResult(null));
    }

    @Test
    public void allowExternalAppsParsesThePropertiesFile() {
        assertTrue(TermuxContract.allowExternalApps(
                "# a comment\nallow-external-apps=true\n"));
        assertTrue(TermuxContract.allowExternalApps(
                "allow-external-apps = TRUE \r\n"));
        assertTrue(TermuxContract.allowExternalApps(
                "allow-external-apps=false\nallow-external-apps=true\n"));
        assertFalse(TermuxContract.allowExternalApps(
                "allow-external-apps=false\n"));
        assertFalse(TermuxContract.allowExternalApps(
                "!allow-external-apps=true\n"));
        assertFalse(TermuxContract.allowExternalApps(
                "#allow-external-apps=true\n"));
        assertFalse(TermuxContract.allowExternalApps(
                "allow-external-apps=yes\n"));
        assertFalse(TermuxContract.allowExternalApps(""));
        assertFalse(TermuxContract.allowExternalApps(null));
    }

    @Test
    public void theFixCommandIsTheSameOneEveryHostShows() {
        assertTrue(TermuxContract.ALLOW_EXTERNAL_APPS_COMMAND
                .contains("allow-external-apps=true"));
        assertTrue(TermuxContract.ALLOW_EXTERNAL_APPS_COMMAND
                .contains("termux-reload-settings"));
    }
}
