#!/bin/bash
#
# build-android.sh
# ---------------
# Build Kaca APK untuk Android.
#
# Persyaratan:
#   - Android Studio (recommended) ATAU
#   - Android SDK + JDK 17 di Mac/Linux
#
# Output: app/build/outputs/apk/debug/app-debug.apk
#
# ============================================================

set -e

cd "$(dirname "$0")"

# Cek Gradle wrapper
if [[ ! -x ./gradlew ]]; then
    echo "!! gradlew tidak ditemukan."
    echo "   Buka folder ini di Android Studio, biarkan Gradle sync,"
    echo "   lalu jalankan script ini lagi."
    echo "   Atau install Gradle manual: https://gradle.org"
    exit 1
fi

echo ">> Building release APK..."
./gradlew assembleRelease

APK="app/build/outputs/apk/release/app-release.apk"

if [[ ! -f "$APK" ]]; then
    echo "!! APK tidak ditemukan di $APK"
    exit 1
fi

echo ""
echo ">> Build selesai!"
echo "   APK: $APK"
echo ""
echo ">> Memindahkan ke folder dist..."
mkdir -p ../dist
mkdir -p ../dist
cp "$APK" ../dist/kaca-android.apk

echo "   Lokasi akhir: ../dist/kaca-android.apk"
echo ""
echo ">> Cara install ke HP:"
echo "   1. Transfer kaca-android.apk ke HP Android"
echo "   2. Di HP, buka file APK (boleh via File Manager)"
echo "   3. Aktifkan 'Install dari sumber tidak dikenal' jika diminta"
echo "   4. Install seperti biasa"
