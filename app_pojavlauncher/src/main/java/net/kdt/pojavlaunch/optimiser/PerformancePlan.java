package net.kdt.pojavlaunch.optimiser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What to change on this device, for this profile, to make the game run.
 *
 * <b>A pure function, deliberately.</b> Everything the decision needs arrives in {@link Inputs} as
 * plain numbers and strings, and what comes out is a list of changes rather than a set of writes.
 * That is what lets the plan be shown before it is applied, undone afterwards, and driven by
 * {@code scripts/plansim} without an Android device in the room. A version of this that wrote
 * settings as it worked them out could be none of those three things.
 *
 * <b>The pixel budget is the biggest lever, and it is not a percentage.</b> Flagship panels are
 * far denser than the game needs: 1440 by 3168 is four and a half million pixels, more than twice
 * a 1080p screen, and Minecraft pays for every one of them every frame. So the plan works back
 * from a number of pixels the tier can afford and solves for the scale that lands on it, which is
 * why the same tier gives 70% on one phone and 100% on another. A fixed percentage would either
 * blur a 720p phone or leave a 1440p one rendering twice what it can display.
 *
 * <b>The plan never undoes somebody's own tuning.</b> Every graphics option carries a bound: a
 * player who already turned particles down to minimal does not get them raised to decreased
 * because the tier says so. Only the distances are set outright, and that is the point of them.
 * A "performance mode" that quietly made something slower would be indefensible, and on a
 * flagship, where the tier asks for very little, it would be most of what it did.
 *
 * <b>Nothing here writes JVM flags.</b> They are the one lever where being wrong does not cost
 * frames, it costs a game that will not start at all, and there is no device in CI to find that
 * out on. The launcher already picks its own; see the handbook's limitations.
 */
public final class PerformancePlan {

    // Renderer ids, as they appear in R.array.renderer_values.
    public static final String RENDERER_GL4ES = "opengles2";
    public static final String RENDERER_MOBILEGLUES = "opengles_mobileglues";
    public static final String RENDERER_ZINK_KOPPER = "opengles3_desktopgl_zink_kopper";

    /** What kind of value a change carries, so the applier knows which setter to reach for. */
    public enum Type { BOOL, INT, STRING }

    /**
     * How far a change is allowed to move a setting the player has already moved.
     *
     * The direction that means "faster" is per key and not guessable: {@code graphicsMode} counts
     * up from fast to fabulous, while {@code particles} counts up from all to minimal. Stating it
     * per option is the only way the guard can be applied by arithmetic rather than by a table of
     * special cases somewhere else.
     */
    public enum Bound {
        /** Written whatever is there. Only the distances, which are the plan's own subject. */
        SET,
        /** Written only when the planned value is not above the current one. */
        NEVER_RAISE,
        /** Written only when the planned value is not below the current one. */
        NEVER_LOWER
    }

    /** One setting the plan would change. */
    public static final class Item {
        public final String key;
        /** Exactly what will be written, so the preview and the write cannot disagree. */
        public final String value;
        public final Type type;
        public final Bound bound;
        /** What a player calls this setting. */
        public final int labelRes;
        /**
         * How to read the value: a plain string, or a one argument format taking {@link #value}.
         * Resolved with {@code getString(valueRes, value)}, which is safe for both.
         */
        public final int valueRes;

        Item(String key, String value, Type type, Bound bound, int labelRes, int valueRes) {
            this.key = key;
            this.value = value;
            this.type = type;
            this.bound = bound;
            this.labelRes = labelRes;
            this.valueRes = valueRes;
        }

        /** The value as a number, with a boolean counting as one so the bound is arithmetic. */
        public double asNumber() {
            return numberOf(value);
        }

        public boolean boolValue() {
            return "true".equals(value);
        }

