#!/bin/sh
# Generates the 1024x500 store feature graphic (no alpha) from
# feature-graphic.svg into build/feature-graphic.png (not committed).
set -e -u
cd "$(dirname "$0")"
. ./render-svg.sh

render_svg feature-graphic.svg 1024 500 build/feature-graphic.png "#16191D"
