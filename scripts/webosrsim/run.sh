#!/bin/sh
# Run the real Lunar Client on a desktop JVM with Amethyst's web engine binding in place of
# Ultralight, and assert it starts.
#
# This is the only check the binding has that runs the client it exists for. It is manual rather
# than part of CI: it needs network access to Lunar's and Mojang's hosts, Xvfb with Mesa, about
# 400 MB of downloads, and three minutes. Nothing it downloads is written inside the repository;
# WORK defaults to a temporary folder.
#
# What it proves: with the three Ultralight libraries absent and this library loaded in their
# place, the client initialises and stays up. Without the binding it fails at
# WebOSR.createEngine, which is the exact call the Android build would otherwise die on. What it
# does not prove: anything about Android's LWJGL or renderers, which the desktop run does not use.
#
# Requirements: python3, curl, gcc, a JDK for jni.h, Xvfb, xrandr (LWJGL asks it for display
# modes and an empty answer crashes 1.8.9 before it opens a window). The client downloads Fabric
# and Mojang libraries itself, from Java, so behind a proxy WEBOSRSIM_JAVA_OPTS has to carry
# -Dhttps.proxyHost and friends: Java does not read HTTPS_PROXY from the environment.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
WORK=${WORK:-${TMPDIR:-/tmp}/webosrsim}
VERSION=${VERSION:-1.21.8}
MODULE=${MODULE:-fabric}
SECONDS_UP=${SECONDS_UP:-180}
DISPLAY_NUM=${DISPLAY_NUM:-:91}

for tool in python3 curl gcc javac Xvfb xrandr; do
    command -v "$tool" >/dev/null 2>&1 || { echo "skipping: $tool not found"; exit 0; }
done

mkdir -p "$WORK/home" "$WORK/natives/resources"
cd "$WORK"

# The binding, built for this machine from the file the APK ships.
JH=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")
gcc -shared -fPIC -O2 -Wall -Wextra -Wno-unused-parameter -I"$JH/include" -I"$JH/include/linux" \
    -o natives/libWebOSR-Binding.so "$ROOT/app_pojavlauncher/src/main/jni/clients/webosr_binding.c"
# The client checks for this one file before it will create the engine.
: > natives/resources/icudt67l.dat

# Ask exactly the questions the app asks. The request JSON is built by hand here rather than by
# LunarApi so that a mistake in LunarApi cannot also hide in the check of it.
ID=$(python3 -c 'import uuid;print(uuid.uuid4())')
PID=$(python3 -c 'import uuid;print(uuid.uuid4())')
curl -sS --fail --max-time 60 -X POST -H 'Content-Type: application/json' -A "AmethystX/webosrsim" \
    -d "{\"installation_id\":\"$ID\",\"os\":\"linux\",\"os_release\":\"6.1.0\",\"arch\":\"x64\",\"launcher_version\":\"3.4.9\",\"version\":\"$VERSION\",\"branch\":\"master\",\"args\":[],\"module\":\"$MODULE\",\"canary_preference\":\"NEUTRAL\",\"profile\":{\"id\":\"$PID\",\"name\":\"webosrsim\"}}" \
    https://api.lunarclientprod.com/launcher/launch -o launch.json
curl -sS --fail --max-time 60 -G -A "AmethystX/webosrsim" --data-urlencode "installation_id=$ID" \
    --data-urlencode os=linux --data-urlencode os_release=6.1.0 --data-urlencode arch=x64 \
    --data-urlencode launcher_version=3.4.9 --data-urlencode branch=master \
    https://api.lunarclientprod.com/launcher/metadata/versions/lunar -o versions.json

python3 -I - "$VERSION" <<'PY'
import json, os, subprocess, sys
version = sys.argv[1]
launch = json.load(open('launch.json'))
assert launch.get('success'), launch
multiver = 'home/.lunarclient/offline/multiver'
for a in launch['launchTypeData']['artifacts']:
    if a['type'] not in ('CLASS_PATH', 'EXTERNAL_FILE'):
        continue  # every native bundle is x86 and is exactly what the binding replaces
    dst = os.path.join(multiver, a['name'])
    if not os.path.exists(dst):
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        subprocess.run(['curl', '-sS', '--fail', '--max-time', '300', '-o', dst, a['url']], check=True)
jre = launch['jre']['download']['url']
if not os.path.exists('jre'):
    subprocess.run(['curl', '-sS', '--fail', '--max-time', '600', '-o', 'jre.tgz', jre], check=True)
    os.makedirs('jre')
    subprocess.run(['tar', '-xzf', 'jre.tgz', '-C', 'jre'], check=True)
