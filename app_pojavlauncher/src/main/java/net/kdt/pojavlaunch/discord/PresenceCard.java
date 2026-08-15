package net.kdt.pojavlaunch.discord;

import java.util.Locale;

/**
 * What the Discord card says, and nothing about how it gets there.
 *
 * <b>Split out because it is the only half that can be checked.</b> Whether the presence actually
 * lands on a profile depends on the Discord app, the Social SDK's native library and a device this
 * project does not have; what the card <i>says</i> is string work, and string work about somebody's
 * public profile is worth being sure of. Plain Java with no Android types, so
 * {@code scripts/presencesim} drives this exact class.
 *
 * <p><b>The account name is never in it.</b> Not truncated, not optional, not behind a switch:
 * absent. A launcher's profile name is usually the Minecraft username, this card is shown to
 * strangers, and the one thing a player cannot undo is a name that has already been seen. The
 * version and the loader are facts about the game rather than about the person.
 *
 * <p><b>Nor is anything the launcher cannot actually see.</b> Desktop mods show the world, the
 * server, the dimension and the coordinates because they run inside the game beside a local Discord
 * socket. This runs outside the JVM and is behind the same wall as the typing preview and
 * {@code LiveTyper}: nothing on the launcher side can read a pixel or a field the game owns. A card
 * that guessed at a world name would be wrong in public.
 */
public final class PresenceCard {

    private PresenceCard() {}

    /**
     * The Discord application this launcher publishes as.
     *
     * <b>Safe to have in the open, unlike almost everything else called an id in this codebase.</b>
     * An application id is a public client identifier: it appears in every OAuth URL and in every
     * presence payload the Discord client receives, and it grants nothing on its own. It is not
     * the client secret, and no secret is needed here at all, because the unauthenticated presence
     * path never calls {@code Connect}.
     *
     * A {@code long} rather than a string so a truncated paste fails to compile rather than
     * failing on somebody's profile: Discord ids are snowflakes and this one is 19 digits.
     */
    public static final long APPLICATION_ID = 1538259396545749012L;

    /**
     * Discord's limit on each activity string.
     *
     * Everything here is far shorter, so this is a guard against a version id from a modpack
     * rather than a bound anybody will meet. It matters because the field is rejected rather than
     * trimmed at the far end, and a rejected field is a card that silently does not appear.
     */
    public static final int FIELD_LIMIT = 128;

    /** The loaders whose name appears in a version id, most specific first. */
    private static final String[][] LOADERS = {
        // Before "forge", because every NeoForge id contains that word too. The same ordering
        // trap, and the same fix, as ModTarget's own loader list.
        {"neoforge", "NeoForge"},
        {"fabric", "Fabric"},
        {"quilt", "Quilt"},
        {"forge", "Forge"},
        {"optifine", "OptiFine"},
    };

    /** What a profile with no loader in its id is running. */
    public static final String VANILLA = "Vanilla";

    /**
     * The upper line: which Minecraft this is.
     *
     * @param versionId    the profile's version id, as the launcher stores it
     * @param inheritsFrom the vanilla version a modded manifest is built on, or null
     */
    public static String details(String versionId, String inheritsFrom) {
        String version = minecraftVersion(versionId, inheritsFrom);
        if (version == null) return truncate("Minecraft");
        return truncate("Minecraft " + version);
    }

    /**
     * The lower line: what it is running, and never who is running it.
     *
     * A loader rather than the renderer, which was the other candidate and is worse: nobody
     * reading a friend's profile has an opinion about gl4es, and a player who does have one is
     * looking at the launch card where it already appears.
     */
    public static String state(String versionId) {
        return truncate(loaderName(versionId));
    }

    /**
     * The loader named in a version id, or {@link #VANILLA}.
     *
     * Matched on the id alone. The profile icon is deliberately not consulted, which is the trap
     * ModTarget documents: a profile with a picture stores the whole PNG as a base64 data URI, and
     * a five letter needle in tens of kilobytes of base64 finds itself sooner or later.
     */
    public static String loaderName(String versionId) {
        if (versionId == null) return VANILLA;
        String haystack = versionId.toLowerCase(Locale.ROOT);
        for (String[] loader : LOADERS) {
            if (haystack.contains(loader[0])) return loader[1];
        }
        return VANILLA;
    }

