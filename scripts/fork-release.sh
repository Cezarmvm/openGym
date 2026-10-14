#!/usr/bin/env bash
# fork-release.sh — builds, checks and (optionally) installs the openGym (Cezar) release APKs.
# Runs in Git Bash on Windows, and on Linux/macOS. See specs/fork-watch/design.md §9.5.
#
#   scripts/fork-release.sh [--install] [--phone SERIAL] [--watch SERIAL] [--skip-tests] [--no-bump]
#
#   --install       adb install -r on the phone (and the watch, once the watch app exists)
#   --phone/--watch device serials; otherwise picked from `adb devices` (a watch reports
#                   "watch" in ro.build.characteristics)
#   --skip-tests    skip the fork's unit tests
#   --no-bump       rebuild the current forkBuild instead of the next one (no commit)
#
# Steps: tools → bump forkBuild → tests → mobile web build → Gradle release → signature check
# (phone and watch must share one certificate) → package check → dist-apk/ → commit → install.
set -euo pipefail

APP_ID='com.cezar.opengym'
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND="$ROOT/frontend"
ANDROID_DIR="$FRONTEND/android"
VERSION_FILE="$ROOT/fork/android/version.properties"
LAST_CODE_FILE="$ROOT/fork/android/last-version-code"
DIST="$ROOT/dist-apk"

INSTALL=0; PHONE_SERIAL=''; WATCH_SERIAL=''; SKIP_TESTS=0; BUMP=1
while [ $# -gt 0 ]; do
  case "$1" in
    --install) INSTALL=1 ;;
    --phone) PHONE_SERIAL="${2:?--phone needs a serial}"; shift ;;
    --watch) WATCH_SERIAL="${2:?--watch needs a serial}"; shift ;;
    --skip-tests) SKIP_TESTS=1 ;;
    --no-bump) BUMP=0 ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

STEP='start'
ORIGINAL_VERSION="$(cat "$VERSION_FILE")"
COMMITTED=0
on_exit() {
  local code=$?
  if [ $code -ne 0 ]; then
    # A failed build must not leave the bumped counter behind.
    if [ $COMMITTED -eq 0 ]; then printf '%s\n' "$ORIGINAL_VERSION" > "$VERSION_FILE"; fi
    echo "" >&2
    echo "✗ fork-release failed at step: $STEP (exit $code)" >&2
  fi
}
trap on_exit EXIT
step() { STEP="$1"; echo ""; echo "▶ $1"; }
die() { echo "✗ $*" >&2; exit 1; }

# ── OS and tools ────────────────────────────────────────────────────────────────────────────────
step 'tools'
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) OS=windows; EXE='.exe'; BAT='.bat' ;;
  Darwin) OS=macos; EXE=''; BAT='' ;;
  *) OS=linux; EXE=''; BAT='' ;;
esac
echo "OS: $OS"

if [ -z "${ANDROID_HOME:-}" ]; then
  case "$OS" in
    windows) ANDROID_HOME="$(cygpath -u "$LOCALAPPDATA")/Android/Sdk" ;;
    macos) ANDROID_HOME="$HOME/Library/Android/sdk" ;;
    linux) ANDROID_HOME="$HOME/Android/Sdk" ;;
  esac
fi
[ -d "$ANDROID_HOME" ] || die "Android SDK not found (set ANDROID_HOME)"
export ANDROID_HOME
if [ -z "${JAVA_HOME:-}" ] && [ "$OS" = windows ]; then
  JAVA_HOME="$(ls -d "/c/Program Files/Microsoft/jdk-21"* 2>/dev/null | tail -1 || true)"
fi
[ -n "${JAVA_HOME:-}" ] && [ -d "$JAVA_HOME" ] || die "JDK 21 not found (set JAVA_HOME)"
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"

BT="$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1)"
[ -n "$BT" ] || die "no Android build-tools installed"
APKSIGNER="$BT/apksigner$BAT"
AAPT2="$BT/aapt2$EXE"
ADB="$ANDROID_HOME/platform-tools/adb$EXE"
for t in "$APKSIGNER" "$AAPT2" "$ADB"; do [ -e "$t" ] || die "missing tool: $t"; done
command -v node >/dev/null || die "node not found"
command -v npx >/dev/null || die "npx not found"
java -version 2>&1 | head -1