assets = None
for v in json.load(open('versions.json'))['versions']:
    for s in v['subversions']:
        if s['id'] == version:
            assets = s['assets']
os.makedirs('home/game/assets/indexes', exist_ok=True)
index = 'home/game/assets/indexes/%s.json' % assets['id']
subprocess.run(['curl', '-sS', '--fail', '--max-time', '60', '-o', index, assets['url']], check=True)
# Without the title screen's textures 1.21 cannot draw its panorama and refuses to boot, which
# would read as a binding failure; sounds are skipped because nothing here listens.
for name, o in json.load(open(index))['objects'].items():
    if name.endswith('.ogg'):
        continue
    h = o['hash']
    dst = 'home/game/assets/objects/%s/%s' % (h[:2], h)
    if not os.path.exists(dst):
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        subprocess.run(['curl', '-sS', '--fail', '--max-time', '60', '-o', dst,
                        'https://resources.download.minecraft.net/%s/%s' % (h[:2], h)], check=True)
with open('args.txt', 'w') as f:
    cp = [a['name'] for a in launch['launchTypeData']['artifacts'] if a['type'] == 'CLASS_PATH']
    ext = [a['name'] for a in launch['launchTypeData']['artifacts'] if a['type'] == 'EXTERNAL_FILE']
    f.write(':'.join(os.path.abspath(os.path.join(multiver, n)) for n in cp) + '\n')
    f.write(','.join(cp) + '\n' + ','.join(ext) + '\n' + assets['id'] + '\n')
    f.write(launch['launchTypeData']['mainClass'] + '\n')
    f.write('\x1f'.join(x for x in launch['jre']['extraArguments']
                        if not x.startswith('-XX:FlightRecorder')) + '\n')
    f.write(os.path.abspath(os.path.join('jre', os.listdir('jre')[0], 'bin', 'java')) + '\n')
PY

CP=$(sed -n 1p args.txt); ICP=$(sed -n 2p args.txt); EXT=$(sed -n 3p args.txt)
ASSETS=$(sed -n 4p args.txt); MAIN=$(sed -n 5p args.txt); JAVA=$(sed -n 7p args.txt)

Xvfb "$DISPLAY_NUM" -screen 0 1280x720x24 >/dev/null 2>&1 &
XPID=$!
trap 'kill $XPID 2>/dev/null' EXIT
sleep 2

LOG="$WORK/client.log"
OLDIFS=$IFS; IFS=$(printf '\037'); set -- $(sed -n 6p args.txt); IFS=$OLDIFS
( cd home/.lunarclient/offline/multiver && env -i HOME="$WORK/home" PATH=/usr/bin:/bin \
    DISPLAY="$DISPLAY_NUM" LIBGL_ALWAYS_SOFTWARE=1 AMETHYST_WEBOSR_TRACE=1 \
    HTTPS_PROXY="$HTTPS_PROXY" https_proxy="$HTTPS_PROXY" \
    timeout "$SECONDS_UP" "$JAVA" "$@" $WEBOSRSIM_JAVA_OPTS -Duser.home="$WORK/home" -Xmx2048m \
    -Djava.library.path="$WORK/natives" -cp "$CP" "$MAIN" \
    --version "$VERSION" --launcherVersion 3.4.9 --installationId "$ID" --launchId "$PID" \
    --username webosrsim --uuid 00000000-0000-0000-0000-000000000001 --accessToken 0 \
    --userProperties '{}' --assetIndex "$ASSETS" --gameDir "$WORK/home/game" \
    --texturesDir "$WORK/home/.lunarclient/textures" --uiDir "$WORK/home/.lunarclient/ui" \
    --webosrDir "$WORK/natives" --workingDirectory . --classpathDir . --width 854 --height 480 \
    --ipcPort 28190 --ichorClassPath "$ICP" --ichorExternalFiles "$EXT" ) > "$LOG" 2>&1 || true

fail=0
grep -q '\[webosr\] createEngine' "$LOG" || { echo "FAIL the client never created the engine"; fail=1; }
grep -q 'took .* to initialize' "$LOG" || { echo "FAIL the client did not finish initialising"; fail=1; }
# The three Ultralight libraries failing to load is expected and tolerated by the client; what
# is fatal is the binding itself missing, or one of its methods missing, which names the class.
if grep -qE "no WebOSR-Binding in|UnsatisfiedLinkError: '[^']*com\.moonsworth\.webosr|Failed to boot Minecraft|Description: Initializing game" "$LOG"; then
    echo "FAIL the client crashed during start up"; fail=1
fi
echo "log: $LOG"
[ $fail -eq 0 ] && echo "ok: Lunar $VERSION ($MODULE) started on the replacement binding"
exit $fail
