#!/bin/bash
# 手作業ビルド (Gradle を使わない最小構成)
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="$HOME/jdk17"
SDK="$HOME/android-sdk"
BT="$SDK/build-tools/34.0.0"
AJAR="$SDK/platforms/android-29/android.jar"
PATH="$JAVA_HOME/bin:$PATH"

rm -rf build
mkdir -p build/gen build/classes build/dex

echo "[1/6] aapt2 compile"
"$BT/aapt2" compile --dir res -o build/res.zip

echo "[2/6] aapt2 link"
"$BT/aapt2" link -o build/app-unsigned.apk -I "$AJAR" \
    --manifest AndroidManifest.xml --java build/gen build/res.zip

echo "[3/6] javac"
find src build/gen -name '*.java' > build/sources.txt
javac -encoding UTF-8 -cp "$AJAR" -d build/classes @build/sources.txt

echo "[4/6] d8"
"$BT/d8" --min-api 24 --output build/dex $(find build/classes -name '*.class')

echo "[5/6] package + zipalign"
cp build/app-unsigned.apk build/app-with-dex.apk
(cd build/dex && zip -q ../app-with-dex.apk classes.dex)
"$BT/zipalign" -f 4 build/app-with-dex.apk build/app-aligned.apk

echo "[6/6] sign"
if [ ! -f debug.keystore ]; then
    keytool -genkeypair -keystore debug.keystore -storepass android \
        -keypass android -alias debug -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=KonomiTV TV, O=tatsuya0902" > /dev/null 2>&1
fi
"$BT/apksigner" sign --ks debug.keystore --ks-pass pass:android \
    --key-pass pass:android --out konomitv-tv.apk build/app-aligned.apk

echo "DONE: konomitv-tv.apk"
ls -la konomitv-tv.apk
