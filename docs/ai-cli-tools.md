# AI coding CLIs in Termux RE

Tested 2026-11-24 (Asia/Dhaka) on Pixel 6, Android 17, aarch64, debug
`com.termux.re`. This is Android/Bionic, not GNU/Linux. Its prefix is
`/data/data/com.termux.re/files/usr`. Never install official Termux `.deb` files
or point this app at official Termux repositories.

## Status and steps

`pkg install nodejs-lts npm git python python-pip make` works from the RE
repository. Node reports `android arm64 v24.22.3`; npm is 12.2.3. npm is a
**separate package**, so installing only nodejs-lts is insufficient.

Current Claude Code 2.1.331 and npm Codex 0.171.0 do not start. Repeating the
suggested npm reinstall does not fix either. No login, API key, model request,
or authenticated coding operation was tested.

### Claude: tested historical JavaScript release

Today, this starts successfully:

```sh
pkg install nodejs-lts npm git
npm install -g --ignore-scripts @anthropic-ai/claude-code@2.1.1
DISABLE_AUTOUPDATER=1 claude --version
DISABLE_AUTOUPDATER=1 claude --help
```

It prints `2.1.1 (Claude Code)`. **This alone does not fix file search.** It is
an old release, not a claim of current vendor support. Do not run `claude update`
or replace the pin with `latest`: newer releases use native binaries.

Once **ripgrep has been built and published for RE**, the provided
[helper](ai-cli-tools.sh) isolates that version from global npm packages and
sets the search/update/temp environment for every invocation:

```sh
pkg install nodejs-lts npm git ripgrep
# From a checkout of this repository inside Termux RE:
bash docs/ai-cli-tools.sh install-claude
bash docs/ai-cli-tools.sh claude --version
bash docs/ai-cli-tools.sh claude --help
# Interactive startup (the owner completes login separately):
bash docs/ai-cli-tools.sh claude
```

The helper installs under
`~/.local/share/termux-re-ai-tools/claude-code-2.1.1`, sets
`USE_BUILTIN_RIPGREP=0`, `DISABLE_AUTOUPDATER=1`, and `TMPDIR="$PREFIX/tmp"`,
and invokes `node .../cli.js` directly. It does not edit shell profiles or
credentials. The helper itself was checked on-device with an independently
cross-compiled Android `rg`; this is not a published apt package.

To remove its install: delete only the above version directory. To remove a
separate global test install: `npm uninstall -g @anthropic-ai/claude-code`.

### Codex: requires a new native package

After **codex and ripgrep have been built, device-tested, and published for RE**:

```sh
# Only if you previously installed the broken npm launcher:
npm uninstall -g @openai/codex
pkg install codex ripgrep
codex --version
codex --help
```

These are future validation steps, not a successful Codex installation claim.
Do not use `npm --force`, pretend the platform is Linux, edit ELF headers, or
use an official Termux binary as the solution. The helper's `codex` action
requires the Debian package, avoiding accidental use of the npm launcher.

## Reproduction and exact failures

Commands ran under the shared device lock with `adb shell run-as com.termux.re`
and HOME, PREFIX, PATH, TMPDIR, LD_PRELOAD and LANG set as for the app.
No app reinstall or system setting change was needed.

### Current Claude Code

`npm i -g @anthropic-ai/claude-code` exits 0, adds version 2.1.331, and reports:

```text
npm warn install-scripts 1 package had install scripts blocked because they are not covered by allowScripts:
npm warn install-scripts   @anthropic-ai/claude-code@2.1.331 (postinstall: node install.cjs)
```

`claude --version` exits 1:

```text
Error: claude native binary not installed.

Either postinstall did not run (--ignore-scripts, some pnpm configs)
or the platform-native optional dependency was not downloaded
(--omit=optional).
```

Manually executing `node "$PREFIX/lib/node_modules/@anthropic-ai/claude-code/install.cjs"`
exits **0**, despite reporting the actual blocker:

