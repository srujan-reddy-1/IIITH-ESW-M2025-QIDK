# ChatApp Automated Deployment

This document explains the automated deployment solutions to handle SELinux configuration and app deployment without manual intervention.

## 🚀 Quick Deployment Options

### Option 1: One-Command Deployment Script
```bash
./deploy_app.sh
```
**What it does:**
- ✅ Automatically checks and configures SELinux to permissive mode
- ✅ Sets proper file permissions for genie bundle
- ✅ Cleans, builds, and installs the debug APK
- ✅ Launches the app automatically
- ✅ Provides options to view logs or check status

### Option 2: Enhanced Gradle Installation
```bash
./gradlew :ChatApp:installDebug
```
**What it does:**
- ✅ Automatically runs device setup before installation
- ✅ Configures SELinux and permissions as part of the build process
- ✅ No separate scripts needed

### Option 3: Super Quick Deploy
```bash
./quick_deploy.sh
```
**What it does:**
- ✅ Just runs the main deployment script
- ✅ Minimal typing required

## 🔧 How It Solves the Permission Problem

### The Issue
- **SELinux resets** to "Enforcing" mode after device reboots
- **Permissions expire** or get reset over time
- **Manual intervention** was required every time: `adb shell su -c "setenforce 0"`

### The Solution
The automated scripts now handle this by:

1. **Checking SELinux status** before each deployment
2. **Automatically enabling ADB root** access
3. **Setting SELinux to permissive** mode if needed
4. **Configuring file permissions** for the genie bundle
5. **Proceeding with build and installation**

### Technical Implementation

#### Deployment Script (`deploy_app.sh`)
```bash
# Check current SELinux status
current_status=$(adb shell getenforce)

# If not permissive, fix it automatically
if [ "$current_status" != "Permissive" ]; then
    adb root
    adb wait-for-device
    adb shell setenforce 0
fi
```

#### Gradle Integration (`build.gradle`)
```gradle
task setupDeviceForChatApp {
    doLast {
        // Enable ADB root
        "adb root".execute().waitFor()
        
        // Configure SELinux
        def selinuxCheck = "adb shell getenforce".execute()
        if (selinuxCheck.text.trim() != "Permissive") {
            "adb shell setenforce 0".execute()
        }
        
        // Set file permissions
        "adb shell chmod 755 /data/local/tmp/genie_bundle/*".execute()
    }
}
```

## 📱 Usage Examples

### Typical Development Workflow
```bash
# Make code changes...
# Then deploy with one command:
./deploy_app.sh

# Or use Gradle directly:
./gradlew :ChatApp:installDebug
```

### Debugging Issues
```bash
# Deploy and immediately view logs
./deploy_app.sh
# Choose 'logs' option when prompted

# Or check current device status
./deploy_app.sh  
# Choose 'status' option when prompted
```

### After Device Reboot
```bash
# No manual steps needed - script handles everything
./deploy_app.sh
```

## 🔍 Troubleshooting

### If SELinux Configuration Fails
1. **Check device connection**: `adb devices`
2. **Enable USB Debugging** on the device
3. **Enable ADB root** in developer options (if available)
4. **Try manual root**: `adb root`

### If File Permissions Fail
1. **Verify genie bundle exists**: `adb shell ls -la /data/local/tmp/genie_bundle/`
2. **Check directory permissions**: Script will show warnings if directory is missing

### If Build Fails
1. **Clean first**: `./gradlew :ChatApp:clean`
2. **Check Java/Android SDK** setup
3. **Review build logs** for specific errors

## 🔒 Security Notes

### Development vs Production
- ✅ **Development**: SELinux permissive mode is acceptable
- ⚠️ **Production**: Should use proper Android security model
- ⚠️ **Root access**: Only for development/testing environments

### What Gets Modified
1. **SELinux enforcement**: Temporarily set to permissive
2. **File permissions**: Only for `/data/local/tmp/genie_bundle/*`
3. **ADB root**: Enabled for the session
4. **No permanent system changes**: Settings revert on reboot

## 📋 Files Created

- `deploy_app.sh` - Main automated deployment script
- `quick_deploy.sh` - Shortcut to main script  
- `build.gradle` - Enhanced with automatic device setup task
- `DEPLOYMENT.md` - This documentation

## 🎯 Benefits

1. **Zero manual intervention** - Just run one command
2. **Handles device reboots** - Automatically reconfigures SELinux
3. **Integrated with build** - Works with existing Gradle workflow
4. **Error handling** - Clear messages if something goes wrong
5. **Time saving** - No more remembering `setenforce 0` commands

---

**Now you can just push and run the app without worrying about permissions! 🎉**