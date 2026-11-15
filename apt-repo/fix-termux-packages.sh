#!/usr/bin/env bash
# Works around problems of termux-packages builds on GitHub Actions runners, in a termux-packages
# checkout. Used by the Build bootstraps and Build packages workflows for any package name.
#
# - scripts/build-bootstraps.sh builds packages by name, and some of its names are now
#   subpackages, like bzip2 of libbz2, which build-package.sh can't build by name. They are
#   replaced by the package that builds them; their .deb files end up in output/ like before.
# - The Docker container of termux-packages may only write to output/ of the checkout (its
#   restricted AppArmor profile), but build-bootstraps.sh moves the bootstrap archives to the
#   checkout itself and ignores that this fails. They are moved to output/ instead, and a
#   failure fails the build.
# - Source downloads that fail are tried from a second place, and only used if they have exactly
#   the checksum the package expects:
#   - git.kernel.org often times out from GitHub Actions runners, for example for the dash
#     sources. Its snapshots are `git archive | gzip -n` of a tag, so termux_download makes them
#     from the kernel.googlesource.com mirror of the repository instead.
#   - downloads.apache.org only has the newest releases, older ones move to archive.apache.org,
#     for example apr-util 1.6.5, a build dependency of git.
#   - invisible-mirror.net returns a small web page instead of the file, for example for the
#     dialog sources that termux-tools needs. invisible-island.net is the site it mirrors.
# - command-not-found builds its database from the Contents-<arch>.gz files of the repositories
#   in repo.json, of which it only uses files under the build's own prefix, and it needs the
#   official repository names. It now uses the file list of the repository in
#   TERMUX_RE_COMMAND_NOT_FOUND_REPO, which patch-termux-packages.sh sets to the Termux RE
#   repository, so it only suggests packages that can be installed. Without it, or if that
#   repository has no file list yet, it uses the official main repository's file list with the
#   prefix replaced by the build's prefix, so it suggests the same packages as Termux. The root
#   and x11 lists are empty, since those repositories don't exist for other package names.
#
# Usage: fix-termux-packages.sh <termux-packages dir>

set -euo pipefail

