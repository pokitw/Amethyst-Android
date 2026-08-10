// In the package under test: optionsFor, renderer and the arithmetic helpers are the decision,
// and widening them so a harness can see them would be the wrong way round.
package net.kdt.pojavlaunch.optimiser;

import net.kdt.pojavlaunch.optimiser.DeviceProfile.Gpu;
import net.kdt.pojavlaunch.optimiser.DeviceProfile.Tier;
import net.kdt.pojavlaunch.optimiser.PerformancePlan.Bound;
import net.kdt.pojavlaunch.optimiser.PerformancePlan.Inputs;
import net.kdt.pojavlaunch.optimiser.PerformancePlan.Item;
import net.kdt.pojavlaunch.optimiser.PerformancePlan.Plan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Drives the shipped performance plan against real devices and real profiles.
 *
 * The plan is where being wrong is silent. A setting written in the wrong format for the
 * Minecraft version is dropped without an error; a bound the wrong way round raises something the
 * player lowered; a resolution solved against the wrong panel simply does nothing on the phone it
 * matters most on. None of those would show up in a build, and there is no device in CI.
 *
 * The Minecraft value formats checked below are read off the game's own option readers, version
 * by version: "ao" and "renderClouds" have accepted the word false since 1.14, "graphicsMode" is
 * an enum id and only exists from 1.16, "simulationDistance" and "prioritizeChunkUpdates" from
 * 1.18, "maxFps" is bounded 10 to 260 and "entityDistanceScaling" 0.5 to 5.
 */
