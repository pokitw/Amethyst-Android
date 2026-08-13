package net.kdt.pojavlaunch.testlaunch;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minecraft's own settings for the control test world, at the floor of everything that costs.
 *
 * A table of its own rather than a block of {@code set} calls, so it can be read as a list of
 * decisions and driven by {@code scripts/testworldsim} without a device. Every value here is the
 * cheapest end of its own option, which is a thing performance mode is explicitly not allowed to
 * do: that one is tuning a game somebody plays and must never undo their choices, this is a
 * scratch directory whose whole purpose is to be light.
 *
 * <p>Written for 1.20.1, which is the only version this profile ever runs, so the keys that moved
 * between versions can be spelled the modern way with no version gating at all. That is the one
 * simplification a fixed version buys.
 */
public final class TestOptions {

    private TestOptions() {}

    public static Map<String, String> values() {
        Map<String, String> o = new LinkedHashMap<>();

        // The two that dominate everything else. Two chunks is Minecraft's own minimum, and on a
        // superflat with no structures there is nothing past it worth drawing anyway.
        o.put("renderDistance", "2");
        o.put("simulationDistance", "5");

        // 60 rather than unlimited: a test session should not run the battery down finding out
        // how many frames a bare superflat can do.
        o.put("maxFps", "60");
        o.put("enableVsync", "true");

        // graphicsMode counts up from fast, particles counts up from all. Both go to the cheap
        // end, which is a different direction for each, which is exactly why performance mode
        // states the direction per key rather than inferring it.
        o.put("graphicsMode", "0");
        o.put("particles", "2");
        o.put("ao", "false");
        o.put("renderClouds", "\"false\"");
        o.put("entityShadows", "false");
        o.put("entityDistanceScaling", "0.5");
        o.put("mipmapLevels", "0");
        o.put("biomeBlendRadius", "0");
        o.put("prioritizeChunkUpdates", "0");
        o.put("bobView", "false");
        o.put("screenEffectScale", "0.0");
        o.put("fovEffectScale", "0.0");
        o.put("darknessEffectScale", "0.0");
        o.put("glintSpeed", "0.0");
        o.put("glintStrength", "0.0");

        // Sound is a thread and a decoder for no benefit here. The controls make no noise.
        o.put("soundCategory_master", "0.0");

        // Skipped on purpose: the game asks once per fresh profile and the answer is remembered.
        o.put("skipMultiplayerWarning", "true");
        o.put("onboardAccessibility", "false");
        o.put("tutorialStep", "none");

        // Big enough to read while holding the phone at arm's length, and it is the size the
        // hotbar guide in the editor is drawn at, so the two agree.
        o.put("guiScale", "0");

        // Autosave is a stall, and there is nothing in here worth saving.
        o.put("pauseOnLostFocus", "false");

        return o;
    }
}
