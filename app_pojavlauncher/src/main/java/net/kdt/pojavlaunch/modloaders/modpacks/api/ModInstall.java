package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Putting one mod, and the mods it needs, into a profile's folder.
 *
 * <b>This is the whole point of the browser.</b> Before it, adding a mod meant leaving the
 * launcher for a browser, finding the right jar for the right loader and the right Minecraft
 * version, downloading it, and importing it through Game files. Every one of those steps is a
 * place to pick the wrong file, and the last one is a file picker over a folder Android will not
 * let you browse. Here the profile already knows its version and its loader, so the only decision
 * left is which mod, and the rest is arithmetic.
 *
 * <b>Required dependencies are installed; nothing else is.</b> Modrinth marks a dependency
 * required, optional, incompatible or embedded. Only the first is fetched. An optional dependency
 * is a suggestion, and a launcher that acted on suggestions would put jars in someone's folder
 * that they did not choose and cannot attribute later. The walk is breadth-first with a seen-set,
 * because dependency graphs have cycles in them and a mod that required itself transitively would
 * otherwise download forever.
 */
public final class ModInstall {
    private static final String TAG = "ModInstall";

    /**
     * How deep the required-dependency walk goes.
     *
     * Not a guess at graph depth so much as a guard: this runs against a remote index, and the
     * cost of being wrong about a cycle is a phone downloading until it runs out of storage. Real
     * chains are two or three deep, an API library behind an API library.
     */
    private static final int MAX_DEPTH = 4;

    /** Why it failed, in terms the screen turns into a string. Never shown to anyone as-is. */
    public static final String ERROR_FOLDER = "folder";
    public static final String ERROR_DOWNLOAD = "download";

    private ModInstall() {}

    /** What happened, in the terms the screen has to report. */
    public static final class Result {
        /** File names actually written, in install order. */
        public final List<String> installed = new ArrayList<>();
        /** Required dependencies that could not be resolved, by project id. */
        public final List<String> missing = new ArrayList<>();
        /** Set when the mod itself could not be installed. */
        @Nullable public String error;

        /** The mod is on disk. Says nothing about whether everything it needs is. */
        public boolean ok() {
            return error == null && !installed.isEmpty();
        }

        /**
         * Whether it is complete, which is a stronger claim than {@link #ok()}.
         *
         * A mod whose required dependency did not arrive is a mod that crashes the game on the
         * next launch, and reporting that as a clean install is worse than reporting a failure:
         * nothing on screen would connect the crash back to this tap.
         */
        public boolean complete() {
            return ok() && missing.isEmpty();
        }
    }

    /**
     * Download a mod into a folder, following its required dependencies.
     *
     * Runs on the caller's thread and blocks; the browser calls it from the shared executor.
     *
     * @param folder     the profile's {@code mods} directory, created if absent
     * @param file       the version to install
     * @param mcVersion  the Minecraft version dependencies must also match, or null for any
     * @param loaderId   the loader dependencies must also match, or null for any
     */
    @NonNull
    public static Result install(@NonNull File folder, @NonNull ModrinthMods.File file,
                                 @Nullable String mcVersion, @Nullable String loaderId) {
        Result result = new Result();
        if (!folder.exists() && !folder.mkdirs()) {
            // A flag rather than a message: what the player is shown is a translated string the
            // screen owns, not a path or an exception, neither of which is theirs to act on.
            Log.w(TAG, "Could not create " + folder);
            result.error = ERROR_FOLDER;
            return result;
        }

        Set<String> seen = new HashSet<>();
        if (file.projectId != null && !file.projectId.isEmpty()) seen.add(file.projectId);

        try {
            download(folder, file);
            result.installed.add(file.fileName);
        } catch (IOException e) {
            Log.w(TAG, "Could not download " + file.fileName, e);
            result.error = ERROR_DOWNLOAD;
            return result;
        }

        // Breadth-first, so the depth cap means what it says. A depth-first walk with the same cap
        // would cut off a wide graph in the middle rather than at the bottom.
        List<ModrinthMods.File> frontier = new ArrayList<>();
        frontier.add(file);
        for (int depth = 0; depth < MAX_DEPTH && !frontier.isEmpty(); depth++) {
            List<ModrinthMods.File> next = new ArrayList<>();
            for (ModrinthMods.File parent : frontier) {
                for (ModrinthMods.Dependency dependency : parent.dependencies) {
                    if (!dependency.isRequired()) continue;
                    // The seen-set is maintained here and nowhere else. It used to be updated
                    // inside resolve() as well, which made the "could not resolve" branch below
                    // unreachable: by the time it ran, the id had already been added, so the
                    // guard was always false and a missing dependency was silently dropped.
                    String projectId = dependency.projectId;
                    if (projectId != null && !projectId.isEmpty() && !seen.add(projectId)) {
                        continue;
                    }
                    ModrinthMods.File resolved = resolve(dependency, mcVersion, loaderId);
                    if (resolved == null) {
                        result.missing.add(projectId != null ? projectId : dependency.versionId);
                        continue;
                    }
                    try {
                        download(folder, resolved);
                        result.installed.add(resolved.fileName);
                        next.add(resolved);
                    } catch (IOException e) {
                        // One dependency failing is not the whole install failing: the mod is
                        // already on disk, and saying which piece is missing is more use than
                        // rolling back something the player asked for.
                        Log.w(TAG, "Could not download dependency " + resolved.fileName, e);
                        result.missing.add(resolved.fileName);
                    }
                }
            }
            frontier = next;
        }
        return result;
    }

