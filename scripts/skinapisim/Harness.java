// In the package under test: the parsers are package-private on purpose, and widening them so a
// harness could see them would be the wrong way round.
package net.kdt.pojavlaunch.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.skin.MojangSkins.Entry;
import net.kdt.pojavlaunch.skin.MojangSkins.Player;

import java.nio.charset.Charset;
import java.util.List;

/**
 * Drives the shipped Mojang skin parsers against fixtures.
 *
 * <b>Mojang is not reachable from the build container</b>, so the wire format is coded from the
 * documented contract and this is the only check available. What it checks is the part that fails
 * silently rather than loudly: the skin URL is not a field on the profile, it is inside a base64
 * blob in a properties array which decodes to a second JSON document, and the slim flag is deeper
 * still at textures.SKIN.metadata.model, absent rather than false for a classic skin. Getting the
 * last one wrong puts a slim skin on a classic model, which reads as a bug in the editor.
 *
 * The fixtures are the shapes the API actually sends, including the awkward ones: a profile with
 * no textures at all (a player on a default skin), a properties array where textures is not the
 * first entry, and nulls where the documentation says a string.
 */
public class Harness {
    private static int failures = 0;
    private static int checks = 0;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            System.out.println("FAIL: " + message);
            failures++;
        }
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    /** A textures property, built the way Mojang builds it: base64 of a JSON document. */
    private static String texturesValue(String inner) {
        return android.util.Base64.encodeToString(
                inner.getBytes(Charset.forName("UTF-8")), android.util.Base64.NO_WRAP);
    }

    private static JsonObject sessionProfile(String name, String inner, boolean texturesFirst) {
        String textures = "{\"name\":\"textures\",\"value\":\"" + texturesValue(inner) + "\"}";
        // Real responses also carry a signature property, and nothing promises textures is first.
        String other = "{\"name\":\"signature_required\",\"value\":\"true\"}";
        String properties = texturesFirst ? textures + "," + other : other + "," + textures;
        return json("{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"" + name
                + "\",\"properties\":[" + properties + "]}");
    }

    // ---------------------------------------------------------------- the public lookup

    private static void classicPlayer() {
        String inner = "{\"timestamp\":1667000000000,"
                + "\"profileId\":\"069a79f444e94726a5befca90e38aaf5\","
                + "\"profileName\":\"Notch\","
                + "\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/aaa\"}}}";
        Player player = MojangSkins.parsePlayer("069a79f444e94726a5befca90e38aaf5",
                sessionProfile("Notch", inner, true));
        check("Notch".equals(player.name), "the player's name was " + player.name);
        check("https://textures.minecraft.net/texture/aaa".equals(player.skinUrl),
                "the skin url was " + player.skinUrl);
        // Absent metadata means classic. Reading absence as slim is the failure this exists for.
        check(!player.slim, "a skin with no model metadata was read as slim");
        check(player.capeUrl == null, "a cape appeared where there is none");
    }

    private static void slimPlayer() {
        String inner = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/bbb\","
                + "\"metadata\":{\"model\":\"slim\"}},"
                + "\"CAPE\":{\"url\":\"https://textures.minecraft.net/texture/ccc\"}}}";
        Player player = MojangSkins.parsePlayer("069a79f444e94726a5befca90e38aaf5",
                sessionProfile("Alex", inner, true));
        check(player.slim, "a slim skin was read as classic");
        check("https://textures.minecraft.net/texture/ccc".equals(player.capeUrl),
                "the cape url was " + player.capeUrl);
    }

    private static void texturesNotFirst() {
        String inner = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/ddd\"}}}";
        Player player = MojangSkins.parsePlayer("069a79f444e94726a5befca90e38aaf5",
                sessionProfile("Someone", inner, false));
        check("https://textures.minecraft.net/texture/ddd".equals(player.skinUrl),
                "textures was missed when it was not the first property: " + player.skinUrl);
    }

    private static void noTextures() {
        // A player who has never uploaded a skin. Mojang serves them a default rather than
        // storing one, so the profile is real and the textures are empty. That is not a failure
        // and must not be reported as one.
        JsonObject profile = json("{\"id\":\"069a79f444e94726a5befca90e38aaf5\","
                + "\"name\":\"Fresh\",\"properties\":[]}");
        Player player = MojangSkins.parsePlayer("069a79f444e94726a5befca90e38aaf5", profile);
        check("Fresh".equals(player.name), "a player with no textures lost their name");
        check(player.skinUrl == null, "a skin url appeared out of nothing");
        check(!player.slim, "a player with no textures was read as slim");

        JsonObject empty = json("{\"id\":\"x\",\"name\":\"Fresh\"}");
        Player none = MojangSkins.parsePlayer("069a79f444e94726a5befca90e38aaf5", empty);
        check(none.skinUrl == null, "a profile with no properties array did not parse");
    }

    private static void hostileResponses() {
        // Each of these is a shape a parser can crash on. None of them may throw.
        String[] bodies = {
                "{\"properties\":[{\"name\":\"textures\",\"value\":\"not base64 at all!!\"}]}",
                "{\"properties\":[{\"name\":\"textures\",\"value\":\"\"}]}",
                "{\"properties\":[{\"name\":\"textures\"}]}",
                "{\"properties\":[null,{\"name\":\"textures\",\"value\":\""
                        + texturesValue("{\"textures\":{}}") + "\"}]}",
                "{\"properties\":[{\"name\":\"textures\",\"value\":\""
                        + texturesValue("[]") + "\"}]}",
                "{\"properties\":\"not an array\"}",
                "{\"name\":null,\"properties\":[]}"
        };
        for (String body : bodies) {
            try {
                Player player = MojangSkins.parsePlayer("069a79f444e94726a5befca90e38aaf5",
                        json(body));
                check(player != null, "a hostile response gave null rather than an empty player");
                check(player.name != null, "a null name was passed through rather than emptied");
            } catch (Throwable t) {
                check(false, "threw on " + body.substring(0, Math.min(48, body.length()))
                        + ": " + t);
            }
        }
    }

    // ---------------------------------------------------------------- the account

    private static void ownProfile() {
        JsonObject profile = json("{\"id\":\"abc\",\"name\":\"Player\",\"skins\":["
                + "{\"id\":\"1\",\"state\":\"ACTIVE\",\"url\":\"https://textures.minecraft.net/texture/eee\","
                + "\"variant\":\"SLIM\",\"alias\":\"ALEX\"}],\"capes\":[]}");
        List<Entry> entries = MojangSkins.parseOwnSkins(profile);
        check(entries.size() == 1, "the account had " + entries.size() + " skins, expected 1");
        Entry entry = entries.get(0);
        check(entry.active, "the active skin was not read as active");
        // "SLIM" upper case here, "slim" lower case in the public API. Both have to land on slim
        // or the same person's skin sits differently depending on which path found it.
        check(entry.slim, "variant SLIM was read as classic");
        check("ALEX".equals(entry.alias), "the alias was " + entry.alias);
    }

    private static void ownProfileOrdering() {
        // Where Mojang does return an inactive entry, the active one still has to come first:
        // it is the one somebody opened the screen to find.
        JsonObject profile = json("{\"skins\":["
                + "{\"id\":\"old\",\"state\":\"INACTIVE\",\"url\":\"https://textures.minecraft.net/texture/1\",\"variant\":\"CLASSIC\"},"
                + "{\"id\":\"now\",\"state\":\"ACTIVE\",\"url\":\"https://textures.minecraft.net/texture/2\",\"variant\":\"CLASSIC\"}]}");
        List<Entry> entries = MojangSkins.parseOwnSkins(profile);
        check(entries.size() == 2, "expected both entries, got " + entries.size());
        check("now".equals(entries.get(0).id), "the active skin was not first");
        check(!entries.get(1).active, "the inactive skin was reported as active");
    }

    private static void ownProfileAwkward() {
        // A skin with no url is not a skin and must be dropped rather than shown as a blank tile.
        JsonObject profile = json("{\"skins\":["
                + "{\"id\":\"1\",\"state\":\"ACTIVE\"},"
                + "{\"id\":\"2\",\"url\":\"\"},"
                + "null,"
                + "\"not an object\","
                + "{\"id\":\"3\",\"url\":\"https://textures.minecraft.net/texture/f\"}]}");
        List<Entry> entries = MojangSkins.parseOwnSkins(profile);
        check(entries.size() == 1, "expected one usable skin, got " + entries.size());
        // No state field at all means the one skin there is, which is what the API sends most
        // often. Defaulting that to inactive would hide the only entry.
        check(entries.get(0).active, "a skin with no state was read as inactive");

        check(MojangSkins.parseOwnSkins(json("{}")).isEmpty(),
                "a profile with no skins array did not come back empty");
        check(MojangSkins.parseOwnSkins(json("{\"skins\":\"nope\"}")).isEmpty(),
                "a skins field that is not an array did not come back empty");
    }

    // ---------------------------------------------------------------- the name guard

    private static void nameGuard() {
        for (String good : new String[]{"Notch", "jeb_", "a", "Player_123", "0123456789012345"}) {
            check(MojangSkins.isPlausibleName(good), good + " was refused as a name");
        }
        // Everything here would go into a URL path, and a search box sends one per typed name.
        for (String bad : new String[]{"", "01234567890123456", "has space", "slash/es",
                "dot.dot", "../../etc", "emoji😀", "semi;colon", "quest?ion", null}) {
            check(!MojangSkins.isPlausibleName(bad),
                    "\"" + bad + "\" was accepted as a player name");
        }
    }

    private static void fieldReading() {
        JsonObject object = json("{\"a\":\"text\",\"b\":null,\"c\":12,\"d\":{},\"e\":[]}");
        check("text".equals(MojangSkins.string(object, "a")), "a string did not read back");
        check(MojangSkins.string(object, "b") == null, "a null read as a value");
        check(MojangSkins.string(object, "missing") == null, "a missing key read as a value");
        check(MojangSkins.string(object, "d") == null, "an object read as a string");
        check(MojangSkins.string(object, "e") == null, "an array read as a string");
        // A number reading as its text is fine and is what Gson does; it must not throw.
        check(MojangSkins.string(object, "c") != null, "a number threw rather than reading");
    }

    public static void main(String[] args) {
        classicPlayer();
        slimPlayer();
        texturesNotFirst();
        noTextures();
        hostileResponses();
        ownProfile();
        ownProfileOrdering();
        ownProfileAwkward();
        nameGuard();
        fieldReading();

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
        System.out.println("mojang skin parsing OK");
    }
}
