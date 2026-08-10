#!/bin/sh
# Reproduce the reported icon cache crash against the shipped ModIconCache.
#
# Loading one icon is a cycle of pool tasks: a cache miss submits a download, and the download
# submits the read back again. Both submissions happen on a pool worker, so a pool shut down
# underneath them throws where nothing can catch it and the app dies. This drives that exact
# sequence, plus the two other things the fix depends on: that shutdown does not interrupt work
# already running, and that the pool's threads time out on their own.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
SRC="$ROOT/app_pojavlauncher/src/main/java"
OUT="$HERE/build"

rm -rf "$OUT"
mkdir -p "$OUT/stub/android/util" "$OUT/stub/net/kdt/pojavlaunch" \
    "$OUT/stub/org/apache/commons/io" "$OUT/pkg/net/kdt/pojavlaunch/modloaders/modpacks/imagecache" \
    "$OUT/classes" "$OUT/cache"

cat > "$OUT/stub/android/util/Log.java" <<'STUB'
package android.util;
public class Log {
    public static int i(String t, String m) { return 0; }
    public static int w(String t, String m) { return 0; }
    public static int e(String t, String m) { return 0; }
}
STUB
cat > "$OUT/stub/android/util/Base64.java" <<'STUB'
package android.util;
public class Base64 {
    public static final int DEFAULT = 0;
    public static String encodeToString(byte[] b, int f) { return ""; }
}
STUB
cat > "$OUT/stub/org/apache/commons/io/IOUtils.java" <<'STUB'
package org.apache.commons.io;
import java.io.*;
public class IOUtils { public static byte[] toByteArray(InputStream in) throws IOException { return new byte[0]; } }
STUB
cat > "$OUT/stub/net/kdt/pojavlaunch/Tools.java" <<'STUB'
package net.kdt.pojavlaunch;
import java.io.File;
public class Tools { public static File DIR_CACHE = new File(System.getProperty("amethyst.cache", ".")); }
STUB

# Only ModIconCache is under test; its two collaborators are stubbed so the check does not drag
# in Bitmap and the network, neither of which the crash involved.
cp "$SRC/net/kdt/pojavlaunch/modloaders/modpacks/imagecache/ModIconCache.java" \
   "$OUT/pkg/net/kdt/pojavlaunch/modloaders/modpacks/imagecache/"
cat > "$OUT/pkg/net/kdt/pojavlaunch/modloaders/modpacks/imagecache/ImageReceiver.java" <<'STUB'
package net.kdt.pojavlaunch.modloaders.modpacks.imagecache;
public interface ImageReceiver { void onImageAvailable(Object image); }
STUB
cat > "$OUT/pkg/net/kdt/pojavlaunch/modloaders/modpacks/imagecache/ReadFromDiskTask.java" <<'STUB'
package net.kdt.pojavlaunch.modloaders.modpacks.imagecache;
class ReadFromDiskTask implements Runnable {
    ReadFromDiskTask(ModIconCache c, ImageReceiver r, String t, String u) {}
    public void run() {}
}
STUB
cp "$HERE/Harness.java" "$OUT/pkg/net/kdt/pojavlaunch/modloaders/modpacks/imagecache/"

javac -nowarn -source 8 -target 8 \
    -sourcepath "$OUT/stub:$OUT/pkg" \
    -d "$OUT/classes" \
    "$OUT/pkg/net/kdt/pojavlaunch/modloaders/modpacks/imagecache/ModIconCache.java" \
    "$OUT/pkg/net/kdt/pojavlaunch/modloaders/modpacks/imagecache/Harness.java" 2>&1 \
    | grep -v 'source value 8\|target value 8\|-Xlint:-options\|^Note:' || true

java -Damethyst.cache="$OUT/cache" -cp "$OUT/classes" \
    net.kdt.pojavlaunch.modloaders.modpacks.imagecache.Harness
