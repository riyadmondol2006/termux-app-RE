# Official Termux packages (compatibility layer)

Enable the switch in Tools → Package repositories. The bootstrap must already be
installed. The app stages its assets and runs the same command available in the
terminal afterwards:

```
termux-re-official on
apt update
pkg install codex ripgrep fd
termux-re-official status
termux-re-official off
apt update
```

This is experimental. Packages are rewritten for Termux RE. Most work, some can
break. Enabling is a choice to execute upstream packages and maintainer scripts.
It does not provide a sandbox or guarantee upstream package compatibility.

## Installation and trust

The source is `https://packages.termux.dev/apt/termux-main stable main`, restricted
with `signed-by` to `$PREFIX/share/keyrings/termux-re-official.gpg`. Its keys are
not added to global apt trust. The asset contains the public `.gpg` keys from
upstream termux-keyring 3.14, downloaded from:

https://packages.termux.dev/apt/termux-main/pool/main/t/termux-keyring/termux-keyring_3.14_all.deb

SHA-256: `ff25fc1fa648552ec1349b30f825b983aa65555c1f3bbec87a955dbf189983bb`.
The hash was checked against the official Packages index. Key rotations require
an app asset update. Repository signatures were successfully verified on device.

Apt verifies downloads normally, then invokes
`$PREFIX/libexec/termux-re-compat/dpkg` using `Dir::Bin::dpkg`. The wrapper only
handles `.deb` arguments in `--unpack`, `--install`, and `-i` operations. It
recognizes upstream archives by their `data/data/com.termux/files` members.
Archives built for our prefix pass through untouched. Direct calls to the real
`dpkg` do not use the layer; use apt/pkg to install upstream packages.

For upstream archives, a private directory under `$PREFIX/tmp` holds an extracted
copy. The file tree moves to `data/data/com.termux.re/files`. Regular-file bytes
and symlink targets replace `/data/data/com.termux/files` with
`/data/data/com.termux.re//f`, both 27 bytes. The app data symlink `f -> files`
makes that runtime path valid. GNU sed in the C locale preserves NULs, invalid
UTF-8 and ELF offsets. Conffile metadata instead names the actual archive paths;
md5sums are regenerated. The archive is rebuilt without compression for speed.
The real dpkg installs it, and temporary copies are removed even on failure.
Cached downloads remain unchanged. Dpkg's file list owns the real paths, so
normal uninstall works.

Allow substantial free disk space: downloaded archive, extraction, uncompressed
rebuilt archive and installed files coexist during an install. Codex alone uses
hundreds of MB. Large binaries may take time to rewrite. Android sed can print
`failed to set default file creation context ... Permission denied`; this was a
warning on the tested device and installation succeeded.

## Package selection and dependency limits

Our origin has priority 700; the official origin has priority 500. All
`termux-*` packages, `apt`, `dpkg`, and `command-not-found` have priority -1 from
the official origin. The wrapper also refuses those packages. This deliberately
protects more than the named core packages, including termux-auth and future
Termux-specific integrations. It means upstream-only termux-* packages cannot be
installed with this layer.

Apt pinning cannot simultaneously always prefer our older package and
automatically choose a lower-priority newer official version to satisfy another
package. If apt reports a version conflict, inspect `apt-cache policy LIBRARY`
and explicitly select the required official version:

```
apt install APPLICATION LIBRARY=VERSION
```

Review apt's proposed changes. Do not override protected app integration
packages. No automatic dependency escalation is implemented. A newer selected
library stays installed (priorities below 1000 do not force downgrades).
No current official version constraint exceeds the corresponding library
version in our repository at verification time. An isolated apt simulation on
the Pixel verified that our fixture library v1 won by default, an application
requiring v2 failed, and explicitly adding `compat-lib=2` resolved it.

Prefix rewriting does not fix app package identifiers, JNI assumptions,
Android permissions, absent APIs, binary signatures or self-checks, or paths
assembled at runtime. Official plugins and app integrations can remain
incompatible. Arbitrary unofficial sources and protected package overrides are
outside this feature's guarantees.

## Disabling and recovery

Off removes the managed source, apt wrapper configuration, preferences and active
keyring, and drops official package list files. It retains installed packages,
the `f` symlink, and inert command/assets so those packages work and the command
can re-enable the layer. Do not delete `f` while rewritten packages remain.
Disabling does not downgrade or convert installed official packages. To revert a
particular package, remove it or explicitly reinstall a version from our repo.
The generic source controls cannot edit the managed official source: use the
compatibility switch instead. Other manually added upstream sources are not
managed or removed by this feature.

## Verification

`assembleDebug assembleRelease test` with bootstrap URL
`https://riyadmondol2006.github.io/termux-app-RE/bootstrap/20261115.11` passed.
Host unit tests cover partial installation detection and actual archive rewriting
(binary bytes, symlinks, conffiles, md5sums, cache integrity, own archive handling
and temporary cleanup after success/failure, protected-package refusal and
dpkg exit-status propagation). The wrapper fixture test requires bash, dpkg-deb and GNU
tools on the build host.

Pixel 6 (`24011FDF6009HJ`) results:

- codex 0.171.0: `--version` passed.
- ripgrep 16.0.0: version and PCRE2 lookbehind search passed.
- fd 10.5.2: version passed.
- python-msgpack 1.2.4: Python pack/unpack round-trip passed.
- bash-completion 2.18.0: sourced successfully; `_init_completion` defined.
- lazygit 0.66.3: Go binary `--version` passed.
- units 2.27 and screen 5.0.2: version passed.
- `pkg upgrade -y`: seven packages upgraded from our repository successfully.
- `pkg uninstall`: temporary packages removed; disabling retained working codex,
  ripgrep, fd, Python and Git; `dpkg --audit` was clean.
- neofetch 7.1.0-1: executable rewritten env/bash shebang and version passed.
- proxychains-ng 4.18: installation passed; runtime help did not produce useful
  output, so no proxy traffic functionality is claimed.
- privoxy 4.2.0: rebuild failed safely before dpkg installation:
  `dpkg-deb: error: conffile '/data/data/com.termux.re/files/usr/var/service/privoxy/log/down' does not appear in package`.
  The upstream archive declares that conffile but does not ship it. Real-package
  conffile installation remains unverified; fixture conffile tests passed.
- bats: unavailable (`E: Unable to locate package bats`).

The debug APK installed successfully over the existing app. Launching the
repository activity with its full class name succeeded; interactive switch
verification remained incomplete because the phone screen was locked. Shell
operations exercise the same installer. A transient DNS failure
(`No address associated with hostname`) affected both repositories during later
tests; normal access recovered without changing settings. No system settings
or other apps were changed. Temporary packages and the old compat-test-1 and
compat-test-2 directories were removed; codex, ripgrep and fd were retained.
