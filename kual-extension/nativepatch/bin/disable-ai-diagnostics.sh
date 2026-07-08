#!/bin/sh
CONFIG="/mnt/us/native-reader-research/ai-assistant.properties"
LOG="/mnt/us/native-reader-research/nativepatch.log"
TS="$(date +%Y%m%d-%H%M%S)"

if [ -f "$CONFIG" ] && grep -q "^diagnostics_enabled=" "$CONFIG"; then
  sed "s|^diagnostics_enabled=.*|diagnostics_enabled=false|" "$CONFIG" > "$CONFIG.tmp" \
    && mv "$CONFIG.tmp" "$CONFIG"
else
  echo "diagnostics_enabled=false" >> "$CONFIG"
fi
echo "[$TS] AI diagnostics disabled" >> "$LOG"
exit 0
