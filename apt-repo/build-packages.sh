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
# - build-package.sh breaks a dependency cycle by installing the previous version of a package in
#   it from the repository, for example libmpg123 for ffmpeg (libmpg123 needs pulseaudio to build,
#   which needs libsndfile, which needs libmpg123). When the repository doesn't have one yet, that
#   package is built once without its dependencies (-s), which build-package.sh then uses instead,
#   and the build is tried again.
#
# Built .deb files end up in output/. Writes a summary in markdown to $SUMMARY_FILE and the
# packages that were asked for to $ROOTS_FILE if set, as "<package> <arch>" lines, which the
# repository keeps with their runtime dependencies on that architecture. Name subpackages, like
# python-ensurepip-wheels, to have them kept too.
# Exits with 1 if a package failed or was not built in time. $BUILD_JOBS, if set, is the number of
# parallel jobs per build (build-package.sh -j), for builds that run out of memory.
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
ROOTS_FILE="${ROOTS_FILE:-/dev/null}"
RUNNER_TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$RUNNER_TEMP_DIR"' EXIT

[ -f build-package.sh ] || { echo "Run this in a termux-packages checkout" >&2; exit 1; }

declare -a BUILD_ARGS=(-C -a "$ARCH")
if [ -n "${BUILD_JOBS:-}" ]; then
	[[ "$BUILD_JOBS" =~ ^[1-9][0-9]*$ ]] || { echo "Invalid BUILD_JOBS '$BUILD_JOBS'" >&2; exit 1; }
	BUILD_ARGS+=(-j "$BUILD_JOBS")
fi
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
declare -A NAMES=() # the requested names that each package builds
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
		continue
	fi
	echo "$name $ARCH" >> "$ROOTS_FILE"
	NAMES[$pkg]="${NAMES[$pkg]:-} $name"
	if [[ " ${PACKAGES[*]} " != *" $pkg "* ]]; then
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

declare -a BUILT=() SKIPPED=() FAILED=() NOT_STARTED=() CYCLES=()
for pkg in "${PACKAGES[@]}"; do
	version="$(package_version "$pkg")"
	published=true
	for name in ${NAMES[$pkg]}; do
		grep -qxF "$name $version" <<< "$PUBLISHED" || published=false
	done
	if [ "$FORCE" != "true" ] && [ -n "$version" ] && [ "$published" = true ]; then
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
	attempt=0 download_retries=0
	while true; do
		attempt=$(( attempt + 1 ))
		echo "::group::Building $pkg $version for $ARCH$([ "$attempt" -gt 1 ] && echo ", attempt $attempt")"
		timeout --kill-after=120 "$remaining" ./scripts/run-docker.sh ./build-package.sh "${BUILD_ARGS[@]}" "$pkg" < /dev/null 2>&1 | tee "$RUNNER_TEMP_DIR/build.log"
		status=${PIPESTATUS[0]}
		echo "::endgroup::"
		[ "$status" -eq 0 ] || [ "$status" -eq 124 ] && break
		cycle="$(sed -n "s/^A circular dependency was found on '\([a-z0-9][a-z0-9+._-]*\)'.*/\1/p" "$RUNNER_TEMP_DIR/build.log" | tail -n 1)"
		if [ -n "$cycle" ] && grep -qF "ERROR: Download of $cycle from" "$RUNNER_TEMP_DIR/build.log" && \
				[[ " ${CYCLES[*]} " != *" $cycle "* ]]; then
			CYCLES+=("$cycle")
			remaining=$(( DEADLINE - $(date +%s) ))
			[ "$remaining" -gt 600 ] || break
			echo "::warning::Building $cycle for $ARCH without its dependencies first, to break a dependency cycle of $pkg"
			echo "::group::Building $cycle for $ARCH without its dependencies"
			timeout --kill-after=120 "$remaining" ./scripts/run-docker.sh ./build-package.sh -s "${BUILD_ARGS[@]}" "$cycle" < /dev/null 2>&1
			cycle_status=$?
			echo "::endgroup::"
			[ "$cycle_status" -eq 0 ] || break
			remaining=$(( DEADLINE - $(date +%s) ))
			continue
		fi
		grep -qE "^Failed to download |Failed to download package repository metadata" "$RUNNER_TEMP_DIR/build.log" || break
		download_retries=$(( download_retries + 1 ))
		remaining=$(( DEADLINE - $(date +%s) - 120 ))
		[ "$download_retries" -le 3 ] && [ "$remaining" -gt 600 ] || break
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
