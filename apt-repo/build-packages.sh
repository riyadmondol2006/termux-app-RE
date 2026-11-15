#!/usr/bin/env bash
# Builds packages for one architecture in a termux-packages checkout configured by
# patch-termux-packages.sh, with its Docker image. Used by the Build packages workflow.
#
# - Subpackage names are built through their parent package, e.g. curl through libcurl.
# - Packages whose current version is already in the repository are skipped unless forced.
# - Once the repository exists, dependencies are downloaded from it (`build-package.sh -I`) when
#   it has the exact version termux-packages wants, and built otherwise.
# - Packages are built one at a time, so one that fails doesn't stop the others. A build that
#   failed to download sources is tried again. No package is started after the deadline, and a
#   running build is stopped at it, so whatever was built in time can still be uploaded before
#   the job time limit.
#
# Built .deb files end up in output/. Writes a summary in markdown to $SUMMARY_FILE if set and
# exits with 1 if a package failed or was not built in time.
#
# Usage: build-packages.sh <arch> <repository url> <deadline as unix time> <force: true|false> <package>...

set -uo pipefail

if [ $# -lt 5 ]; then
	echo "Usage: $0 <arch> <repository url> <deadline as unix time> <force: true|false> <package>..." >&2
	exit 1
fi
ARCH="$1" REPO_URL="${2%/}" DEADLINE="$3" FORCE="$4"
shift 4
SUMMARY_FILE="${SUMMARY_FILE:-/dev/null}"
RUNNER_TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$RUNNER_TEMP_DIR"' EXIT

[ -f build-package.sh ] || { echo "Run this in a termux-packages checkout" >&2; exit 1; }

declare -a BUILD_ARGS=(-C -a "$ARCH")
if curl -fsSL --retry 3 -o /dev/null "$REPO_URL/dists/stable/InRelease?nocache=$RANDOM$RANDOM"; then
	echo "Downloading dependencies from $REPO_URL where possible"
	BUILD_ARGS=(-I "${BUILD_ARGS[@]}")
	PUBLISHED="$(curl -fsSL --retry 3 "$REPO_URL/dists/stable/main/binary-$ARCH/Packages?nocache=$RANDOM$RANDOM" |
		awk '/^Package:/ { p = $2 } /^Version:/ { print p " " $2 }')" || PUBLISHED=""
else
	echo "No repository at $REPO_URL yet, building all dependencies"
	PUBLISHED=""
fi

# Resolve subpackages to the package that builds them, without duplicates.
declare -a PACKAGES=()
declare -a UNKNOWN=()
for name in "$@"; do
	pkg=""
	if [ -f "packages/$name/build.sh" ]; then
		pkg="$name"
	else
		subpackage="$(compgen -G "packages/*/$name.subpackage.sh" | head -n 1)"
		[ -n "$subpackage" ] && pkg="$(basename "$(dirname "$subpackage")")"
	fi
	if [ -z "$pkg" ]; then
		UNKNOWN+=("$name")
	elif [[ " ${PACKAGES[*]} " != *" $pkg "* ]]; then
		[ "$pkg" != "$name" ] && echo "Building $name through $pkg"
		PACKAGES+=("$pkg")
	fi
done

package_version() {
	bash -c 'export TERMUX_ARCH="$1" TERMUX_INSTALL_DEPS=true
		source scripts/properties.sh >/dev/null 2>&1
		source scripts/build/termux_extract_dep_info.sh
		termux_extract_dep_info "$2" "packages/$2"' _ "$ARCH" "$1" 2>/dev/null | tail -n 1 | cut -d' ' -f2
}

declare -a BUILT=() SKIPPED=() FAILED=() NOT_STARTED=()
for pkg in "${PACKAGES[@]}"; do
	version="$(package_version "$pkg")"
	if [ "$FORCE" != "true" ] && [ -n "$version" ] && grep -qxF "$pkg $version" <<< "$PUBLISHED"; then
		echo "Skipping $pkg $version, it is already in the repository"
		SKIPPED+=("$pkg $version")
		continue
	fi
	remaining=$(( DEADLINE - $(date +%s) ))
	if [ "$remaining" -lt 300 ]; then
		NOT_STARTED+=("$pkg")
		continue
	fi
	start=$(date +%s)
	# A build that failed to download sources is tried again. It resumes, build-package.sh skips
	# the dependencies that were built already.
	for attempt in 1 2 3 4; do
		echo "::group::Building $pkg $version for $ARCH$([ "$attempt" -gt 1 ] && echo ", attempt $attempt")"
		timeout --kill-after=120 "$remaining" ./scripts/run-docker.sh ./build-package.sh "${BUILD_ARGS[@]}" "$pkg" < /dev/null 2>&1 | tee "$RUNNER_TEMP_DIR/build.log"
		status=${PIPESTATUS[0]}
		echo "::endgroup::"
		[ "$status" -eq 0 ] || [ "$status" -eq 124 ] && break
		grep -qE "^Failed to download |Failed to download package repository metadata" "$RUNNER_TEMP_DIR/build.log" || break
		remaining=$(( DEADLINE - $(date +%s) - 120 ))
		[ "$attempt" -lt 4 ] && [ "$remaining" -gt 600 ] || break
		echo "::warning::Building $pkg for $ARCH failed to download sources, trying again in 2 minutes"
		sleep 120
	done
	minutes=$(( ($(date +%s) - start) / 60 ))
	if [ "$status" -eq 0 ]; then
		echo "Built $pkg $version in $minutes min"
		BUILT+=("$pkg $version ($minutes min)")
	else
		[ "$status" -eq 124 ] && echo "::error::Building $pkg for $ARCH was stopped at the deadline after $minutes min" \
			|| echo "::error::Building $pkg for $ARCH failed with exit code $status after $minutes min"
		FAILED+=("$pkg ($([ "$status" -eq 124 ] && echo "stopped at the deadline" || echo "exit code $status"), $minutes min)")
	fi
done

list() { local item; for item in "$@"; do echo "- $item"; done; }
{
	echo "### $ARCH"
	echo
	[ ${#BUILT[@]} -gt 0 ] && { echo "Built:"; list "${BUILT[@]}"; echo; }
	[ ${#SKIPPED[@]} -gt 0 ] && { echo "Already in the repository:"; list "${SKIPPED[@]}"; echo; }
	[ ${#FAILED[@]} -gt 0 ] && { echo "Failed:"; list "${FAILED[@]}"; echo; }
	[ ${#NOT_STARTED[@]} -gt 0 ] && { echo "Not started, out of time (run again to build them):"; list "${NOT_STARTED[@]}"; echo; }
	[ ${#UNKNOWN[@]} -gt 0 ] && { echo "Unknown packages:"; list "${UNKNOWN[@]}"; echo; }
	echo "Packages in output/: $(find output -maxdepth 1 -name '*.deb' 2>/dev/null | wc -l)"
} | tee -a "$SUMMARY_FILE"

[ ${#FAILED[@]} -eq 0 ] && [ ${#NOT_STARTED[@]} -eq 0 ] && [ ${#UNKNOWN[@]} -eq 0 ]
