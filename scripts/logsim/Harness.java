package net.kdt.pojavlaunch.logs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Drive the shipped {@link LogParser}.
 *
 * The class under test is the real one, compiled from the app's own source: nothing here
 * reimplements the algorithm, which is the mistake this harness replaced. Breaking any rule in
 * LogParser has to fail a check here, and the mutations that proved it are listed in run.sh.
 */
public class Harness {

    private static int failures = 0;
    private static int checks = 0;

    public static void main(String[] args) throws Exception {
        File security = new File(args[0]);
        formatGroundTruth(security);
        session();
        rawOutput();
        messageIsNotALevel();
        filtering();
        edges();

        if (failures > 0) {
            System.out.println(failures + " of " + checks + " checks failed");
            System.exit(1);
        }
        System.out.println("ok: " + checks + " checks against the shipped LogParser");
    }

    /**
     * The format is not remembered, it is read.
     *
     * This launcher hands the game {@code -Dlog4j.configurationFile=} pointing at the configs in
     * assets, so those files decide what a log line looks like. Each {@code %level} layout is
     * rendered the way log4j would render it and fed back through the parser, which means a
     * pattern that changes in assets and not in the parser fails here rather than in somebody's
     * error filter. (Handbook 16.20: the fixtures come from what the source sends.)
     */
    private static void formatGroundTruth(File security) throws IOException {
        File[] configs = security.listFiles();
        if (configs == null || configs.length == 0) {
            throw new IOException("no log4j configs at " + security + "; ground truth has moved");
        }
        Pattern layoutPattern = Pattern.compile("PatternLayout pattern=\"([^\"]+)\"");
        Arrays.sort(configs);
        int seen = 0;
        for (File config : configs) {
            if (!config.getName().endsWith(".xml")) continue;
            String text = new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8);
            Matcher matcher = layoutPattern.matcher(text);
            while (matcher.find()) {
                String layout = matcher.group(1);
                if (!layout.contains("%level")) continue;
                seen++;
                for (String level : new String[]{"INFO", "WARN", "ERROR"}) {
                    String rendered = layout
                            .replace("%d{HH:mm:ss}", "21:07:44")
                            .replace("%t", "Render thread")
                            .replace("%level", level)
                            .replace("%msg{nolookups}", "Setting user: Dev")
                            .replace("%msg", "Setting user: Dev")
                            .replace("%n", "");
                    List<LogParser.Line> lines = LogParser.parse(rendered);
                    check(config.getName() + " " + level,
                            lines.get(0).level, LogParser.Level.valueOf(level));
                    check(config.getName() + " declares", lines.get(0).own, true);
                }
            }
        }
        if (seen == 0) throw new IOException("no %level layouts found; ground truth has moved");
    }

    /** A session as the game writes one, including the crash in the middle of it. */
    private static final String[] SESSION = {
            "[21:07:41] [main/INFO]: Loading Minecraft 1.20.1 with Fabric Loader 0.15.11",
            "[21:07:43] [Render thread/WARN]: Unable to resolve texture reference: minecraft:x",
            "[21:07:44] [Render thread/INFO]: Setting user: Dev",
            "[21:07:52] [Render thread/ERROR]: Reported exception thrown!",
            "net.minecraft.class_148: Rendering overlay",
            "\tat net.minecraft.class_310.method_1523(class_310.java:1119)",
            "Caused by: java.lang.NullPointerException",
            "",
            "[21:07:53] [Render thread/INFO]: Stopping!",
            "[21:07:53] [Server thread/INFO] [FML]: Unloading dimension 0",
            "[21:07:54] [Worker-Main-3/DEBUG]: Cache hit",
    };

    private static void session() {
        List<LogParser.Line> lines = LogParser.parse(join(SESSION));
        check("line count", lines.size(), SESSION.length);
        LogParser.Level[] want = {
                LogParser.Level.INFO, LogParser.Level.WARN, LogParser.Level.INFO,
                LogParser.Level.ERROR,
                // The trace, its cause and the blank line between belong to the error above.
                LogParser.Level.ERROR, LogParser.Level.ERROR, LogParser.Level.ERROR,
                LogParser.Level.ERROR,
                LogParser.Level.INFO, LogParser.Level.INFO, LogParser.Level.DEBUG,
        };
        for (int i = 0; i < want.length; i++) {
            check("level of line " + i, lines.get(i).level, want[i]);
        }
        for (int i = 4; i <= 7; i++) {
            check("line " + i + " declares nothing", lines.get(i).own, false);
        }
        // The older Forge shape puts [FML] after the level; the first token found has to win.
        check("FML line declares", lines.get(9).own, true);
        check("levels are declared somewhere", LogParser.declaresLevels(lines), true);
    }

    /** Output that never went through the game's logger, which is where the marks earn a place. */
    private static void rawOutput() {
        String[] raw = {
                "OpenJDK 64-Bit Server VM warning: Options -Xverify:none are deprecated",
                "Exception in thread \"main\" java.lang.UnsatisfiedLinkError: no lwjgl in path",
                "\tat java.base/java.lang.ClassLoader.loadLibrary(ClassLoader.java:2402)",
                "Fatal signal 11 (SIGSEGV), code 1, fault addr 0x0 in tid 12345",
        };
        List<LogParser.Line> lines = LogParser.parse(join(raw));
        check("jvm warning inherits", lines.get(0).level, LogParser.Level.INFO);
        check("jvm warning declares nothing", lines.get(0).own, false);
        check("exception in thread is an error", lines.get(1).level, LogParser.Level.ERROR);
        check("exception in thread declares", lines.get(1).own, true);
        check("its trace inherits the error", lines.get(2).level, LogParser.Level.ERROR);
        check("its trace declares nothing", lines.get(2).own, false);
        check("fatal signal is an error", lines.get(3).level, LogParser.Level.ERROR);

        // A log with no levels at all is what the screen asks about before offering a filter.
        List<LogParser.Line> plain = LogParser.parse("starting\nworking\ndone\n");
        check("a level-less log says so", LogParser.declaresLevels(plain), false);
    }

    /** The level belongs to the prefix a logger wrote, never to the message a mod printed. */
    private static void messageIsNotALevel() {
        StringBuilder padding = new StringBuilder();
        for (int i = 0; i < 300; i++) padding.append('x');
        String[] messages = {
                "[21:08:01] [Render thread/INFO]: [CHAT] <dev> the ERROR was mine",
                "[21:08:02] [Render thread/INFO]: Reading /INFO] from the config",
                // A mod's own System.out dump: no prefix of its own, so it inherits, and it
                // carries a bracketed level well past the bound. This is the only shape that can
                // hold PREFIX_CHARS in place, because a line that has a proper prefix is immune
                // either way: the first match wins, and its own level is the first match.
                // Constructed rather than captured, and here to pin the bound, not the format.
                "  dumped buffer: " + padding + " [ERROR] from an hour ago",
                "[21:08:04] [pool-2-thread-1/SEVERE]: java.util.logging says so",
        };
        List<LogParser.Line> lines = LogParser.parse(join(messages));
        check("chat quoting ERROR stays INFO", lines.get(0).level, LogParser.Level.INFO);
        check("a quoted marker stays INFO", lines.get(1).level, LogParser.Level.INFO);
        check("a level past the prefix is ignored", lines.get(2).own, false);
        check("and inherits instead", lines.get(2).level, LogParser.Level.INFO);
        check("SEVERE is an error", lines.get(3).level, LogParser.Level.ERROR);
    }

    /** What each setting of the filter actually shows. */
    private static void filtering() {
        List<LogParser.Line> lines = LogParser.parse(join(SESSION));
        check("errors keep their trace",
                LogParser.visible(lines, "", LogParser.Level.ERROR), new int[]{3, 4, 5, 6});
        check("warnings and worse",
                LogParser.visible(lines, "", LogParser.Level.WARN), new int[]{1, 3, 4, 5, 6});
        check("all keeps the blank line",
                LogParser.visible(lines, "", LogParser.Level.DEBUG).length, SESSION.length);
        // Line 0 says "Loading Minecraft", lines 1, 4 and 5 say it in lower case: the point is
        // that a query matches regardless of the case either side wrote it in.
        check("search is case insensitive",
                LogParser.visible(lines, "minecraft", LogParser.Level.DEBUG),
                new int[]{0, 1, 4, 5});
        check("search and filter compose",
                LogParser.visible(lines, "minecraft", LogParser.Level.ERROR), new int[]{4, 5});
        check("a search that finds nothing",
                LogParser.visible(lines, "zzzz", LogParser.Level.DEBUG).length, 0);
        check("blank lines go while filtering",
                contains(LogParser.visible(lines, "", LogParser.Level.WARN), 7), false);
    }

    /** The shapes that make a parser throw rather than answer. */
    private static void edges() {
        check("null text", LogParser.parse(null).size(), 0);
        check("empty text", LogParser.parse("").size(), 1);
        check("one unterminated line", LogParser.parse("hello").size(), 1);
        // A trailing newline ends the last line; it does not begin another one with content.
        check("trailing newline", LogParser.parse("a\nb\n").size(), 3);
        check("windows endings are trimmed", LogParser.parse("a\r\nb").get(0).text, "a");
        List<LogParser.Line> lines = LogParser.parse("a\nb");
        check("null query is not a search",
                LogParser.visible(lines, null, LogParser.Level.DEBUG).length, 2);
    }

    private static String join(String[] lines) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) builder.append('\n');
            builder.append(lines[i]);
        }
        return builder.toString();
    }

    private static boolean contains(int[] values, int wanted) {
        for (int value : values) {
            if (value == wanted) return true;
        }
        return false;
    }

    private static void check(String name, Object got, Object want) {
        checks++;
        boolean same = got instanceof int[] && want instanceof int[]
                ? Arrays.equals((int[]) got, (int[]) want)
                : (got == null ? want == null : got.equals(want));
        if (!same) {
            failures++;
            System.out.println("FAIL: " + name + ": got " + show(got) + ", wanted " + show(want));
        }
    }

    private static String show(Object value) {
        if (value instanceof int[]) return Arrays.toString((int[]) value);
        return String.valueOf(value);
    }
}
