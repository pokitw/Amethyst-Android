package net.kdt.pojavlaunch.skin;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.Tools;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reading skins out of Mojang: the signed-in account's own, and any player's by name.
 *
 * <b>The one thing to know before reading further: Mojang does not keep a skin history.</b> There
 * is no endpoint for "skins this account has worn", there never has been, and the name history
 * endpoint that did exist was withdrawn in 2022. The profile response carries a {@code skins}
 * array with a {@code state} of ACTIVE or INACTIVE, which looks like a history and is not one: in
 * practice it holds the skin being worn now. So a real history has to be kept by whatever applies
 * the skins, which is what {@link SkinHistory} is for, and this class reads the two things that
 * genuinely can be read.
 *
 * <b>Every player's skin is the largest skin database there is, and it is Mojang's own.</b> Looking
 * one up is two public requests with no key and no account: a name gives a UUID, and a UUID gives a
 * profile whose {@code textures} property is a base64 blob holding the skin URL and, when the skin
 * is slim, a metadata field saying so. That is the same path every skin site is built on top of.
 *
 * <b>Three separate hosts, three different rate limits.</b> {@code api.mojang.com} for the name
 * lookup, {@code sessionserver.mojang.com} for the textures (roughly one request per profile per
 * minute, which is why a result is worth caching), and {@code api.minecraftservices.com} for the
 * signed-in account. A 429 from any of them is a real answer and is reported as one rather than
 * being turned into "check your connection", which insults somebody whose connection is fine.
 */
public final class MojangSkins {
    private static final String TAG = "MojangSkins";

    private static final String NAME_LOOKUP = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_PROFILE =
            "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String OWN_PROFILE =
            "https://api.minecraftservices.com/minecraft/profile";

    private static final int TIMEOUT_MS = 15000;
    /** A skin PNG is a few kilobytes. Anything past this is not one and is not worth reading. */
    private static final int MAX_TEXTURE_BYTES = 512 * 1024;

    private MojangSkins() {}

    /** Why a request came back with nothing, so the screen can say something true about it. */
    public enum Failure { NONE, OFFLINE, RATE_LIMITED, NOT_FOUND, SIGNED_OUT, SERVER }

    private static volatile Failure lastFailure = Failure.NONE;

    public static Failure lastFailure() {
        return lastFailure;
    }

    /* ------------------------------------------------------------------ models */

    /** One skin entry on a profile: where it is, which arm model it uses, and whether it is worn. */
    public static final class Entry {
        @Nullable public final String id;
        public final String url;
        public final boolean slim;
        public final boolean active;
        /**
         * Mojang's own name for a default skin ("STEVE", "ALEX"), or null for an uploaded one.
         * Worth keeping because a default is not something anybody wants to re-import.
         */
        @Nullable public final String alias;

        Entry(@Nullable String id, String url, boolean slim, boolean active,
              @Nullable String alias) {
            this.id = id;
            this.url = url;
            this.slim = slim;
            this.active = active;
            this.alias = alias;
        }
    }

    /** A player, as the public API describes them. */
    public static final class Player {
        /** The UUID without dashes, which is the form both endpoints use. */
        public final String uuid;
        public final String name;
        @Nullable public final String skinUrl;
        @Nullable public final String capeUrl;
        public final boolean slim;

        Player(String uuid, String name, @Nullable String skinUrl, @Nullable String capeUrl,
               boolean slim) {
            this.uuid = uuid;
            this.name = name;
            this.skinUrl = skinUrl;
            this.capeUrl = capeUrl;
            this.slim = slim;
        }
    }

    /* ------------------------------------------------------------------ the account */

    /**
     * The skins on the signed-in account.
     *
     * The active one first, then anything Mojang describes as inactive. <b>Expect one entry.</b>
     * The array is shaped as though it were a history and is not one; where a second entry does
     * come back it is worth showing, and where it does not the screen must not look broken for
     * the absence.
     *
     * @param accessToken the account's Minecraft access token
     * @return the entries, or an empty list with {@link #lastFailure()} saying why
     */
    @NonNull
    public static List<Entry> ownSkins(@Nullable String accessToken) {
        List<Entry> entries = new ArrayList<>();
        if (accessToken == null || accessToken.isEmpty() || "0".equals(accessToken)) {
            // "0" is what this launcher stores for an account that never signed in, so this is an
            // offline account arriving here rather than a session that expired.
            lastFailure = Failure.SIGNED_OUT;
            return entries;
        }
        JsonElement parsed = getJson(OWN_PROFILE, accessToken);
        if (parsed == null || !parsed.isJsonObject()) return entries;
        return parseOwnSkins(parsed.getAsJsonObject());
    }

