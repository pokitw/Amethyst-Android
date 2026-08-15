package net.kdt.pojavlaunch.discord;

import java.util.ArrayList;
import java.util.List;

/**
 * Drive the shipped {@link PresenceCard}.
 *
 * This is a public profile. Everything asserted here is something a stranger would read, and the
 * two failures that matter are both silent: a field that says the wrong version, and a field that
 * says something about the player that they never agreed to publish.
 */
public class Harness {

    private static int sChecks = 0;
    private static final List<String> FAILURES = new ArrayList<>();

    public static void main(String[] args) {
        versionsAreReadByFormat();
        neoForgeFallsBackToTheManifest();
        loadersAreOrderedMostSpecificFirst();
        nothingIsInventedFromNothing();
        nothingIdentifiesThePlayer();
        fieldsFitDiscordsLimits();
        theApplicationIdIsPlausible();

        System.out.println(sChecks + " checks");
        if (!FAILURES.isEmpty()) {
            for (String failure : FAILURES) System.out.println("FAIL: " + failure);
            System.out.println(FAILURES.size() + " FAILURES");
            System.exit(1);
        }
        System.out.println("all passed");
    }

    /** The four id layouts, read by the format that wrote each one. */
    private static void versionsAreReadByFormat() {
        version("1.20.1", null, "1.20.1");
        version("1.21", null, "1.21");
        version("1.20.1-forge-47.2.0", null, "1.20.1");
        version("fabric-loader-0.15.7-1.20.1", null, "1.20.1");
        version("quilt-loader-0.23.1-1.21", null, "1.21");
        // Forge has written its id at least two ways, and OptiFine a third. A pattern tight
        // enough to name one silently reports the others as having no version at all.
        version("1.7.10-Forge10.13.4.1614-1.7.10", null, "1.7.10");
        version("1.20.1-OptiFine_HD_U_I6", null, "1.20.1");
        version("1.12.2-forge1.12.2-14.23.5.2860", null, "1.12.2");

        // A loose search would find "1.66" here, which is not a version and never has been.
        version("neoforge-21.1.66", null, null);
        check(!"1.66".equals(PresenceCard.minecraftVersion("neoforge-21.1.66", null)),
                "a NeoForge build number was read as a Minecraft version");
    }

    private static void neoForgeFallsBackToTheManifest() {
        version("neoforge-21.1.66", "1.21.1", "1.21.1");
        version("neoforge-21.0.167", "1.21", "1.21");
        // And an inheritsFrom that is not a version is not believed either.
        version("neoforge-21.1.66", "garbage", null);
        version("neoforge-21.1.66", "", null);
    }

    private static void loadersAreOrderedMostSpecificFirst() {
        loader("neoforge-21.1.66", "NeoForge");
        loader("fabric-loader-0.15.7-1.20.1", "Fabric");
        loader("quilt-loader-0.23.1-1.21", "Quilt");
        loader("1.20.1-forge-47.2.0", "Forge");
        loader("1.20.1-OptiFine_HD_U_I6", "OptiFine");
        loader("1.20.1", PresenceCard.VANILLA);
        loader(null, PresenceCard.VANILLA);

        // The ordering trap: every NeoForge id contains "forge" too.
        check("NeoForge".equals(PresenceCard.loaderName("neoforge-21.1.66")),
                "a NeoForge profile reported as Forge");
    }

    /** A snapshot is a version this cannot confidently name, so it says nothing rather than guess. */
    private static void nothingIsInventedFromNothing() {
        version("24w14a", null, null);
        version("1.21-pre1", null, null);
        version("1.20.1-rc1", null, null);
        version("", null, null);
        version(null, null, null);
        version("1.", null, null);
        version("1.2.3.4", null, null);
        version("1.999", null, null);
        // Not Minecraft versions at all. Without the "1." prefix test these parse as perfectly
        // ordinary dotted numbers and reach somebody's profile as a version of the game.
        version("12.5", null, null);
        version("0.30", null, null);
        version("2.0", null, null);

        // And the card still reads as something true rather than as an empty field.
        check("Minecraft".equals(PresenceCard.details("24w14a", null)),
                "a snapshot produced: " + PresenceCard.details("24w14a", null));
        check(PresenceCard.details(null, null).length() > 0, "an unknown version produced nothing");
    }

