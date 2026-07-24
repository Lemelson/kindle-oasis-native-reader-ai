#!/bin/sh
CONFIG="/mnt/us/native-reader-research/ai-assistant.properties"
LOG="/mnt/us/native-reader-research/nativepatch.log"
TS="$(date +%Y%m%d-%H%M%S)"

set_prop() {
  KEY="$1"
  VALUE="$2"
  TMP="$CONFIG.tmp"
  if [ -f "$CONFIG" ] && grep -q "^$KEY=" "$CONFIG"; then
    sed "s|^$KEY=.*|$KEY=$VALUE|" "$CONFIG" > "$TMP" && mv "$TMP" "$CONFIG"
  else
    echo "$KEY=$VALUE" >> "$CONFIG"
  fi
}

mkdir -p /mnt/us/native-reader-research
touch "$CONFIG"
set_prop diagnostics_enabled true
set_prop diagnostic_attempts 1
set_prop gemini_base_url https://generativelanguage.googleapis.com/v1beta
set_prop gemini_models gemini-3.6-flash
set_prop gemini_thinking_level high
set_prop gemini_daily_request_limit 20
set_prop gemini_quota_window_ms 86400000
set_prop gemini_quota_cooldown_ms 3600000
echo "[$TS] AI diagnostics enabled" >> "$LOG"
exit 0
