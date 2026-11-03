# Build and install LibreTube on your Pixel 8

These steps build the audiobook fork from this checkout and install it on a Google Pixel 8.

## One-time setup on Windows

Install Android Studio and, in **SDK Manager**, install:

- Android SDK Platform 36
- Android SDK Build-Tools (the project uses Android Gradle Plugin's selected build-tools version)
- Android SDK Platform-Tools (includes `adb`)
- Android SDK Command-line Tools (latest)

This project compiles against SDK 36. You do not need Google's USB driver on Windows for a Pixel; Google's driver is for some other device manufacturers. Use the JBR bundled with Android Studio. This checkout built successfully with Android Studio's JBR 25; if you install Java separately, use JDK 17 or newer. Android Studio's JBR is usually at:

```text
C:\Program Files\Android\Android Studio\jbr
```

In PowerShell, set `JAVA_HOME` for the current window before building (adjust the path if Android Studio is installed elsewhere):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

If `java -version` prints a Java version, run the Gradle build in that same PowerShell window. These settings last only until you close the window. To make them permanent, add `JAVA_HOME` as a Windows environment variable pointing to the JBR/JDK folder, then reopen PowerShell. Set `ANDROID_HOME` to your SDK folder if Gradle cannot find the Android SDK. A typical SDK folder is:

```text
C:\Users\<your-Windows-user>\AppData\Local\Android\Sdk
```

For example, in PowerShell:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

Verify that the SDK location is right:

```powershell
Test-Path "$env:ANDROID_HOME\platform-tools\adb.exe"
```

If this prints `False`, open Android Studio's **SDK Manager** and check the **Android SDK Location** shown at the top, then set `ANDROID_HOME` to that exact folder. You can instead create a machine-local `local.properties` in the project root with your SDK path:

```properties
sdk.dir=C:\\Users\\<your-Windows-user>\\AppData\\Local\\Android\\Sdk
```

Do not commit `local.properties`; it contains a path specific to your computer.

## Build a debug APK

Open PowerShell in this project folder (the folder containing `gradlew.bat`) and run:

```powershell
.\gradlew.bat assembleDebug
```

The APK will be created at:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Install over USB with ADB

1. On the Pixel, enable **Developer options**: open **Settings → About phone**, then tap **Build number** seven times.
2. Open **Settings → System → Developer options**, enable **USB debugging**, and connect the phone to the computer with a data-capable USB cable.
3. Accept the USB debugging authorization prompt on the phone.
4. In PowerShell, from the project folder, verify that ADB sees the phone:

   ```powershell
   & "$env:ANDROID_HOME\platform-tools\adb.exe" devices
   ```

5. Install or update the debug app:

   ```powershell
   & "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
   ```

The debug build has the application ID `com.github.libretube.debug` and is named **LibreTube Debug**, so it can normally be installed alongside the regular LibreTube app. It has its own separate app data. If you build a different variant that uses the same application ID as an installed app, Android may reject installation because the APKs have different signing keys; back up anything important before uninstalling the existing app.

## Copy the APK to the phone without ADB

After building, copy `app\build\outputs\apk\debug\app-debug.apk` to the Pixel using USB File Transfer, Google Drive, or another file-transfer method. Open the APK on the phone and follow Android's prompts. If Android asks, allow installation of unknown apps for the app you used to open the APK. Only install APKs you trust.

## Debug builds and release builds

Android projects commonly have **debug** and **release** build variants, similar in purpose to debug and release builds of Windows applications:

- **Debug** is intended for development and testing. It is debuggable, usually builds faster, and this project adds `.debug` to its application ID. Use it for your phone while developing.
- **Release** is optimized and minified for distribution. It must be signed with a release signing key. This project reads signing details from a root-level `keystore.properties` file when that file is present. Without your own release keystore, a release APK may be unsigned and cannot be installed like a normal app. Keep the keystore and passwords private and backed up; future updates must use the same signing key.

To create a release APK after configuring a release keystore, run:

```powershell
.\gradlew.bat assembleRelease
```

The output is typically `app\build\outputs\apk\release\app-release.apk`. For your personal testing, use the debug APK unless you have a specific reason to configure and maintain a release signing key.

## Update the build later

Rebuild with `assembleDebug`, then run the same `adb install -r` command. The `-r` option updates the installed debug app while keeping its data, as long as the package ID and signing key remain compatible.

## Collect playback-resume logs

If playback resumes at the wrong position, connect the phone with USB debugging enabled and run this from PowerShell in the project folder. It saves the playback checkpoint and resume messages to `playback-resume-log.txt`:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" logcat -d -v time |
    Select-String 'AbstractPlayerService|OnlinePlayerService' |
    Set-Content playback-resume-log.txt
```

Collect the log soon after reproducing the problem. It can include nearby playback events, so review it before sharing.
