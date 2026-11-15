#!/usr/bin/env bash
# Configures a termux/termux-packages checkout to build packages for the Termux RE app and its
# own package repository instead of the official Termux ones:
#
# - Sets the app package name, and declares that the package repository in repo.json is built
#   for that package name too, so `build-package.sh -i/-I` downloads already built dependencies
#   from it instead of compiling them again.
# - Points repo.json at the Termux RE repository. Only the main package directory is kept:
#   root-packages and x11-packages have no repository here, and with -i/-I build-package.sh
#   fails if a repository in repo.json can't be downloaded.
# - apt: sources.list points at the Termux RE repository.
# - termux-keyring: also installs the Termux RE repository signing key.
# - termux-tools: every mirror file that `pkg` and `termux-change-repo` choose from points at
#   the Termux RE repository, and `pkg` uses the default one, so mirror selection can't switch
#   back to the official repositories. Mirror files of other repositories left over from an
#   earlier termux-tools version are deleted when it is upgraded.
#
# The package build steps are wrapped, not replaced, so upstream changes to them still apply.
# patch-bootstrap.py writes the same files into existing bootstrap archives, from the same
# templates in files/, so a bootstrap and the packages in the repository never disagree, which
# also keeps dpkg from asking about changed configuration files on upgrades.
#
# Usage: patch-termux-packages.sh <termux-packages dir> <app package name> <repository url>

set -euo pipefail

