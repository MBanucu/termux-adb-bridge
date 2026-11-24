package io.github.mbanucu.termux;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Map;
import org.junit.Test;

/**
 * The intent shape without a device: the spec is exactly what
 * {@link TermuxCommand} copies onto the Intent.
 */
public class TermuxIntentSpecTest {

    @Test
    public void defaultsAddressTermuxsServiceInTheHomeDirectory() {
        TermuxIntentSpec spec = TermuxIntentSpec.builder().build();

        assertEquals("com.termux.RUN_COMMAND", spec.action());
        assertEquals("com.termux", spec.componentPackage());
        assertEquals("com.termux.app.RunCommandService", spec.componentClass());
        assertEquals("/data/data/com.termux/files/usr/bin/sh", spec.script());
        assertEquals("/data/data/com.termux/files/home", spec.workdir());
        assertTrue(spec.background());

        Map<String, Object> extras = spec.extras();
        assertEquals(4, extras.size());
        assertEquals("/data/data/com.termux/files/usr/bin/sh",
                extras.get(TermuxIntentSpec.EXTRA_PATH));
        assertArrayEquals(new String[0],
                (String[]) extras.get(TermuxIntentSpec.EXTRA_ARGUMENTS));
        assertEquals("/data/data/com.termux/files/home",
                extras.get(TermuxIntentSpec.EXTRA_WORKDIR));
        assertEquals(Boolean.TRUE, extras.get(TermuxIntentSpec.EXTRA_BACKGROUND));
        assertNull(extras.get(TermuxIntentSpec.EXTRA_RUNNER));
        assertNull(extras.get(TermuxIntentSpec.EXTRA_COMMAND_LABEL));
        assertNull(extras.get(TermuxIntentSpec.EXTRA_SESSION_ACTION));
    }

    @Test
    public void aFullCommandCarriesEveryDocumentedExtra() {
        TermuxIntentSpec spec = TermuxIntentSpec.builder()
                .script("/data/data/com.termux/files/usr/bin/bash")
                .args("-lc", "echo $PWD")
                .workdir("/data/data/com.termux/files/home/project")
                .background(false)
                .runner("app-shell")
                .label("build")
                .description("run the build")
                .sessionAction(0)
                .build();

        assertFalse(spec.background());
        Map<String, Object> extras = spec.extras();
        assertEquals(8, extras.size());
        assertEquals("/data/data/com.termux/files/usr/bin/bash",
                extras.get(TermuxIntentSpec.EXTRA_PATH));
        assertArrayEquals(new String[] {"-lc", "echo $PWD"},
                (String[]) extras.get(TermuxIntentSpec.EXTRA_ARGUMENTS));
        assertEquals("/data/data/com.termux/files/home/project",
                extras.get(TermuxIntentSpec.EXTRA_WORKDIR));
        assertEquals(Boolean.FALSE, extras.get(TermuxIntentSpec.EXTRA_BACKGROUND));
        assertEquals("app-shell", extras.get(TermuxIntentSpec.EXTRA_RUNNER));
        assertEquals("build", extras.get(TermuxIntentSpec.EXTRA_COMMAND_LABEL));
        assertEquals("run the build", extras.get(TermuxIntentSpec.EXTRA_COMMAND_DESCRIPTION));
        assertEquals("0", extras.get(TermuxIntentSpec.EXTRA_SESSION_ACTION));
    }

    @Test
    public void theBridgeShapeIsTheSameAsTheOriginalActivity() {
        TermuxIntentSpec spec = TermuxIntentSpec.builder()
                .args("-c", "whoami")
                .sessionAction(0)
                .label("adb")
                .build();

        Map<String, Object> extras = spec.extras();
        assertArrayEquals(new String[] {"-c", "whoami"},
                (String[]) extras.get(TermuxIntentSpec.EXTRA_ARGUMENTS));
        assertEquals("0", extras.get(TermuxIntentSpec.EXTRA_SESSION_ACTION));
        assertEquals("adb", extras.get(TermuxIntentSpec.EXTRA_COMMAND_LABEL));
        assertEquals("com.termux", spec.componentPackage());
        assertEquals("com.termux.app.RunCommandService", spec.componentClass());
        assertEquals("com.termux.RUN_COMMAND", spec.action());
    }

    @Test
    public void argsReplaceInsteadOfAppend() {
        TermuxIntentSpec spec = TermuxIntentSpec.builder()
                .args("old", "arguments")
                .args(Arrays.asList("-c", "new"))
                .build();
        assertArrayEquals(new String[] {"-c", "new"},
                (String[]) spec.extras().get(TermuxIntentSpec.EXTRA_ARGUMENTS));
    }

    @Test
    public void extrasAreUnmodifiable() {
        Map<String, Object> extras = TermuxIntentSpec.builder().build().extras();
        try {
            extras.put("x", "y");
            fail("extras should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void emptyScriptAndWorkdirAreRejected() {
        try {
            TermuxIntentSpec.builder().script(" ").build();
            fail("empty script accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            TermuxIntentSpec.builder().workdir("").build();
            fail("empty workdir accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            TermuxIntentSpec.builder().args("-c", null).build();
            fail("null argument accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void requestCodesAreUnique() {
        int first = TermuxPendingIntents.nextRequestCode();
        int second = TermuxPendingIntents.nextRequestCode();
        assertFalse(first == second);
    }
}
