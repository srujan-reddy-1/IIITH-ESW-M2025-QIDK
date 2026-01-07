#!/bin/bash
# Script to copy .env file to assets for development builds

echo "🔧 Development Build Helper"
echo "Copying Cerebras API key from .env to assets..."

# Check if .env file exists
if [ ! -f "CEREBRAS/.env" ]; then
    echo "❌ Error: CEREBRAS/.env file not found!"
    echo "Please create CEREBRAS/.env with your CEREBRAS_API_KEY"
    exit 1
fi

# Create assets directory if it doesn't exist
mkdir -p ChatApp/src/main/assets

# Copy the .env file to assets
cp CEREBRAS/.env ChatApp/src/main/assets/cerebras.env

echo "✅ API key copied to assets"
echo "📱 Building and installing app..."

# Build and install
./gradlew assembleDebug
if [ $? -eq 0 ]; then
    echo "✅ Build successful"
    adb install -r ChatApp/build/outputs/apk/debug/ChatApp-debug.apk
    if [ $? -eq 0 ]; then
        echo "✅ App installed successfully"
        echo "🚀 Launching app..."
        adb shell am start -n com.quicinc.chatapp/.MainActivity
    else
        echo "❌ Installation failed"
    fi
else
    echo "❌ Build failed"
fi