package net.kdt.pojavlaunch.optimiser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Drives the shipped performance-mode backup through a capture and restore round trip.
 *
 * The thing worth proving is the one an obvious implementation gets wrong: a setting that was
 * never written is not a setting holding its default, and restoring the first has to REMOVE the
 * key so the launcher goes back to working it out. Getting that backwards freezes a computed
 * value forever and the player never finds out, because the number that appears is the one that
 * would have appeared anyway on the device they happened to be holding.
 */
public class Harness {
    private static int failures = 0;

    private static void check(boolean condition, String message) {
        if (!condition) {
            System.out.println("FAIL: " + message);
            failures++;
        }
    }

    private static final String[] KEYS = {
            "resolutionRatio", "allocation", "force_vsync", "sustainedPerformance", "javaArgs"
    };

    public static void main(String[] args) {
        // A device where some of the keys have been written and some never have.
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("resolutionRatio", 100);
        current.put("force_vsync", false);
        current.put("javaArgs", "-Dfoo=bar");
        // "allocation" and "sustainedPerformance" were never set.

        String captured = PerformanceBackup.capture(current, KEYS);
        check(captured != null, "capture should produce something");
        check(PerformanceBackup.isUsable(captured), "a capture with keys should be usable");

        Map<String, Object> restored = PerformanceBackup.restore(captured);
        check(restored.size() == KEYS.length,
                "every owned key should be recorded, got " + restored.size());

        // Values come back with their types intact.
        check(Integer.valueOf(100).equals(restored.get("resolutionRatio")),
                "an int came back as " + restored.get("resolutionRatio"));
        check(Boolean.FALSE.equals(restored.get("force_vsync")),
                "a false boolean came back as " + restored.get("force_vsync"));
        check("-Dfoo=bar".equals(restored.get("javaArgs")),
                "a string came back as " + restored.get("javaArgs"));

        // The distinction that matters: never-set keys are PRESENT and null.
        check(restored.containsKey("allocation"), "a never-set key must still be recorded");
        check(restored.get("allocation") == null,
                "a never-set key must restore as null so the caller removes it");
        check(restored.containsKey("sustainedPerformance"), "a never-set boolean must be recorded");
        check(restored.get("sustainedPerformance") == null,
                "a never-set boolean must restore as null, not as false");

        // A false boolean and an unset boolean must not collapse into each other, which is the
        // single most likely way to get this wrong.
        check(restored.get("force_vsync") != null && restored.get("sustainedPerformance") == null,
                "a written false and an unwritten key must stay distinguishable");

        // Nothing to restore, said clearly.
        check(PerformanceBackup.restore(null).isEmpty(), "a null capture restores nothing");
        check(PerformanceBackup.restore("").isEmpty(), "an empty capture restores nothing");
        check(PerformanceBackup.restore("not json").isEmpty(), "a broken capture restores nothing");
        check(!PerformanceBackup.isUsable(null), "null is not usable");
        check(!PerformanceBackup.isUsable("{}"), "an empty object is not usable");

        // A capture of a device where NOTHING had been written is still usable, because turning
        // the mode off then has real work to do: it removes every key it added.
        String virgin = PerformanceBackup.capture(new LinkedHashMap<String, Object>(), KEYS);
        check(PerformanceBackup.isUsable(virgin), "an all-null capture is still a real capture");
        Map<String, Object> back = PerformanceBackup.restore(virgin);
        check(back.size() == KEYS.length, "an all-null capture should record every key");
        for (String key : KEYS) {
            check(back.containsKey(key) && back.get(key) == null,
                    key + " should restore as null on a device that had nothing set");
        }

        System.out.println("checked capture, types, the unset-versus-false distinction and "
                + "unreadable captures");
        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("performance backup OK");
    }
}
