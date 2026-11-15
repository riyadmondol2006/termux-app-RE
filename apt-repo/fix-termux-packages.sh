#!/usr/bin/env bash
# Works around problems of termux-packages builds on GitHub Actions runners, in a termux-packages
# checkout. Used by the Build bootstraps and Build packages workflows for any package name.
#
# - scripts/build-bootstraps.sh builds packages by name, and some of its names are now
#   subpackages, like bzip2 of libbz2, which build-package.sh can't build by name. They are
#   replaced by the package that builds them; their .deb files end up in output/ like before.
# - Source downloads that fail are tried from a second place, and only used if they have exactly
#   the checksum the package expects:
#   - git.kernel.org often times out from GitHub Actions runners, for example for the dash
#     sources. Its snapshots are `git archive | gzip -n` of a tag, so termux_download makes them
#     from the kernel.googlesource.com mirror of the repository instead.
#   - downloads.apache.org only has the newest releases, older ones move to archive.apache.org,
#     for example apr-util 1.6.5, a build dependency of git.
#   - invisible-mirror.net returns a small web page instead of the file, for example for the
#     dialog sources that termux-tools needs. invisible-island.net is the site it mirrors.
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
