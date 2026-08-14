package net.kdt.pojavlaunch.logs;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What each line of the log is, and which lines a filter should show.
 *
 * <b>Plain Java with no Android in it, on purpose.</b> This is the one part of the log viewer that
 * can be wrong without looking wrong: a line mis-read as INFO simply does not appear when the
 * filter is set to errors, and nothing anywhere says so. There is no Kotlin compiler in the build
 * container and no device in CI, so the only way this gets checked before a user sees it is
 * `scripts/logsim`, which compiles this file at source 8 and drives it. Written in Kotlin it
 * would be unverifiable, which is the whole reason every other parser here that has a harness
 * ({@code ModrinthMods}, {@code MojangSkins}, {@code LoaderIndex}) is Java too.
 */
public final class LogParser {

    private LogParser() {}

    /** How loud a line is. Declared quietest first, so a filter is an ordinal comparison. */
    public enum Level { DEBUG, INFO, WARN, ERROR }

    /** One line of the log, with the level it will be filtered and coloured by. */
    public static final class Line {
        public final String text;
        public final Level level;
        /**
         * Whether the line declared that level itself.
         *
         * The difference matters twice over. A stack trace under an exception carries no level
         * and must still filter as the error it belongs to, or turning the filter on shows a
         * one-line exception with its cause hidden. But it must not be <i>coloured</i> as one,
         * or a single crash paints forty lines red and nothing on screen stands out.
         */
        public final boolean own;

        Line(String text, Level level, boolean own) {
            this.text = text;
            this.level = level;
            this.own = own;
        }
    }

    /**
     * The level marker as every logger writing this file emits it.
     *
     * Ground truth is in this repository rather than remembered: the launcher itself hands the
     * game {@code -Dlog4j.configurationFile=} pointing at
     * {@code assets/components/security/log4j-rce-patch-*.xml}, and every one of those sets
     * {@code [%d{HH:mm:ss}] [%t/%level]: %msg%n}. So the level arrives after a {@code /} or a
     * {@code [} and is closed by {@code ]}, {@code /} or {@code :}, which also covers the older
     * Forge shape that puts {@code [FML]} after it. The harness reads those configs and renders
     * them, so a format that changes there fails a check rather than a user's error filter.
     */
    static final Pattern LEVEL = Pattern.compile(
            "[\\[/](FATAL|ERROR|SEVERE|WARN|WARNING|INFO|DEBUG|TRACE)[\\]/:]");

    /**
     * How far into a line a level may be found.
     *
     * A level belongs to the prefix a logger writes, never to the message a mod printed: chat
     * routinely quotes the word, and a player typing "the ERROR was mine" must not file their
     * own sentence under Errors.
     */
    static final int PREFIX_CHARS = 100;

    /**
     * Lines that are an error while carrying no level of their own.
     *
     * The JVM's last words and Android's native crash line are written straight to the stream
     * rather than through the game's logger, so nothing brackets them, and they are the two most
     * worth finding in the whole file.
     */
    static final String[] ERROR_MARKS = {
            "Exception in thread", "FATAL EXCEPTION", "Fatal signal"
    };

    /**
     * Split the text into lines and work out what each one is.
     *
     * <b>A line with no level of its own inherits the line above it.</b> That is what keeps a
     * stack trace attached to the exception that threw it when the filter is set to errors, which
     * is the one thing a level filter over a Java log has to get right.
     */
    public static List<Line> parse(String text) {
        List<Line> lines = new ArrayList<>();
        if (text == null) return lines;
        Level previous = Level.INFO;
        int from = 0;
        while (from <= text.length()) {
            int end = text.indexOf('\n', from);
            if (end == -1) end = text.length();
            String raw = text.substring(from, end);
            if (raw.endsWith("\r")) raw = raw.substring(0, raw.length() - 1);
            Level own = levelOf(raw);
            if (own != null) previous = own;
            lines.add(new Line(raw, own != null ? own : previous, own != null));
            if (end == text.length()) break;
            from = end + 1;
        }
        return lines;
    }

    /** The level this line declares, or null when it is a continuation of the one before it. */
    static Level levelOf(String line) {
        String prefix = line.length() > PREFIX_CHARS ? line.substring(0, PREFIX_CHARS) : line;
        Matcher matcher = LEVEL.matcher(prefix);
        if (matcher.find()) {
            String word = matcher.group(1);
            if ("FATAL".equals(word) || "ERROR".equals(word) || "SEVERE".equals(word)) {
                return Level.ERROR;
            }
            if ("WARN".equals(word) || "WARNING".equals(word)) return Level.WARN;
            if ("INFO".equals(word)) return Level.INFO;
            return Level.DEBUG;
        }
        for (String mark : ERROR_MARKS) {
            if (prefix.contains(mark)) return Level.ERROR;
        }
        return null;
    }

    /** Whether any line said what it was; see the viewer for why it asks. */
    public static boolean declaresLevels(List<Line> lines) {
        for (Line line : lines) {
            if (line.own) return true;
        }
        return false;
    }

    /**
     * Which lines to show, as positions in the parsed list.
     *
     * Positions rather than the lines themselves, because a search result has to be able to say
     * where it came from: tapping one clears the search and goes to that line among its
     * neighbours, which is the whole reason to look at a log rather than at a list of matches.
     *
     * Blank lines are dropped whenever anything is filtering, since a run of empty lines between
     * two matches is padding rather than context, and kept otherwise so the log reads as written.
     */
    public static int[] visible(List<Line> lines, String query, Level minimum) {
        boolean searching = query != null && !query.isEmpty();
        boolean filtering = searching || minimum != Level.DEBUG;
        String needle = searching ? query.toLowerCase(java.util.Locale.ROOT) : null;
        int[] kept = new int[lines.size()];
        int count = 0;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (line.level.ordinal() < minimum.ordinal()) continue;
            if (filtering && line.text.trim().isEmpty()) continue;
            if (searching && !line.text.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                continue;
            }
            kept[count++] = i;
        }
        int[] result = new int[count];
        System.arraycopy(kept, 0, result, 0, count);
        return result;
    }
}