if [ $# -ne 1 ]; then
	echo "Usage: $0 <termux-packages dir>" >&2
	exit 1
fi
cd "$1"

fail() { echo "fix-termux-packages.sh: $*" >&2; exit 1; }

### Subpackages in the bootstrap package list
BOOTSTRAPS=scripts/build-bootstraps.sh
for name in $(grep -oP '^\s*PACKAGES\+=\("\K[a-z0-9][a-z0-9+._-]*(?="\))' "$BOOTSTRAPS" | sort -u); do
	[ -f "packages/$name/build.sh" ] && continue
	subpackage="$(compgen -G "packages/*/$name.subpackage.sh" | head -n 1 || true)"
	[ -n "$subpackage" ] || fail "'$name' in $BOOTSTRAPS is neither a package nor a subpackage"
	parent="$(basename "$(dirname "$subpackage")")"
	sed -i -E "s/^(\s*)PACKAGES\+=\(\"$name\"\)/\1PACKAGES+=(\"$parent\") # Termux RE: builds the $name subpackage/" "$BOOTSTRAPS"
	echo "Bootstrap package $name is built through $parent"
done

### Bootstrap archives in output/
MOVE='	mv -f "${BOOTSTRAP_TMPDIR}/bootstrap-${1}.zip" "$TERMUX_PACKAGES_DIRECTORY/"'
grep -qxF "$MOVE" "$BOOTSTRAPS" || fail "build-bootstraps.sh no longer moves the archives to the checkout"
sed -i "s|^\tmv -f \"\${BOOTSTRAP_TMPDIR}/bootstrap-\${1}.zip\" \"\$TERMUX_PACKAGES_DIRECTORY/\"\$|\tmv -f \"\${BOOTSTRAP_TMPDIR}/bootstrap-\${1}.zip\" \"\$TERMUX_BUILT_DEBS_DIRECTORY/\" \|\| return 1 # Termux RE: output/ is writable in Docker|" "$BOOTSTRAPS"
grep -qF '"$TERMUX_BUILT_DEBS_DIRECTORY/" || return 1 # Termux RE' "$BOOTSTRAPS" || fail "Failed to change where build-bootstraps.sh puts the archives"
echo "Bootstrap archives are written to output/"

### Source download fallbacks
DOWNLOAD=scripts/build/termux_download.sh
grep -q '^termux_download() {' "$DOWNLOAD" || fail "termux_download not found in $DOWNLOAD"
grep -q 'termux_re_upstream_termux_download' "$DOWNLOAD" && fail "$DOWNLOAD is already patched"
cat >> "$DOWNLOAD" <<'EOF'

# Termux RE: added by apt-repo/fix-termux-packages.sh of the Termux RE app repository.
# Sources that can't be downloaded are tried from a second place, see there.
eval "termux_re_upstream_$(declare -f termux_download)"
termux_download() {
	termux_re_upstream_termux_download "$@" && return 0
	local status=$? url="$1" destination="$2" checksum="${3:-}"
	local alternative=""
	if [[ "$url" =~ ^https://(downloads|www)\.apache\.org/(dist/)?(.+)$ ]]; then
		alternative="https://archive.apache.org/dist/${BASH_REMATCH[3]}"
	elif [[ "$url" =~ ^https://invisible-mirror\.net/archives/(.+)$ ]]; then
		alternative="https://invisible-island.net/archives/${BASH_REMATCH[1]}"
	fi
	if [[ -n "$alternative" && "$checksum" =~ ^[0-9a-f]{64}$ ]]; then
		echo "Trying $alternative instead"
		termux_re_upstream_termux_download "$alternative" "$destination" "$checksum" && return 0
		return $status
	fi
	# git.kernel.org cgit snapshots are `git archive | gzip -n` of a tag: make the snapshot from
	# the kernel.googlesource.com mirror.
	if [[ ! "$checksum" =~ ^[0-9a-f]{64}$ ]] || \
			[[ ! "$url" =~ ^https://git\.kernel\.org/pub/scm/(.+)\.git/snapshot/(([^/]+)-([^/]+))\.tar\.gz$ ]]; then
		return $status
	fi
	local repo="${BASH_REMATCH[1]}" prefix="${BASH_REMATCH[2]}" version="${BASH_REMATCH[4]}"
	local mirror="https://kernel.googlesource.com/pub/scm/$repo" tmpdir ref attempt
	echo "Making $prefix.tar.gz from $mirror instead"
	tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/termux-re-snapshot.XXXXXXXX")"
	for attempt in 1 2 3; do
		rm -rf "$tmpdir/git"
		git clone --quiet --bare --filter=blob:none "$mirror" "$tmpdir/git" && break
		sleep 30
	done
	for ref in "$prefix" "v$version" "$version"; do
		if git -C "$tmpdir/git" rev-parse --verify --quiet "refs/tags/$ref^{commit}" >/dev/null; then
			git -C "$tmpdir/git" archive --format=tar --prefix="$prefix/" "refs/tags/$ref" | gzip -cn > "$tmpdir/snapshot.tar.gz" || continue
			if [[ "$(sha256sum "$tmpdir/snapshot.tar.gz" | cut -d' ' -f1)" == "$checksum" ]]; then
				mv "$tmpdir/snapshot.tar.gz" "$destination"
				rm -rf "$tmpdir"
				echo "Made $prefix.tar.gz from tag $ref of $mirror, checksum matches"
				return 0
			fi
			echo "$prefix.tar.gz made from tag $ref of $mirror has a different checksum"
		fi
	done
	rm -rf "$tmpdir"
	return $status
}
EOF
echo "termux_download falls back to kernel.googlesource.com, archive.apache.org and invisible-island.net"

### command-not-found database
CNF=packages/command-not-found/build.sh
grep -q 'termux_re_upstream_termux_step_pre_configure' "$CNF" && fail "$CNF is already patched"
cat >> "$CNF" <<'EOF'

# Termux RE: added by apt-repo/fix-termux-packages.sh of the Termux RE app repository. Build the
# database from the file list of TERMUX_RE_COMMAND_NOT_FOUND_REPO, or of the official main
# repository with the build's prefix.
if declare -F termux_step_pre_configure >/dev/null; then eval "termux_re_upstream_$(declare -f termux_step_pre_configure)"; else termux_re_upstream_termux_step_pre_configure() { :; }; fi
termux_step_pre_configure() {
	termux_re_upstream_termux_step_pre_configure
	local dir="$TERMUX_PKG_TMPDIR/termux-re-command-not-found"
	rm -rf "$dir"
	mkdir -p "$dir/main/dists/stable" "$dir/empty/dists/stable"
	local contents="dists/stable/Contents-$TERMUX_ARCH.gz"
	if [ -n "${TERMUX_RE_COMMAND_NOT_FOUND_REPO:-}" ] && \
			curl -fsSL --retry 3 -o "$dir/main/$contents" "$TERMUX_RE_COMMAND_NOT_FOUND_REPO/$contents?nocache=$RANDOM$RANDOM"; then
		echo "Using the file list of $TERMUX_RE_COMMAND_NOT_FOUND_REPO"
	else
		echo "Using the file list of the official repository"
		termux_download "https://packages-cf.termux.dev/apt/termux-main/$contents" "$dir/official.gz" SKIP_CHECKSUM
		gzip -dc "$dir/official.gz" | sed "s|^data/data/com\.termux/files/usr/|${TERMUX_PREFIX#/}/|" | gzip -n > "$dir/main/$contents"
	fi
	gzip -n < /dev/null > "$dir/empty/dists/stable/Contents-$TERMUX_ARCH.gz"
	jq -n --arg main "file://$dir/main" --arg empty "file://$dir/empty" '{
		"pkg_format": "debian",
		"packages": {"name": "termux-main", "distribution": "stable", "component": "main", "url": $main},
		"root-packages": {"name": "termux-root", "distribution": "stable", "component": "main", "url": $empty},
		"x11-packages": {"name": "termux-x11", "distribution": "stable", "component": "main", "url": $empty}
	}' > "$dir/repo.json"
	grep -q 'join(TERMUX_SCRIPTDIR, "repo.json")' "$TERMUX_PKG_SRCDIR/generate-db.js" || \
		termux_error_exit "generate-db.js no longer reads repo.json from TERMUX_SCRIPTDIR"
	sed -i "s|join(TERMUX_SCRIPTDIR, \"repo.json\")|\"$dir/repo.json\"|" "$TERMUX_PKG_SRCDIR/generate-db.js"
}
EOF
echo "command-not-found uses the official file list with the build's prefix"
