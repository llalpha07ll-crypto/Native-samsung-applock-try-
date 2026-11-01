# One UI Style AppLock (Android)

A Samsung-inspired AppLock prototype for Android 8.0+ with a native Android biometric/device-credential prompt.

## Build APK
This repository includes a GitHub Actions workflow. Open **Actions → Build AppLock APK → Run workflow**. When it finishes, download the **app-debug-apk** artifact.

## First-time setup
1. Install the APK.
2. Open AppLock and choose apps to protect.
3. Tap **Enable AppLock service** and enable the AppLock accessibility service in Android Settings.
4. When a protected app opens, Android's native biometric/device credential prompt appears.

## Important limitations
- This is a third-party app, not a privileged Samsung system component.
- The app uses Android Accessibility to detect app launches. The user must explicitly enable the service.
- Biometric/device credential UI is provided by Android/OEM. This app cannot read or recover the device PIN, pattern, or password.
- This is an initial debug build. Test carefully before relying on it to protect sensitive apps.
