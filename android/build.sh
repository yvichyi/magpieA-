#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_dir"
source android/version.properties
core_source_dir="${CORE_SOURCE_DIR:-$project_dir/magpie-core}"
android_sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/workspace/toolchains/android-sdk}}"
build_tools="$android_sdk/build-tools/35.0.0"
android_jar="$android_sdk/platforms/android-35/android.jar"
go_bin="${GO_BIN:-go}"
ndk_dir="${ANDROID_NDK_HOME:-$android_sdk/ndk/27.2.12479018}"
ndk_host="${NDK_HOST_TAG:-linux-x86_64}"
arm_cc="${ARM64_CC:-$ndk_dir/toolchains/llvm/prebuilt/$ndk_host/bin/aarch64-linux-android26-clang}"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}"
export MAGPIE_STOREPASS="${MAGPIE_STOREPASS:-android}"
export MAGPIE_KEYPASS="${MAGPIE_KEYPASS:-android}"
build_dir="${MAGPIE_BUILD_DIR:-$project_dir/android/build}"
core_lib_dir="${CORE_LIB_DIR:-$project_dir/android/build/lib}"
abi="${TARGET_ABI:-arm64-v8a}"
case "$abi" in
  arm64-v8a) go_arch=arm64; compiler="$arm_cc"; label=arm64 ;;
  x86_64) go_arch=amd64; compiler="${X86_64_CC:-$ndk_dir/toolchains/llvm/prebuilt/$ndk_host/bin/x86_64-linux-android26-clang}"; label=x86_64 ;;
  *) echo 'TARGET_ABI must be arm64-v8a or x86_64' >&2; exit 1 ;;
esac
mkdir -p "$build_dir/classes" "$build_dir/gen" "$build_dir/dex" "$build_dir/stage" "$project_dir/dist" "$core_lib_dir/$abi"
find "$build_dir/classes" "$build_dir/gen" "$build_dir/dex" "$build_dir/stage" -mindepth 1 -delete
mkdir -p "$build_dir/stage/lib/$abi"
if [[ "${USE_PREBUILT_CORE:-0}" != 1 ]]; then
  bash android/fetch-core.sh "$core_source_dir"
  if [[ ! -x "$compiler" ]]; then echo 'Android NDK compiler missing; set ANDROID_NDK_HOME.' >&2; exit 1; fi
  CC="$compiler" CGO_ENABLED=1 CGO_LDFLAGS="${CGO_LDFLAGS:-} -Wl,-z,max-page-size=16384" GOOS=android GOARCH="$go_arch" "$go_bin" -C "$core_source_dir" build -tags nogui -trimpath -ldflags="-s -w -X main.version=$CORE_VERSION" -o "$core_lib_dir/$abi/libmagpie.so" .
fi
if [[ ! -f "$core_lib_dir/$abi/libmagpie.so" ]]; then echo 'Requested prebuilt core is missing.' >&2; exit 1; fi
python3 android/fetch-bun.py "$abi" "$build_dir/runtime/$abi/libmagpie-bun.so"
cp LICENSE android/assets/LICENSE
cp THIRD_PARTY_NOTICES android/assets/THIRD_PARTY_NOTICES
"$build_tools/aapt2" compile --dir android/res -o "$build_dir/resources.zip"
"$build_tools/aapt2" link -o "$build_dir/resources.apk" --manifest android/AndroidManifest.xml --version-code "$ANDROID_VERSION_CODE" --version-name "$ANDROID_VERSION" -I "$android_jar" --java "$build_dir/gen" -A android/assets -0 ttf "$build_dir/resources.zip"
mkdir -p "$build_dir/gen/ai/magpie/android"
cat > "$build_dir/gen/ai/magpie/android/BuildInfo.java" <<EOF
package ai.magpie.android;
final class BuildInfo {
    static final String APP_VERSION="$ANDROID_VERSION", CORE_VERSION="$CORE_VERSION";
}
EOF
mapfile -t java_sources < <(find android/src "$build_dir/gen" -name '*.java' -type f | sort)
"${java_bin}javac" -encoding UTF-8 -source 17 -target 17 -classpath "$android_jar" -d "$build_dir/classes" "${java_sources[@]}"
python3 android/tests/android_api_test.py "$build_dir/classes" "$android_sdk"
mapfile -t class_files < <(find "$build_dir/classes" -name '*.class' -type f | sort)
"$build_tools/d8" --lib "$android_jar" --min-api 28 --output "$build_dir/dex" "${class_files[@]}"
cp "$build_dir/resources.apk" "$build_dir/unsigned.apk"
cp "$build_dir/dex/classes.dex" "$build_dir/stage/classes.dex"
cp "$core_lib_dir/$abi/libmagpie.so" "$build_dir/stage/lib/$abi/"
cp "$build_dir/runtime/$abi/libmagpie-bun.so" "$build_dir/stage/lib/$abi/"
(cd "$build_dir/stage" && zip -q -r "$build_dir/unsigned.apk" classes.dex lib)
"$build_tools/zipalign" -P 16 -f 4 "$build_dir/unsigned.apk" "$build_dir/aligned.apk"
keystore="${MAGPIE_KEYSTORE:-$project_dir/android/build/magpie-development.jks}"
key_alias="${MAGPIE_KEY_ALIAS:-magpie}"
if [[ ! -f "$keystore" ]]; then
  "${java_bin}keytool" -genkeypair -keystore "$keystore" -storepass:env MAGPIE_STOREPASS -keypass:env MAGPIE_KEYPASS -alias "$key_alias" -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Magpie Android Development' > "$build_dir/signing.log" 2>&1
fi
output_dir="${MAGPIE_OUTPUT_DIR:-$project_dir/dist}"
mkdir -p "$output_dir"
output="$output_dir/Magpie-Android-$ANDROID_VERSION-$label.apk"
"$build_tools/apksigner" sign --ks "$keystore" --ks-key-alias "$key_alias" --ks-pass "env:MAGPIE_STOREPASS" --key-pass "env:MAGPIE_KEYPASS" --out "$output" "$build_dir/aligned.apk"
"$build_tools/apksigner" verify --verbose "$output"
"$build_tools/aapt2" dump badging "$output" | sed -n '1,6p'
sha256sum "$output"
