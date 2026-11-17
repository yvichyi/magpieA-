#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk="${ANDROID_SDK_ROOT:-/workspace/toolchains/android-sdk}"
bt="$sdk/build-tools/35.0.0"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}"
main_dir="${MAGPIE_BUILD_DIR:-android/build}"
work_dir="$main_dir/instrumentation"
mkdir -p "$work_dir/classes" "$work_dir/dex"
"${java_bin}javac" -encoding UTF-8 -source 17 -target 17 -cp "$sdk/platforms/android-35/android.jar:$main_dir/classes" -d "$work_dir/classes" android/tests/NativeHeatmapAcceptance.java android/tests/NativeEmbeddedAcceptance.java android/tests/NativeNavigationAcceptance.java android/tests/NativeResponsiveAcceptance.java android/tests/NativeRefineAcceptance.java android/tests/NativeDailyAcceptance.java android/tests/NativeAcceptance.java android/tests/NativeSetupAcceptance.java android/tests/PluginRuntimeProbe.java android/tests/NavigationRaceProbe.java
mapfile -t files < <(find "$work_dir/classes" -name '*.class' -type f)
"$bt/d8" --lib "$sdk/platforms/android-35/android.jar" --classpath "$main_dir/classes" --min-api 26 --output "$work_dir/dex" "${files[@]}"
"$bt/aapt2" link --manifest android/tests/instrumentation-manifest.xml -I "$sdk/platforms/android-35/android.jar" -o "$work_dir/test.apk"
(cd "$work_dir/dex" && zip -q ../test.apk classes.dex)
"$bt/zipalign" -f 4 "$work_dir/test.apk" "$work_dir/aligned.apk"
export MAGPIE_STOREPASS="${MAGPIE_STOREPASS:-android}"
export MAGPIE_KEYPASS="${MAGPIE_KEYPASS:-android}"
"$bt/apksigner" sign --ks "${MAGPIE_KEYSTORE:-android/build/magpie-development.jks}" --ks-key-alias "${MAGPIE_KEY_ALIAS:-magpie}" --ks-pass env:MAGPIE_STOREPASS --key-pass env:MAGPIE_KEYPASS --out "$work_dir/native-tests.apk" "$work_dir/aligned.apk"
"$bt/apksigner" verify "$work_dir/native-tests.apk"
