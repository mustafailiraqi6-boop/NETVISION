#!/bin/bash
set -e

LOG_FILE="/app/applet/build_output.log"
exec > >(tee -a "$LOG_FILE") 2>&1

echo "========================================="
echo "NETVISION - Android APK Build Started"
echo "Timestamp: $(date)"
echo "========================================="

# 1. Java 17 Check & Install
if ! command -v java &> /dev/null; then
    echo "[1/4] Installing OpenJDK 17 Headless..."
    apt-get update -y -qq
    apt-get install -y -qq openjdk-17-jdk-headless curl unzip wget > /dev/null 2>&1
fi
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
echo "Java Version: $(java -version 2>&1 | head -n 1)"

# 2. Gradle Check & Install
if [ ! -f /usr/local/bin/gradle ]; then
    echo "[2/4] Downloading Gradle 8.10.2..."
    mkdir -p /opt
    wget -q https://services.gradle.org/distributions/gradle-8.10.2-bin.zip -O /tmp/gradle.zip
    unzip -q -o /tmp/gradle.zip -d /opt
    ln -sf /opt/gradle-8.10.2/bin/gradle /usr/local/bin/gradle
    rm -f /tmp/gradle.zip
fi
echo "Gradle Version: $(/usr/local/bin/gradle -v | grep '^Gradle ')"

# 3. Android SDK Check & Install
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
export PATH=$PATH:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools

if [ ! -d /opt/android-sdk/platforms/android-35 ]; then
    echo "[3/4] Setting up Android SDK Platform 35 and Build Tools 35.0.0..."
    mkdir -p /opt/android-sdk/cmdline-tools
    if [ ! -d /opt/android-sdk/cmdline-tools/latest ]; then
        wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline.zip
        unzip -q -o /tmp/cmdline.zip -d /opt/android-sdk/cmdline-tools
        mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest
        rm -f /tmp/cmdline.zip
    fi
    yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null 2>&1 || true
    /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools" > /dev/null 2>&1
fi
echo "Android SDK Platforms: $(ls /opt/android-sdk/platforms)"

# 4. Run Gradle Build
echo "[4/4] Compiling and Assembling Debug APK..."
cd /app/applet
/usr/local/bin/gradle --no-daemon :app:assembleDebug --stacktrace

echo "========================================="
echo "Checking APK Output:"
if [ -f /app/applet/app/build/outputs/apk/debug/app-debug.apk ]; then
    ls -lh /app/applet/app/build/outputs/apk/debug/app-debug.apk
    echo "SUCCESS: APK file successfully generated!"
else
    echo "FAILED: APK file was not found!"
    exit 1
fi
echo "========================================="
