#!/bin/sh
# Generates the large logo exports:
# - logo-512.png: used by README.md.
# - logo-banner.png: 1280x640, for the GitHub social preview.
# - fastlane/metadata/android/en-US/images/icon.png: 512x512 store icon.
# - build/logo-round-512.png: round variant (not committed).
set -e -u
cd "$(dirname "$0")"
. ./render-svg.sh

render_svg logo.svg 512 512 logo-512.png
render_svg logo-banner.svg 1280 640 logo-banner.png
render_svg logo.svg 512 512 ../fastlane/metadata/android/en-US/images/icon.png
render_svg logo-round.svg 512 512 build/logo-round-512.png
