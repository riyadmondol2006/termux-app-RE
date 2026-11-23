set -eu
root=$(mktemp -d)
trap 'rm -rf "$root"' EXIT
mkdir -p "$root/p/bin" "$root/p/tmp" "$root/pkg/DEBIAN" "$root/pkg/data/data/com.termux/files/usr/etc" "$root/pkg/data/data/com.termux/files/usr/bin"
cat > "$root/pkg/DEBIAN/control" <<'CONTROL'
Package: compat-fixture
Version: 1
Architecture: all
Maintainer: Test <test@example.com>
Description: compatibility fixture
CONTROL
printf '/data/data/com.termux/files/usr/etc/test\n' > "$root/pkg/DEBIAN/conffiles"
printf 'ABC\0/data/data/com.termux/files\0\377XYZ' > "$root/pkg/data/data/com.termux/files/usr/bin/binary"
printf '#!/data/data/com.termux/files/usr/bin/bash\n' > "$root/pkg/data/data/com.termux/files/usr/etc/test"
ln -s /data/data/com.termux/files/usr/etc/test "$root/pkg/data/data/com.termux/files/usr/bin/link"
printf 'unused\n' > "$root/pkg/DEBIAN/md5sums"
dpkg-deb -b "$root/pkg" "$root/input.deb"
sha256sum "$root/input.deb" > "$root/before"
ln -s "$(command -v dpkg-deb)" "$root/p/bin/dpkg-deb"
cat > "$root/p/bin/dpkg" <<'DPKG'
#!/bin/bash
set -eu
[[ "$1" == --unpack ]]
dpkg-deb -R "$2" "$TEST_ROOT/output"
DPKG
chmod +x "$root/p/bin/dpkg"
# Relocate just the wrapper's tool prefix, so no host installation is possible.
sed -e "s|^P=.*|P=$root/p|" -e 's|export PATH=.*|export PATH=/usr/bin:/bin|' "$1" > "$root/wrapper"
TEST_ROOT="$root" bash "$root/wrapper" --unpack "$root/input.deb"
sha256sum -c "$root/before"
base="$root/output/data/data/com.termux.re/files/usr"
printf 'ABC\0/data/data/com.termux.re//f\0\377XYZ' > "$root/expected"
cmp "$root/expected" "$base/bin/binary"
grep -Fx '/data/data/com.termux.re/files/usr/etc/test' "$root/output/DEBIAN/conffiles"
[[ $(readlink "$base/bin/link") == /data/data/com.termux.re//f/usr/etc/test ]]
(cd "$root/output"; md5sum -c DEBIAN/md5sums)
[[ -z $(ls -A "$root/p/tmp") ]]
# A failing dpkg must propagate its status and still clean temporary copies.
cp "$root/p/bin/dpkg" "$root/fake-dpkg"
printf '#!/bin/bash\nexit 42\n' > "$root/p/bin/dpkg"
set +e
TEST_ROOT="$root" bash "$root/wrapper" --unpack "$root/input.deb"
result=$?
set -e
[[ $result == 42 ]]
[[ -z $(ls -A "$root/p/tmp") ]]
cp "$root/fake-dpkg" "$root/p/bin/dpkg"
# Protected app integrations must fail before real dpkg is called.
sed -i 's/Package: compat-fixture/Package: termux-tools/' "$root/pkg/DEBIAN/control"
dpkg-deb -b "$root/pkg" "$root/protected.deb"
if TEST_ROOT="$root" bash "$root/wrapper" --unpack "$root/protected.deb"; then
    echo 'protected package was accepted' >&2; exit 1
fi
# Our archive is handed through byte-for-byte, with the original pathname.
rm -rf "$root/pkg/data/data/com.termux" "$root/output"
mkdir -p "$root/pkg/data/data/com.termux.re/files/usr"
echo own > "$root/pkg/data/data/com.termux.re/files/usr/own"
rm "$root/pkg/DEBIAN/conffiles" "$root/pkg/DEBIAN/md5sums"
dpkg-deb -b "$root/pkg" "$root/own.deb"
TEST_ROOT="$root" bash "$root/wrapper" --unpack "$root/own.deb"
test -f "$root/output/data/data/com.termux.re/files/usr/own"
echo 'wrapper fixtures PASS'