    /**
     * The assertion this whole class exists for.
     *
     * The card is public. A username reaching it is not a bug that can be taken back, so it is
     * checked as a property of the output rather than trusted to the call sites.
     */
    private static void nothingIdentifiesThePlayer() {
        String[] identities = {
            "Notch", "steve_2011", "player@example.com",
            "d1f4a6c2-0000-4000-8000-000000000000", "MyRealName",
        };
        String[] ids = {"1.20.1", "fabric-loader-0.15.7-1.20.1", "neoforge-21.1.66", "24w14a"};
        for (String id : ids) {
            String card = PresenceCard.details(id, "1.21.1") + " " + PresenceCard.state(id);
            for (String identity : identities) {
                check(!card.toLowerCase().contains(identity.toLowerCase()),
                        "the card carried an identity: \"" + card + "\"");
            }
            check(!card.contains("@"), "the card carried an address: \"" + card + "\"");
        }
    }

    /** An over-long field is dropped by Discord rather than trimmed, so the card would vanish. */
    private static void fieldsFitDiscordsLimits() {
        StringBuilder absurd = new StringBuilder();
        for (int i = 0; i < 50; i++) absurd.append("modpack-name-");
        String id = absurd.toString();

        check(PresenceCard.details(id, null).length() <= PresenceCard.FIELD_LIMIT,
                "details ran past Discord's field limit");
        check(PresenceCard.state(id).length() <= PresenceCard.FIELD_LIMIT,
                "state ran past Discord's field limit");
        check(PresenceCard.truncate(null).length() == 0, "a null field was not handled");
        check(PresenceCard.truncate("short").equals("short"), "a short field was altered");

        StringBuilder exact = new StringBuilder();
        for (int i = 0; i < PresenceCard.FIELD_LIMIT; i++) exact.append('x');
        check(PresenceCard.truncate(exact.toString()).length() == PresenceCard.FIELD_LIMIT,
                "a field of exactly the limit was cut");
        check(PresenceCard.truncate(exact.toString() + "y").length() == PresenceCard.FIELD_LIMIT,
                "a field one over the limit was not cut");
    }

    /**
     * The application id is a Discord snowflake, and a truncated one is the failure to guard
     * against: it would compile, it would look right, and presence would simply never appear
     * because it named an application that does not exist.
     */
    private static void theApplicationIdIsPlausible() {
        long id = PresenceCard.APPLICATION_ID;
        check(id > 0, "the application id is not set");
        String digits = Long.toString(id);
        check(digits.length() >= 17 && digits.length() <= 20,
                "the application id is " + digits.length()
                        + " digits, which is not the shape of a Discord snowflake");
        // Snowflakes carry a timestamp in their high bits: (id >> 22) + the Discord epoch, in
        // milliseconds. An id from before Discord existed, or from the future, is a wrong number
        // rather than merely an odd one.
        long epochMs = (id >>> 22) + 1420070400000L;
        check(epochMs > 1420070400000L, "the application id predates Discord itself");
        check(epochMs < 4102444800000L, "the application id claims to be from the next century");
    }

    /* ------------------------------------------------------------------ plumbing */

    private static void version(String id, String inheritsFrom, String expected) {
        String actual = PresenceCard.minecraftVersion(id, inheritsFrom);
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        check(ok, "version of \"" + id + "\" (inherits " + inheritsFrom + ") was " + actual
                + " not " + expected);
    }

    private static void loader(String id, String expected) {
        check(expected.equals(PresenceCard.loaderName(id)),
                "loader of \"" + id + "\" was " + PresenceCard.loaderName(id) + " not " + expected);
    }

    private static void check(boolean condition, String message) {
        sChecks++;
        if (!condition) FAILURES.add(message);
    }
}
