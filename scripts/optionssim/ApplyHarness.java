// In the package under test, like the merge harness beside it.
package net.kdt.pojavlaunch.optimiser;

import net.kdt.pojavlaunch.optimiser.DeviceProfile.Gpu;
import net.kdt.pojavlaunch.optimiser.DeviceProfile.Tier;
import net.kdt.pojavlaunch.optimiser.PerformancePlan.Inputs;
import net.kdt.pojavlaunch.optimiser.PerformancePlan.Item;
import net.kdt.pojavlaunch.optimiser.PerformancePlan.Plan;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;


/**
 * Drives performance mode's apply and undo against a real options.txt on disk.
 *
 * <b>This is the file the launcher does not own.</b> Minecraft writes it, players hand-edit it,
 * and it carries hundreds of keys this launcher has never heard of. The merge is checked next
 * door; what is checked here is the layer above it: that the plan writes what it said it would,
 * declines to write what it said it would not, and that turning the mode off leaves the file byte
 * for byte as it was found.
 *
 * The fixture is deliberately awkward, because a real one is: a hand-added duplicate key, a
 * comment, blank lines, a resource pack list with commas and quotes in it, and quality settings
 * the player had already turned down further than the plan would.
 */
public class ApplyHarness {
    private static int failures = 0;
    private static int checks = 0;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            System.out.println("FAIL: " + message);
            failures++;
        }
    }

    private static final String FIXTURE = String.join("\n",
            "version:3465",
            "# a comment somebody added by hand",
            "renderDistance:16",
            "",
            "particles:2",
            "graphicsMode:0",
            "entityShadows:true",
            "ao:true",
            "biomeBlendRadius:5",
            "maxFps:260",
            "key_key.forward:key.keyboard.w",
            "key_key.attack:key.mouse.left",
            "soundCategory_master:0.7",
            "resourcePacks:[\"vanilla\",\"file/Faithful 32x.zip\"]",
            "lang:en_gb",
            "simulationDistance:12",
            "renderDistance:10",
            "") + "\n";

    private static void write(File file, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(Charset.forName("UTF-8")));
        }
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), Charset.forName("UTF-8"));
    }

    private static Plan flagshipPlan() {
        return PerformancePlan.build(new Inputs(Tier.FLAGSHIP, Gpu.ADRENO, 1440L * 3168L, 120f,
                12288, 8192, 8, "1.20.1", "fabric", true,
                Arrays.asList("opengles2", "opengles_mobileglues"), null));
    }

    /** A key's value in a written file, read the way Minecraft reads it: last occurrence wins. */
    private static String valueIn(String file, String key) {
        String found = null;
        for (String line : file.split("\n", -1)) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).equals(key)) {
                found = line.substring(colon + 1);
            }
        }
        return found;
    }

    public static void main(String[] args) throws Exception {
        File root = new File(args[0], "apply");
        //noinspection ResultOfMethodCallIgnored
        root.mkdirs();

        roundTrip(root);
        bounds(root);
        neverLaunched(root);
        appliedTwice(root);

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
        System.out.println("performance apply OK");
    }

    /** Apply, then turn it off, and the file has to come back exactly as it was found. */
    private static void roundTrip(File root) throws Exception {
        File dir = new File(root, "roundtrip");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File options = GameOptions.fileIn(dir);
        write(options, FIXTURE);

        Plan plan = flagshipPlan();
        PerformanceMode.Report report = new PerformanceMode.Report();
        PerformanceMode.applyOptions(dir, plan, report);
        check(!report.optionsFailed, "applying to a normal options.txt failed");
        check(report.optionsChanged > 0, "applying changed nothing at all");

        String after = read(options);
        // Everything the launcher does not own comes out the other side untouched, including the
        // comment, the keybinds and a resource pack list full of commas and quotes.
        for (String line : new String[]{
                "version:3465",
                "# a comment somebody added by hand",
                "key_key.forward:key.keyboard.w",
                "key_key.attack:key.mouse.left",
                "soundCategory_master:0.7",
                "resourcePacks:[\"vanilla\",\"file/Faithful 32x.zip\"]",
                "lang:en_gb"}) {
            check(after.contains(line + "\n"), "the round trip lost or changed: " + line);
        }
        check(after.contains("\n\n"), "the round trip lost the blank line");

        // A duplicate key is rewritten where Minecraft would read it, which is the last one.
        check("12".equals(valueIn(after, "renderDistance")),
                "renderDistance ended as " + valueIn(after, "renderDistance") + ", expected 12");
        check(after.indexOf("renderDistance:16") >= 0,
                "the earlier duplicate of renderDistance was rewritten rather than left alone");

        boolean restored = PerformanceMode.restoreOptions(dir);
        check(restored, "there was nothing to restore after an apply");
        check(FIXTURE.equals(read(options)),
                "turning it off did not give the file back exactly:\n" + read(options));
        check(!GameOptions.hasBackup(dir), "the backup was left behind after a restore");
    }

    /**
     * The plan must never move a setting to the slower side of where the player left it.
     *
     * The fixture is somebody who has already tuned: particles at minimal, graphics on fast,
     * biome blend at 5. A flagship plan asks for decreased particles and fancy graphics, and both
     * of those would be a performance mode making the game slower, so both must be declined.
     */
    private static void bounds(File root) throws Exception {
        File dir = new File(root, "bounds");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        write(GameOptions.fileIn(dir), FIXTURE);

        Plan plan = flagshipPlan();
        PerformanceMode.applyOptions(dir, plan, new PerformanceMode.Report());
        String after = read(GameOptions.fileIn(dir));

        check("2".equals(valueIn(after, "particles")),
                "particles was raised from minimal to " + valueIn(after, "particles"));
        check("0".equals(valueIn(after, "graphicsMode")),
                "graphics was raised from fast to " + valueIn(after, "graphicsMode"));
        // Biome blend at 5 is above what a flagship plan asks for, so this one does come down.
        check("2".equals(valueIn(after, "biomeBlendRadius")),
                "biomeBlendRadius stayed at " + valueIn(after, "biomeBlendRadius"));
        // The distances are the plan's own subject and are set outright, in both directions.
        check("12".equals(valueIn(after, "renderDistance")), "render distance was not set");
        check("8".equals(valueIn(after, "simulationDistance")),
                "simulation distance was not brought down from 12");

        // And the same check the other way round: every declined item must be exactly as it was.
        for (Item item : plan.options) {
            String before = valueIn(FIXTURE, item.key);
            if (before == null) continue;
            if (!PerformancePlan.allows(item, before)) {
                check(before.equals(valueIn(after, item.key)),
                        item.key + " was written despite its bound: " + before + " became "
                                + valueIn(after, item.key));
            }
        }
    }

    /** A profile that has never been launched has no options.txt, and that is not an error. */
    private static void neverLaunched(File root) throws Exception {
        File dir = new File(root, "fresh");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        PerformanceMode.Report report = new PerformanceMode.Report();
        PerformanceMode.applyOptions(dir, flagshipPlan(), report);
        check(report.optionsAbsent, "a profile with no options.txt was not reported as such");
        check(!report.optionsFailed, "a profile with no options.txt was treated as a failure");
        check(GameOptions.fileIn(dir).isFile(), "no options.txt was written for a fresh profile");
        String written = read(GameOptions.fileIn(dir));
        check("12".equals(valueIn(written, "renderDistance")),
                "the fresh file did not get the plan's render distance");
        // Nothing was taken away, because there was nothing there: Minecraft fills in every key it
        // does not find with its own default on first read.
        check(written.split("\n", -1).length <= flagshipPlan().options.size() + 2,
                "the fresh file gained keys the plan never asked for");
        // There was no file to back up, so turning the mode off has nothing to put back and must
        // say so rather than deleting the file it just wrote.
        check(!PerformanceMode.restoreOptions(dir),
                "a restore claimed to have put back a file that never existed");
        check(GameOptions.fileIn(dir).isFile(),
                "the restore deleted the options file the apply had written");
    }

    /**
     * A second apply must not overwrite the backup.
     *
     * This is the one that loses somebody's settings forever: back up the already-modified file
     * and "off" gives them performance mode's own values, presented as their own.
     */
    private static void appliedTwice(File root) throws Exception {
        File dir = new File(root, "twice");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        write(GameOptions.fileIn(dir), FIXTURE);

        PerformanceMode.applyOptions(dir, flagshipPlan(), new PerformanceMode.Report());
        Plan low = PerformancePlan.build(new Inputs(Tier.LOW, Gpu.MALI, 720L * 1600L, 60f,
                3072, 1536, 8, "1.20.1", null, false,
                Arrays.<String>asList("opengles2"), null));
        PerformanceMode.applyOptions(dir, low, new PerformanceMode.Report());

        check(FIXTURE.equals(read(GameOptions.backupIn(dir))),
                "the second apply overwrote the backup with an already modified file");
        PerformanceMode.restoreOptions(dir);
        check(FIXTURE.equals(read(GameOptions.fileIn(dir))),
                "after two applies, turning it off did not give the original back");
    }
}
