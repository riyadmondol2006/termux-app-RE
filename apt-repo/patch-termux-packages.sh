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
# - command-not-found: suggests the packages of the Termux RE repository (with
#   fix-termux-packages.sh). Its version gets the build date, so every build of it is a new
#   version that `pkg upgrade` installs, with the packages added since.
# - termux-tools: the welcome message doesn't suggest the root-repo and x11-repo packages, and
#   its Docs, Donate, Community and issue links point at the app repository instead of
#   termux.dev (DONATE_URL can be set to a donation page). Its revision is raised so it replaces
#   termux-tools packages without these changes. Every mirror file that `pkg` and
#   `termux-change-repo` choose from points at the Termux RE repository, and `pkg` uses the
#   default one, so mirror selection can't switch back to the official repositories. Mirror
#   files of other repositories left over from an earlier termux-tools version are deleted when
#   it is upgraded. And when `pkg install` fails, files/package-not-found links to the package
#   request form for every package that the repository doesn't have, and explains that official
#   Termux packages only work through the app's compatibility layer.
#
# - Packages are compressed with xz's strongest preset, -9e, so more of them fit in the 1 GB of
#   GitHub Pages.
#
# The package build steps are wrapped, not replaced, so upstream changes to them still apply.
#
# Usage: patch-termux-packages.sh <termux-packages dir> <app package name> <package repository url> <app repository url>

set -euo pipefail

