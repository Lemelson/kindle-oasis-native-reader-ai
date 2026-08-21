#!/bin/sh

LOG="/mnt/us/native-reader-research/nativepatch.log"
READER_PATCH="/mnt/us/native-reader-research/patched/Reader-plugin.v29.jar"
SDK_PATCH="/mnt/us/native-reader-research/patched/ReaderSDK-impl.ai-v29.jar"
READER_STABLE="/mnt/us/native-reader-research/patched/Reader-plugin.v3.jar"
SDK_ORIGINAL="/mnt/us/native-reader-research/jars/ReaderSDK-impl.jar"
READER_TARGET="/opt/amazon/ebook/lib/Reader-plugin.jar"
SDK_TARGET="/opt/amazon/ebook/lib/ReaderSDK-impl.jar"
CONFIG="/mnt/us/native-reader-research/ai-assistant.properties"
BACKUP_DIR="/mnt/us/native-reader-research/backups"
TS="$(date +%Y%m%d-%H%M%S)"

EXPECTED_READER_SHA256="e43a30553e5fc1f28b83493da222772de14b4e80a91fc8120bee9bfd39c80f4e"
EXPECTED_SDK_SHA256="c77251ea08b4165f7a0e8cb0928cbb59627042bf8205bdfda2180c2f154ba377"
EXPECTED_STABLE_READER_SHA256="d978e1e97c7d2042176aa8d12e40e24582d335770e6141a14dffb9659fe1f907"
EXPECTED_ORIGINAL_SDK_SHA256="5acc17fb817e67a8d849d1be7c999b6f6d56a5a8c39cae3ab1826cde325abf40"

READER_BACKUP="$BACKUP_DIR/Reader-plugin.jar.before-ai-note-v29.$TS"
SDK_BACKUP="$BACKUP_DIR/ReaderSDK-impl.jar.before-ai-note-v29.$TS"
CONFIG_BACKUP="$BACKUP_DIR/ai-assistant.properties.before-ai-note-v29.$TS"
READER_STAGE="$READER_TARGET.v29.$$"
SDK_STAGE="$SDK_TARGET.v29.$$"
READER_RESTORE_STAGE="$READER_TARGET.restore.$$"
SDK_RESTORE_STAGE="$SDK_TARGET.restore.$$"
CONFIG_STAGE="$CONFIG.v29.$$"
ROOT_RW=0
CONFIG_CHANGED=0
INSTALL_COMPLETE=0

fail() {
  echo "ERROR: $*"
  exit 1
}

verify_sha256() {
  LABEL="$1"
  FILE="$2"
  EXPECTED="$3"

  case "$EXPECTED" in
    REPLACE_WITH_*)
      fail "$LABEL expected SHA-256 has not been configured"
      ;;
  esac

  [ -f "$FILE" ] || fail "missing $LABEL: $FILE"
  ACTUAL="$(sha256sum "$FILE" 2>/dev/null | awk '{print $1}')"
  [ "$ACTUAL" = "$EXPECTED" ] ||
    fail "$LABEL SHA-256 mismatch: expected $EXPECTED, got ${ACTUAL:-unavailable}"
}

verify_backup_copy() {
  LABEL="$1"
  SOURCE="$2"
  COPY="$3"

  SOURCE_SHA256="$(sha256sum "$SOURCE" 2>/dev/null | awk '{print $1}')"
  COPY_SHA256="$(sha256sum "$COPY" 2>/dev/null | awk '{print $1}')"
  [ -n "$SOURCE_SHA256" ] && [ "$SOURCE_SHA256" = "$COPY_SHA256" ] ||
    fail "$LABEL backup verification failed"
}

set_prop() {
  KEY="$1"
  VALUE="$2"
  awk -v key="$KEY" -v value="$VALUE" '
    BEGIN { found = 0 }
    index($0, key "=") == 1 {
      if (!found) {
        print key "=" value
        found = 1
      }
      next
    }
    { print }
    END {
      if (!found) {
        print key "=" value
      }
    }
  ' "$CONFIG" > "$CONFIG_STAGE" || return 1
  mv -f "$CONFIG_STAGE" "$CONFIG" || return 1
}

