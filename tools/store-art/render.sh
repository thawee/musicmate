#!/bin/sh
# Renders the Play store icon (512x512) and feature graphic (1024x500) with headless Chrome.
# Play wants the feature graphic without alpha and the icon as 32-bit PNG: convert before upload
# (the published copies are in app/src/play/play/listings/en-US/graphics/).
set -e
cd "$(dirname "$0")"
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
"$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
  --window-size=512,512 --screenshot="$PWD/icon.png" "file://$PWD/icon.html"
"$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
  --window-size=1024,500 --screenshot="$PWD/feature.png" "file://$PWD/feature.html"
