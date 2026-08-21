#!/bin/sh

CONFIG="${AI_CONFIG_PATH:-/mnt/us/native-reader-research/ai-assistant.properties}"
LOG="${AI_LOG_PATH:-/mnt/us/native-reader-research/nativepatch.log}"
MODEL="deepseek/deepseek-v4-flash-0731"
TS="$(date +%Y%m%d-%H%M%S)"
TMP="$CONFIG.tmp.$$"

cleanup() {
  rm -f "$TMP"
}

fail() {
  echo "ERROR: $*" >> "$LOG"
  exit 1
}

set_prop() {
  KEY="$1"
  VALUE="$2"

  if grep -q "^$KEY=" "$CONFIG"; then
    sed "s|^$KEY=.*|$KEY=$VALUE|" "$CONFIG" > "$TMP" ||
      fail "failed to update $KEY"
  else
    cp "$CONFIG" "$TMP" || fail "failed to stage $KEY"
    printf '%s=%s\n' "$KEY" "$VALUE" >> "$TMP" ||
      fail "failed to append $KEY"
  fi
  mv "$TMP" "$CONFIG" || fail "failed to activate $KEY"
}

remove_prop() {
  KEY="$1"
  if ! grep -q "^$KEY=" "$CONFIG"; then
    return
  fi
  sed "/^$KEY=/d" "$CONFIG" > "$TMP" ||
    fail "failed to remove legacy $KEY"
  mv "$TMP" "$CONFIG" || fail "failed to activate $KEY removal"
}

trap cleanup 0 HUP INT TERM
mkdir -p "${CONFIG%/*}" || exit 1
touch "$CONFIG" || exit 1

set_prop model "$MODEL"
set_prop reasoning_effort high
remove_prop pro_model
remove_prop pro_fallback_model

grep -q "^model=$MODEL$" "$CONFIG" ||
  fail "model verification failed"
grep -q "^reasoning_effort=high$" "$CONFIG" ||
  fail "reasoning effort verification failed"
! grep -q "^pro_model=" "$CONFIG" ||
  fail "legacy pro_model removal failed"
! grep -q "^pro_fallback_model=" "$CONFIG" ||
  fail "legacy pro_fallback_model removal failed"

echo "[$TS] switched OpenRouter to DeepSeek V4 Flash 0731 High" >> "$LOG"
sync
exit 0