    /**
     * The vanilla Minecraft version a profile runs, or null when it cannot be worked out.
     *
     * <b>Read by the format that wrote it, not searched for</b>, which is the same rule and the
     * same reason as ModTarget: a loose search inside {@code neoforge-21.1.66} finds "1.66", which
     * is not a Minecraft version and has never existed. Each loader lays its id out a fixed way,
     * so the segment that would hold the version is known before looking, and only a segment that
     * is a version in its entirety counts.
     *
     * NeoForge states its Minecraft version nowhere in the id, so it falls to the manifest's
     * {@code inheritsFrom}, which is the field the game itself uses for exactly this.
     */
    public static String minecraftVersion(String versionId, String inheritsFrom) {
        String fromId = versionInId(versionId);
        if (fromId != null) return fromId;
        if (inheritsFrom != null && isVersion(inheritsFrom.trim())) return inheritsFrom.trim();
        return null;
    }

    private static String versionInId(String versionId) {
        if (versionId == null || versionId.length() == 0) return null;
        String id = versionId.toLowerCase(Locale.ROOT);

        // A plain release, which is every vanilla profile.
        String whole = candidate(versionId);
        if (whole != null) return whole;

        // Fabric and Quilt put the game version last.
        if (id.startsWith("fabric-loader-") || id.startsWith("quilt-loader-")) {
            return candidate(versionId.substring(versionId.lastIndexOf('-') + 1));
        }

        // Every other loader puts the version first, taken at the FIRST hyphen rather than matched
        // against a fixed shape: Forge has written its id at least two ways, "1.20.1-forge-47.2.0"
        // and the historical "1.7.10-Forge10.13.4.1614-1.7.10", and OptiFine writes
        // "1.20.1-OptiFine_HD_U_I6". A pattern tight enough to name one of those silently reports
        // the others as having no version at all. First rather than last is the rule the loader
        // index already documents, and for the same reason: splitting on the last hyphen gives a
        // Minecraft version of "1614".
        //
        // <b>Only when a loader is actually named, which is what makes it a loader rule rather
        // than a hyphen rule.</b> Without that condition it also splits "1.21-pre1" into "1.21"
        // and "1.20.1-rc1" into "1.20.1", putting a version that is not out yet on somebody's
        // public profile. The remainder after the hyphen is a loader in the ids this is for and a
        // version qualifier in the ones it is not, and the two are told apart by asking.
        //
        // NeoForge needs no case of its own here and deliberately does not have one. Its id names
        // only itself, so the first segment of "neoforge-21.1.66" is the word "neoforge", which is
        // not a version, and the manifest fallback takes over. An early return for it would be
        // unreachable, and worse than unreachable: were NeoForge ever to write
        // "1.21.1-neoforge-21.1.66", falling through gets that right and a special case would not.
        int hyphen = versionId.indexOf('-');
        if (hyphen > 0 && !VANILLA.equals(loaderName(versionId))) {
            return candidate(versionId.substring(0, hyphen));
        }
        return null;
    }

    private static String candidate(String value) {
        String trimmed = value == null ? "" : value.trim();
        return isVersion(trimmed) ? trimmed : null;
    }

    /**
     * Whether a string is a plain Minecraft release, in its entirety.
     *
     * Releases only. A snapshot or a pre-release is a version this cannot confidently name, and a
     * card that said the wrong one is worse than a card that says "Minecraft", which is still true.
     */
    private static boolean isVersion(String value) {
        if (value == null || value.length() < 3) return false;
        if (value.charAt(0) != '1' || value.charAt(1) != '.') return false;
        int digits = 0;
        int dots = 1;
        for (int i = 2; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                digits++;
                if (digits > 2) return false;
            } else if (c == '.') {
                if (digits == 0 || ++dots > 2) return false;
                digits = 0;
            } else {
                return false;
            }
        }
        return digits > 0;
    }

    /** Cut to Discord's field limit, because an over-long field is dropped rather than trimmed. */
    public static String truncate(String value) {
        if (value == null) return "";
        return value.length() <= FIELD_LIMIT ? value : value.substring(0, FIELD_LIMIT);
    }
}
