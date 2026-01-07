#!/bin/bash

# ChatApp Automated Deployment Script
# This script handles SELinux configuration, builds, and deploys the app

set -e  # Exit on any error

echo "🚀 Starting ChatApp deployment..."

# Function to check if device is connected
check_device() {
    if ! adb devices | grep -q "device$"; then
        echo "❌ No Android device connected. Please connect your device and try again."
        exit 1
    fi
    echo "✅ Device connected: $(adb devices | grep device | cut -f1)"
}

# Function to configure SELinux
configure_selinux() {
    echo "🔧 Checking SELinux status..."
    current_status=$(adb shell getenforce 2>/dev/null || echo "Unknown")
    echo "   Current SELinux status: $current_status"
    
    if [ "$current_status" != "Permissive" ]; then
        echo "🔓 Setting SELinux to permissive mode..."
        
        # Try with adb root first
        echo "   Enabling ADB root access..."
        adb root >/dev/null 2>&1 || true
        sleep 2
        adb wait-for-device
        
        # Set SELinux to permissive
        if adb shell setenforce 0 2>/dev/null; then
            echo "✅ SELinux set to permissive mode"
        else
            echo "⚠️  Warning: Could not set SELinux to permissive. App may have permission issues."
        fi
        
        # Verify the change
        new_status=$(adb shell getenforce 2>/dev/null || echo "Unknown")
        echo "   New SELinux status: $new_status"
    else
        echo "✅ SELinux already in permissive mode"
    fi
}

# Function to set file permissions
set_file_permissions() {
    echo "📁 Setting genie bundle file permissions..."
    if adb shell test -d /data/local/tmp/genie_bundle 2>/dev/null; then
        adb shell chmod 755 /data/local/tmp/genie_bundle/* 2>/dev/null || true
        echo "✅ File permissions set"
    else
        echo "⚠️  Genie bundle directory not found - this is OK if using a different model path"
    fi
}

# Function to build and install app
build_and_install() {
    echo "🔨 Building and installing ChatApp..."
    
    # Clean build
    echo "   Cleaning previous build..."
    ./gradlew :ChatApp:clean >/dev/null
    
    # Build and install
    echo "   Building and installing debug APK..."
    if ./gradlew :ChatApp:installDebug --quiet; then
        echo "✅ App built and installed successfully"
    else
        echo "❌ Build or installation failed"
        exit 1
    fi
}

# Function to launch app
launch_app() {
    echo "🚀 Launching ChatApp..."
    
    # Force stop any running instance
    adb shell am force-stop com.quicinc.chatapp >/dev/null 2>&1 || true
    sleep 1
    
    # Launch the app
    if adb shell am start -n com.quicinc.chatapp/.MainActivity >/dev/null; then
        echo "✅ ChatApp launched successfully"
    else
        echo "❌ Failed to launch app"
        exit 1
    fi
}

# Function to show logs
show_logs() {
    echo "📋 Showing recent app logs (Ctrl+C to stop)..."
    echo "----------------------------------------"
    adb logcat -s ChatApp:* GenieWrapper:* -v time 2>/dev/null || true
}

# Main execution
main() {
    echo "ChatApp Deployment Script v1.0"
    echo "================================"
    
    check_device
    configure_selinux
    set_file_permissions
    build_and_install
    launch_app
    
    echo ""
    echo "🎉 Deployment completed successfully!"
    echo ""
    echo "Options:"
    echo "  - Press Enter to exit"
    echo "  - Type 'logs' to view app logs"
    echo "  - Type 'status' to check current setup"
    
    read -p "Choice: " choice
    
    case "$choice" in
        "logs")
            show_logs
            ;;
        "status")
            echo "Device: $(adb devices | grep device | cut -f1)"
            echo "SELinux: $(adb shell getenforce 2>/dev/null || echo 'Unknown')"
            echo "App installed: $(adb shell pm list packages | grep com.quicinc.chatapp >/dev/null && echo 'Yes' || echo 'No')"
            ;;
        *)
            echo "👋 Goodbye!"
            ;;
    esac
}

# Run the script
main