remove_prop() {
  KEY="$1"
  awk -v key="$KEY" '
    index($0, key "=") != 1 { print }
  ' "$CONFIG" > "$CONFIG_STAGE" || return 1
  mv -f "$CONFIG_STAGE" "$CONFIG" || return 1
}

restore_config_after_failure() {
  if [ "$CONFIG_CHANGED" -eq 1 ] && [ -f "$CONFIG_BACKUP" ]; then
    if cp -p "$CONFIG_BACKUP" "$CONFIG"; then
      echo "restored AI config from $CONFIG_BACKUP"
      CONFIG_CHANGED=0
    else
      echo "ERROR: failed to restore AI config from $CONFIG_BACKUP"
    fi
  fi
}

restore_jars_after_failure() {
  echo "install failed after root became writable; restoring both pre-install jars"

  if [ -f "$READER_BACKUP" ]; then
    if cp -p "$READER_BACKUP" "$READER_RESTORE_STAGE" &&
       chown root:root "$READER_RESTORE_STAGE" &&
       chmod 0644 "$READER_RESTORE_STAGE" &&
       mv -f "$READER_RESTORE_STAGE" "$READER_TARGET"; then
      echo "restored Reader-plugin.jar from $READER_BACKUP"
    else
      echo "ERROR: failed to restore Reader-plugin.jar from $READER_BACKUP"
    fi
  fi

  if [ -f "$SDK_BACKUP" ]; then
    if cp -p "$SDK_BACKUP" "$SDK_RESTORE_STAGE" &&
       chown root:root "$SDK_RESTORE_STAGE" &&
       chmod 0644 "$SDK_RESTORE_STAGE" &&
       mv -f "$SDK_RESTORE_STAGE" "$SDK_TARGET"; then
      echo "restored ReaderSDK-impl.jar from $SDK_BACKUP"
    else
      echo "ERROR: failed to restore ReaderSDK-impl.jar from $SDK_BACKUP"
    fi
  fi
  sync
}

cleanup() {
  STATUS=$?
  trap - 0
  trap '' HUP INT TERM

  if [ "$STATUS" -ne 0 ] && [ "$ROOT_RW" -eq 1 ] &&
     [ "$INSTALL_COMPLETE" -eq 0 ]; then
    restore_jars_after_failure
  fi
  if [ "$STATUS" -ne 0 ] && [ "$INSTALL_COMPLETE" -eq 0 ]; then
    restore_config_after_failure
  fi

  rm -f "$READER_STAGE" "$SDK_STAGE" "$CONFIG_STAGE" \
    "$READER_RESTORE_STAGE" "$SDK_RESTORE_STAGE"

  if [ "$ROOT_RW" -eq 1 ]; then
    if mntroot ro; then
      ROOT_RW=0
    else
      echo "ERROR: best-effort root remount read-only failed; rebooting"
      sync
      reboot
    fi
  fi

  if [ "$STATUS" -ne 0 ]; then
    echo "[$TS] install-ai-note-v29 failed"
  fi
  exit "$STATUS"
}

mkdir -p "$BACKUP_DIR" || exit 1
exec >> "$LOG" 2>&1
trap cleanup 0
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

echo "[$TS] install-ai-note-v29 start"

verify_sha256 "v29 reader patch" "$READER_PATCH" "$EXPECTED_READER_SHA256"
verify_sha256 "v29 SDK patch" "$SDK_PATCH" "$EXPECTED_SDK_SHA256"
verify_sha256 "stable reader rollback jar" "$READER_STABLE" "$EXPECTED_STABLE_READER_SHA256"
verify_sha256 "original SDK rollback jar" "$SDK_ORIGINAL" "$EXPECTED_ORIGINAL_SDK_SHA256"

[ -f "$READER_TARGET" ] || fail "missing system reader jar: $READER_TARGET"
[ -f "$SDK_TARGET" ] || fail "missing system SDK jar: $SDK_TARGET"
[ -f "$CONFIG" ] || fail "missing AI config: $CONFIG"

