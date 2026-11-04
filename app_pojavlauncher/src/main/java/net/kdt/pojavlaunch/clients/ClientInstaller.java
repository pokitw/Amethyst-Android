package net.kdt.pojavlaunch.clients;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.value.DependentLibrary;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fetches everything one Lunar version needs and leaves a launch spec the game process reads.
 *
 * <p>Runs in the launcher process, off the main thread. It never touches the vanilla folders: its
 * whole tree is {@link #home()}, and the only thing it reads from outside it is Amethyst's own
 * LWJGL and, as a copy source, the shared assets folder.
 *
 * <p>The decisions are in {@link ClientPlan} and {@link LunarApi}, which are plain Java and are
 * driven by {@code scripts/clientsim}. What is here is the I/O around them.
 */
public final class ClientInstaller {
    private static final String TAG = "ClientInstaller";
    public static final String SPEC = "launch-spec.json";
    private static final int TIMEOUT_MS = 20_000;

    /** Where every file a client uses lives, kept apart from every vanilla profile. */
    public static File home() {
        return new File(Tools.DIR_GAME_HOME, "clients/lunar");
    }

    /** Why an install stopped, phrased for the player. */
    public static final class ClientException extends IOException {
        public final String code;

        ClientException(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    public interface Progress {
        /** @param fraction 0..1, or a negative number when the step has no measurable length */
        void onProgress(@NonNull String stage, float fraction);
    }

    // ---- listing -----------------------------------------------------------------------------

    /** The versions Lunar offers, newest first as the server orders them. */
    public static List<LunarApi.Version> fetchVersions() throws IOException {
        StringBuilder url = new StringBuilder(LunarApi.VERSIONS_URL);
        char sep = '?';
        for (Map.Entry<String, String> e : LunarApi.versionsQuery(installationId()).entrySet()) {
            url.append(sep).append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
                    .append(URLEncoder.encode(e.getValue(), "UTF-8"));
            sep = '&';
        }
        JsonElement root = JsonParser.parseString(http(url.toString(), null));
        if (!root.isJsonObject()) throw new IOException("Unexpected versions answer");
        return LunarApi.parseVersions(root.getAsJsonObject());
    }

    // ---- install -----------------------------------------------------------------------------

    /**
     * Installs or refreshes one version and module. Safe to run again: anything already present
     * and matching its checksum is kept.
     */
    public static void install(LunarApi.Subversion version, LunarApi.Module module, Progress progress)
            throws IOException {
        File home = home();
        FileUtils.ensureDirectory(home);

        progress.onProgress("Asking Lunar for " + version.id, -1);
        String installation = installationId();
        JsonObject body = LunarApi.launchBody(installation, UUID.randomUUID().toString(),
                version.id, module.id);
        JsonElement answer = JsonParser.parseString(http(LunarApi.LAUNCH_URL, body.toString()));
        if (!answer.isJsonObject()) throw new IOException("Unexpected launch answer");
        LunarApi.Launch launch = LunarApi.parseLaunch(answer.getAsJsonObject());
        if (!launch.success) throw new ClientException(launch.errorCode, launch.errorMessage);

        progress.onProgress("Reading Minecraft " + version.id, -1);
        JMinecraftVersionList.Version mojang = mojangVersion(version.id, home);
        List<String> coords = new ArrayList<>();
        if (mojang.libraries != null) for (DependentLibrary l : mojang.libraries) {
            if (l != null && l.name != null) coords.add(l.name);
        }
        String lwjgl = ClientPlan.lwjglVersion(coords);
        String reason = ClientPlan.unsupportedReason(lwjgl);
        if (reason != null) throw new ClientException(reason, "LWJGL2".equals(reason)
                ? "Minecraft " + version.id + " runs on LWJGL 2, which this version of Lunar patches"
                + " in a way Amethyst's LWJGL cannot take yet."
                : "Could not tell which LWJGL Minecraft " + version.id + " uses.");

        List<ClientPlan.Download> downloads = ClientPlan.downloads(launch, home);
        Log.i(TAG, "Skipping x86 native bundles: " + ClientPlan.skippedNatives(launch));
        int done = 0;
        byte[] buffer = new byte[65536];
        for (ClientPlan.Download d : downloads) {
            final int index = done;
            final int total = downloads.size();
            progress.onProgress("Downloading " + d.name, (float) index / total);
            DownloadUtils.ensureSha1(d.target, d.sha1, () -> {
                DownloadUtils.downloadFileMonitored(d.url, d.target, buffer, (cur, max) -> {
                    if (max > 0) progress.onProgress("Downloading " + d.name,
                            (index + (float) cur / max) / total);
                });
                return null;
            });
            done++;
        }

        progress.onProgress("Fitting Amethyst's LWJGL", -1);
        seedLwjgl(coords, lwjgl, home);

        installAssets(version, home, progress);

        File marker = ClientPlan.webosrMarker(home);
        FileUtils.ensureParentDirectory(marker);
        if (!marker.exists() && !marker.createNewFile()) throw new IOException("Could not write " + marker);
        FileUtils.ensureDirectory(ClientPlan.gameDir(home));

        JsonObject spec = new JsonObject();
        spec.addProperty("version", version.id);
        spec.addProperty("module", module.id);
        spec.addProperty("assetIndex", version.assetIndex);
        spec.addProperty("lwjgl", lwjgl);
        spec.add("launch", answer);
        write(new File(home, SPEC), spec.toString().getBytes(StandardCharsets.UTF_8));
        progress.onProgress("Ready", 1f);
    }

    /**
     * Mojang's own file for this version, kept where Lunar's loader looks for it. The installer
     * needs it first, because the LWJGL a version asks for decides what can be offered and what
     * has to be swapped.
     */
    private static JMinecraftVersionList.Version mojangVersion(String id, File home) throws IOException {
        File out = new File(ClientPlan.minecraftDir(home), "versions/" + id + "/" + id + ".json");
        JMinecraftVersionList list = Tools.GLOBAL_GSON.fromJson(
                http(LauncherPreferences.PREF_VERSION_REPOS, null), JMinecraftVersionList.class);
        JMinecraftVersionList.Version entry = null;
        if (list != null && list.versions != null) for (JMinecraftVersionList.Version v : list.versions) {
            if (v != null && id.equals(v.id)) { entry = v; break; }
        }
        if (entry == null || entry.url == null)
            throw new ClientException("UNKNOWN_VERSION", "Mojang does not list Minecraft " + id + ".");
        final String url = entry.url;
        DownloadUtils.ensureSha1(out, entry.sha1, () -> {
            DownloadUtils.downloadFile(url, out);
            return null;
        });
        JMinecraftVersionList.Version v = Tools.GLOBAL_GSON.fromJson(read(out),
                JMinecraftVersionList.Version.class);
        if (v == null) throw new IOException("Could not read " + out);
        return v;
    }

    /**
     * Puts Amethyst's LWJGL where Lunar's loader keeps its own. Always overwritten, because a jar
     * the loader fetched itself on an earlier run would otherwise be the desktop LWJGL, and it
     * would never be checked again.
     */
    private static void seedLwjgl(List<String> coords, String lwjgl, File home) throws IOException {
        String folder = ClientPlan.amethystLwjglFolder(lwjgl);
        File source = new File(Tools.DIR_GAME_HOME, "lwjgl3/" + folder);
        String[] names = source.list();
        if (names == null) throw new IOException("Amethyst's LWJGL is missing from " + source
                + ". Restart the app so it is unpacked again.");
        Set<String> shipped = new HashSet<>();
        for (String n : names) shipped.add(n);
        File libraries = new File(ClientPlan.minecraftDir(home), "libraries");
        for (ClientPlan.Seed s : ClientPlan.lwjglSeeds(coords, folder, shipped)) {
            copy(new File(source, s.amethystJar), new File(libraries, s.libraryPath));
        }
        for (String path : ClientPlan.lwjglNativesJars(coords)) {
            write(new File(libraries, path), ClientPlan.emptyJar());
        }
    }

    private static void installAssets(LunarApi.Subversion version, File home, Progress progress)
            throws IOException {
        if (version.assetIndex == null || version.assetIndexUrl == null) return;
        File assets = ClientPlan.assetsDir(home);
        File index = new File(assets, "indexes/" + version.assetIndex + ".json");
        final String indexUrl = version.assetIndexUrl;
        DownloadUtils.ensureSha1(index, version.assetIndexSha1, () -> {
            DownloadUtils.downloadFile(indexUrl, index);
            return null;
        });
        JsonElement parsed = JsonParser.parseString(read(index));
        if (!parsed.isJsonObject()) throw new IOException("Unreadable asset index " + index);
        List<ClientPlan.AssetObject> objects = ClientPlan.assetObjects(parsed.getAsJsonObject());
        File shared = new File(Tools.DIR_GAME_NEW, "assets");

        AtomicInteger finished = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<?>> work = new ArrayList<>();
        for (ClientPlan.AssetObject o : objects) {
            work.add(pool.submit(() -> {
                File target = new File(assets, o.relativePath);
                if (!(target.exists() && (o.size < 0 || target.length() == o.size))) {
                    File local = new File(shared, o.relativePath);
                    if (local.exists() && (o.size < 0 || local.length() == o.size)) {
                        copy(local, target);
                    } else {
                        DownloadUtils.ensureSha1(target, o.hash, () -> {
                            DownloadUtils.downloadFile(o.url(), target);
                            return null;
                        });
                    }
                }
                int n = finished.incrementAndGet();
                if (n % 32 == 0 || n == objects.size())
                    progress.onProgress("Game assets " + n + " of " + objects.size(),
                            (float) n / objects.size());
                return null;
            }));
        }
        pool.shutdown();
        try {
            for (Future<?> f : work) f.get();
        } catch (Exception e) {
            pool.shutdownNow();
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw cause instanceof IOException ? (IOException) cause : new IOException(cause);
        }
    }

    // ---- launch spec -------------------------------------------------------------------------

    /** Whether {@link #install} has finished for this version and module. */
    public static boolean isInstalled(String version, String module) {
        JsonObject spec = readSpec(home());
        return spec != null && version.equals(LunarApi.string(spec, "version"))
                && module.equals(LunarApi.string(spec, "module"));
    }

    @Nullable
    static JsonObject readSpec(File home) {
        try {
            JsonElement e = JsonParser.parseString(read(new File(home, SPEC)));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- plumbing ----------------------------------------------------------------------------

    /**
     * One id per install, as the official launcher keeps one. Generated once and kept, because
     * the server ties nothing to it that a fresh one would break, and a new one per launch would
     * read as a new installation every time.
     */
    static synchronized String installationId() throws IOException {
        File f = new File(home(), "installation-id");
        if (f.exists()) {
            String id = read(f).trim();
            if (id.length() == 36) return id;
        }
        String id = UUID.randomUUID().toString();
        FileUtils.ensureParentDirectory(f);
        write(f, id.getBytes(StandardCharsets.UTF_8));
        return id;
    }

    private static String http(String url, @Nullable String jsonBody) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", LunarApi.userAgent(BuildConfig.VERSION_NAME));
        c.setRequestProperty("Accept", "application/json");
        if (jsonBody != null) {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = c.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
        }
        try {
            int code = c.getResponseCode();
            // A refusal still carries a JSON body worth reading, so it is returned rather than
            // thrown; parseLaunch turns it into the server's own sentence.
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) throw new IOException("HTTP " + code + " from " + url);
            try (InputStream is = in) {
                return new String(readAll(is), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) != -1) out.write(b, 0, n);
        return out.toByteArray();
    }

    private static String read(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        }
    }

    private static void write(File f, byte[] data) throws IOException {
        FileUtils.ensureParentDirectory(f);
        File tmp = new File(f.getPath() + ".part");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        }
        if (!tmp.renameTo(f)) {
            if (!f.delete() || !tmp.renameTo(f)) throw new IOException("Could not write " + f);
        }
    }

    private static void copy(File from, File to) throws IOException {
        FileUtils.ensureParentDirectory(to);
        File tmp = new File(to.getPath() + ".part");
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(tmp)) {
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
        }
        if (to.exists() && !to.delete()) throw new IOException("Could not replace " + to);
        if (!tmp.renameTo(to)) throw new IOException("Could not write " + to);
    }
}