    /**
     * The skins out of a profile document.
     *
     * Split from the request so the whole parse can be driven by {@code scripts/skinapisim}
     * against fixtures. Mojang is not reachable from the build container, which makes the parse
     * the only part of this that can be checked at all, and it is also the part that fails
     * silently: a variant read wrongly is a slim skin worn on a classic model, which looks like
     * an editor bug rather than a parsing one.
     */
    @NonNull
    static List<Entry> parseOwnSkins(@NonNull JsonObject profile) {
        List<Entry> entries = new ArrayList<>();
        JsonArray skins = array(profile, "skins");
        if (skins == null) return entries;
        for (JsonElement element : skins) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject skin = element.getAsJsonObject();
            // Normalised here rather than at the fetch, so every reader of an Entry has a URL it
            // can actually use and there is one place this rule lives.
            String url = secureUrl(string(skin, "url"));
            if (url == null || url.isEmpty()) continue;
            // "SLIM" here, "slim" in the public API's metadata. Compared case insensitively so
            // one spelling cannot quietly become a classic model on the other path.
            String variant = string(skin, "variant");
            boolean slim = variant != null && variant.toLowerCase(Locale.ROOT).contains("slim");
            String state = string(skin, "state");
            boolean active = state == null || "ACTIVE".equalsIgnoreCase(state);
            entries.add(new Entry(string(skin, "id"), url, slim, active, string(skin, "alias")));
        }
        // Active first, because that is the one somebody is looking for.
        List<Entry> ordered = new ArrayList<>();
        for (Entry entry : entries) if (entry.active) ordered.add(entry);
        for (Entry entry : entries) if (!entry.active) ordered.add(entry);
        return ordered;
    }

    /* ------------------------------------------------------------------ any player */

    /**
     * A player's UUID from their name.
     *
     * <b>An unknown name is not an error and must not be reported as one.</b> Mojang answers 204
     * on some deployments and 404 on others for a name nobody has, and both mean the same thing
     * to the person typing: no such player. Treating that as a server fault would put "something
     * went wrong" under every typo.
     */
    @Nullable
    public static String uuidOf(@NonNull String username) {
        String name = username.trim();
        if (!isPlausibleName(name)) {
            lastFailure = Failure.NOT_FOUND;
            return null;
        }
        JsonElement parsed = getJson(NAME_LOOKUP + encode(name), null);
        if (parsed == null || !parsed.isJsonObject()) return null;
        String id = string(parsed.getAsJsonObject(), "id");
        if (id == null || id.isEmpty()) {
            lastFailure = Failure.NOT_FOUND;
            return null;
        }
        return id;
    }

    /**
     * A player's textures from their UUID.
     *
     * The interesting part is not the request, it is what comes back: the skin URL is not a field,
     * it is inside a <b>base64 blob</b> in a properties array, which decodes to a second JSON
     * document. The slim flag is deeper still, at {@code textures.SKIN.metadata.model}, and it is
     * absent rather than false for a classic skin. Every one of those is a place to be silently
     * wrong, which is why {@code scripts/skinapisim} drives this against fixtures.
     */
    @Nullable
    public static Player playerOf(@NonNull String uuid) {
        String id = uuid.replace("-", "").trim();
        if (id.length() != 32) {
            lastFailure = Failure.NOT_FOUND;
            return null;
        }
        JsonElement parsed = getJson(SESSION_PROFILE + encode(id), null);
        if (parsed == null || !parsed.isJsonObject()) return null;
        return parsePlayer(id, parsed.getAsJsonObject());
    }

    /** The textures out of a session profile. Split from the request for the same reason. */
    @NonNull
    static Player parsePlayer(@NonNull String id, @NonNull JsonObject profile) {
        String name = string(profile, "name");
        JsonObject textures = decodeTextures(profile);
        if (textures == null) {
            // A profile with no textures is a real answer: that account is wearing a default skin,
            // which Mojang serves rather than stores. The player still exists.
            return new Player(id, name == null ? "" : name, null, null, false);
        }
        JsonObject skin = object(textures, "SKIN");
        JsonObject cape = object(textures, "CAPE");
        String skinUrl = skin == null ? null : secureUrl(string(skin, "url"));
        String capeUrl = cape == null ? null : secureUrl(string(cape, "url"));
        boolean slim = false;
        if (skin != null) {
            JsonObject metadata = object(skin, "metadata");
            String model = metadata == null ? null : string(metadata, "model");
            slim = model != null && "slim".equalsIgnoreCase(model.trim());
        }
        return new Player(id, name == null ? "" : name, skinUrl, capeUrl, slim);
    }

    /** Name then textures, which is what a search box actually needs. */
    @Nullable
    public static Player search(@NonNull String username) {
        String uuid = uuidOf(username);
        if (uuid == null) return null;
        return playerOf(uuid);
    }

    /**
     * The {@code textures} property, base64 decoded and parsed.
     *
     * Read by name out of the properties array rather than by taking the first entry: the array is
     * documented to carry more than one property, and "the first one" is the sort of assumption
     * that works until the day it does not.
     */
    @Nullable
    static JsonObject decodeTextures(@NonNull JsonObject profile) {
        JsonArray properties = array(profile, "properties");
        if (properties == null) return null;
        for (JsonElement element : properties) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject property = element.getAsJsonObject();
            if (!"textures".equals(string(property, "name"))) continue;
            String value = string(property, "value");
            if (value == null || value.isEmpty()) return null;
            try {
                byte[] decoded = android.util.Base64.decode(value, android.util.Base64.DEFAULT);
                JsonElement parsed =
                        JsonParser.parseString(new String(decoded, Charset.forName("UTF-8")));
                if (!parsed.isJsonObject()) return null;
                return object(parsed.getAsJsonObject(), "textures");
            } catch (Throwable t) {
                Log.w(TAG, "The textures property was not base64 JSON", t);
                return null;
            }
        }
        return null;
    }

    /* ------------------------------------------------------------------ the image */

    /**
     * Fetch a skin PNG.
     *
     * Bounded, because this is bytes from a URL that arrived inside another response, and a skin
     * is a few kilobytes. Returns the bytes rather than a Bitmap so the caller decodes on the
     * thread it wants to and this stays testable without a graphics stack.
     */
    @Nullable
    public static byte[] texture(@NonNull String rawUrl) {
        String url = secureUrl(rawUrl);
        if (url == null) {
            lastFailure = Failure.SERVER;
            Log.w(TAG, "Refusing a texture url that is not https: " + rawUrl);
            return null;
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                lastFailure = code == 429 ? Failure.RATE_LIMITED : Failure.SERVER;
                return null;
            }
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    if (out.size() > MAX_TEXTURE_BYTES) {
                        lastFailure = Failure.SERVER;
                        Log.w(TAG, "Refusing a texture larger than a skin can be: " + url);
                        return null;
                    }
                }
                lastFailure = Failure.NONE;
                return out.toByteArray();
            }
        } catch (Throwable t) {
            lastFailure = Failure.OFFLINE;
            Log.w(TAG, "Could not fetch a texture", t);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /* ------------------------------------------------------------------ plumbing */

    /**
     * A texture URL, over https.
     *
     * <b>Mojang hands these out as {@code http://textures.minecraft.net/texture/...}</b>, in plain
     * HTTP, inside a response fetched over HTTPS. That is not a mistake to route around quietly:
     * from Android 9 cleartext is blocked by default, so an http URL taken at face value is a skin
     * that silently never loads, and the host serves the identical bytes over https. Upgrading it
     * is what every tool that reads this API does.
     *
     * This cost a user-visible bug. The first version required https and rejected everything
     * Mojang actually sends, and because the only symptom was a missing picture it surfaced as
     * "this player is wearing a default skin", which was the wrong answer to a question nobody
     * had asked.
     *
     * @return the URL to fetch, or null when it is not one worth fetching at all
     */
    @Nullable
    static String secureUrl(@Nullable String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (trimmed.startsWith("http://")) trimmed = "https://" + trimmed.substring(7);
        if (!trimmed.startsWith("https://")) return null;
        return trimmed;
    }

    /**
     * A name Mojang could plausibly have.
     *
     * Checked before the request rather than after, because a search box sends one of these per
     * keystroke's worth of typing and a name with a slash in it is a URL path, not a lookup.
     */
    static boolean isPlausibleName(@Nullable String name) {
        if (name == null) return false;
        int length = name.length();
        if (length < 1 || length > 16) return false;
        for (int i = 0; i < length; i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_';
            if (!ok) return false;
        }
        return true;
    }

    @Nullable
    private static JsonElement getJson(@NonNull String url, @Nullable String bearer) {
        String body = fetch(url, bearer);
        if (body == null) return null;
        try {
            return JsonParser.parseString(body);
        } catch (Throwable t) {
            lastFailure = Failure.SERVER;
            Log.w(TAG, "Mojang sent something that is not JSON", t);
            return null;
        }
    }

    @Nullable
    private static String fetch(@NonNull String url, @Nullable String bearer) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer);
            int code = connection.getResponseCode();
            // 204 and 404 both mean "no such player" on the name lookup, and neither is a fault.
            if (code == 204 || code == 404) {
                lastFailure = Failure.NOT_FOUND;
                return null;
            }
            if (code == 401 || code == 403) {
                lastFailure = Failure.SIGNED_OUT;
                return null;
            }
            if (code == 429) {
                lastFailure = Failure.RATE_LIMITED;
                return null;
            }
            if (code < 200 || code >= 300) {
                lastFailure = Failure.SERVER;
                Log.w(TAG, "Mojang answered " + code + " for " + url);
                return null;
            }
            try (InputStream input = connection.getInputStream()) {
                String body = Tools.read(input);
                // A 204 with a body of nothing reaches here on some deployments.
                if (body == null || body.trim().isEmpty()) {
                    lastFailure = Failure.NOT_FOUND;
                    return null;
                }
                lastFailure = Failure.NONE;
                return body;
            }
        } catch (Throwable t) {
            lastFailure = Failure.OFFLINE;
            Log.w(TAG, "Could not reach Mojang", t);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Throwable t) {
            return value;
        }
    }

    @Nullable
    static String string(@NonNull JsonObject object, @NonNull String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return null;
        try {
            return element.getAsString();
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static JsonArray array(@NonNull JsonObject object, @NonNull String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    @Nullable
    private static JsonObject object(@NonNull JsonObject parent, @NonNull String key) {
        JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }
}
