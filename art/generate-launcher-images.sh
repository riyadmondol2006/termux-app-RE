#!/bin/sh
# Generates the legacy launcher icons (used before Android 8.0) from logo.svg
# and logo-round.svg. Android 8.0+ uses the adaptive icon instead:
# app/src/main/res/mipmap-anydpi-v26/ic_launcher*.xml, drawn from
# drawable/ic_background.xml, ic_foreground.xml and ic_monochrome.xml.
set -e -u
cd "$(dirname "$0")"
. ./render-svg.sh

for DENSITY in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
	case $DENSITY in
		mdpi) SIZE=48;;
		hdpi) SIZE=72;;
		xhdpi) SIZE=96;;
		xxhdpi) SIZE=144;;
		xxxhdpi) SIZE=192;;
	esac

	FOLDER=../app/src/main/res/mipmap-$DENSITY
	render_svg logo.svg $SIZE $SIZE "$FOLDER/ic_launcher.png"
	render_svg logo-round.svg $SIZE $SIZE "$FOLDER/ic_launcher_round.png"
done