if [ $# -ne 4 ]; then
	echo "Usage: $0 <termux-packages dir> <app package name> <package repository url> <app repository url>" >&2
	exit 1
fi

TERMUX_PACKAGES_DIR="$1"
PACKAGE_NAME="$2"
REPO_URL="${3%/}"
APP_REPO_URL="${4%/}"
# The links of the welcome message.
DOCS_URL="$APP_REPO_URL#readme"
DONATE_URL="${DONATE_URL:-$APP_REPO_URL}"
COMMUNITY_URL="$APP_REPO_URL/issues"
ISSUES_URL="$APP_REPO_URL/issues"
REQUEST_URL="$APP_REPO_URL/issues/new?template=03-package-request.yml"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

fail() { echo "patch-termux-packages.sh: $*" >&2; exit 1; }

[[ "$PACKAGE_NAME" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]] || fail "Invalid package name '$PACKAGE_NAME'"
[[ "$REPO_URL" =~ ^https://[A-Za-z0-9.-]+(/[A-Za-z0-9._~-]+)*$ ]] || fail "Invalid repository url '$REPO_URL'"
for url in "$APP_REPO_URL" "$DONATE_URL"; do
	[[ "$url" =~ ^https://[A-Za-z0-9.-]+(/[A-Za-z0-9._~-]+)*/?$ ]] || fail "Invalid url '$url'"
done

# Writes template $1 from files/ to $2 with the placeholders replaced.
render() {
	sed -e "s|@REPO_URL@|$REPO_URL|g" -e "s|@PACKAGE_NAME@|$PACKAGE_NAME|g" -e "s|@REQUEST_URL@|$REQUEST_URL|g" \
		"$SCRIPT_DIR/files/$1" > "$2"
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

### Package compression
CREATE_DEB=scripts/build/termux_step_create_debian_package.sh
grep -qF 'XZ_OPT="-T$TERMUX_PKG_MAKE_PROCESSES"' "$CREATE_DEB" || fail "$CREATE_DEB no longer sets XZ_OPT"
sed -i 's/XZ_OPT="-T\$TERMUX_PKG_MAKE_PROCESSES"/XZ_OPT="-T$TERMUX_PKG_MAKE_PROCESSES -9e"/' "$CREATE_DEB"
grep -qF 'XZ_OPT="-T$TERMUX_PKG_MAKE_PROCESSES -9e"' "$CREATE_DEB" || fail "Failed to set the xz preset in $CREATE_DEB"

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
render package-not-found packages/termux-tools/termux-re-package-not-found
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

	# The welcome message suggests root-repo and x11-repo, which don't exist in this repository.
	sed -i -e '/Subscribing to additional repos/d' -e '/pkg install root-repo/d' -e '/pkg install x11-repo/d' \
		"$TERMUX_PREFIX/etc/motd" "$TERMUX_PREFIX/etc/motd.sh"
	sed -i -e "s|https://termux\.dev/docs|$TERMUX_RE_DOCS_URL|" -e "s|https://termux\.dev/donate|$TERMUX_RE_DONATE_URL|" \
		-e "s|https://termux\.dev/community|$TERMUX_RE_COMMUNITY_URL|" -e "s|https://termux\.dev/issues|$TERMUX_RE_ISSUES_URL|" \
		"$TERMUX_PREFIX/etc/motd" "$TERMUX_PREFIX/etc/motd.sh"
	if grep -n "termux\.dev" "$TERMUX_PREFIX/etc/motd" "$TERMUX_PREFIX/etc/motd.sh"; then
		termux_error_exit "The welcome message still links to termux.dev"
	fi

	# When `pkg install` fails, package-not-found explains the packages that this repository
	# doesn't have.
	local handler="$TERMUX_PREFIX/libexec/termux-re/package-not-found" pkg_script="$TERMUX_PREFIX/bin/pkg" content
	sed "s|@TERMUX_PREFIX@|$TERMUX_PREFIX|g" "$TERMUX_PKG_BUILDER_DIR/termux-re-package-not-found" > "$TERMUX_PKG_TMPDIR/package-not-found"
	install -Dm755 "$TERMUX_PKG_TMPDIR/package-not-found" "$handler"
	local install_line='add|i*) select_mirror; update_apt_cache; apt install "$@";;'
	local new_line='add|i*) select_mirror; update_apt_cache; apt install "$@" || { termux_re_status=$?; '"$handler"' "$@"; exit $termux_re_status; };;'
	grep -qF "$install_line" "$pkg_script" || termux_error_exit "pkg no longer has the line '$install_line'"
	content="$(cat "$pkg_script")"
	printf '%s\n' "${content/"$install_line"/"$new_line"}" > "$pkg_script"
	grep -qF "$new_line" "$pkg_script" || termux_error_exit "Failed to add package-not-found to pkg"

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

# Raise the revision by one more whenever the changes above change, so a termux-tools package
# with them replaces one without them, in the repository and on devices.
TOOLS_REVISION_INCREASE=4
TOOLS_BUILD=packages/termux-tools/build.sh
revision="$(sed -n -E 's/^TERMUX_PKG_REVISION="?([0-9]+)"?( #.*)?$/\1/p' "$TOOLS_BUILD")"
[ -n "$revision" ] || fail "No TERMUX_PKG_REVISION in $TOOLS_BUILD"
sed -i -E "s/^TERMUX_PKG_REVISION=.*/TERMUX_PKG_REVISION=$((revision + TOOLS_REVISION_INCREASE)) # Termux RE: raised by apt-repo\/fix-termux-packages.sh and patch-termux-packages.sh/" "$TOOLS_BUILD"
{
	echo
	echo "# Termux RE: added by apt-repo/patch-termux-packages.sh."
	echo "TERMUX_RE_DOCS_URL=\"$DOCS_URL\""
	echo "TERMUX_RE_DONATE_URL=\"$DONATE_URL\""
	echo "TERMUX_RE_COMMUNITY_URL=\"$COMMUNITY_URL\""
	echo "TERMUX_RE_ISSUES_URL=\"$ISSUES_URL\""
} >> "$TOOLS_BUILD"

### command-not-found
# Suggest the packages of the Termux RE repository, see fix-termux-packages.sh.
CNF_BUILD=packages/command-not-found/build.sh
printf '\n# Termux RE: added by apt-repo/patch-termux-packages.sh.\nTERMUX_RE_COMMAND_NOT_FOUND_REPO="%s"\n' "$REPO_URL" >> "$CNF_BUILD"
# Its database lists the packages of the repository, which change while its version stays the
# same, so the version gets the build date (3.5.0 becomes 3.5.0+re20261124) and every build of
# it reaches devices with `pkg upgrade`.
version="$(sed -n -E 's/^TERMUX_PKG_VERSION="?([^"]*)"?$/\1/p' "$CNF_BUILD")"
[[ "$version" =~ ^[0-9][0-9a-z.]*$ ]] || fail "Unexpected TERMUX_PKG_VERSION '$version' in $CNF_BUILD"
sed -i -E -e "/^TERMUX_PKG_SRCURL=/s/\\$\\{?TERMUX_PKG_VERSION\\}?/$version/g" \
	-e "s/^TERMUX_PKG_VERSION=.*/TERMUX_PKG_VERSION=$version+re$(date -u +%Y%m%d) # Termux RE: build date, see apt-repo\/patch-termux-packages.sh/" "$CNF_BUILD"
grep -q '^TERMUX_PKG_SRCURL=.*TERMUX_PKG_VERSION' "$CNF_BUILD" && fail "TERMUX_PKG_SRCURL of $CNF_BUILD still uses TERMUX_PKG_VERSION"
grep -qE "^TERMUX_PKG_VERSION=$version\+re[0-9]{8} " "$CNF_BUILD" || fail "Failed to set the version of command-not-found"

echo "Configured $(pwd) for package name '$PACKAGE_NAME' and repository '$REPO_URL'"