public class PlanHarness {
    private static int failures = 0;
    private static int checks = 0;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            System.out.println("FAIL: " + message);
            failures++;
        }
    }

    private static final List<String> MODERN_RENDERERS = Arrays.asList(
            "opengles2", "opengles3_desktopgl_zink_kopper", "vulkan_zink",
            "opengles_mobileglues", "opengles3_ltw");

    /** A device, named the way somebody would name it, so a failure says which phone broke. */
    private static Inputs device(Tier tier, long pixels, float hz, int memory, String mcVersion,
                                String loader) {
        return new Inputs(tier, Gpu.ADRENO, pixels, hz, memory, memory / 2, 8, mcVersion, loader,
                loader != null, MODERN_RENDERERS, null);
    }

    private static Item option(Plan plan, String key) {
        for (Item item : plan.options) if (item.key.equals(key)) return item;
        return null;
    }

    private static Item preference(Plan plan, String key) {
        for (Item item : plan.preferences) if (item.key.equals(key)) return item;
        return null;
    }

    private static String valueOf(Plan plan, String key) {
        Item item = option(plan, key);
        return item == null ? null : item.value;
    }

    // ---------------------------------------------------------------- the pixel budget

    private static void resolution() {
        // A OnePlus 12 is 1440 by 3168. Rendering that natively is four and a half million pixels
        // a frame, more than twice a 1080p screen, and it is the single biggest thing wrong with
        // the defaults on a flagship.
        int onePlus12 = PerformancePlan.resolutionPercent(Tier.FLAGSHIP, 1440L * 3168L);
        check(onePlus12 == 70, "OnePlus 12 flagship scale was " + onePlus12 + "%, expected 70%");
        long drawn = Math.round(1440L * 3168L * (onePlus12 / 100.0) * (onePlus12 / 100.0));
        check(drawn > 2_000_000 && drawn < 2_600_000,
                "OnePlus 12 would draw " + drawn + " pixels, which is not near the budget");

        // The same tier on a 1080p panel needs almost no reduction, which is the whole reason the
        // budget is solved rather than fixed. A flat percentage would blur this phone for nothing.
        int fhd = PerformancePlan.resolutionPercent(Tier.FLAGSHIP, 1080L * 2400L);
        check(fhd > onePlus12, "a 1080p flagship scaled to " + fhd
                + "%, which is not above the 1440p one at " + onePlus12 + "%");
        check(fhd >= 90, "a 1080p flagship was cut to " + fhd + "%, which is a panel it can afford");

        // A weak phone with a big screen is the worst case and the one that needs the most cut.
        int lowBig = PerformancePlan.resolutionPercent(Tier.LOW, 1440L * 3168L);
        check(lowBig >= 50 && lowBig <= 45 + 15,
                "a low tier 1440p phone scaled to " + lowBig + "%, expected the floor near 50%");

        // Never past the point of unreadable, and never above native.
        for (Tier tier : Tier.values()) {
            for (long pixels : new long[]{720L * 1280L, 1080L * 2400L, 1440L * 3168L, 2000L * 4000L}) {
                int percent = PerformancePlan.resolutionPercent(tier, pixels);
                check(percent >= 50 && percent <= 100,
                        tier + " at " + pixels + " px gave " + percent + "%, outside 50 to 100");
                check(percent % 5 == 0,
                        tier + " at " + pixels + " px gave " + percent + "%, off the slider's step");
            }
        }
        // A panel that could not be measured must not produce a divide by zero or a blur.
        check(PerformancePlan.resolutionPercent(Tier.MID, 0) == 100,
                "an unknown panel did not fall back to full scale");
    }

    // ---------------------------------------------------------------- the frame cap

    private static void frameCap() {
        check(PerformancePlan.frameCap(Tier.FLAGSHIP, 120f) == 120,
                "a 120Hz flagship was not capped at its panel");
        check(PerformancePlan.frameCap(Tier.FLAGSHIP, 60f) == 60,
                "a 60Hz flagship was allowed past its panel");
        // A device that cannot hold 60 will not hold 120, and asking it to try is how a frame rate
        // becomes uneven rather than merely low.
        check(PerformancePlan.frameCap(Tier.LOW, 120f) == 60,
                "a low tier phone with a 120Hz panel was not held at 60");
        check(PerformancePlan.frameCap(Tier.MID, 90f) == 60,
                "a mid tier phone with a 90Hz panel was not held at 60");
        // Minecraft's own range is 10 to 260; outside it the value will not parse.
        for (Tier tier : Tier.values()) {
            for (float hz : new float[]{0f, 24f, 30f, 60f, 90f, 120f, 144f, 165f, 240f, 1000f}) {
                int cap = PerformancePlan.frameCap(tier, hz);
                check(cap >= 10 && cap <= 260,
                        tier + " at " + hz + "Hz gave maxFps " + cap + ", outside Minecraft's range");
            }
        }
        // A refresh rate the platform could not report must not become a one frame per second cap.
        check(PerformancePlan.frameCap(Tier.HIGH, 0f) == 60,
                "an unreadable refresh rate did not fall back to 60");
    }

    // ---------------------------------------------------------------- memory

    private static void memory() {
        check(PerformancePlan.memoryMb(Tier.FLAGSHIP, 12288, 8192) == 3072,
                "a 12GB flagship did not get 3GB of heap");
        // More device memory must never mean more heap than the game can use: the collector pays
        // for every megabyte and Android needs the rest.
        check(PerformancePlan.memoryMb(Tier.FLAGSHIP, 24576, 12288) == 3072,
                "a 24GB phone was given more heap than a 12GB one");
        check(PerformancePlan.memoryMb(Tier.LOW, 3072, 2048) <= 1536,
                "a 3GB phone was given a heap it cannot spare");
        // The launcher's own ceiling wins, because past it the JVM will not start at all.
        check(PerformancePlan.memoryMb(Tier.FLAGSHIP, 12288, 1024) == 1024,
                "the launcher's memory ceiling was ignored");
        // Android does not swap, so a heap the JVM may fill is memory the system cannot take
        // back. Half the device is the ceiling on every rung of the ladder, including the bottom
        // one, which is where an unguarded table hands a 1GB phone its entire memory.
        for (int device : new int[]{512, 1024, 2048, 3072, 4096, 6144, 8192, 12288, 16384}) {
            int heap = PerformancePlan.memoryMb(Tier.MID, device, device);
            check(heap >= 256 && heap <= device / 2,
                    device + "MB device got a " + heap + "MB heap, which is not a share of it");
        }
    }

    // ---------------------------------------------------------------- the renderer

    private static void renderer() {
        // Under 1.17 the game wants OpenGL 2.1 and GL4ES is both the fastest and most compatible
        // translation of it. From 1.17 it wants 3.2 core, which GL4ES cannot serve at all.
        check("opengles2".equals(PerformancePlan.renderer("1.12.2", MODERN_RENDERERS, null)),
                "1.12.2 was not put on GL4ES");
        check("opengles2".equals(PerformancePlan.renderer("1.16.5", MODERN_RENDERERS, null)),
                "1.16.5 was not put on GL4ES");
        check("opengles_mobileglues".equals(
                PerformancePlan.renderer("1.17.1", MODERN_RENDERERS, null)),
                "1.17.1 was not put on MobileGlues");
        check("opengles_mobileglues".equals(
                PerformancePlan.renderer("1.20.1", MODERN_RENDERERS, null)),
                "1.20.1 was not put on MobileGlues");

        // vulkan_zink renders through OSMesa, which has no EGL surface, so choosing it would take
        // recording away as the price of a frame rate plan. It must never be chosen.
        List<String> noGlues = new ArrayList<>(MODERN_RENDERERS);
        noGlues.remove("opengles_mobileglues");
        String fallback = PerformancePlan.renderer("1.20.1", noGlues, null);
        check("opengles3_desktopgl_zink_kopper".equals(fallback),
                "without MobileGlues, 1.20.1 fell to " + fallback + " rather than Zink Kopper");
        for (String version : new String[]{"1.17", "1.18.2", "1.19.4", "1.20.1", "1.21"}) {
            for (List<String> available : Arrays.asList(MODERN_RENDERERS, noGlues)) {
                String chosen = PerformancePlan.renderer(version, available, null);
                check(!"vulkan_zink".equals(chosen),
                        version + " was put on vulkan_zink, which cannot be recorded");
            }
        }

        // A renderer the device cannot run is not offered, and neither is one already selected.
        check(PerformancePlan.renderer("1.20.1", Collections.<String>emptyList(), null) == null,
                "a renderer was chosen from an empty compatible list");
        check(PerformancePlan.renderer("1.20.1", MODERN_RENDERERS, "opengles_mobileglues") == null,
                "the renderer already in use was offered as a change");
        // A snapshot or an unreadable version must not be guessed at.
        check(PerformancePlan.renderer("23w31a", MODERN_RENDERERS, null) == null,
                "a snapshot id was guessed at");
        check(PerformancePlan.renderer(null, MODERN_RENDERERS, null) == null,
                "an unknown version was guessed at");
    }

    private static void versionParsing() {
        check(PerformancePlan.minorVersionOf("1.20.1") == 20, "1.20.1 did not parse as 20");
        check(PerformancePlan.minorVersionOf("1.7.10") == 7, "1.7.10 did not parse as 7");
        check(PerformancePlan.minorVersionOf("1.21") == 21, "1.21 did not parse as 21");
        check(PerformancePlan.minorVersionOf("1.8") == 8, "1.8 did not parse as 8");
        check(PerformancePlan.minorVersionOf("23w31a") == 0, "a snapshot parsed as a version");
        check(PerformancePlan.minorVersionOf("") == 0, "an empty version parsed");
        check(PerformancePlan.minorVersionOf(null) == 0, "a null version parsed");
        check(PerformancePlan.minorVersionOf("1.x") == 0, "a nonsense version parsed");
    }

    // ---------------------------------------------------------------- version gating

    private static void versionGating() {
        Plan modern = PerformancePlan.build(device(Tier.FLAGSHIP, 1440L * 3168L, 120f, 12288,
                "1.20.1", "fabric"));
        check(valueOf(modern, "graphicsMode") != null, "1.20.1 got no graphicsMode");
        check(valueOf(modern, "fancyGraphics") == null,
                "1.20.1 got fancyGraphics, which it stopped reading in 1.16");
        check(valueOf(modern, "simulationDistance") != null, "1.20.1 got no simulationDistance");
        check(valueOf(modern, "prioritizeChunkUpdates") != null,
                "1.20.1 got no prioritizeChunkUpdates");

        Plan sixteen = PerformancePlan.build(device(Tier.HIGH, 1080L * 2400L, 90f, 8192,
                "1.16.5", "forge"));
        check(valueOf(sixteen, "graphicsMode") != null, "1.16.5 got no graphicsMode");
        check(valueOf(sixteen, "simulationDistance") == null,
                "1.16.5 got simulationDistance, which arrived in 1.18");
        check(valueOf(sixteen, "prioritizeChunkUpdates") == null,
                "1.16.5 got prioritizeChunkUpdates, which arrived in 1.18");
        check(valueOf(sixteen, "entityDistanceScaling") != null,
                "1.16.5 got no entityDistanceScaling, which it does read");

        Plan old = PerformancePlan.build(device(Tier.MID, 1080L * 2400L, 60f, 6144,
                "1.12.2", null));
        check(valueOf(old, "fancyGraphics") != null, "1.12.2 got no fancyGraphics");
        check(valueOf(old, "graphicsMode") == null,
                "1.12.2 got graphicsMode, a key it has never read");
        check(valueOf(old, "entityDistanceScaling") == null,
                "1.12.2 got entityDistanceScaling, which arrived in 1.16");

        // An unknown version gets only the keys whose spelling has never changed.
        Plan unknown = PerformancePlan.build(device(Tier.MID, 1080L * 2400L, 60f, 6144,
                null, null));
        check(valueOf(unknown, "graphicsMode") == null && valueOf(unknown, "fancyGraphics") == null,
                "an unknown version was given a graphics key that might be the wrong one");
        check(valueOf(unknown, "renderDistance") != null,
                "an unknown version got no render distance, which every version has read");
        check(valueOf(unknown, "ao") != null && valueOf(unknown, "renderClouds") != null,
                "an unknown version lost the keys that have never changed spelling");
    }

    // ---------------------------------------------------------------- value formats

    private static void valueFormats() {
        String[] versions = {"1.12.2", "1.14.4", "1.16.5", "1.17.1", "1.18.2", "1.20.1", "1.21", null};
        String[] loaders = {"fabric", "forge", "quilt", "neoforge", null};
        for (String version : versions) {
            for (String loader : loaders) {
                for (Tier tier : Tier.values()) {
                    Plan plan = PerformancePlan.build(device(tier, 1080L * 2400L, 120f, 8192,
                            version, loader));
                    String where = tier + " on " + version + " with " + loader;
                    for (Item item : plan.options) {
                        checkFormat(where, item);
                    }
                    for (Item item : plan.preferences) {
                        check(item.labelRes != 0, where + ": " + item.key + " has no label");
                        check(item.valueRes != 0, where + ": " + item.key + " has no readable value");
                    }
                }
            }
        }
    }

    private static void checkFormat(String where, Item item) {
        check(item.labelRes != 0, where + ": " + item.key + " has no label");
        check(item.valueRes != 0, where + ": " + item.key + " has no readable value");
        if ("ao".equals(item.key) || "renderClouds".equals(item.key)
                || "entityShadows".equals(item.key) || "fancyGraphics".equals(item.key)) {
            // Every one of these is only ever turned off. Writing true would be a performance mode
            // undoing somebody's own tuning, which is the one thing it must never do.
            check("false".equals(item.value),
                    where + ": " + item.key + " was written as " + item.value);
        }
        if ("maxFps".equals(item.key)) {
            int fps = Integer.parseInt(item.value);
            check(fps >= 10 && fps <= 260, where + ": maxFps " + fps + " is outside 10 to 260");
        }
        if ("renderDistance".equals(item.key) || "simulationDistance".equals(item.key)) {
            int chunks = Integer.parseInt(item.value);
            check(chunks >= 2 && chunks <= 32, where + ": " + item.key + " " + chunks
                    + " is outside what the game accepts");
        }
        if ("simulationDistance".equals(item.key)) {
            // Five is the game's own floor; below it the slider will not go and entities stop
            // ticking around the player.
            check(Integer.parseInt(item.value) >= 5,
                    where + ": simulationDistance below the game's floor of 5");
        }
        if ("graphicsMode".equals(item.key)) {
            int mode = Integer.parseInt(item.value);
            check(mode == 0 || mode == 1, where + ": graphicsMode " + mode
                    + " is not fast or fancy (fabulous is never a performance answer)");
        }
        if ("particles".equals(item.key)) {
            int particles = Integer.parseInt(item.value);
            check(particles == 1 || particles == 2,
                    where + ": particles " + particles + " is not decreased or minimal");
        }
        if ("entityDistanceScaling".equals(item.key)) {
            double scale = Double.parseDouble(item.value);
            check(scale >= 0.5 && scale <= 5.0,
                    where + ": entityDistanceScaling " + scale + " is outside the game's range");
        }
        if ("biomeBlendRadius".equals(item.key)) {
            int radius = Integer.parseInt(item.value);
            check(radius >= 0 && radius <= 7,
                    where + ": biomeBlendRadius " + radius + " is outside 0 to 7");
        }
        if ("prioritizeChunkUpdates".equals(item.key)) {
            int mode = Integer.parseInt(item.value);
            check(mode >= 0 && mode <= 2, where + ": prioritizeChunkUpdates " + mode + " is not an id");
        }
        if (item.type == PerformancePlan.Type.BOOL) {
            check("true".equals(item.value) || "false".equals(item.value),
                    where + ": " + item.key + " is a boolean holding " + item.value);
        }
        if (item.type == PerformancePlan.Type.INT) {
            try {
                Integer.parseInt(item.value);
            } catch (NumberFormatException e) {
                check(false, where + ": " + item.key + " is an int holding " + item.value);
            }
        }
    }

    // ---------------------------------------------------------------- the bounds

    private static void bounds() {
        Plan plan = PerformancePlan.build(device(Tier.FLAGSHIP, 1440L * 3168L, 120f, 12288,
                "1.20.1", "fabric"));

        // A player already on Fast is not raised to Fancy by a mode called performance.
        Item graphics = option(plan, "graphicsMode");
        check(graphics != null && "1".equals(graphics.value),
                "a flagship with Sodium did not keep Fancy graphics");
        check(!PerformancePlan.allows(graphics, "0"),
                "the plan would raise graphicsMode from Fast to Fancy");
        check(PerformancePlan.allows(graphics, "2"),
                "the plan would not bring graphicsMode down from Fabulous");

        // Particles count the other way: minimal is the highest id and the fastest.
        Item particles = option(plan, "particles");
        check(particles != null && "1".equals(particles.value),
                "a flagship was not put on decreased particles");
        check(!PerformancePlan.allows(particles, "2"),
                "the plan would raise particles from minimal to decreased");
        check(PerformancePlan.allows(particles, "0"),
                "the plan would not bring particles down from all");

        // Distances are the plan's own subject and are set outright, which is the whole point of
        // giving a flagship twelve chunks rather than leaving it wherever it was.
        Item distance = option(plan, "renderDistance");
        check(distance != null && distance.bound == Bound.SET,
                "render distance is not set outright");
        check(PerformancePlan.allows(distance, "4") && PerformancePlan.allows(distance, "32"),
                "render distance refused to be set");

        // Anything that will not parse was not a value somebody chose.
        check(PerformancePlan.allows(graphics, "banana"),
                "an unparseable current value blocked the plan");
        check(PerformancePlan.allows(graphics, null),
                "a missing current value blocked the plan");

        // Across every plan the harness can build, the guard must never let a value through that
        // makes the game slower than it already is.
        for (Tier tier : Tier.values()) {
            for (String loader : new String[]{"fabric", "forge", null}) {
                Plan any = PerformancePlan.build(device(tier, 1080L * 2400L, 120f, 8192,
                        "1.20.1", loader));
                for (Item item : any.options) {
                    if (item.bound == Bound.SET) continue;
                    double planned = item.asNumber();
                    double slower = item.bound == Bound.NEVER_RAISE ? planned - 1 : planned + 1;
                    check(!PerformancePlan.allows(item, Double.toString(slower)),
                            tier + " " + loader + ": " + item.key
                                    + " would be moved to the slower side of " + slower);
                }
            }
        }
    }

    // ---------------------------------------------------------------- mods and distances

    private static void mods() {
        Plan vanilla = PerformancePlan.build(device(Tier.FLAGSHIP, 1440L * 3168L, 120f, 12288,
                "1.20.1", null));
        check(vanilla.mods.isEmpty(), "a vanilla profile was offered mods it cannot load");
        check(vanilla.modsSkipped == PerformancePlan.ModsSkipped.NO_LOADER,
                "a vanilla profile did not say why there are no mods");

        Plan fabric = PerformancePlan.build(device(Tier.FLAGSHIP, 1440L * 3168L, 120f, 12288,
                "1.20.1", "fabric"));
        check(fabric.mods.size() >= 5, "the Fabric set is only " + fabric.mods.size() + " mods");
        boolean sodium = false;
        for (PerformancePlan.Mod mod : fabric.mods) if ("sodium".equals(mod.slug)) sodium = true;
        check(sodium, "the Fabric set does not include Sodium, which is most of the gain");

        Plan forge = PerformancePlan.build(device(Tier.FLAGSHIP, 1440L * 3168L, 120f, 12288,
                "1.20.1", "forge"));
        boolean embeddium = false;
        for (PerformancePlan.Mod mod : forge.mods) {
            check(!"sodium".equals(mod.slug), "Sodium was offered to a Forge profile");
            if ("embeddium".equals(mod.slug)) embeddium = true;
        }
        check(embeddium, "the Forge set does not include Embeddium");

        // Quilt runs Fabric mods and takes the Fabric set; NeoForge takes the Forge one.
        check(PerformancePlan.modsFor("quilt").length == PerformancePlan.modsFor("fabric").length,
                "Quilt did not get the Fabric set");
        check(PerformancePlan.modsFor("neoforge").length == PerformancePlan.modsFor("forge").length,
                "NeoForge did not get the Forge set");
        check(PerformancePlan.modsFor(null).length == 0, "a null loader was given mods");

        // Every mod has a name and a line saying what it does, or the sheet is a list of slugs.
        for (PerformancePlan.Mod mod : fabric.mods) {
            check(mod.slug != null && !mod.slug.isEmpty(), "a mod has no slug");
            check(mod.name != null && !mod.name.isEmpty(), mod.slug + " has no name");
            check(mod.summaryRes != 0, mod.slug + " has no summary");
        }

        // The mods are what buy the view distance back. A plan that took distance away and gave
        // nothing for it is the preset everybody turns off after one evening.
        check(fabric.renderDistance > vanilla.renderDistance,
                "installing Sodium did not buy any render distance back: " + fabric.renderDistance
                        + " against " + vanilla.renderDistance);
        for (Tier tier : Tier.values()) {
            Plan bare = PerformancePlan.build(device(tier, 1080L * 2400L, 120f, 8192, "1.20.1", null));
            Plan modded = PerformancePlan.build(device(tier, 1080L * 2400L, 120f, 8192, "1.20.1",
                    "fabric"));
            check(modded.renderDistance > bare.renderDistance,
                    tier + " gained no render distance from the mods");
            check(bare.renderDistance >= 5,
                    tier + " was cut to " + bare.renderDistance + " chunks, which is not playable");
        }
    }

    // ---------------------------------------------------------------- the whole plan

    private static void wholePlan() {
        // The device this was designed against, stated as its real hardware.
        Inputs onePlus12 = new Inputs(Tier.FLAGSHIP, Gpu.ADRENO, 1440L * 3168L, 120f, 12288, 8192,
                8, "1.20.1", "fabric", true, MODERN_RENDERERS, "opengles2");
        Plan plan = PerformancePlan.build(onePlus12);

        check("70".equals(preference(plan, "resolutionRatio").value),
                "OnePlus 12 resolution was " + preference(plan, "resolutionRatio").value);
        check("3072".equals(preference(plan, "allocation").value),
                "OnePlus 12 heap was " + preference(plan, "allocation").value);
        check("true".equals(preference(plan, "alternate_surface").value),
                "OnePlus 12 was left on a TextureView");
        check("true".equals(preference(plan, "sustainedPerformance").value),
                "a flagship did not get sustained performance, which is the throttling answer");
        check("opengles_mobileglues".equals(plan.rendererId),
                "OnePlus 12 on 1.20.1 was left on " + plan.rendererId);
        check("120".equals(valueOf(plan, "maxFps")), "OnePlus 12 frame cap was "
                + valueOf(plan, "maxFps") + ", not its 120Hz panel");
        check("12".equals(valueOf(plan, "renderDistance")),
                "OnePlus 12 with Sodium got " + valueOf(plan, "renderDistance") + " chunks");
        check(plan.changeCount() >= 12,
                "the whole plan is only " + plan.changeCount() + " changes");

        // A low tier phone gets the opposite of sustained performance: it has no headroom to give
        // up, so holding a clock it can sustain would only cost it frames.
        Plan weak = PerformancePlan.build(device(Tier.LOW, 720L * 1600L, 60f, 3072, "1.20.1", null));
        check("false".equals(preference(weak, "sustainedPerformance").value),
                "a low tier phone was put on sustained performance");

        // Every preference key must be one the launcher actually reads, or the plan writes into
        // nothing and reports that it worked.
        List<String> known = Arrays.asList("resolutionRatio", "allocation", "alternate_surface",
                "force_vsync", "sustainedPerformance", "bigCoreAffinity", "vsync_in_zink");
        for (Tier tier : Tier.values()) {
            Plan any = PerformancePlan.build(device(tier, 1080L * 2400L, 120f, 8192, "1.20.1",
                    "fabric"));
            for (Item item : any.preferences) {
                check(known.contains(item.key),
                        item.key + " is not a preference the launcher reads");
            }
            // The backup has to be able to name every key it will have to put back.
            check(any.preferenceKeys().length == any.preferences.size(),
                    tier + ": the backup key list does not match the changes");
        }
    }

    public static void main(String[] args) {
        resolution();
        frameCap();
        memory();
        renderer();
        versionParsing();
        versionGating();
        valueFormats();
        bounds();
        mods();
        wholePlan();

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
        System.out.println("performance plan OK");
    }
}
