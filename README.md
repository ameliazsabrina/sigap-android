# SIGAP Android

The Android app for SIGAP. The backend, API contract and architecture decisions live in the separate `sigap` repo.

> SIGAP is a prototype that runs on **simulated data**. It makes no diagnosis, gives no treatment advice and makes no claim of clinical effectiveness.

## What you need

| Tool | Version |
| --- | --- |
| Android Studio | 2026.2 or newer (the project uses AGP 9.5 and compileSdk 37) |
| JDK | 25. Android Studio's bundled JDK works. If Gradle can't find one, it downloads it automatically |
| Android SDK | Platform 37, installed from Android Studio's SDK Manager |
| `google-services.json` | For the Firebase project `sigap-cb69a`. **Not in this repo**. See step 2 |
| A phone or emulator | Android 8.0 (API 26) or newer, with Google Play services |

## 1. Clone

```bash
git clone https://github.com/ameliazsabrina/sigap-android.git
cd sigap-android
```

## 2. Add the Firebase config

The app uses Firebase Cloud Messaging for alarms. The build **fails** without the Firebase config:

```
File google-services.json is missing.
```

Get the file in one of two ways:

- **From the Firebase console (preferred).** Ask the project owner to add you to the Firebase project `sigap-cb69a`. Then open Project settings → General → Your apps, choose the Android app `fit.sigap.app.staging`, and download `google-services.json`.
- **From the project owner directly**, through a private channel.

Put it here, in the `staging` flavor's folder, not in `app/`:

```
app/src/staging/google-services.json
```

`.gitignore` already excludes this file. Never commit it.

## 3. Build and run

### With Android Studio

1. Choose **File → Open** and select the `sigap-android` folder. Let the Gradle sync finish.
2. Open **View → Tool Windows → Build Variants** and set `app` to **`stagingDebug`**.
3. Select your phone or emulator in the toolbar and click **Run ▶**.

### From the command line

Gradle needs a JDK. On macOS, Android Studio's bundled JDK is the simplest:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

On Windows, the equivalent is `set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`, with `gradlew.bat` in place of `./gradlew`.

Then, from the project root:

```bash
./gradlew assembleStagingDebug
```

The APK is written to `app/build/outputs/apk/staging/debug/app-staging-debug.apk`.

To build and install on a connected device in one step:

```bash
./gradlew installStagingDebug
```

The app appears as **SIGAP**, with the package `fit.sigap.app.staging`.

### Installing on a real phone

1. On the phone, open Settings → About phone and tap **Build number** seven times to enable Developer options.
2. In Settings → System → Developer options, turn on **USB debugging**.
3. Connect the phone over USB and accept the prompt on the phone.
4. Check that the phone is listed:

   ```bash
   adb devices
   ```

5. Run `./gradlew installStagingDebug`, or install a prebuilt APK:

   ```bash
   adb install -r app/build/outputs/apk/staging/debug/app-staging-debug.apk
   ```

## Build variants

| Variant | Package | API it talks to | Status |
| --- | --- | --- | --- |
| `stagingDebug` | `fit.sigap.app.staging` | `https://api-staging.sigap.fit/api/v1/` | **Use this one** |
| `stagingRelease` | `fit.sigap.app.staging` | staging | Builds an unsigned APK, which is useful for testing R8 |
| `productionDebug` / `productionRelease` | `fit.sigap.app` | `https://api.sigap.fit/api/v1/` | **Does not build yet.** The Firebase project has no app for `fit.sigap.app` |

Release builds are unsigned because there is no release keystore yet.

## Signing in

The app has no username and password screen. On first launch it asks you to scan the **pairing QR code** shown in the SIGAP web app for your account. You need an account on the matching backend, which is staging by default.

## Running against a local backend

To run the `sigap` backend on your laptop (`pnpm dev` in the `sigap` repo), point the app at it while building:

```bash
./gradlew installStagingDebug \
  -Psigap.apiBaseUrl=http://10.0.2.2:4000/api/v1/ \
  -Psigap.pairOrigin=http://localhost:3000
```

`10.0.2.2` is how the Android emulator reaches your computer. Debug builds allow plain HTTP **only** to `10.0.2.2` (see `app/src/debug/res/xml/network_security_config.xml`), so this setup works on the emulator, not on a real phone.

The emulator can't scan QR codes. Debug builds can receive the QR contents through an intent instead:

```bash
adb shell am start -S -n fit.sigap.app.staging/fit.sigap.app.MainActivity \
  -a android.intent.action.VIEW -d '<pairing QR contents>'
```

## Tests and checks

```bash
./gradlew testStagingDebugUnitTest      # unit tests
./gradlew lintStagingDebug              # Android lint
./gradlew connectedStagingDebugAndroidTest   # UI tests, needs a running device
```

The end-to-end flows in `.maestro/` need a local backend. See [docs/test-plan-nurse-codeblue.md](docs/test-plan-nurse-codeblue.md).

## Troubleshooting

**`File google-services.json is missing`**
The file is missing or in the wrong folder. It must be at `app/src/staging/google-services.json`. You'll also get this error if you picked a `production` variant.

**`No matching client found for package name 'fit.sigap.app.staging'`**
You downloaded the config for a different Firebase app or project. Download it again for the `fit.sigap.app.staging` app in `sigap-cb69a`.

**Gradle complains about the JDK, or `jlink ... does not exist`**
Gradle picked up a different JDK, for example the one bundled with VS Code's Java extension. Point it at Android Studio's JDK:

```bash
./gradlew assembleStagingDebug \
  -Porg.gradle.java.installations.auto-detect=false \
  "-Porg.gradle.java.installations.paths=$JAVA_HOME"
```

**`SDK location not found`**
Open the project once in Android Studio, which creates `local.properties`. You can also create that file yourself with a line pointing to your SDK, such as `sdk.dir=/Users/<you>/Library/Android/sdk`.

**No alarms or notifications arrive on the emulator**
Firebase needs a recent version of Google Play services. Use an emulator image that has the **Google Play** label, sign in to the Play Store, and update Google Play services. On a real phone, allow notifications and, when the app asks, exempt it from battery optimisation.

**The app works in debug but crashes in release**
R8 strips code that is only reached through reflection. Add keep rules to `app/proguard-rules.pro`, and test with `./gradlew assembleStagingRelease`.
