package net.kdt.pojavlaunch.skin;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Putting a skin on a Microsoft account.
 *
 * <b>A skin lives on Mojang's profile, not in the game folder.</b> That is the fact the community
 * thread settled before this was ever built: "it all goes through microsoft, not us", and "local
 * accs have no skins". A server asks Mojang's session service what a player looks like, so an
 * offline account has nothing for it to ask about. The launcher can store a skin for an offline
 * account and show it in the editor, and it must say plainly that this is all it can do rather
 * than appearing to work.
 *
 * The request is the documented one: {@code PUT /minecraft/profile/skins} to
 * {@code api.minecraftservices.com}, multipart, with the account's existing Minecraft access
 * token as a bearer. That is the same token the launch path already puts on the command line, so
 * nothing new is stored and nothing new is asked of the player.
 *
 * <b>Failures are told apart</b>, because they mean different things to the person holding the
 * phone: a 401 is a session that needs signing in again, a 429 is Mojang asking for a pause, and
 * anything else is worth a message that does not blame the account.
 */
public final class SkinUpload {
    private static final String TAG = "SkinUpload";
    private static final String ENDPOINT =
            "https://api.minecraftservices.com/minecraft/profile/skins";
    private static final int TIMEOUT_MS = 30000;
    /** Mojang refuses anything larger, and so should this rather than spending the upload. */
    private static final long MAX_BYTES = 24576;

    public enum Result { OK, SIGNED_OUT, RATE_LIMITED, TOO_LARGE, REJECTED, OFFLINE, NOT_A_SKIN }

    /**
     * Whatever Mojang said about the last refusal, or null.
     *
     * <b>Kept because throwing it away was the bug.</b> The first version showed "Mojang would not
     * accept that skin" and discarded the response body, which is the one place the actual reason
     * lives. A message the player cannot act on, covering a message that would have told them
     * exactly what was wrong, is worse than no message.
     */
    @Nullable
    private static volatile String lastReason;

    @Nullable
    public static String lastReason() {
        return lastReason;
    }

    private SkinUpload() {}

    /**
     * Upload a skin file.
     *
     * @param accessToken the account's Minecraft access token
     * @param skin        a PNG, 64 by 64 or 64 by 32
     * @param slim        true for the three pixel arm model, which Mojang calls "slim"
     */
    @NonNull
    public static Result upload(@Nullable String accessToken, @NonNull File skin, boolean slim) {
        if (accessToken == null || accessToken.isEmpty() || "0".equals(accessToken)) {
            // "0" is what this launcher stores for an account that never signed in, so this is
            // the offline case arriving here rather than an expired session.
            return Result.SIGNED_OUT;
        }
        lastReason = null;
        if (!skin.isFile()) return Result.REJECTED;
        if (skin.length() > MAX_BYTES) return Result.TOO_LARGE;

        // Checked here rather than left to Mojang, because Mojang's answer to a wrongly sized
        // image is a bare 400 and the launcher can say something useful about it instead.
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(skin.getAbsolutePath(), bounds);
        boolean legalSize = bounds.outWidth == 64
                && (bounds.outHeight == 64 || bounds.outHeight == 32);
        if (!legalSize) {
            lastReason = bounds.outWidth + " by " + bounds.outHeight;
            Log.w(TAG, "Refusing to upload a " + lastReason + " image as a skin");
            return Result.NOT_A_SKIN;
        }

        String boundary = "----AmethystSkin" + System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            // Order matters: setDoOutput promotes a GET to POST, so the method is set after it.
            connection.setDoOutput(true);
            connection.setRequestMethod("PUT");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setRequestProperty("Content-Type",
                    "multipart/form-data; boundary=" + boundary);

            try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
                writePart(out, boundary, "variant", slim ? "slim" : "classic");
                out.writeBytes("--" + boundary + "\r\n");
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\""
                        + skin.getName() + "\"\r\n");
                out.writeBytes("Content-Type: image/png\r\n\r\n");
                try (InputStream in = new FileInputStream(skin)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                }
                out.writeBytes("\r\n--" + boundary + "--\r\n");
            }

            int code = connection.getResponseCode();
            if (code >= 200 && code < 300) return Result.OK;
            lastReason = readError(connection);
            Log.w(TAG, "Mojang answered " + code + " for a skin upload: " + lastReason);
            if (code == 401 || code == 403) return Result.SIGNED_OUT;
            if (code == 429) return Result.RATE_LIMITED;
            return Result.REJECTED;
        } catch (IOException e) {
            Log.w(TAG, "Could not reach Mojang to upload a skin", e);
            return Result.OFFLINE;
        } catch (Throwable t) {
            Log.w(TAG, "Skin upload failed", t);
            return Result.REJECTED;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * Mojang's explanation, out of the error stream.
     *
     * Bounded and reduced to the one field worth showing: this is a remote string on its way to
     * a screen, so it is capped and stripped of anything that is not ordinary text rather than
     * pasted through. A body that is not the shape expected simply yields null and the caller
     * falls back to its own wording.
     */
    @Nullable
    private static String readError(@NonNull HttpURLConnection connection) {
        try (InputStream stream = connection.getErrorStream()) {
            if (stream == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = stream.read(buffer)) != -1 && out.size() < 4096) {
                out.write(buffer, 0, read);
            }
            String body = new String(out.toByteArray(), "UTF-8");
            String message = valueOf(body, "errorMessage");
            if (message == null) message = valueOf(body, "error");
            if (message == null) return null;
            message = message.replaceAll("[^\\p{Print}]", " ").trim();
            if (message.isEmpty()) return null;
            return message.length() > 160 ? message.substring(0, 160) : message;
        } catch (Throwable t) {
            return null;
        }
    }

    /** One string field out of a flat JSON object, without pulling in a parser for it. */
    @Nullable
    private static String valueOf(@NonNull String body, @NonNull String field) {
        String needle = "\"" + field + "\"";
        int at = body.indexOf(needle);
        if (at < 0) return null;
        int colon = body.indexOf(':', at + needle.length());
        if (colon < 0) return null;
        int open = body.indexOf('"', colon);
        if (open < 0) return null;
        int close = body.indexOf('"', open + 1);
        if (close < 0) return null;
        return body.substring(open + 1, close);
    }

    private static void writePart(DataOutputStream out, String boundary, String name, String value)
            throws IOException {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.writeBytes(value + "\r\n");
    }
}
