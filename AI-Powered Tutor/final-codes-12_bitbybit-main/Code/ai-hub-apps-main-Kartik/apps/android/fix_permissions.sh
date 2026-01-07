#!/bin/bash
# Fix SELinux and permissions for BitbyBit app

echo "🔧 Fixing SELinux and permissions for BitbyBit..."

# Disable SELinux enforcement
echo "📱 Disabling SELinux enforcement..."
adb shell su 0 setenforce 0

# Set proper permissions for genie_bundle
echo "🔐 Setting permissions for genie_bundle..."
adb shell su 0 chmod -R 777 /data/local/tmp/genie_bundle

echo "✅ Done! You can now use BitbyBit."
echo ""
echo "Note: You may need to run this script again after device reboot."
