# One UI Style AppLock (Android)

A Samsung-inspired AppLock for Android 8.0+ (API 26). It follows the design of AOSP's
[App Lock](https://source.android.com/docs/automotive/unbundled_apps/app-lock): protect individual
apps behind a unique PIN, off by default, with configurable lockable apps and a recovery flow.

## Build APK
This repository includes a GitHub Actions workflow. Open **Actions → Build AppLock APK → Run workflow**
(it also runs on pushes to `main`). When it finishes, download the **app-debug-apk** artifact.

## First-time setup
1. Install the APK and open AppLock.
2. Turn on **AppLock**. You are asked to create and confirm a PIN (4–8 digits).
3. Tap **Open accessibility settings** and enable the AppLock accessibility service.
4. Switch on the apps you want to protect.

Opening a protected app now shows the PIN pad. You can also unlock with the device screen lock
or biometrics (**Use screen lock**). Back or Cancel sends you Home; it never reveals the app.

## How it maps to AOSP App Lock
| AOSP App Lock | This app |
|---|---|
| Disabled by default; enabling prompts for a PIN | Same: the AppLock switch opens PIN setup |
| Per-app PIN prompt when opening a locked app | `LockActivity` + `PinPadView` |
| `res/values/config.xml`: `system_lockable_packages` | Same name; system apps are lockable only if listed |
| Core apps (Settings, launcher, dialer...) can't be locked | `never_lockable_packages` plus any launcher app |
| `config_enableMediaAppsLocking` | Same name; when `false`, apps with a `MediaBrowserService` are hidden |
| PIN pad dimens (`pin_pad_*`) | Same names in `res/values/dimens.xml`, scaled for a phone |
| Notification after installing an app | `PackageAddedReceiver` (needs notification permission on Android 13+) |
| PIN recovery with an account, then resets the PIN and clears app data | Recovery with the device screen lock, then resets the PIN (see below) |

Style the PIN pad by editing `res/values/dimens.xml`. Edit `res/values/config.xml` to change
which apps are lockable.

## Security notes
- The PIN is stored only as a salted PBKDF2-SHA256 hash. After 5 wrong attempts the pad locks for
  30 s, doubling on each further group of 5 (up to 15 min).
- The lock screen and PIN setup use `FLAG_SECURE`, so they are hidden from screenshots and Recents.
- Leaving a protected app keeps it unlocked for 10 s so quick app switches don't re-prompt.

## Differences from AOSP (it's a third-party app)
- AOSP App Lock is a platform-signed privileged app for Android Automotive. This is a normal app.
  It uses an Accessibility service to see which app opened, so the user must enable it, and the
  lock screen appears just after the app does (there can be a brief flash of the app).
- It can't verify an account (`config_recoveryAccountType`) or clear another app's data. **Forgot
  PIN?** verifies the device screen lock instead and only resets the PIN. Without a device screen
  lock there is no recovery: clear this app's data or reinstall.
- AOSP's suspend-to-RAM service entry, `privapp-permissions`, `preinstalled-packages.xml` and
  `Android.bp` settings apply to the platform image and have no equivalent here.
- This is an early debug build. Test carefully before relying on it to protect sensitive apps.
