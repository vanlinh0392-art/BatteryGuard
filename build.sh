#!/data/user/0/com.agy/files/usr/bin/bash
set -e

export JAVA_HOME="/data/user/0/com.agy/files/usr/lib/jvm/java-17-openjdk"
export PATH="$JAVA_HOME/bin:/data/user/0/com.agy/files/usr/bin:$PATH"
export HOME="/data/user/0/com.agy/files"
export GRADLE_USER_HOME="/data/user/0/com.agy/files/.gradle"
export ANDROID_HOME="/data/user/0/com.agy/files/android-sdk"
export ANDROID_USER_HOME="/data/user/0/com.agy/files/.android"
export TMPDIR="/data/user/0/com.agy/files/usr/tmp"

TASK="${1:-assembleDebug}"

if [ $# -gt 0 ]; then
    echo "🚀 Building BatteryGuard: ./gradlew $*"
    ./gradlew "$@"
else
    echo "🚀 Building BatteryGuard: ./gradlew $TASK"
    ./gradlew "$TASK"
fi

if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "✅ APK Debug: app/build/outputs/apk/debug/app-debug.apk"
fi
if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
    echo "✅ APK Release: app/build/outputs/apk/release/app-release.apk"
fi
