#!/bin/sh
# Generates the Android TV banner from tv-banner.svg:
# - app/src/main/res/drawable/banner.png: 320x180, used by android:banner.
# - build/tv-banner.png: 1280x720 store version (same aspect ratio, not committed).
set -e -u
cd "$(dirname "$0")"
. ./render-svg.sh

render_svg tv-banner.svg 320 180 ../app/src/main/res/drawable/banner.png "#16191D"
render_svg tv-banner.svg 1280 720 build/tv-banner.png "#16191D"