if [ $# -ne 3 ]; then
	echo "Usage: $0 <termux-packages dir> <app package name> <repository url>" >&2
	exit 1
fi

TERMUX_PACKAGES_DIR="$1"
PACKAGE_NAME="$2"
REPO_URL="${3%/}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

fail() { echo "patch-termux-packages.sh: $*" >&2; exit 1; }

[[ "$PACKAGE_NAME" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]] || fail "Invalid package name '$PACKAGE_NAME'"
[[ "$REPO_URL" =~ ^https://[A-Za-z0-9.-]+(/[A-Za-z0-9._~-]+)*$ ]] || fail "Invalid repository url '$REPO_URL'"

# Writes template $1 from files/ to $2 with the placeholders replaced.
render() {
	sed -e "s|@REPO_URL@|$REPO_URL|g" -e "s|@PACKAGE_NAME@|$PACKAGE_NAME|g" "$SCRIPT_DIR/files/$1" > "$2"
}

# Appends a wrapper for build step function $2 to build.sh $1: the step as defined so far runs
# first, then the commands read from stdin.
wrap_step() {
	local build_sh="$1" step="$2"
	grep -q "^termux_re_upstream_$step()" "$build_sh" && fail "$build_sh is already patched"
	{
		echo
		echo "# Termux RE: added by apt-repo/patch-termux-packages.sh of the Termux RE app repository."
		echo "if declare -F $step >/dev/null; then eval \"termux_re_upstream_\$(declare -f $step)\"; else termux_re_upstream_$step() { :; }; fi"
		echo "$step() {"
		echo "	termux_re_upstream_$step"
		cat
		echo "}"
	} >> "$build_sh"
}

cd "$TERMUX_PACKAGES_DIR"
[ -f build-package.sh ] && [ -f scripts/properties.sh ] || fail "'$TERMUX_PACKAGES_DIR' is not a termux-packages checkout"

### scripts/properties.sh
PROPERTIES=scripts/properties.sh
for var in TERMUX_APP__PACKAGE_NAME TERMUX_REPO_APP__PACKAGE_NAME; do
	sed -i -E "s|^$var=\"[^\"]*\"$|$var=\"$PACKAGE_NAME\"|" "$PROPERTIES"
	grep -qx "$var=\"$PACKAGE_NAME\"" "$PROPERTIES" || fail "Failed to set $var in $PROPERTIES"
done
for var in TERMUX_REPO_APP__DATA_DIR TERMUX_REPO__CORE_DIR TERMUX_REPO__APPS_DIR TERMUX_REPO__ROOTFS TERMUX_REPO__HOME TERMUX_REPO__PREFIX; do
	sed -i -E "s|^$var=\"/data/data/[^/\"]+|$var=\"/data/data/$PACKAGE_NAME|" "$PROPERTIES"
	grep -qE "^$var=\"/data/data/$PACKAGE_NAME(/[^\"]*)?\"$" "$PROPERTIES" || fail "Failed to set $var in $PROPERTIES"
done

### repo.json
jq -n --arg url "$REPO_URL" '{
	"pkg_format": "debian",
	"packages": {"name": "termux-re-main", "distribution": "stable", "component": "main", "url": $url}
}' > repo.json

### apt
render sources.list packages/apt/termux-re-sources.list
grep -q '> $TERMUX_PREFIX/etc/apt/sources.list$' packages/apt/build.sh || fail "apt no longer writes sources.list in termux_step_post_make_install"
wrap_step packages/apt/build.sh termux_step_post_make_install <<'EOF'
	install -Dm644 "$TERMUX_PKG_BUILDER_DIR/termux-re-sources.list" "$TERMUX_PREFIX/etc/apt/sources.list"
EOF

### termux-keyring
cp "$SCRIPT_DIR/termux-re.gpg" packages/termux-keyring/termux-re.gpg
wrap_step packages/termux-keyring/build.sh termux_step_make_install <<'EOF'
	install -Dm600 "$TERMUX_PKG_BUILDER_DIR/termux-re.gpg" "$TERMUX_PREFIX/share/termux-keyring/termux-re.gpg"
	ln -sf "$TERMUX_PREFIX/share/termux-keyring/termux-re.gpg" "$TERMUX_PREFIX/etc/apt/trusted.gpg.d/termux-re.gpg"
EOF

### termux-tools
render mirror packages/termux-tools/termux-re-mirror
grep -q 'TERMUX_PKG_CONFFILES="$(cat "$TERMUX_PKG_BUILDDIR/conffiles")"' packages/termux-tools/build.sh || \
	fail "termux-tools no longer reads its conffiles from \$TERMUX_PKG_BUILDDIR/conffiles"
wrap_step packages/termux-tools/build.sh termux_step_post_make_install <<'EOF'
	local mirrors_dir="$TERMUX_PREFIX/etc/termux/mirrors" group
	local -a groups=()
	for group in "$mirrors_dir"/*/; do
		groups+=("$(basename "$group")")
	done
	[ ${#groups[@]} -gt 0 ] || termux_error_exit "No mirror groups found in $mirrors_dir"
	rm -rf "$mirrors_dir"
	install -Dm644 "$TERMUX_PKG_BUILDER_DIR/termux-re-mirror" "$mirrors_dir/default"
	for group in "${groups[@]}"; do
		install -Dm644 "$TERMUX_PKG_BUILDER_DIR/termux-re-mirror" "$mirrors_dir/$group/termux-re"
	done
	ln -sf "$mirrors_dir/default" "$TERMUX_PREFIX/etc/termux/chosen_mirrors"

	# termux-change-repo always offers this official mirror in its single mirror dialog.
	sed -i '/europe\/packages\.termux\.dev")"/d' "$TERMUX_PREFIX/bin/termux-change-repo"

	sed -i '\|^etc/termux/mirrors/|d' "$TERMUX_PKG_BUILDDIR/conffiles"
	(cd "$TERMUX_PREFIX" && find etc/termux/mirrors -type f | LC_ALL=C sort) >> "$TERMUX_PKG_BUILDDIR/conffiles"
	TERMUX_PKG_CONFFILES="$(cat "$TERMUX_PKG_BUILDDIR/conffiles")"

	cat >> "$TERMUX_PKG_BUILDDIR/preinst" <<-PREINST

	# Termux RE: delete mirrors of other repositories left over from an earlier version.
	if [ -d "$TERMUX_PREFIX/etc/termux/mirrors" ]; then
	    find "$TERMUX_PREFIX/etc/termux/mirrors" -type f ! -name default ! -name termux-re -delete
	fi
	PREINST
EOF

echo "Configured $(pwd) for package name '$PACKAGE_NAME' and repository '$REPO_URL'"
