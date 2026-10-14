#!/usr/bin/env bash
# Builds and signs both Android releases on the release machine:
#   openGym-<version>.apk   the sideloaded APK (GitHub, opengym.ch): updates itself
#   openGym-<version>.aab   the Google Play bundle: Play updates it
# Each from its own phone build (owner, v1.4.2): the APK carries the box-free 240 px animations
# built in (quality/240) and has no packs; the Play bundle carries 180 px built in, like the App
# Store app, and every sharper quality whose folder exists as an on-demand Play asset pack
# (scripts/android/make-asset-packs.mjs), which Google Play delivers when someone picks it. These
# animations go into the packages and nowhere else (Gym visual licence, Part B), which is why this
# runs here and not in CI; the pack modules are taken out of the project again at the end. Same
# key for both: a phone can move from the APK to Google Play and keep its data, as long as Play App
# Signing uses this key too (docs/APP_STORES.md).
#
# Usage: scripts/android/build-release.sh <keystore> <password-file> <alias-file> [out-dir]
#   QUALITY_ROOT  the private quality folders, default /mnt/backup-hdd/opengym-media/quality
#   APK_PX        the APK's built-in size, default 240;  PLAY_PX  the bundle's, default 180
#   NO_PACKS=1    a Play bundle without asset packs
set -euo pipefail

KS=${1:?keystore}; PASS=${2:?file with the keystore password}; ALIAS=$(cat "${3:?file with the key alias}")
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=${4:-$PWD}
QUALITY_ROOT=${QUALITY_ROOT:-/mnt/backup-hdd/opengym-media/quality}
APK_PX=${APK_PX:-240}
PLAY_PX=${PLAY_PX:-180}
export VITE_CLIP_EXT=webp
: "${ANDROID_HOME:?set ANDROID_HOME}"
BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
for px in "$APK_PX" "$PLAY_PX"; do
  [ -d "$QUALITY_ROOT/$px" ] || { echo "no $QUALITY_ROOT/$px" >&2; exit 1; }
done

cd "$ROOT/frontend"
VER=$(node -p "require('./package.json').version")
GRADLE_VER=$(sed -n 's/.*versionName "\(.*\)"/\1/p' android/app/build.gradle)
[ "$VER" = "$GRADLE_VER" ] || { echo "package.json says $VER, build.gradle says $GRADLE_VER" >&2; exit 1; }

# The phone build with one size of animations built in: the files and the size the page assumes.
phone_build() {
  APP_MEDIA_DIR="$QUALITY_ROOT/$1" VITE_BUILTIN_CLIP_PX="$1" npm run build:mobile
  local n
  n=$(find android/app/src/main/assets/public/exercise-media/clip -name '*.webp' | wc -l)
  [ "$n" -gt 6000 ] || { echo "the $1 px phone build has only $n animations" >&2; exit 1; }
}

# 1. The APK: 240 px built in, no packs.
node "$ROOT/scripts/android/make-asset-packs.mjs" --clean
phone_build "$APK_PX"
(cd android && ./gradlew --no-daemon clean assembleSideloadRelease)
APK_BUILT="$OUT/.openGym-$VER-sideload-unsigned.apk"   # beside the outputs: the root disk has no room
cp android/app/build/outputs/apk/sideload/release/app-sideload-release-unsigned.apk "$APK_BUILT"

# 2. The Play bundle: 180 px built in, the sharper ones as asset packs.
phone_build "$PLAY_PX"
if [ -z "${NO_PACKS:-}" ]; then
  trap 'node "$ROOT/scripts/android/make-asset-packs.mjs" --clean; rm -f "$APK_BUILT"' EXIT
  node "$ROOT/scripts/android/make-asset-packs.mjs" --builtin "$PLAY_PX" --src-root "$QUALITY_ROOT"
else
  trap 'rm -f "$APK_BUILT"' EXIT
fi
(cd android && ./gradlew --no-daemon bundlePlayRelease)

APK_IN=$APK_BUILT
AAB_IN=android/app/build/outputs/bundle/playRelease/app-play-release.aab

"$BT/zipalign" -f -p 4 "$APK_IN" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass "file:$PASS" --ks-key-alias "$ALIAS" \
  --out "$OUT/openGym-$VER.apk" "$OUT/aligned.apk"
rm -f "$OUT/aligned.apk" "$OUT/openGym-$VER.apk.idsig"
"$BT/apksigner" verify --print-certs "$OUT/openGym-$VER.apk" | grep -i "SHA-256"
(cd "$OUT" && sha256sum "openGym-$VER.apk" > "openGym-$VER.apk.sha256")

# An app bundle is signed like a jar; Play checks it with the upload key and re-signs the APKs
# it builds from it with the app signing key.
cp "$AAB_IN" "$OUT/openGym-$VER.aab"
jarsigner -keystore "$KS" -storepass:file "$PASS" -keypass:file "$PASS" \
  -sigalg SHA256withRSA -digestalg SHA-256 "$OUT/openGym-$VER.aab" "$ALIAS" >/dev/null
jarsigner -verify "$OUT/openGym-$VER.aab" | grep -q "jar verified" || { echo "the bundle did not verify" >&2; exit 1; }

ls -la "$OUT/openGym-$VER.apk" "$OUT/openGym-$VER.aab"
# Google Play's limits: 500 MB for the base module, 1.5 GB per asset pack (the packs are split
# below that by make-asset-packs.mjs). The base is what the bundle holds outside the packs.
BASE=$(unzip -l "$OUT/openGym-$VER.aab" | awk '$4 != "" && $4 !~ /^anim_/ {s+=$1} END {print s}')
echo "base module: $((BASE / 1024 / 1024)) MB (Play allows 500 MB)"
[ "$BASE" -lt $((500 * 1024 * 1024)) ] || { echo "the base module is over Play's 500 MB" >&2; exit 1; }
