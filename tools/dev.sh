#!/usr/bin/env bash
# Spatial Ruler developer helper (Git Bash). Every device command is pinned to emulator-5556
# (AVD PICO_6.1_Ruler) and refuses to run when that serial is another AVD.
#
#   tools/dev.sh build                 assembleDebug            -> .scratch/build.log
#   tools/dev.sh test                  testDebugUnitTest        -> .scratch/unit.log
#   tools/dev.sh fg                    which app is in front on the emulator
#   tools/dev.sh install | launch | stop | relaunch
#   tools/dev.sh cmd NAME [NUMBERS] [TEXT]     debug broadcast (see app/src/debug/.../DebugCommands.kt)
#   tools/dev.sh log [REGEX] [LINES] [WIDTH]   logcat, tag SpatialRuler
#   tools/dev.sh crash                 last crash lines of the app
#   tools/dev.sh gates                 the plugin's machine gates (needs the emulator)
DEV=emulator-5556
AVD=PICO_6.1_Ruler
PKG=io.embodit.spatialruler
TAG=SpatialRuler
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export ANDROID_SERIAL=$DEV ADB_SERIAL=$DEV PICO_CLI_DEVICE=$DEV
export JAVA_HOME='C:\Users\Admin\.pico\primer-cli\jdk\jdk-21.0.12.1+1'
export PICO_HOME='C:\Users\Admin\AppData\Local\PICO\sdk'
mkdir -p "$ROOT/.scratch"

check() {
  avd="$(adb -s $DEV emu avd name 2>/dev/null | head -1 | tr -d '\r')"
  if [ "$avd" != "$AVD" ]; then echo "WRONG AVD on $DEV: '$avd' (expected $AVD)" >&2; exit 2; fi
}

case "${1:-}" in
  build)
    cd "$ROOT" && ./gradlew.bat assembleDebug --no-daemon --console=plain > .scratch/build.log 2>&1
    echo "exit=$?"; grep -E '^e: |^w: |error:|BUILD|FAILED' .scratch/build.log | head -40 ;;
  test)
    cd "$ROOT" && ./gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain > .scratch/unit.log 2>&1
    echo "exit=$?"; grep -E '^e: |FAILED|BUILD|tests completed' .scratch/unit.log | head -40 ;;
  fg)
    check; adb -s $DEV shell "dumpsys activity activities | grep -E 'topResumedActivity' | head -3" | tr -d '\r' ;;
  install)
    check; pico-cli app install "$ROOT/app/build/outputs/apk/debug/app-debug.apk" --device $DEV 2>&1 | tail -2 ;;
  launch)
    check; adb -s $DEV logcat -c; pico-cli app launch $PKG --device $DEV 2>&1 | tail -1 ;;
  stop)
    check; adb -s $DEV shell am force-stop $PKG; echo stopped ;;
  relaunch)
    check; adb -s $DEV shell am force-stop $PKG; sleep 1; adb -s $DEV logcat -c; pico-cli app launch $PKG --device $DEV 2>&1 | tail -1 ;;
  cmd)
    check
    extra=""
    [ -n "${3:-}" ] && extra="$extra --es a '$3'"
    [ -n "${4:-}" ] && extra="$extra --es s '$4'"
    adb -s $DEV shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd ${2:?cmd NAME} $extra" | tail -1 | tr -d '\r' ;;
  log)
    check; adb -s $DEV logcat -d -v time -s $TAG:V 2>/dev/null | grep -E -- "${2:-.}" | tail -n "${3:-400}" | cut -c1-"${4:-900}" ;;
  crash)
    check; adb -s $DEV logcat -d -v time 2>/dev/null | grep -E "AndroidRuntime|FATAL EXCEPTION|Process: $PKG" | tail -n 40 | cut -c1-400 ;;
  gates)
    check
    SKILL="$(ls -d "$HOME"/.claude/plugins/cache/pico-xr/pico-spatial-agentic-tools/*/skills/spatial-design-to-app 2>/dev/null | sort -V | tail -1)"
    [ -d "$SKILL" ] || { echo "plugin skill not found" >&2; exit 2; }
    export PATH="$ROOT/tools/shims:$PATH"
    bash "$SKILL/scripts/validate_workflow_and_build.sh" "$ROOT" --input-mode intent_only --generation-mode new_project \
      --design-color labelPrimaryLight=#FFFFFF --design-color error=#FF5F52 --design-color alert=#FF8A1F \
      --design-color passable=#66E08A --design-color interaction=#FFC72C \
      --design-color tapeYellow=#FFC72C --design-color tapeInk=#16181C --design-color bladeEdge=#0E0F12 \
      --design-color meterRed=#E23B2E --design-color steel=#C9CED6 --design-color steelShade=#8A9099 \
      --design-color plateInk=#F0121417 --design-color bumper=#1D1F24 --design-color onInkFill=#24FFFFFF \
      --design-color onInkSecondary=#A8FFFFFF --design-color staleGrey=#9A9A96 ;;
  *) echo "usage: dev.sh build|test|fg|install|launch|stop|relaunch|cmd|log|crash|gates" ;;
esac