    /**
     * Turn a dependency into something downloadable, or null.
     *
     * A dependency names a version, a project, or both. The version is preferred because it is
     * what the mod author actually tested against; the project is resolved the same way the
     * browser resolves a search hit, which is the newest release that fits the profile.
     */
    @Nullable
    private static ModrinthMods.File resolve(@NonNull ModrinthMods.Dependency dependency,
                                             @Nullable String mcVersion,
                                             @Nullable String loaderId) {
        try {
            if (dependency.versionId != null && !dependency.versionId.isEmpty()) {
                ModrinthMods.File exact = ModrinthMods.version(dependency.versionId);
                if (exact != null) return exact;
            }
            if (dependency.projectId == null || dependency.projectId.isEmpty()) return null;
            return ModrinthMods.best(
                    ModrinthMods.versions(dependency.projectId, mcVersion, loaderId));
        } catch (Throwable t) {
            Log.w(TAG, "Could not resolve a dependency", t);
            return null;
        }
    }

    /**
     * Write one file, unless it is already there, and never write a broken one.
     *
     * <b>Downloaded beside the target and renamed into place.</b> {@code downloadFile} deletes a
     * failed download only when it is zero bytes, and rethrows only then: a transfer cut off
     * halfway leaves a truncated file on disk and returns <i>normally</i>. Writing straight to the
     * final name would therefore report a corrupt jar as an installed mod, and the game would
     * crash on next launch with nothing pointing back here. So the bytes land in a temporary file,
     * are checked, and only then take the real name.
     *
     * <b>Checked against the hash, or failing that the size.</b> Every Modrinth file carries a
     * sha1 in practice; the size is the fallback for the day one does not, and it still catches
     * the truncation this whole dance exists for.
     *
     * The skip for an existing file is on the hash and not the name, for the same reason: a jar
     * left truncated by an older version of this code has the right name and the wrong contents,
     * and a name-only check would leave it there forever.
     */
    private static void download(@NonNull File folder, @NonNull ModrinthMods.File file)
            throws IOException {
        File target = new File(folder, safeName(file.fileName));
        if (target.isFile() && verify(target, file)) return;

        File partial = new File(folder, target.getName() + ".part");
        //noinspection ResultOfMethodCallIgnored
        partial.delete();
        try {
            DownloadUtils.downloadFile(file.url, partial);
            if (!verify(partial, file)) {
                throw new IOException("Downloaded " + file.fileName + " did not verify");
            }
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            if (!partial.renameTo(target)) {
                throw new IOException("Could not put " + file.fileName + " in place");
            }
        } finally {
            if (partial.exists()) {
                //noinspection ResultOfMethodCallIgnored
                partial.delete();
            }
        }
    }

    /**
     * Whether a file on disk is the file the index described.
     *
     * A version with neither a hash nor a size is the one case where there is nothing to check,
     * and then the answer has to be yes: refusing would make such a mod uninstallable rather than
     * merely unverified.
     */
    private static boolean verify(@NonNull File target, @NonNull ModrinthMods.File file) {
        if (file.sha1 != null && !file.sha1.isEmpty()) return matches(target, file.sha1);
        if (file.size > 0) return target.length() == file.size;
        return target.length() > 0;
    }

    /** SHA-1 of a file against an expected hex digest. Callers guarantee the hash is present. */
    private static boolean matches(@NonNull File target, @NonNull String sha1) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            try (InputStream input = new DigestInputStream(new FileInputStream(target), digest)) {
                //noinspection StatementWithEmptyBody
                while (input.read(buffer) != -1) { /* digesting */ }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b));
            return hex.toString().equalsIgnoreCase(sha1);
        } catch (NoSuchAlgorithmException | IOException e) {
            return false;
        }
    }

    /**
     * The file name, with anything that could leave the folder taken out.
     *
     * A name from a remote index is a name from a stranger, and this is the third place in the
     * launcher that unpacks one: world extraction and the texture pack importer canonicalise for
     * the same reason. A mod file name has no legitimate need for a separator.
     */
    @NonNull
    static String safeName(@NonNull String fileName) {
        String name = fileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[^A-Za-z0-9._+\\- ]", "_").trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) return "mod.jar";
        if (name.length() > 200) name = name.substring(name.length() - 200);
        return name;
    }
}