KS_PROPS="${OPENGYM_KEYSTORE_PROPERTIES:-$ROOT/fork/keystore.properties}"
[ -f "$KS_PROPS" ] || die "keystore.properties not found ($KS_PROPS) — see docs/FORK_INSTALL.md (Keystore)"

HAS_WATCH=0
[ -d "$ANDROID_DIR/wear" ] && HAS_WATCH=1

# ── Version ─────────────────────────────────────────────────────────────────────────────────────
step 'version'
FORK_BUILD="$(sed -n 's/^forkBuild=\([0-9][0-9]*\).*/\1/p' "$VERSION_FILE")"
[ -n "$FORK_BUILD" ] || die "forkBuild missing in $VERSION_FILE"
if [ $BUMP -eq 1 ]; then
  FORK_BUILD=$((FORK_BUILD + 1))
  printf 'forkBuild=%s\n' "$FORK_BUILD" > "$VERSION_FILE"
fi
echo "forkBuild: $FORK_BUILD"

# ── Tests ───────────────────────────────────────────────────────────────────────────────────────
if [ $SKIP_TESTS -eq 0 ]; then
  step 'tests (fork)'
  # the fork's own tests and upstream's watch link (the part the fork builds on)
  (cd "$FRONTEND" && npx vitest run src/fork src/lib/import-csv.pt-br.test.js src/lib/watch-sync src/lib/watch-settings src/views/Settings.watch.test.jsx src/views/Workout.watch.test.jsx)
  step 'tests (Kotlin)'
  (cd "$ANDROID_DIR" && ./gradlew --no-daemon -q :watch-core:test :fork-wearbridge:testDebugUnitTest)
fi

# ── Mobile web build (what `npm run build:mobile` does; its VAR=value syntax breaks in cmd) ──────
step 'mobile web build'
(
  cd "$FRONTEND"
  VITE_MOBILE=1 npx vite build
  node ../scripts/catalogue/stage-media.mjs dist
  npx cap sync android
  node scripts/check-mobile-bundle.mjs
)

# ── Gradle ──────────────────────────────────────────────────────────────────────────────────────
step 'gradle release build'
TASKS=(:app:forkVersionCheck :app:assembleSideloadRelease)   # upstream builds sideload/play flavors; the fork ships the sideload APK
[ $HAS_WATCH -eq 1 ] && TASKS+=(:wear:assembleRelease)
(cd "$ANDROID_DIR" && ./gradlew --no-daemon "${TASKS[@]}")
PHONE_APK="$ANDROID_DIR/app/build/outputs/apk/sideload/release/app-sideload-release.apk"
WATCH_APK="$ANDROID_DIR/wear/build/outputs/apk/release/wear-release.apk"
[ -f "$PHONE_APK" ] || die "phone APK not produced: $PHONE_APK"
[ $HAS_WATCH -eq 0 ] || [ -f "$WATCH_APK" ] || die "watch APK not produced: $WATCH_APK"

# ── Signature ───────────────────────────────────────────────────────────────────────────────────
step 'signature check'
cert_of() { "$APKSIGNER" verify --print-certs "$1" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1 | tr -d '\r'; }
PHONE_CERT="$(cert_of "$PHONE_APK")"
[ -n "$PHONE_CERT" ] || die "phone APK is not signed"
echo "phone cert SHA-256: $PHONE_CERT"
if [ $HAS_WATCH -eq 1 ]; then
  WATCH_CERT="$(cert_of "$WATCH_APK")"
  echo "watch cert SHA-256: $WATCH_CERT"
  [ "$PHONE_CERT" = "$WATCH_CERT" ] || die "phone and watch APKs are signed with DIFFERENT certificates — the Data Layer would not connect them"
fi

