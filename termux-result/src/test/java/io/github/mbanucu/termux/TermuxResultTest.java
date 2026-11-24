package io.github.mbanucu.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

/**
 * Lifted from wireless-adb-keeper's {@code tools/ResultProbe.java}: the checks
 * that used to need a manual main and now run as ordinary JVM tests.
 */
public class TermuxResultTest {

    private static TermuxResult parse(String script, String stdout, String stderr,
                                      Integer exit, int err, String errmsg, long at) {
        return TermuxResult.parse(script, stdout, stderr, exit, err, errmsg, at);
    }

    @Test
    public void plainSuccessParses() {
        TermuxResult pinned = parse("pin-adb-port.sh",
                "attaching...\nRESULT pinned=5555\n", "", 0, -1, "", 1L);
        assertTrue(pinned.ok());
        assertEquals("pinned=5555", pinned.summary());
        assertEquals("5555", pinned.values.get("pinned"));
        assertEquals("pinned=5555 (exit 0)", pinned.uiLine());
        assertTrue(pinned.journal().contains("script=pin-adb-port.sh"));
        assertTrue(pinned.journal().contains("exit=0"));
        assertTrue(pinned.journal().contains("err=-1"));
    }

    @Test
    public void errorLineIsAFailureAndKeepsTheReason() {
        TermuxResult failed = parse("pin-adb-port.sh",
                "RESULT error=no-adbd\n", "adbd: no devices\n", 1, -1, "", 2L);
        assertFalse(failed.ok());
        assertEquals("error=no-adbd", failed.summary());
        assertEquals("error=no-adbd (exit 1); adbd: no devices", failed.uiLine());
    }

    @Test
    public void theLastResultLineWins() {
        TermuxResult last = parse("pin-adb-port.sh",
                "RESULT error=no-adbd\nmore work\nRESULT pinned=5555\n", "", 0, -1, "", 3L);
        assertTrue(last.ok());
        assertEquals("pinned=5555", last.summary());
    }

    @Test
    public void skippedIsSuccessButDoesNotReportTheKey() {
        TermuxResult skipped = parse("pin-adb-port.sh",
                "RESULT skipped=running\n", "", 0, -1, "", 13L);
        assertTrue(skipped.ok());
        assertFalse(skipped.reports("pinned"));

        TermuxResult pinned = parse("pin-adb-port.sh",
                "RESULT pinned=5555\n", "", 0, -1, "", 1L);
        assertTrue(pinned.reports("pinned"));
        assertFalse(parse("pin-adb-port.sh", "RESULT error=x\n", "", 1, -1, "", 2L)
                .reports("pinned"));
    }

    @Test
    public void crlfOutputParses() {
        TermuxResult crlf = parse("wifi-on.sh",
                "wifi_on=1\r\nRESULT wifi=on\r\n", "", 0, -1, "", 4L);
        assertTrue(crlf.ok());
        assertEquals("wifi=on", crlf.summary());
        assertTrue(crlf.isScript("wifi-on.sh"));
    }

    @Test
    public void nonZeroExitIsAFailure() {
        assertFalse(parse("pin-adb-port.sh",
                "RESULT pinned=5555\n", "", 3, -1, "", 5L).ok());
    }

    @Test
    public void termuxInternalErrorIsAFailure() {
        assertFalse(parse("pin-adb-port.sh",
                "RESULT pinned=5555\n", "", 0, 1, "Termux service was killed", 6L).ok());
    }

    @Test
    public void missingExitCodeIsNotGuessedAsSuccess() {
        assertFalse(parse("pin-adb-port.sh",
                "RESULT pinned=5555\n", "", null, -1, "", 7L).ok());
    }

    @Test
    public void noResultLineIsExplainedNotAccepted() {
        TermuxResult silent = parse("pin-adb-port.sh",
                "attaching...\n", "", 1, -1, "", 8L);
        assertFalse(silent.ok());
        assertEquals("no RESULT line", silent.summary());
        assertEquals("no RESULT line (exit 1)", silent.uiLine());
    }

    @Test
    public void noResultLineUsesTermuxErrorWhenPresent() {
        TermuxResult failedBeforeShell = parse("x.sh", "", "", null, 1,
                "Termux is not installed", 8L);
        assertFalse(failedBeforeShell.ok());
        assertEquals("no RESULT line: Termux is not installed",
                failedBeforeShell.summary());
    }

    @Test
    public void bareResultParses() {
        TermuxResult bare = parse("wait_adb.sh", "RESULT\n", "", 0, -1, "", 9L);
        assertTrue(bare.ok());
        assertEquals("RESULT", bare.summary());
        assertNull(bare.values.get("anything"));
    }

    @Test
    public void aNonResultWordIsIgnored() {
        TermuxResult prefixed = parse("pin-adb-port.sh",
                "RESULT pinned=5555\nRESULTX pinned=6666\n", "", 0, -1, "", 10L);
        assertTrue(prefixed.ok());
        assertEquals("pinned=5555", prefixed.summary());
    }

    @Test
    public void severalTokensParseEvenWithEqualsInAValue() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("error", "port-closed");
        expected.put("port", "5555");
        expected.put("detail", "a=b");
        TermuxResult values = parse("pin-adb-port.sh",
                "RESULT error=port-closed port=5555 detail=a=b\n", "", 1, -1, "", 11L);
        assertEquals(expected, values.values);
    }

    @Test
    public void scriptNamesAreComparedByBasename() {
        TermuxResult result = parse(
                "/data/data/com.termux/files/home/termux-adb-bridge/pin-adb-port.sh",
                "RESULT pinned=5555\n", "", 0, -1, "", 12L);
        assertTrue(result.isScript("pin-adb-port.sh"));
        assertFalse(result.isScript("other.sh"));
        assertEquals("pin-adb-port.sh", result.basename());
    }

    @Test
    public void nullInputsAreNormalised() {
        TermuxResult result = TermuxResult.parse(null, null, null, null, -1, null, 0L);
        assertEquals("", result.script);
        assertEquals("", result.stdout);
        assertEquals("", result.stderr);
        assertEquals("", result.errmsg);
        assertNull(result.line);
        assertFalse(result.ok());
        assertEquals("no RESULT line (exit ?)", result.uiLine());
    }

    @Test
    public void parseValuesIgnoresMalformedTokens() {
        Map<String, String> values = TermuxResult.parseValues(" a=1 =x b= c=d=e ");
        assertEquals(3, values.size());
        assertEquals("1", values.get("a"));
        assertEquals("", values.get("b"));
        assertEquals("d=e", values.get("c"));
    }
}
