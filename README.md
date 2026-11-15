<p align="center">
  <img src="art/logo-512.png" alt="Termux RE logo" width="128" height="128">
</p>

<h1 align="center">Termux RE</h1>

<p align="center">
  A fork of the <a href="https://github.com/termux/termux-app">Termux</a> terminal emulator and Linux environment for Android that installs next to the official app.
</p>

***

Termux RE is the [Termux app](https://github.com/termux/termux-app) (the user interface and terminal emulation) built under its own package name, `com.termux.re`, with an updated toolchain. It is an unofficial fork and is not affiliated with or endorsed by the Termux project. Report problems with Termux RE [here](https://github.com/riyadmondol2006/termux-app-RE/issues), not upstream.

If you want the official app, install Termux from one of the sources in the [upstream README](https://github.com/termux/termux-app#installation).

## Contents

- [Differences from Termux](#differences-from-termux)
- [Packages and the bootstrap](#packages-and-the-bootstrap)
- [Installation](#installation)
- [Plugins](#plugins)
- [Android 12+ phantom process killer](#android-12-phantom-process-killer)
- [Building](#building)
- [Native code](#native-code)
- [Debugging and reporting issues](#debugging-and-reporting-issues)
- [Contributing](#contributing)
- [Credits](#credits)
- [License](#license)

## Differences from Termux

- **App name and package.** The app is called Termux RE and its default package name is `com.termux.re`, so it installs side by side with official Termux. Its files live in `/data/data/com.termux.re/files`, separate from Termux's. The package name is a single Gradle property, `termuxPackageName` in `gradle.properties`.
- **Android 7.0+ only** (minSdk 24). Only the `apt-android-7` bootstrap variant is built. The old `apt-android-5` variant was dropped: on current Android its APKs only showed the error that the bootstrap variant is not compatible with the Android version.
- **Toolchain:** compileSdk 37, Android Gradle Plugin 9.5, Gradle 9.9, NDK r30 and Java 17.
- **targetSdk stays at 28 on purpose.** With targetSdk 29 or higher, Android blocks executing files from the app's data directory (W^X), which Termux depends on: every program under `$PREFIX` is run from there. As a result, Termux RE can't be published on Google Play, which requires a much higher targetSdk.
- **CI** builds APKs on every push, signs release APKs with your own key, and builds bootstraps for the fork's package name.
- **New icon**, so you can tell it apart from Termux on the home screen.

## Packages and the bootstrap

**Read this before installing a `com.termux.re` build.**

Termux packages are compiled with their install prefix hardcoded: `/data/data/<package name>/files/usr`. It appears in binaries, scripts, shebangs and configuration files, and can't be changed after the fact. The *bootstrap* is the minimal set of packages (shell, coreutils, `apt`, …) that the app embeds and extracts on first launch. That has three consequences:

- The official bootstrap and the official package repositories only work in an app whose package name is `com.termux`.
- A `com.termux.re` build needs bootstrap archives built for `com.termux.re`. This repository's [`build_bootstraps.yml`](.github/workflows/build_bootstraps.yml) workflow builds them with [termux-packages](https://github.com/termux/termux-packages) and publishes them as a GitHub release, which the app build then downloads.
- `pkg install` of anything that is not in the bootstrap needs an apt repository of packages built for `com.termux.re`. Packages from the official Termux repositories are built for `com.termux` and don't work in Termux RE. Until you host your own repository, a `com.termux.re` build is limited to what the bootstrap contains.

To build bootstraps or packages yourself, set the package name in a termux-packages checkout and build with its Docker image:

```sh
git clone https://github.com/termux/termux-packages.git
cd termux-packages
# In scripts/properties.sh, set:
#   TERMUX_APP__PACKAGE_NAME="com.termux.re"
./scripts/run-docker.sh ./scripts/build-bootstraps.sh
```

Build further packages from the same configured checkout (see [Building packages](https://github.com/termux/termux-packages/wiki/Building-packages)). Then serve the `.deb` files as an apt repository, for example with [termux-apt-repo](https://github.com/termux/termux-apt-repo), and point `$PREFIX/etc/apt/sources.list` in Termux RE at it.

If you need the official repositories instead, build with `-PtermuxPackageName=com.termux` (see [Building](#building)). That build uses the official bootstraps and repositories, but it can't be installed next to official Termux: it has the same package name and a different signing key.

## Installation

Requirements: Android 7.0 or newer.

- **Release APKs** are attached to [GitHub Releases](https://github.com/riyadmondol2006/termux-app-RE/releases). They are signed with the fork's release key.
- **Debug APKs** are built for every push. Find them under *Artifacts* on a run in [GitHub Actions](https://github.com/riyadmondol2006/termux-app-RE/actions); you must be logged into GitHub to download them. Debug APKs are signed with the publicly known test key `app/testkey_untrusted.jks` inherited from upstream. Anyone can sign an APK with that key and it will install as an update over a debug build, so only install debug APKs that come from this repository.

### Which APK to download

APKs are split by CPU architecture (ABI), plus one universal APK that contains all of them:

| APK file ends in | Use it on |
| --- | --- |
| `_arm64-v8a.apk` | Nearly all current phones and tablets. **Pick this one if unsure.** |
| `_armeabi-v7a.apk` | Older 32-bit ARM devices, and devices that run 32-bit Android on a 64-bit CPU |
| `_x86_64.apk` | 64-bit x86 emulators and Chromebooks |
| `_x86.apk` | 32-bit x86 emulators |
| `_universal.apk` | Any device. It is larger because it contains every ABI. |

To check a device's ABI, run `adb shell getprop ro.product.cpu.abi`.

### Updating and uninstalling

Android only installs an update that is signed with the same key as the installed app. Release and debug builds use different keys, so to switch between them you have to uninstall first, which deletes the app's data. [Back up](https://wiki.termux.com/wiki/Backing_up_Termux) your home directory before you uninstall. Uninstalling Termux RE doesn't affect an installed official Termux, and the other way round.

## Plugins

The official plugin apps (Termux:API, Termux:Boot, Termux:Float, Termux:Styling, Termux:Tasker, Termux:Widget) don't work with Termux RE. Each plugin uses the `sharedUserId` `com.termux`, talks to the `com.termux` package, and must be signed with the same key as the main app. Using them would mean rebuilding each plugin for your package name and signing it with your key.

## Android 12+ phantom process killer

Android 12 and later kill "phantom" processes (child processes of apps) when there are more than 32 of them across all apps, and also kill processes that use a lot of CPU in the background. In Termux RE this shows up as `[Process completed (signal 9) - press Enter]` without you exiting the shell. To turn this off:

- **Android 14 and later:** Settings → Developer options → enable **Disable child process restrictions**.
- **Android 12 and 13:** run these from a computer over [adb](https://developer.android.com/tools/adb):

  ```sh
  adb shell "/system/bin/device_config set_sync_disabled_for_tests persistent"
  adb shell "/system/bin/device_config put activity_manager max_phantom_processes 2147483647"
  ```

  On Android 12L and 13 you can also run `adb shell settings put global settings_enable_monitor_phantom_procs false`.

These settings can be reset by system updates. For background, see upstream issue [#2366](https://github.com/termux/termux-app/issues/2366) and [Phantom, cached and empty processes](https://github.com/agnostic-apollo/Android-Docs/blob/master/en/docs/apps/processes/phantom-cached-and-empty-processes.md).

## Building

You need:

- JDK 17 or newer (CI uses JDK 21).
- The Android SDK with platform 37.
- The NDK version set as `ndkVersion` in `gradle.properties` (r30).

Gradle is provided by the wrapper.

```sh
git clone https://github.com/riyadmondol2006/termux-app-RE.git
cd termux-app-RE
./gradlew assembleDebug
```

The APKs are written to `app/build/outputs/apk/debug/`. The build downloads the bootstrap archives for the configured package name.

To build with a different package name, override the Gradle property:

```sh
./gradlew assembleDebug -PtermuxPackageName=com.termux
```

With `com.termux`, the official Termux bootstraps are used. With any other package name, bootstraps built for that name are required (see [Packages and the bootstrap](#packages-and-the-bootstrap)).

### CI and release signing

GitHub Actions builds APKs on every push. Release APKs are signed with a keystore that is stored in four repository secrets:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | The keystore file, base64 encoded on a single line |
| `KEYSTORE_PASSWORD` | The keystore password |
| `KEY_ALIAS` | The key alias, e.g. `termux-re` |
| `KEY_PASSWORD` | The key password |

To create a keystore and encode it:

```sh
keytool -genkeypair -v -keystore termux-re-release.jks -alias termux-re \
    -keyalg RSA -keysize 4096 -validity 10000

base64 -w 0 termux-re-release.jks > termux-re-release.jks.b64     # Linux
base64 -i termux-re-release.jks -o termux-re-release.jks.b64      # macOS
```

`keytool` creates a PKCS12 keystore by default, and in a PKCS12 keystore the key password is the keystore password, so use the same value for `KEY_PASSWORD` and `KEYSTORE_PASSWORD`. Add the secrets under *Settings → Secrets and variables → Actions*, or with the GitHub CLI:

```sh
gh secret set KEYSTORE_BASE64 < termux-re-release.jks.b64
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS --body termux-re
gh secret set KEY_PASSWORD
```

Keep the keystore and its password backed up somewhere safe, and never commit them. If you lose the key, users can't update to your new builds without uninstalling first.

## Native code

> **TODO:** describe the app's native code here.

## Debugging and reporting issues

Report bugs in Termux RE in this repository's [issue tracker](https://github.com/riyadmondol2006/termux-app-RE/issues). Report to [upstream Termux](https://github.com/termux/termux-app/issues) only if you can reproduce the problem with the official app. Many problems with a `com.termux.re` build come from the bootstrap or from packages built for another prefix (see [Packages and the bootstrap](#packages-and-the-bootstrap)), so say which bootstrap and package repository you use.

To collect logs:

1. In the app's settings, open *Debugging* and raise *Log Level*. *Normal* logs errors, warnings and info, *Debug* adds debug messages, and *Verbose* logs everything. Set it back to *Normal* when you are done, since verbose logs can contain private data.
2. Reproduce the problem.
3. Run `logcat -d > logcat.txt` in the terminal, or from a computer run `adb logcat --pid=$(adb shell pidof -s com.termux.re)`.

You can also long-press the terminal and choose *More → Report Issue*, then accept the prompt to add debug info. This generates a report with file `stat` info and a `logcat` dump. In an issue, include the full text of the report, not screenshots. Also include:

- The APK file name (version, ABI, debug or release).
- The package name, if you built it yourself.
- Your Android version and device model.

## Contributing

Commit messages follow the format used upstream: a capitalised type from *Added*, *Changed*, *Deprecated*, *Removed*, *Fixed* or *Security*, an optional scope, then a description in the present tense. For example, `Fixed(terminal): Fix cursor position after resize` or `Added|Changed: Add foo and change bar`. Add `!` before the colon for breaking changes.

Shared constants and utilities belong in the [`termux-shared`](termux-shared) library, as upstream. Respect the licenses listed below when adding code.

## Credits

Termux RE is built on [Termux](https://termux.dev), created by Fredrik Fornwall and developed by the Termux maintainers and contributors. Upstream projects:

- [termux/termux-app](https://github.com/termux/termux-app): the Android app this repository is forked from.
- [termux/termux-packages](https://github.com/termux/termux-packages): the package build system used for the bootstraps.
- [Termux Wiki](https://wiki.termux.com/wiki/Main_Page) and [termux-packages wiki](https://github.com/termux/termux-packages/wiki), including the [Termux file system layout](https://github.com/termux/termux-packages/wiki/Termux-file-system-layout).
- [Terminal Emulator for Android](https://github.com/jackpal/Android-Terminal-Emulator): the original basis of Termux's terminal emulation.

The logo is derived from the Termux app icon. The "Termux" wordmark in `art/` is set in [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) (SIL Open Font License 1.1).

## License

This repository inherits upstream's licensing:

- The repository is released under [GPLv3 only](https://www.gnu.org/licenses/gpl-3.0.html), see [`LICENSE.md`](LICENSE.md).
- The [`terminal-emulator`](terminal-emulator) and [`terminal-view`](terminal-view) libraries use code from Terminal Emulator for Android, which is released under [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0).
- The [`termux-shared`](termux-shared) library is released under [MIT](https://opensource.org/licenses/MIT), with these exceptions:
  - `com/termux/shared/termux/*` is GPLv3 only, except `TermuxConstants.java` and `TermuxPropertyConstants.java`, which are MIT.
  - `com/termux/shared/file/filesystem/*` is GPLv2 only with the Classpath exception.
  - `StreamGobbler.java` is Apache 2.0.

  See [`termux-shared/LICENSE.md`](termux-shared/LICENSE.md) for details.
