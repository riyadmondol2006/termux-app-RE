# Sourced by the generate-*.sh scripts in this directory.
#
# Requires ImageMagick 7 (`magick`) built with librsvg, or `rsvg-convert`
# (librsvg) together with ImageMagick. zopflipng is used if available.

if command -v magick >/dev/null 2>&1; then
	MAGICK=magick
elif command -v convert >/dev/null 2>&1; then
	MAGICK=convert
else
	echo "ImageMagick (magick or convert) is required" 1>&2
	exit 1
fi

# render_svg <input.svg> <width> <height> <output.png> [background]
#
# Renders the SVG at exactly <width>x<height> pixels as an 8-bit PNG without
# metadata, so that re-running the scripts gives byte-identical files.
# If [background] is given, the image is flattened onto it and written
# without an alpha channel (store graphics such as the feature graphic).
render_svg() {
	mkdir -p "$(dirname "$4")"
	if command -v rsvg-convert >/dev/null 2>&1; then
		rsvg-convert -w "$2" -h "$3" -o "$4" "$1"
		set -- "$4" "$2" "$3" "$4" "${5:-}"
	fi
	if [ -n "${5:-}" ]; then
		$MAGICK -background none -size "$2x$3" "$1" \
			-background "$5" -flatten -alpha off -strip "PNG24:$4"
	else
		$MAGICK -background none -size "$2x$3" "$1" -strip "PNG32:$4"
	fi
	if command -v zopflipng >/dev/null 2>&1; then
		zopflipng -y "$4" "$4" >/dev/null
	fi
	echo "$4 (${2}x${3})"
}
