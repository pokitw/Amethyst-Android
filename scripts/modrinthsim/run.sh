#!/bin/sh
# Compile the shipped Modrinth parsing against stubs and drive it with fixtures.
#
# There is no Android SDK in the dev container and api.modrinth.com is not reachable from it, so
# this is the only thing that can catch a parsing mistake before a user does. It compiles the real
# ModrinthMods and ModInstall (source 8, like the app) and asserts on what they return.
#
# Needs a Gson jar. Gradle's cache has one after any build; set GSON_JAR to point elsewhere.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
SRC="$ROOT/app_pojavlauncher/src/main/java"
OUT="$HERE/build"

# The launcher ships one in libs/, which is the copy the app itself compiles against, so this
# harness parses with exactly the Gson the phone will.
if [ -z "$GSON_JAR" ]; then
    GSON_JAR=$(ls "$ROOT"/app_pojavlauncher/libs/gson-*.jar 2>/dev/null | head -1)
fi
if [ -z "$GSON_JAR" ]; then
    GSON_JAR=$(find "$HOME/.gradle" "$HOME/.m2" -name 'gson-*.jar' 2>/dev/null \
        | grep -v sources | head -1)
fi
if [ -z "$GSON_JAR" ] || [ ! -f "$GSON_JAR" ]; then
    echo "skipping: no gson jar found (set GSON_JAR to one)"
    exit 0
fi

rm -rf "$OUT"
mkdir -p "$OUT/stub/android/util" "$OUT/stub/androidx/annotation" "$OUT/stub/net/kdt/pojavlaunch" \
    "$OUT/stub/net/kdt/pojavlaunch/utils" "$OUT/stub/net/kdt/pojavlaunch/value/launcherprofiles" \
    "$OUT/classes"

cat > "$OUT/stub/android/util/Log.java" <<'EOF'
package android.util;
public class Log {
    public static int d(String t, String m) { return 0; }
    public static int i(String t, String m) { return 0; }
    public static int w(String t, String m) { return 0; }
    public static int w(String t, String m, Throwable e) { return 0; }
    public static int e(String t, String m) { return 0; }
    public static int e(String t, String m, Throwable e) { return 0; }
}
EOF
cat > "$OUT/stub/android/util/ArrayMap.java" <<'EOF'
package android.util;
import java.util.HashMap;
public class ArrayMap<K, V> extends HashMap<K, V> {}
EOF
for A in NonNull Nullable; do
cat > "$OUT/stub/androidx/annotation/$A.java" <<EOF
package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD, ElementType.LOCAL_VARIABLE,
         ElementType.TYPE_USE})
public @interface $A {}
EOF
done
cat > "$OUT/stub/net/kdt/pojavlaunch/Tools.java" <<'EOF'
package net.kdt.pojavlaunch;
import java.io.File;
import java.io.InputStream;
public class Tools {
    public static String read(InputStream in) { return ""; }
    public static File getGameDirPath(
            net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile p) { return null; }
}
EOF
cat > "$OUT/stub/net/kdt/pojavlaunch/utils/DownloadUtils.java" <<'EOF'
package net.kdt.pojavlaunch.utils;
import java.io.File;
import java.io.IOException;
public class DownloadUtils {
    public static void downloadFile(String url, File out) throws IOException {}
}
EOF
cat > "$OUT/stub/net/kdt/pojavlaunch/value/launcherprofiles/MinecraftProfile.java" <<'EOF'
package net.kdt.pojavlaunch.value.launcherprofiles;
public class MinecraftProfile { public String name, lastVersionId, icon, gameDir; }
EOF
cat > "$OUT/stub/net/kdt/pojavlaunch/value/launcherprofiles/LauncherProfiles.java" <<'EOF'
package net.kdt.pojavlaunch.value.launcherprofiles;
public class LauncherProfiles {
    public static MinecraftProfile getCurrentProfile() { return new MinecraftProfile(); }
}
EOF

javac -nowarn -source 8 -target 8 \
    -cp "$GSON_JAR" \
    -sourcepath "$OUT/stub:$SRC:$HERE" \
    -d "$OUT/classes" \
    "$SRC/net/kdt/pojavlaunch/modloaders/modpacks/api/ModrinthMods.java" \
    "$SRC/net/kdt/pojavlaunch/modloaders/modpacks/api/ModInstall.java" \
    "$HERE/Harness.java" 2>&1 | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -cp "$OUT/classes:$GSON_JAR" net.kdt.pojavlaunch.modloaders.modpacks.api.Harness "$HERE/fixtures/versions.json"