```text
[@anthropic-ai/claude-code postinstall] Native binaries for linux-arm64-android are not available on this release channel.
  Available: darwin-arm64, darwin-x64, linux-x64, linux-arm64, linux-x64-musl, linux-arm64-musl, win32-x64, win32-arm64
```

Thus enabling npm postinstall is insufficient. The current package is a native
launcher, not the old JS CLI; `--ignore-scripts` cannot restore a JS entry point.
Sources: [current npm metadata](https://registry.npmjs.org/@anthropic-ai/claude-code/2.1.331),
[tested JS metadata](https://registry.npmjs.org/@anthropic-ai/claude-code/2.1.1).

In 2.1.1, source inspection shows the default rg path is computed as
`vendor/ripgrep/${process.arch}-${process.platform}/rg`, hence `arm64-android/rg`,
which is absent. Running its supplied `arm64-linux/rg --version` directly yields:

```text
Could not find a PHDR: broken executable?
Aborted
```

Exit status is 134. Source inspection confirms `USE_BUILTIN_RIPGREP=0` selects
system `rg` when found in PATH; the helper explicitly requires it. A native
Android build of ripgrep 15.2.0 successfully prints its version and searches a
fixture on the phone. This test build omitted PCRE2; publish the actual upstream
recipe with its PCRE2 dependency instead.

The old JS file contains no `/data/data/com.termux` string. It does contain
`/tmp/claude` in sandbox-specific code. Setting TMPDIR is appropriate for normal
Node temp files, but is not proof that every upstream sandbox path works.
Authenticated tools and Claude's sandbox were not exercised.

### npm Codex

`npm i -g @openai/codex` exits 0, installs 0.171.0; `codex --version` exits 1:

```text
Error: Missing optional dependency @openai/codex-linux-arm64. Reinstall Codex: npm install -g @openai/codex@latest
```

The installed `bin/codex.js` explicitly maps `android` + `arm64` to
`aarch64-unknown-linux-musl`, but npm skips its optional dependency because its
metadata specifies Linux. This version does not throw `Unsupported platform`.
Direct installation of the platform package rejects it:

```text
npm error code EBADPLATFORM
npm error notsup Unsupported platform for @openai/codex@0.171.0-linux-arm64: wanted {"os":"linux","cpu":"arm64"} (current: {"os":"android","cpu":"arm64"})
```

Even installing that package with `--force --ignore-scripts` and executing the
binary directly exits 1:

```text
error: "/data/data/com.termux.re/files/home/ai-cli-probe/node_modules/@openai/codex/vendor/aarch64-unknown-linux-musl/bin/codex" has unexpected e_type: 2
```

It is a Linux ET_EXEC binary, rejected by Android's loader. Changing the npm
platform selection cannot make it a native Android executable.
Source: [versioned npm package](https://registry.npmjs.org/@openai/codex/0.171.0).

## Other dependencies

| Check | Device result |
| --- | --- |
| git | `git version 2.56.1` |
| Python | 3.14.10; ssl and sqlite3 import; OpenSSL 3.6.5, SQLite 3.54.1 |
| pip / venv | pip 26.3; venv creation works; packaging 26.4 wheel installs/imports offline |
| pip online | run-as probe could not resolve pypi.org: `Failed to resolve 'pypi.org' ([Errno 7] No address associated with hostname)`; no settings changed; normal terminal networking needs a separate recheck |
| native npm | bufferutil installed for a source-build probe; node-gyp 13 finds Python and installed Node headers, generates makefiles, then `make: cc: No such file or directory` |
| npm 12 flags | `npm rebuild bufferutil --build-from-source` rejects `--build-from-source` with `EUNKNOWNCONFIG`; invoke node-gyp directly for this probe |
| ripgrep, fd | apt says `E: Unable to locate package ripgrep` / `fd`; independent NDK Android builds rg 15.2.0 and fd 10.5.2 both print versions and find/search the fixture |
| compiler | `E: Package 'clang' has no installation candidate`; same for `pkg-config` |
| shebang rewriting | Node execFile of `/usr/bin/env node --version` succeeds with termux-exec preload, returning v24.22.3 |
| temporary files | `require('os').tmpdir()` returns `/data/data/com.termux.re/files/usr/tmp` |

Reproduce the native addon probe after the build tools are published:

```sh
pkg install nodejs-lts npm python make clang pkg-config
mkdir -p "$HOME/addon-check" && cd "$HOME/addon-check"
npm install --ignore-scripts bufferutil
cd node_modules/bufferutil
node "$PREFIX/lib/node_modules/npm/node_modules/node-gyp/bin/node-gyp.js" rebuild --nodedir="$PREFIX"
node -e 'require("./")'
```

Some native modules require additional Android porting; installing a compiler
cannot turn glibc prebuilds into Bionic libraries.

## Package build handoff

Add these exact top-level package names to the RE package build, retaining the
existing nodejs-lts, npm, git, python, python-pip, python-ensurepip-wheels and make:

```text
ripgrep fd codex clang pkg-config
```

Let the build resolve and publish their dependency closure (including pcre2 for
ripgrep; libc++ and openssl for codex). Do not silently import official binaries.
Build codex for **aarch64** first; its current recipe excludes arm and i686.

Recipes inspected at termux-packages commit
`d85d18f456d732d26d8b87d826155c2ced0342e8`:

- [codex](https://github.com/termux/termux-packages/tree/d85d18f456d732d26d8b87d826155c2ced0342e8/packages/codex): 0.171.0, Rust Android target, builds V8 from source and installs both `codex` and `codex-code-mode-host`. Keep the entire recipe directory, including Android daemon/MXC patches, Rust cc patches, rusty-v8 patch and nested V8 patches. This is a substantial build, not an npm wrapper.
- [ripgrep](https://github.com/termux/termux-packages/tree/d85d18f456d732d26d8b87d826155c2ced0342e8/packages/ripgrep) and [fd](https://github.com/termux/termux-packages/tree/d85d18f456d732d26d8b87d826155c2ced0342e8/packages/fd) use the Android Rust target.

No additional RE-specific recipe patch was identified: these recipes install
using `$TERMUX_PREFIX`. Apply the existing RE patch/fix scripts before building,
so framework properties, dependency repository and prefix are RE's. Never just
copy a codex binary built for com.termux. The full Codex RE package build was
**not run or proven** here; the device has no candidate package and this checkout
has no prepared termux-packages builder. Parent build acceptance gate:

```sh
# In the configured/patched termux-packages builder, not this app checkout:
./build-package.sh -a aarch64 -I ripgrep fd codex clang pkg-config
# Publish signed packages to the RE repository, then on the test device:
pkg update
pkg install ripgrep fd codex clang pkg-config
rg --version
fd --version
codex --version
codex --help
```

Then rerun the native addon probe and helper checks. Verify packaged files and
runtime logs contain no `/data/data/com.termux/` paths. Codex sandboxing and
Android process execution need testing beyond `--help` before claiming full
coding functionality. No new source patch is supplied without a reproduced
prefix-specific defect.

## Validation and cleanup

The app source was unchanged. `assembleDebug assembleRelease test` passed in
4m 12s (340 actionable tasks); no APK installation was needed. Helper syntax,
isolated install, `--version`, `--help`, and missing-rg/native-codex guards were
checked. Required app checks use:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/riyadm/Android/Sdk \
  ./gradlew assembleDebug assembleRelease test \
  -PtermuxBootstrapUrl=https://riyadmondol2006.github.io/termux-app-RE/bootstrap/20261115.11
```

Device probes used only the app's private files and packages. Test-only CLI
installs, isolated helper install, venv, downloaded Linux binary and fixture
files were removed after verification. Existing development packages remain.
No service login, other app, app data reset or system setting was involved.
