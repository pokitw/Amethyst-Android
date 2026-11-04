package net.kdt.pojavlaunch.clients;

import static net.kdt.pojavlaunch.Architecture.archAsStringAndroid;
import static net.kdt.pojavlaunch.Architecture.getDeviceArchitecture;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Starts an installed client inside the game process. Separate from {@code Tools.launchMinecraft}
 * on purpose: nothing here resolves a vanilla version, reads a launcher profile's version or
 * touches the shared game folder. What it shares is the engine room, {@link JREUtils#launchJavaVM},
 * because the renderer, the surface and the controls are the same whatever runs on top of them.
 */
public final class ClientLaunch {
    private static final String TAG = "ClientLaunch";

    public final File home;
    public final String version, module, assetIndex, lwjgl;
    public final LunarApi.Launch launch;
    private final JMinecraftVersionList.Version mojang;

    private ClientLaunch(File home, String version, String module, String assetIndex, String lwjgl,
                         LunarApi.Launch launch, JMinecraftVersionList.Version mojang) {
        this.home = home;
        this.version = version;
        this.module = module;
        this.assetIndex = assetIndex;
        this.lwjgl = lwjgl;
        this.launch = launch;
        this.mojang = mojang;
    }

    /** The installed client in {@code home}, or null if the install is missing or incomplete. */
    @Nullable
    public static ClientLaunch read(File home) {
        try {
            JsonObject spec = ClientInstaller.readSpec(home);
            if (spec == null) return null;
            String version = LunarApi.string(spec, "version");
            JsonElement l = spec.get("launch");
            if (version == null || l == null || !l.isJsonObject()) return null;
            LunarApi.Launch launch = LunarApi.parseLaunch(l.getAsJsonObject());
            if (!launch.success) return null;
            File json = new File(ClientPlan.minecraftDir(home), "versions/" + version + "/" + version + ".json");
            JMinecraftVersionList.Version mojang = Tools.GLOBAL_GSON.fromJson(readText(json),
                    JMinecraftVersionList.Version.class);
            if (mojang == null) return null;
            return new ClientLaunch(home, version, LunarApi.string(spec, "module"),
                    LunarApi.string(spec, "assetIndex"), LunarApi.string(spec, "lwjgl"), launch, mojang);
        } catch (Exception e) {
            Log.e(TAG, "Could not read the client install in " + home, e);
            return null;
        }
    }

    /** Mojang's file for this version, which the game activity reads its asset and input rules from. */
    public JMinecraftVersionList.Version versionInfo() {
        return mojang;
    }

    /** The Java the client asks for, never below what Mojang's file asks for. */
    public int requiredJava() {
        int mojangJava = mojang.javaVersion == null ? 8 : mojang.javaVersion.majorVersion;
        return Math.max(mojangJava, launch.javaMajor);
    }

    public void launch(AppCompatActivity activity, MinecraftAccount account,
                       MinecraftProfile profile, int width, int height) throws Throwable {
        Runtime runtime = MultiRTUtils.forceReread(Tools.pickRuntime(profile, requiredJava()));
        File runtimeHome = MultiRTUtils.getRuntimeHome(runtime.name);
        Set<String> modules = ClientPlan.modulesFromRelease(readOrNull(new File(runtimeHome, "release")));

        // The vanilla path sets these while it resolves a version's libraries, which this path
        // never does, and JREUtils reads them for LWJGL's library path and FreeType.
        String folder = ClientPlan.amethystLwjglFolder(lwjgl);
        Tools.sLwjglVersion = folder;
        Tools.lwjglNativesDir = String.format("%s/lwjgl-%s-natives/%s", Tools.DIR_DATA, folder,
                archAsStringAndroid(getDeviceArchitecture()));

        List<String> args = new ArrayList<>();
        Tools.getCacioJavaArgs(args, runtime.javaVersion == 8, activity);
        // Amethyst's native folder carries libWebOSR-Binding.so, which the client asks for by name.
        args.add("-Djava.library.path=" + Tools.lwjglNativesDir + ":" + Tools.NATIVE_LIB_DIR);
        args.add("-Dorg.lwjgl.librarypath=" + Tools.lwjglNativesDir);
        args.addAll(ClientPlan.jvmArguments(launch.jvmArguments, modules));
        // Added after getJavaArgs' own user.home in the final list, and the last one wins. The
        // client writes .lunarclient and .minecraft under it, so this is what keeps them here.
        args.add("-Duser.home=" + home.getAbsolutePath());
        args.add("-cp");
        args.add(join(ClientPlan.classpath(launch, home)));
        args.add(launch.mainClass);

        boolean offline = account == null || account.isLocal() || account.accessToken == null;
        ClientPlan.Account who = new ClientPlan.Account(
                account == null ? "Player" : account.username,
                account == null ? UUID.randomUUID().toString() : account.profileId,
                offline ? null : account.accessToken);
        args.addAll(ClientPlan.programArguments(launch, version, assetIndex, home, who, width, height,
                ClientInstaller.installationId(), UUID.randomUUID().toString()));

        int emptied = emptyDesktopNatives(new File(ClientPlan.minecraftDir(home), "libraries/org/lwjgl"));
        Logger.appendToLog("Client: Lunar " + version + " (" + module + "), Java " + runtime.javaVersion
                + ", LWJGL " + lwjgl + " served by Amethyst's " + folder
                + ", skipped x86 natives " + ClientPlan.skippedNatives(launch)
                + ", emptied " + emptied + " desktop LWJGL native jars");
        JREUtils.launchJavaVM(activity, runtime, ClientPlan.multiverDir(home), args,
                LauncherPreferences.PREF_CUSTOM_JAVA_ARGS);
    }

    /**
     * Empties every desktop LWJGL native jar under {@code dir}, on every launch. The installer
     * empties the ones Mojang's file names, but the client fetches further LWJGL modules of its
     * own the first time it runs (lz4, zstd, opus, a newer tinyfd), and those only exist to be
     * found from the second launch on. Unable to throw, because this is the launch path (16.15).
     */
    private static int emptyDesktopNatives(File dir) {
        int count = 0;
        try {
            File[] children = dir.listFiles();
            if (children == null) return 0;
            for (File f : children) {
                if (f.isDirectory()) {
                    count += emptyDesktopNatives(f);
                } else if (ClientPlan.isDesktopNativesJar(f.getName())
                        && f.length() != ClientPlan.emptyJar().length) {
                    try (java.io.OutputStream out = new java.io.FileOutputStream(f)) {
                        out.write(ClientPlan.emptyJar());
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not empty desktop natives in " + dir + ": " + e);
        }
        return count;
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(':');
            sb.append(p);
        }
        return sb.toString();
    }

    @Nullable
    private static String readOrNull(File f) {
        try {
            return readText(f);
        } catch (Exception e) {
            return null;
        }
    }

    private static String readText(File f) throws java.io.IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