        public int intValue() {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    /**
     * One mod the plan would install.
     *
     * The slug is Modrinth's, and it is what the installer asks for: the API takes a slug
     * wherever it takes a project id. Names are proper nouns and are not translated; the one line
     * saying what each does is, because that is the part somebody reads to decide.
     */
    public static final class Mod {
        public final String slug;
        public final String name;
        public final int summaryRes;

        Mod(String slug, String name, int summaryRes) {
            this.slug = slug;
            this.name = name;
            this.summaryRes = summaryRes;
        }
    }

    /** Why the plan is not offering to install anything. */
    public enum ModsSkipped { NONE, NO_LOADER, NO_VERSION, NO_FOLDER }

    /** Everything the decision needs, and nothing that would tie it to a device. */
    public static final class Inputs {
        public final DeviceProfile.Tier tier;
        public final DeviceProfile.Gpu gpu;
        /** Panel pixels at full scale, which is what the resolution is solved against. */
        public final long panelPixels;
        public final float refreshRate;
        public final int deviceMemoryMb;
        /** The most the launcher will let the game have, which caps the allocation. */
        public final int maxMemoryMb;
        public final int cores;
        /** The vanilla Minecraft version, or null when it could not be worked out. */
        @Nullable public final String mcVersion;
        /** Modrinth's id for the loader, or null on a vanilla profile. */
        @Nullable public final String loaderId;
        /** Whether there is a mods folder to write into. */
        public final boolean hasModsFolder;
        /** The renderers this device can actually run, from Tools.getCompatibleRenderers. */
        public final List<String> availableRenderers;
        /** What the profile is set to now, so an unchanged renderer is not offered as a change. */
        @Nullable public final String currentRenderer;

        public Inputs(DeviceProfile.Tier tier, DeviceProfile.Gpu gpu, long panelPixels,
                      float refreshRate, int deviceMemoryMb, int maxMemoryMb, int cores,
                      @Nullable String mcVersion, @Nullable String loaderId, boolean hasModsFolder,
                      @NonNull List<String> availableRenderers, @Nullable String currentRenderer) {
            this.tier = tier;
            this.gpu = gpu;
            this.panelPixels = panelPixels;
            this.refreshRate = refreshRate;
            this.deviceMemoryMb = deviceMemoryMb;
            this.maxMemoryMb = maxMemoryMb;
            this.cores = cores;
            this.mcVersion = mcVersion;
            this.loaderId = loaderId;
            this.hasModsFolder = hasModsFolder;
            this.availableRenderers = availableRenderers;
            this.currentRenderer = currentRenderer;
        }
    }

    /** The answer. */
    public static final class Plan {
        public final DeviceProfile.Tier tier;
        /** The renderer to switch the profile to, or null to leave it as it is. */
        @Nullable public final String rendererId;
        public final List<Item> preferences;
        /** Changes to Minecraft's own options.txt. Empty when the version is not understood. */
        public final List<Item> options;
        public final List<Mod> mods;
        public final ModsSkipped modsSkipped;
        /** Chunks of render distance, which is the number people ask about first. */
        public final int renderDistance;

        Plan(DeviceProfile.Tier tier, @Nullable String rendererId, List<Item> preferences,
             List<Item> options, List<Mod> mods, ModsSkipped modsSkipped, int renderDistance) {
            this.tier = tier;
            this.rendererId = rendererId;
            this.preferences = Collections.unmodifiableList(preferences);
            this.options = Collections.unmodifiableList(options);
            this.mods = Collections.unmodifiableList(mods);
            this.modsSkipped = modsSkipped;
            this.renderDistance = renderDistance;
        }

        /** Every preference key the plan owns, which is what the backup has to capture. */
        @NonNull
        public String[] preferenceKeys() {
            String[] keys = new String[preferences.size()];
            for (int i = 0; i < preferences.size(); i++) keys[i] = preferences.get(i).key;
            return keys;
        }

        public int changeCount() {
            return preferences.size() + options.size() + (rendererId == null ? 0 : 1);
        }
    }

    private PerformancePlan() {}

    // ------------------------------------------------------------------ the policy

    /**
     * Pixels a tier is asked to draw per frame, before the panel is consulted.
     *
     * Flagship sits a little above 1080p rather than at native, because past that the return on
     * a five inch panel is nothing and the cost is every frame. Low sits near 720p, which is
     * where the game stops being a slideshow on the hardware that lands there.
     */
    private static long pixelBudget(DeviceProfile.Tier tier) {
        switch (tier) {
            case FLAGSHIP: return 2_300_000L;
            case HIGH: return 1_600_000L;
            case LOW: return 800_000L;
            default: return 1_100_000L;
        }
    }

    /**
     * Render distance in chunks.
     *
     * <b>Two columns, because the mods change the answer.</b> Sodium rewrites chunk rendering, so
     * a phone that could manage eight chunks without it manages twelve with it. Handing a
     * flagship the same eight chunks either way would be a performance mode that took the view
     * distance away and gave nothing back, which is exactly the complaint people have about
     * every "optimised" preset they have ever been given.
     */
    private static int renderDistance(DeviceProfile.Tier tier, boolean withMods) {
        switch (tier) {
            case FLAGSHIP: return withMods ? 12 : 8;
            case HIGH: return withMods ? 10 : 7;
            case LOW: return withMods ? 6 : 5;
            default: return withMods ? 8 : 6;
        }
    }

    /** Simulation distance, which is a tick cost rather than a draw cost. Five is the floor. */
    private static int simulationDistance(DeviceProfile.Tier tier) {
        switch (tier) {
            case FLAGSHIP: return 8;
            case HIGH: return 6;
            default: return 5;
        }
    }

    /**
     * The frame cap.
     *
     * <b>The panel is the ceiling.</b> A frame the screen cannot show is heat and battery for
     * nothing, and on a phone that heat comes back as a throttle a few minutes later, so the cap
     * is a smoothness setting as much as a temperature one. The lower tiers are held at 60 even
     * on a 120Hz panel: a device that cannot hold 60 will not hold 120, and asking it to try is
     * how a frame rate becomes uneven rather than merely low.
     */
    static int frameCap(DeviceProfile.Tier tier, float refreshRate) {
        int refresh = Math.round(refreshRate);
        if (refresh < 30 || refresh > 480) refresh = 60;
        int cap;
        if (tier == DeviceProfile.Tier.FLAGSHIP) cap = Math.min(refresh, 144);
        else if (tier == DeviceProfile.Tier.HIGH) cap = Math.min(refresh, 120);
        else cap = Math.min(refresh, 60);
        // Minecraft's own range. Below ten it will not parse; 260 is its "unlimited".
        return Math.max(10, Math.min(260, cap));
    }

    /**
     * The resolution scale, solved against the pixel budget.
     *
     * Snapped to the same five percent step the slider uses, so the number the plan chose is one
     * the player can see and move afterwards. Never below 50: past that the game is not blurry,
     * it is unreadable, and a plan nobody would keep is worth no frames at all.
     */
    static int resolutionPercent(DeviceProfile.Tier tier, long panelPixels) {
        if (panelPixels <= 0) return 100;
        double scale = Math.sqrt((double) pixelBudget(tier) / (double) panelPixels);
        int percent = (int) Math.round(scale * 100.0 / 5.0) * 5;
        return Math.max(50, Math.min(100, percent));
    }

    /**
     * How much heap to give the game.
     *
     * More is not better past the point the game needs: the JVM collects a bigger heap in bigger
     * pauses, and every megabyte here is one Android cannot use for the launcher, the compositor
     * and whatever else is open. These are the numbers a modded 1.20 client actually uses, capped
     * by what the launcher will allow on the device at all.
     */
    static int memoryMb(DeviceProfile.Tier tier, int deviceMemoryMb, int maxMemoryMb) {
        int wanted;
        if (deviceMemoryMb >= 11264) wanted = 3072;
        else if (deviceMemoryMb >= 7168) wanted = 2560;
        else if (deviceMemoryMb >= 5632) wanted = 2048;
        else if (deviceMemoryMb >= 3584) wanted = 1536;
        else if (deviceMemoryMb >= 2560) wanted = 1024;
        else if (deviceMemoryMb >= 1536) wanted = 640;
        else wanted = 400;
        // A weak device does not get a big heap just for having a lot of memory: it is bound by
        // the GPU long before the heap, and the collector pauses would be the only thing it felt.
        if (tier == DeviceProfile.Tier.LOW) wanted = Math.min(wanted, 1536);
        // Never more than half the device. Android does not swap: a heap the JVM is entitled to
        // fill is memory the system cannot take back, and the phone kills the game to get it.
        // Without this the ladder's own bottom rung hands a 1GB phone its entire memory.
        wanted = Math.min(wanted, deviceMemoryMb / 2);
        if (maxMemoryMb > 0) wanted = Math.min(wanted, maxMemoryMb);
        return Math.max(256, wanted);
    }

    /**
     * The renderer, which depends on the Minecraft version far more than on the device.
     *
     * Under 1.17 the game asks for OpenGL 2.1, and GL4ES translating that is both the fastest and
     * the most compatible thing here. From 1.17 the game asks for 3.2 core, which GL4ES cannot
     * serve at all, and MobileGlues is the modern translator for it.
     *
     * <b>{@code vulkan_zink} is never chosen</b>, and that is a design decision rather than a
     * performance one: it renders through OSMesa, which has no EGL surface, so selecting it would
     * silently take away recording (handbook 11) as the price of a plan the player was told was
     * about frame rate. Anything the plan cannot improve, it leaves alone.
     *
     * @return the renderer to move to, or null to keep whatever is set
     */
    @Nullable
    static String renderer(@Nullable String mcVersion, @NonNull List<String> available,
                           @Nullable String current) {
        int minor = minorVersionOf(mcVersion);
        if (minor <= 0) return null;
        String wanted;
        if (minor < 17) {
            wanted = RENDERER_GL4ES;
        } else if (available.contains(RENDERER_MOBILEGLUES)) {
            wanted = RENDERER_MOBILEGLUES;
        } else {
            wanted = RENDERER_ZINK_KOPPER;
        }
        if (!available.contains(wanted)) return null;
        return wanted.equals(current) ? null : wanted;
    }

    /**
     * The minor number of a Minecraft version: "1.20.1" gives 20.
     *
     * A snapshot id ("23w31a") and anything else unrecognised gives 0, which every caller reads
     * as "do not guess". Guessing here is how a 1.12 profile gets {@code graphicsMode} written
     * into it, a key that version has never heard of, in place of the {@code fancyGraphics} it
     * actually reads.
     */
    static int minorVersionOf(@Nullable String mcVersion) {
        if (mcVersion == null) return 0;
        String version = mcVersion.trim();
        if (!version.startsWith("1.")) return 0;
        int start = 2;
        int end = start;
        while (end < version.length() && Character.isDigit(version.charAt(end))) end++;
        if (end == start) return 0;
        try {
            return Integer.parseInt(version.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ the mod sets

    /**
     * The mods, per loader family.
     *
     * <b>Every one of these is a rendering or memory fix that changes no behaviour.</b> That is
     * the line: a performance pack that quietly alters what the game does is a modpack, and
     * somebody who turned on a switch called performance mode did not ask for one. Nothing here
     * adds a block, a recipe, a key binding or a screen.
     */
    private static final Mod[] FABRIC_MODS = {
            new Mod("sodium", "Sodium", R.string.performance_mod_sodium),
            new Mod("lithium", "Lithium", R.string.performance_mod_lithium),
            new Mod("ferrite-core", "FerriteCore", R.string.performance_mod_ferritecore),
            new Mod("immediatelyfast", "ImmediatelyFast", R.string.performance_mod_immediatelyfast),
            new Mod("entityculling", "EntityCulling", R.string.performance_mod_entityculling),
            new Mod("modernfix", "ModernFix", R.string.performance_mod_modernfix)
    };

    private static final Mod[] FORGE_MODS = {
            new Mod("embeddium", "Embeddium", R.string.performance_mod_embeddium),
            new Mod("canary", "Canary", R.string.performance_mod_canary),
            new Mod("ferrite-core", "FerriteCore", R.string.performance_mod_ferritecore),
            new Mod("immediatelyfast", "ImmediatelyFast", R.string.performance_mod_immediatelyfast),
            new Mod("entityculling", "EntityCulling", R.string.performance_mod_entityculling),
            new Mod("modernfix", "ModernFix", R.string.performance_mod_modernfix)
    };

    /**
     * Which set a loader gets.
     *
     * Quilt takes the Fabric set because Quilt runs Fabric mods; the installer asks Modrinth for
     * the Quilt build first and falls back to the Fabric one, which is the same order the Quilt
     * project itself recommends. NeoForge takes the Forge set because Embeddium and Canary both
     * publish for it.
     */
    @NonNull
    static Mod[] modsFor(@Nullable String loaderId) {
        if (loaderId == null) return new Mod[0];
        String loader = loaderId.toLowerCase(Locale.ROOT);
        if (loader.contains("fabric") || loader.contains("quilt")) return FABRIC_MODS;
        if (loader.contains("forge")) return FORGE_MODS;
        return new Mod[0];
    }

    // ------------------------------------------------------------------ building

    @NonNull
    public static Plan build(@NonNull Inputs in) {
        ModsSkipped skipped = ModsSkipped.NONE;
        if (in.loaderId == null) skipped = ModsSkipped.NO_LOADER;
        else if (in.mcVersion == null) skipped = ModsSkipped.NO_VERSION;
        else if (!in.hasModsFolder) skipped = ModsSkipped.NO_FOLDER;

        Mod[] mods = skipped == ModsSkipped.NONE ? modsFor(in.loaderId) : new Mod[0];
        boolean withMods = mods.length > 0;

        List<Item> preferences = new ArrayList<>();
        preferences.add(new Item("resolutionRatio",
                Integer.toString(resolutionPercent(in.tier, in.panelPixels)),
                Type.INT, Bound.SET,
                R.string.mcl_setting_title_resolution_scaler, R.string.performance_value_percent));
        preferences.add(new Item("allocation",
                Integer.toString(memoryMb(in.tier, in.deviceMemoryMb, in.maxMemoryMb)),
                Type.INT, Bound.SET,
                R.string.mcl_memory_allocation, R.string.performance_value_megabytes));
        // SurfaceView hands the frame straight to the compositor; a TextureView copies it through
        // the view hierarchy first, which is a full screen of bandwidth per frame for nothing.
        preferences.add(new Item("alternate_surface", "true", Type.BOOL, Bound.SET,
                R.string.mcl_setting_title_use_surface_view, R.string.performance_value_on));
        // Presenting in step with the panel is what turns a high average frame rate into a steady
        // one, and the frames it declines to draw are the ones the screen could never have shown.
        preferences.add(new Item("force_vsync", "true", Type.BOOL, Bound.SET,
                R.string.preference_force_vsync_title, R.string.performance_value_on));
        // Sustained performance asks Android for a clock it can hold rather than a peak it will
        // throttle away from. It costs the first two minutes and pays for the next thirty, which
        // is the right trade only where there is headroom to give up.
        boolean sustained = in.tier == DeviceProfile.Tier.FLAGSHIP
                || in.tier == DeviceProfile.Tier.HIGH;
        preferences.add(new Item("sustainedPerformance", Boolean.toString(sustained),
                Type.BOOL, Bound.SET, R.string.preference_sustained_performance_title,
                sustained ? R.string.performance_value_on : R.string.performance_value_off));

        String renderer = renderer(in.mcVersion, in.availableRenderers, in.currentRenderer);
        int distance = renderDistance(in.tier, withMods);

        return new Plan(in.tier, renderer, preferences,
                optionsFor(in.tier, in.mcVersion, distance, in.refreshRate, withMods),
                Arrays.asList(mods), skipped, distance);
    }

    /**
     * The changes to Minecraft's own options.
     *
     * <b>Gated on the version, because the value formats moved.</b> Ambient occlusion and clouds
     * have accepted the word "false" since 1.14 and still do, so those are written everywhere.
     * Graphics quality was a boolean called {@code fancyGraphics} until 1.16 and an enum id called
     * {@code graphicsMode} after it. Simulation distance and threaded chunk building arrived in
     * 1.18. Writing the modern spelling into an older file does not fail loudly, it fails
     * silently: the game does not recognise the key, drops it on the next save, and the setting
     * the plan promised simply never happened.
     */
    @NonNull
    static List<Item> optionsFor(DeviceProfile.Tier tier, @Nullable String mcVersion,
                                 int renderDistance, float refreshRate, boolean withMods) {
        List<Item> items = new ArrayList<>();
        int minor = minorVersionOf(mcVersion);

        items.add(new Item("renderDistance", Integer.toString(renderDistance),
                Type.INT, Bound.SET,
                R.string.performance_option_render_distance, R.string.performance_value_chunks));
        items.add(new Item("maxFps", Integer.toString(frameCap(tier, refreshRate)),
                Type.INT, Bound.SET,
                R.string.performance_option_max_fps, R.string.performance_value_fps));

        if (minor >= 18) {
            items.add(new Item("simulationDistance", Integer.toString(simulationDistance(tier)),
                    Type.INT, Bound.SET, R.string.performance_option_simulation_distance,
                    R.string.performance_value_chunks));
            // Zero is "threaded": chunks are built off the render thread, so a new area arrives a
            // frame or two late instead of stopping the frame it arrives on.
            items.add(new Item("prioritizeChunkUpdates", "0", Type.INT, Bound.NEVER_RAISE,
                    R.string.performance_option_chunk_updates,
                    R.string.performance_value_threaded));
        }

        // Fancy graphics is worth its cost once chunk rendering has been rewritten, and is not
        // before. This is the one place the mods change a quality setting rather than a distance.
        boolean fancy = withMods
                && (tier == DeviceProfile.Tier.FLAGSHIP || tier == DeviceProfile.Tier.HIGH);
        if (minor >= 16) {
            items.add(new Item("graphicsMode", fancy ? "1" : "0", Type.INT, Bound.NEVER_RAISE,
                    R.string.performance_option_graphics,
                    fancy ? R.string.performance_value_fancy : R.string.performance_value_fast));
            items.add(new Item("entityDistanceScaling",
                    tier == DeviceProfile.Tier.LOW ? "0.5" : "0.75",
                    Type.STRING, Bound.NEVER_RAISE,
                    R.string.performance_option_entity_distance, R.string.performance_value_plain));
        } else if (minor > 0) {
            items.add(new Item("fancyGraphics", "false", Type.BOOL, Bound.NEVER_RAISE,
                    R.string.performance_option_graphics, R.string.performance_value_fast));
        }

        // Decreased rather than minimal. Minimal hides crit sparks, potion effects and the smoke
        // off a fire, which is feedback the game is played on, and the frames between the two are
        // very few. "Not everything at zero" is the difference between a preset people keep and
        // one they turn off after an evening.
        items.add(new Item("particles", tier == DeviceProfile.Tier.LOW ? "2" : "1",
                Type.INT, Bound.NEVER_LOWER, R.string.performance_option_particles,
                tier == DeviceProfile.Tier.LOW ? R.string.performance_value_minimal
                        : R.string.performance_value_decreased));
        items.add(new Item("entityShadows", "false", Type.BOOL, Bound.NEVER_RAISE,
                R.string.performance_option_entity_shadows, R.string.performance_value_off));
        items.add(new Item("renderClouds", "false", Type.BOOL, Bound.NEVER_RAISE,
                R.string.performance_option_clouds, R.string.performance_value_off));

        // Ambient occlusion and biome blending are both paid at chunk build time, which is what
        // makes walking into new terrain stutter. A flagship running Sodium has the budget for
        // both, so it keeps them.
        boolean spare = withMods && tier == DeviceProfile.Tier.FLAGSHIP;
        if (!spare) {
            items.add(new Item("ao", "false", Type.BOOL, Bound.NEVER_RAISE,
                    R.string.performance_option_ao, R.string.performance_value_off));
        }
        if (minor >= 13 || minor == 0) {
            items.add(new Item("biomeBlendRadius",
                    tier == DeviceProfile.Tier.LOW ? "0" : (spare ? "2" : "1"),
                    Type.INT, Bound.NEVER_RAISE,
                    R.string.performance_option_biome_blend, R.string.performance_value_plain));
        }
        return items;
    }

    /**
     * Whether a planned value may be written over what is there now.
     *
     * The whole guard is one comparison, which is only possible because every option states which
     * way is faster rather than leaving it to be inferred. A current value that will not parse is
     * not a value the player chose, so the plan writes over it.
     */
    public static boolean allows(@NonNull Item item, @Nullable String current) {
        if (item.bound == Bound.SET || current == null) return true;
        double now = numberOf(current.trim());
        if (Double.isNaN(now)) return true;
        double planned = item.asNumber();
        if (Double.isNaN(planned)) return true;
        return item.bound == Bound.NEVER_RAISE ? planned <= now : planned >= now;
    }

    /** A setting's value as a number, counting true as one and false as zero. */
    private static double numberOf(@Nullable String value) {
        if (value == null) return Double.NaN;
        if ("true".equalsIgnoreCase(value)) return 1;
        if ("false".equalsIgnoreCase(value)) return 0;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