# ── Package ─────────────────────────────────────────────────────────────────────────────────────
step 'package check'
badging() { "$AAPT2" dump badging "$1" | sed -n '1p' | tr -d '\r'; }
field() { printf '%s' "$1" | sed -n "s/.* $2='\([^']*\)'.*/\1/p"; }
PHONE_PKG="$(badging "$PHONE_APK")"
echo "$PHONE_PKG"
[ "$(printf '%s' "$PHONE_PKG" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")" = "$APP_ID" ] || die "phone applicationId is not $APP_ID"
VERSION_CODE="$(field "$PHONE_PKG" versionCode)"
VERSION_NAME="$(field "$PHONE_PKG" versionName)"
if [ $HAS_WATCH -eq 1 ]; then
  WATCH_PKG="$(badging "$WATCH_APK")"
  echo "$WATCH_PKG"
  [ "$(printf '%s' "$WATCH_PKG" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")" = "$APP_ID" ] || die "watch applicationId is not $APP_ID"
  [ "$(field "$WATCH_PKG" versionCode)" = "$VERSION_CODE" ] || die "phone and watch versionCode differ"
fi

# ── dist-apk/ ───────────────────────────────────────────────────────────────────────────────────
step 'copy to dist-apk'
mkdir -p "$DIST"
PHONE_OUT="$DIST/openGym-Cezar-phone-$VERSION_NAME-$VERSION_CODE.apk"
cp "$PHONE_APK" "$PHONE_OUT"
(cd "$DIST" && sha256sum "$(basename "$PHONE_OUT")" > "$(basename "$PHONE_OUT").sha256")
echo "→ $PHONE_OUT"
if [ $HAS_WATCH -eq 1 ]; then
  WATCH_OUT="$DIST/openGym-Cezar-watch-$VERSION_NAME-$VERSION_CODE.apk"
  cp "$WATCH_APK" "$WATCH_OUT"
  (cd "$DIST" && sha256sum "$(basename "$WATCH_OUT")" > "$(basename "$WATCH_OUT").sha256")
  echo "→ $WATCH_OUT"
fi

# ── Record the release ──────────────────────────────────────────────────────────────────────────
if [ $BUMP -eq 1 ]; then
  step 'commit version'
  printf '%s\n' "$VERSION_CODE" > "$LAST_CODE_FILE"
  git -C "$ROOT" add "$VERSION_FILE" "$LAST_CODE_FILE"
  git -C "$ROOT" commit -q -m "chore(fork): release $VERSION_NAME ($VERSION_CODE)"
  COMMITTED=1
  echo "committed chore(fork): release $VERSION_NAME"
fi

# ── Install ─────────────────────────────────────────────────────────────────────────────────────
if [ $INSTALL -eq 1 ]; then
  step 'install'
  mapfile -t SERIALS < <("$ADB" devices | tr -d '\r' | awk 'NR>1 && $2=="device" {print $1}')
  for s in "${SERIALS[@]:-}"; do
    [ -n "$s" ] || continue
    kind="$("$ADB" -s "$s" shell getprop ro.build.characteristics 2>/dev/null | tr -d '\r')"
    if [[ "$kind" == *watch* ]]; then [ -n "$WATCH_SERIAL" ] || WATCH_SERIAL="$s"
    else [ -n "$PHONE_SERIAL" ] || PHONE_SERIAL="$s"; fi
  done
  [ -n "$PHONE_SERIAL" ] || die "no phone found by adb (connect it with USB debugging on, or pass --phone)"
  echo "phone: $PHONE_SERIAL"
  "$ADB" -s "$PHONE_SERIAL" install -r "$PHONE_OUT"
  if [ $HAS_WATCH -eq 1 ]; then
    [ -n "$WATCH_SERIAL" ] || die "no watch found by adb (adb pair/connect it, or pass --watch)"
    echo "watch: $WATCH_SERIAL"
    "$ADB" -s "$WATCH_SERIAL" install -r "$WATCH_OUT"
  fi
fi

STEP='done'
echo ""
echo "✓ $VERSION_NAME ($VERSION_CODE) ready in dist-apk/"
