# Build and install LibreTube on an Android 17 phone

These instructions build the audiobook fork from this checkout and install it on a phone running Android 17. This project compiles against Android SDK Platform 36; the phone's Android version does not change that project setting.

## One-time setup on Windows

Install Android Studio and, in **SDK Manager**, install:

- Android SDK Platform 36
- Android SDK Build-Tools (the project uses Android Gradle Plugin's selected build-tools version)
- Android SDK Platform-Tools (includes `adb`)
- Android SDK Command-line Tools (latest)

Use the JBR bundled with Android Studio. This checkout built successfully with Android Studio's JBR 25; if installing Java separately, use JDK 17 or newer. Android Studio's JBR is usually at:

```text
C:\Program Files\Android\Android Studio\jbr
```

In PowerShell, set `JAVA_HOME` for the current window before building (adjust the path if Android Studio is installed elsewhere):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

Run Gradle in that same PowerShell window. These settings last only until you close it. To make them permanent, add `JAVA_HOME` as a Windows environment variable pointing to your JBR/JDK folder, then reopen PowerShell.

Set `ANDROID_HOME` to your SDK folder if Gradle cannot find the Android SDK. A typical location is:

```text
C:\Users\<your-Windows-user>\AppData\Local\Android\Sdk
```

For example:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
Test-Path "$env:ANDROID_HOME\platform-tools\adb.exe"
```

If `Test-Path` prints `False`, open Android Studio's **SDK Manager**, check the **Android SDK Location** shown at the top, and set `ANDROID_HOME` to that exact folder. Alternatively, create a machine-local `local.properties` file in the project root:

```properties
sdk.dir=C:\\Users\\<your-Windows-user>\\AppData\\Local\\Android\\Sdk
```

Do not commit `local.properties`; its path is specific to your computer.

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

1. On the phone, enable **Developer options**: open **Settings → About phone**, then tap **Build number** seven times. Menu names can vary by manufacturer.
2. Open **Developer options**, enable **USB debugging**, and connect the phone to your computer with a data-capable USB cable.
3. Unlock the phone and accept the USB debugging authorization prompt.
4. In PowerShell, from the project folder, check that ADB sees the phone:

   ```powershell
   & "$env:ANDROID_HOME\platform-tools\adb.exe" devices
   ```

   The phone should appear with status `device`. If it says `unauthorized`, accept the prompt on the phone. If no device appears, try another data-capable cable or USB port and install the phone manufacturer's ADB driver on Windows if required.

5. Install or update the debug app:

   ```powershell
   & "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
   ```

The debug build has application ID `com.github.libretube.debug` and is named **LibreTube Debug**, so it can normally be installed alongside regular LibreTube. It has separate app data. If installing a variant with the same application ID as an existing app, Android may reject it because of a different signing key; back up anything important before uninstalling the existing app.

## Copy the APK without ADB

Copy `app\build\outputs\apk\debug\app-debug.apk` to the phone using USB File Transfer, Google Drive, or another file-transfer method. Open the APK on the phone and follow Android's prompts. If asked, allow installation of unknown apps for the app used to open the APK.

## Debug builds and release builds

Android projects commonly have **debug** and **release** build variants, similar in purpose to debug and release builds of Windows applications:

- **Debug** is intended for development and testing. It is debuggable, usually builds faster, and this project adds `.debug` to the application ID. Use it while developing.
- **Release** is optimized and minified for distribution. It must be signed with a release signing key. This project reads signing details from a root-level `keystore.properties` file when present. Without your own release keystore, a release APK may be unsigned and cannot be installed normally. Keep the keystore and passwords private and backed up; future updates must use the same signing key.

To create a release APK after configuring a release keystore, run:

```powershell
.\gradlew.bat assembleRelease
```

The output is typically `app\build\outputs\apk\release\app-release.apk`. For personal testing, use the debug APK unless you specifically need a signed release build.

## Automatic upstream sync and workflow failures

The **Sync upstream** GitHub Actions workflow runs daily and can also be started manually from your fork's **Actions** tab. It syncs the original LibreTube `master` into your fork's `master`, then stages that branch's changes on `audiobook-mode-merge`. If there are no conflicts, it builds the staged branch. A successful build updates `audiobook-mode` and deletes the temporary merge branches.

To investigate a run, open your fork on GitHub, select **Actions → Sync upstream**, and open the failed or in-progress run. The step with the red X identifies what needs attention.

### Upstream changes conflict with your fork's `master`

The workflow stops before updating your fork's `master`. Resolve the conflict locally from the project folder:

```powershell
git remote -v
```

If `git remote -v` does not list an `upstream` remote, add it once:

```powershell
git remote add upstream https://github.com/libre-tube/LibreTube.git
```

Then run:

```powershell
git fetch upstream master
git switch master
git merge upstream/master
```

Open each conflicted file, choose the correct code, and remove the `<<<<<<<`, `=======`, and `>>>>>>>` markers. Save the files, then run:

```powershell
git status
git add .
git commit
git push origin master
```

After the push completes, start **Sync upstream** manually from the Actions tab.

### The merge into `audiobook-mode-merge` conflicts

The workflow opens a pull request for review and requests you as reviewer. Open it from your fork's **Pull requests** tab. If GitHub offers **Resolve conflicts**, use it, review the result, and merge the PR. If GitHub cannot resolve it in the browser, resolve it locally on the PR's source branch (named `master-sync-...` in the PR):

```powershell
git fetch origin
git switch --track -c <pr-source-branch> origin/<pr-source-branch>
git merge origin/master
```

Replace `<pr-source-branch>` with the actual `master-sync-...` branch name shown in the PR. If the first merge reports conflicts, resolve them in your editor, then run `git add .` and `git commit` before continuing. Next, merge the PR base branch into the source branch:

```powershell
git merge origin/audiobook-mode-merge
```

If this merge reports conflicts too, resolve them and commit the resolution. Then push the PR source branch:

```powershell
git add .
git commit
git push origin <pr-source-branch>
```

Return to GitHub and merge the PR. When it closes as merged, the workflow builds the result; if the build passes, it updates `audiobook-mode` and deletes the temporary branches.

### New upstream changes conflict with an open review PR

The workflow still updates your fork's `master` first. It then tries to merge the latest `master` into the PR's source branch and push that update. If this merge conflicts, the workflow comments on the PR; conflicting code cannot be applied automatically. Use the local PR source-branch steps above, merging the latest `origin/master` and `origin/audiobook-mode-merge`, resolve the conflicts, and push the PR source branch. The PR can then be reviewed and merged as usual.

### The debug build fails

The workflow creates or updates an issue assigned to you with a link to the failed run. It keeps `audiobook-mode-merge` so you can fix it. In the failed run, open **Build debug app after merge** or **Build reviewed merge** to see the Gradle error. Then, locally:

```powershell
git fetch origin
git switch --track -c audiobook-mode-merge origin/audiobook-mode-merge
```

If you already have a local `audiobook-mode-merge` branch, use `git switch audiobook-mode-merge` instead. Fix the code, build it, then commit and push:

```powershell
.\gradlew.bat assembleDebug
git add .
git commit
git push origin audiobook-mode-merge
```

When ready, start **Sync upstream** manually from the Actions tab. It retries the build and, if it succeeds, updates `audiobook-mode` and deletes the temporary branches. Close the build-failure issue after the fix is merged.

### Another workflow step fails

Open the failed step's log to see whether the problem was a network, permission, push, or Git error. Fix the reported issue and start **Sync upstream** manually again. If the final merge or push step failed, check the fork on GitHub before retrying; `audiobook-mode-merge` is retained unless the workflow completed its cleanup.

## Update the app later

Rebuild with `assembleDebug`, then run the same `adb install -r` command. The `-r` option updates the installed debug app while keeping its data, as long as the package ID and signing key remain compatible.

## Collect playback-resume logs

If playback resumes at the wrong position, connect the phone with USB debugging enabled and run this from PowerShell in the project folder. It saves playback checkpoint and resume messages to `playback-resume-log.txt`:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" logcat -d -v time |
    Select-String 'AbstractPlayerService|OnlinePlayerService' |
    Set-Content playback-resume-log.txt
```

Collect the log soon after reproducing the problem. It can include nearby playback events, so review it before sharing.
