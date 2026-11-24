package io.github.mbanucu.termux;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One finished Termux {@code RUN_COMMAND}, reduced to what a host can show.
 *
 * <p>Termux sends stdout, stderr, the exit code and its own error fields back
 * through the {@code PendingIntent} the caller supplied (Termux &ge; 0.109).
 * Scripts commonly answer with one final line, {@code RESULT key=value ...},
 * so a host does not have to infer success from a side effect. The parser here
 * is pure Java and is covered by device-free tests; the Android service and
 * receiver in {@code termux-run-command} are only thin adapters around it.
 *
 * <p>The class is immutable and carries no Android dependency.
 */
public final class TermuxResult {
    /** Termux's "no internal error" code, {@code Activity.RESULT_OK}. */
    public static final int ERR_OK = TermuxContract.ERR_OK;

    /** The script Termux was asked to run, verbatim (may be a full path). */
    public final String script;
    /** Standard output; never null. */
    public final String stdout;
    /** Standard error; never null. */
    public final String stderr;
    /** Termux's own error message; never null. */
    public final String errmsg;
    /** Null when Termux did not report an exit code (it failed before the shell). */
    public final Integer exitCode;
    /** Termux's internal error code; {@link #ERR_OK} means none. */
    public final int errCode;
    /** The text after the final {@code RESULT}, or null when stdout had no such line. */
    public final String line;
    /** The parsed {@code key=value} tokens of {@link #line}; unmodifiable. */
    public final Map<String, String> values;
    /** Caller-supplied timestamp, for example {@code SystemClock.elapsedRealtime()}. */
    public final long at;

    private TermuxResult(String script, String stdout, String stderr, String errmsg,
                         Integer exitCode, int errCode, String line, long at) {
        this.script = script == null ? "" : script;
        this.stdout = stdout;
        this.stderr = stderr;
        this.errmsg = errmsg;
        this.exitCode = exitCode;
        this.errCode = errCode;
        this.line = line;
        this.values = parseValues(line);
        this.at = at;
    }

    /**
     * Parses one result. Null streams are normalised to empty strings; a null
     * exit code stays null instead of being guessed as success.
     */
    public static TermuxResult parse(String script, String stdout, String stderr,
                                     Integer exitCode, int errCode, String errmsg, long at) {
        String out = stdout == null ? "" : stdout;
        return new TermuxResult(script, out, stderr == null ? "" : stderr,
                errmsg == null ? "" : errmsg, exitCode, errCode,
                lastResultLine(out), at);
    }

    /** True when Termux itself and the script both reported success. */
    public boolean ok() {
        return line != null && !values.containsKey("error")
                && exitCode != null && exitCode == 0 && errCode == ERR_OK;
    }

    /**
     * True when the command succeeded and its final {@code RESULT} line
     * carried {@code key}. Replaces script-specific helpers such as
     * "pinned" or "wifi": the caller names the key it cares about.
     */
    public boolean reports(String key) {
        return key != null && ok() && values.containsKey(key);
    }

    /** True when {@link #script} ends in {@code name} (a path or a bare name). */
    public boolean isScript(String name) {
        return name != null && name.equals(basename(script));
    }

    /** The final RESULT payload, or an explanation when there was none. */
    public String summary() {
        if (line != null) {
            return line.isEmpty() ? "RESULT" : line;
        }
        String err = firstLine(errmsg);
        if (!err.isEmpty()) return "no RESULT line: " + cap(err);
        return "no RESULT line";
    }

    /** One line for a UI: payload, exit code and the first stderr line. */
    public String uiLine() {
        StringBuilder out = new StringBuilder(summary());
        out.append(" (exit ").append(exitText()).append(')');
        String err = firstLine(stderr);
        if (!err.isEmpty()) out.append("; ").append(cap(err));
        return out.toString();
    }

    /** One line for a diagnostic journal, with both streams and Termux's error. */
    public String journal() {
        StringBuilder out = new StringBuilder();
        out.append("script=").append(basename(script))
                .append(" result=").append(line == null ? "none" : line)
                .append(" exit=").append(exitText())
                .append(" err=").append(errCode);
        String err = firstLine(stderr);
        if (!err.isEmpty()) out.append(" stderr=").append(cap(err));
        String message = firstLine(errmsg);
        if (!message.isEmpty()) out.append(" errmsg=").append(cap(message));
        return out.toString();
    }

    /**
     * The last {@code RESULT ...} line in stdout; lines before it are ignored.
     * A bare {@code RESULT} yields {@code ""}; a word such as {@code RESULTX}
     * is not a RESULT line.
     */
    public static String lastResultLine(String stdout) {
        String found = null;
        String text = stdout == null ? "" : stdout;
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        for (String raw : text.split("\n", -1)) {
            String line = raw.trim();
            if (!line.startsWith("RESULT")) continue;
            if (line.length() > 6 && !Character.isWhitespace(line.charAt(6))) continue;
            found = line.length() > 6 ? line.substring(7).trim() : "";
        }
        return found;
    }

    /**
     * Parses {@code key=value} tokens separated by whitespace. A token without
     * {@code =}, or with an empty key, is ignored; a value may itself contain
     * {@code =}. Returns an unmodifiable map.
     */
    public static Map<String, String> parseValues(String line) {
        if (line == null || line.isEmpty()) return Collections.emptyMap();
        Map<String, String> values = new LinkedHashMap<>();
        for (String token : line.split("\\s+")) {
            int eq = token.indexOf('=');
            if (eq <= 0) continue;
            values.put(token.substring(0, eq), token.substring(eq + 1));
        }
        return Collections.unmodifiableMap(values);
    }

    /** The file name of {@link #script}, without directories. */
    public String basename() {
        return basename(script);
    }

    private static String basename(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String firstLine(String text) {
        for (String raw : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            String line = raw.trim();
            if (!line.isEmpty()) return line;
        }
        return "";
    }

    private String exitText() {
        return exitCode == null ? "?" : String.valueOf(exitCode);
    }

    private static String cap(String value) {
        return value.length() <= 300 ? value : value.substring(0, 300) + "...";
    }
}