cp -p "$READER_TARGET" "$READER_BACKUP" || fail "failed to back up Reader-plugin.jar"
cp -p "$SDK_TARGET" "$SDK_BACKUP" || fail "failed to back up ReaderSDK-impl.jar"
cp -p "$CONFIG" "$CONFIG_BACKUP" || fail "failed to back up AI config"

verify_backup_copy "Reader-plugin.jar" "$READER_TARGET" "$READER_BACKUP"
verify_backup_copy "ReaderSDK-impl.jar" "$SDK_TARGET" "$SDK_BACKUP"
verify_backup_copy "AI config" "$CONFIG" "$CONFIG_BACKUP"

set_prop vpn_enabled false || fail "failed to disable legacy VPN setting"
CONFIG_CHANGED=1
set_prop model deepseek/deepseek-v4-flash-0731 ||
  fail "failed to select DeepSeek V4 Flash 0731"
set_prop gemini_models gemini-3.7-flash ||
  fail "failed to select Gemini 3.7 Flash"
set_prop gemini_thinking_level high ||
  fail "failed to enable high Gemini thinking"
set_prop reasoning_effort high ||
  fail "failed to enable high OpenRouter reasoning"
set_prop reasoning_exclude false ||
  fail "failed to expose OpenRouter reasoning stream"
set_prop max_tokens 6750 ||
  fail "failed to set shared output budget"
remove_prop pro_model || fail "failed to remove legacy pro_model"
remove_prop pro_fallback_model ||
  fail "failed to remove legacy pro_fallback_model"
[ "$(awk -F= '$1 == "vpn_enabled" { print $2; exit }' "$CONFIG")" = "false" ] ||
  fail "legacy VPN setting was not disabled"
[ "$(awk -F= '$1 == "model" { print $2; exit }' "$CONFIG")" =
    "deepseek/deepseek-v4-flash-0731" ] ||
  fail "DeepSeek model setting did not persist"
[ "$(awk -F= '$1 == "gemini_models" { print $2; exit }' "$CONFIG")" =
    "gemini-3.7-flash" ] ||
  fail "Gemini model setting did not persist"

sha256sum "$READER_TARGET" "$SDK_TARGET" "$READER_PATCH" "$SDK_PATCH" \
  "$READER_BACKUP" "$SDK_BACKUP" "$CONFIG_BACKUP" || fail "failed to record pre-install hashes"

ROOT_RW=1
if ! mntroot rw; then
  ROOT_RW=0
  fail "failed to remount root read-write"
fi

cp "$READER_PATCH" "$READER_STAGE" || fail "failed to stage Reader-plugin.jar"
cp "$SDK_PATCH" "$SDK_STAGE" || fail "failed to stage ReaderSDK-impl.jar"
chown root:root "$READER_STAGE" "$SDK_STAGE" || fail "failed to set staged jar ownership"
chmod 0644 "$READER_STAGE" "$SDK_STAGE" || fail "failed to set staged jar permissions"

verify_sha256 "staged v29 reader patch" "$READER_STAGE" "$EXPECTED_READER_SHA256"
verify_sha256 "staged v29 SDK patch" "$SDK_STAGE" "$EXPECTED_SDK_SHA256"

mv -f "$READER_STAGE" "$READER_TARGET" || fail "failed to activate Reader-plugin.jar"
mv -f "$SDK_STAGE" "$SDK_TARGET" || fail "failed to activate ReaderSDK-impl.jar"

verify_sha256 "installed v29 reader patch" "$READER_TARGET" "$EXPECTED_READER_SHA256"
verify_sha256 "installed v29 SDK patch" "$SDK_TARGET" "$EXPECTED_SDK_SHA256"
sync || fail "failed to sync installed jars"
INSTALL_COMPLETE=1

if mntroot ro; then
  ROOT_RW=0
else
  echo "WARNING: root remount read-only failed; reboot will restore mount state"
fi

echo "[$TS] install-ai-note-v29 done; live thinking enabled; rebooting"
reboot
exit 0
