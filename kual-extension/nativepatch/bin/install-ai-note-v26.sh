#!/bin/sh

LOG="/mnt/us/native-reader-research/nativepatch.log"
READER_PATCH="/mnt/us/native-reader-research/patched/Reader-plugin.v26.jar"
SDK_PATCH="/mnt/us/native-reader-research/patched/ReaderSDK-impl.ai-v26.jar"
READER_STABLE="/mnt/us/native-reader-research/patched/Reader-plugin.v3.jar"
SDK_ORIGINAL="/mnt/us/native-reader-research/jars/ReaderSDK-impl.jar"
READER_TARGET="/opt/amazon/ebook/lib/Reader-plugin.jar"
SDK_TARGET="/opt/amazon/ebook/lib/ReaderSDK-impl.jar"
BACKUP_DIR="/mnt/us/native-reader-research/backups"
TS="$(date +%Y%m%d-%H%M%S)"

EXPECTED_READER_SHA256="ec70bfbcdf276b071437e9fe8e1a4e50b68c2f1c03a2bf2eca6012edc3136466"
EXPECTED_SDK_SHA256="ebe5a91d04ebe2fc18a817dcf4a2cabe225affa48ed3f5d488dbcd34861f2844"
EXPECTED_STABLE_READER_SHA256="d978e1e97c7d2042176aa8d12e40e24582d335770e6141a14dffb9659fe1f907"
EXPECTED_ORIGINAL_SDK_SHA256="5acc17fb817e67a8d849d1be7c999b6f6d56a5a8c39cae3ab1826cde325abf40"

READER_BACKUP="$BACKUP_DIR/Reader-plugin.jar.before-ai-note-v26.$TS"
SDK_BACKUP="$BACKUP_DIR/ReaderSDK-impl.jar.before-ai-note-v26.$TS"
READER_STAGE="$READER_TARGET.v26.$$"
SDK_STAGE="$SDK_TARGET.v26.$$"
READER_RESTORE_STAGE="$READER_TARGET.restore.$$"
SDK_RESTORE_STAGE="$SDK_TARGET.restore.$$"
ROOT_RW=0
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

restore_backups_after_failure() {
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
    restore_backups_after_failure
  fi

  rm -f "$READER_STAGE" "$SDK_STAGE" \
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
    echo "[$TS] install-ai-note-v26 failed"
  fi
  exit "$STATUS"
}

mkdir -p "$BACKUP_DIR" || exit 1
exec >> "$LOG" 2>&1
trap cleanup 0
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

echo "[$TS] install-ai-note-v26 start"

verify_sha256 "v26 reader patch" "$READER_PATCH" "$EXPECTED_READER_SHA256"
verify_sha256 "v26 SDK patch" "$SDK_PATCH" "$EXPECTED_SDK_SHA256"
verify_sha256 "stable reader rollback jar" "$READER_STABLE" "$EXPECTED_STABLE_READER_SHA256"
verify_sha256 "original SDK rollback jar" "$SDK_ORIGINAL" "$EXPECTED_ORIGINAL_SDK_SHA256"

[ -f "$READER_TARGET" ] || fail "missing system reader jar: $READER_TARGET"
[ -f "$SDK_TARGET" ] || fail "missing system SDK jar: $SDK_TARGET"

cp -p "$READER_TARGET" "$READER_BACKUP" || fail "failed to back up Reader-plugin.jar"
cp -p "$SDK_TARGET" "$SDK_BACKUP" || fail "failed to back up ReaderSDK-impl.jar"

verify_backup_copy "Reader-plugin.jar" "$READER_TARGET" "$READER_BACKUP"
verify_backup_copy "ReaderSDK-impl.jar" "$SDK_TARGET" "$SDK_BACKUP"

sha256sum "$READER_TARGET" "$SDK_TARGET" "$READER_PATCH" "$SDK_PATCH" \
  "$READER_BACKUP" "$SDK_BACKUP" || fail "failed to record pre-install hashes"

ROOT_RW=1
if ! mntroot rw; then
  ROOT_RW=0
  fail "failed to remount root read-write"
fi

cp "$READER_PATCH" "$READER_STAGE" || fail "failed to stage Reader-plugin.jar"
cp "$SDK_PATCH" "$SDK_STAGE" || fail "failed to stage ReaderSDK-impl.jar"
chown root:root "$READER_STAGE" "$SDK_STAGE" || fail "failed to set staged jar ownership"
chmod 0644 "$READER_STAGE" "$SDK_STAGE" || fail "failed to set staged jar permissions"

verify_sha256 "staged v26 reader patch" "$READER_STAGE" "$EXPECTED_READER_SHA256"
verify_sha256 "staged v26 SDK patch" "$SDK_STAGE" "$EXPECTED_SDK_SHA256"

mv -f "$READER_STAGE" "$READER_TARGET" || fail "failed to activate Reader-plugin.jar"
mv -f "$SDK_STAGE" "$SDK_TARGET" || fail "failed to activate ReaderSDK-impl.jar"

verify_sha256 "installed v26 reader patch" "$READER_TARGET" "$EXPECTED_READER_SHA256"
verify_sha256 "installed v26 SDK patch" "$SDK_TARGET" "$EXPECTED_SDK_SHA256"
sync || fail "failed to sync installed jars"
INSTALL_COMPLETE=1

if mntroot ro; then
  ROOT_RW=0
else
  echo "WARNING: root remount read-only failed; reboot will restore mount state"
fi

echo "[$TS] install-ai-note-v26 done; rebooting"
reboot
exit 0
