#!/bin/sh

LOG="/mnt/us/native-reader-research/nativepatch.log"
SDK_TARGET="/opt/amazon/ebook/lib/ReaderSDK-impl.jar"
SDK_ORIGINAL="/mnt/us/native-reader-research/jars/ReaderSDK-impl.jar"
READER_TARGET="/opt/amazon/ebook/lib/Reader-plugin.jar"
READER_STABLE="/mnt/us/native-reader-research/patched/Reader-plugin.v3.jar"
BACKUP_DIR="/mnt/us/native-reader-research/backups"
TS="$(date +%Y%m%d-%H%M%S)"

EXPECTED_ORIGINAL_SDK_SHA256="5acc17fb817e67a8d849d1be7c999b6f6d56a5a8c39cae3ab1826cde325abf40"
EXPECTED_STABLE_READER_SHA256="d978e1e97c7d2042176aa8d12e40e24582d335770e6141a14dffb9659fe1f907"

SDK_BACKUP="$BACKUP_DIR/ReaderSDK-impl.jar.before-rollback-ai.$TS"
READER_BACKUP="$BACKUP_DIR/Reader-plugin.jar.before-rollback-ai.$TS"
SDK_STAGE="$SDK_TARGET.rollback.$$"
READER_STAGE="$READER_TARGET.rollback.$$"
SDK_RESTORE_STAGE="$SDK_TARGET.restore.$$"
READER_RESTORE_STAGE="$READER_TARGET.restore.$$"
ROOT_RW=0
ROLLBACK_COMPLETE=0

fail() {
  echo "ERROR: $*"
  exit 1
}

verify_sha256() {
  LABEL="$1"
  FILE="$2"
  EXPECTED="$3"

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

restore_pre_rollback_jars() {
  echo "rollback failed after root became writable; restoring pre-rollback jars"

  if [ -f "$READER_BACKUP" ]; then
    cp -p "$READER_BACKUP" "$READER_RESTORE_STAGE" &&
      chown root:root "$READER_RESTORE_STAGE" &&
      chmod 0644 "$READER_RESTORE_STAGE" &&
      mv -f "$READER_RESTORE_STAGE" "$READER_TARGET" ||
      echo "ERROR: failed to restore pre-rollback Reader-plugin.jar"
  fi

  if [ -f "$SDK_BACKUP" ]; then
    cp -p "$SDK_BACKUP" "$SDK_RESTORE_STAGE" &&
      chown root:root "$SDK_RESTORE_STAGE" &&
      chmod 0644 "$SDK_RESTORE_STAGE" &&
      mv -f "$SDK_RESTORE_STAGE" "$SDK_TARGET" ||
      echo "ERROR: failed to restore pre-rollback ReaderSDK-impl.jar"
  fi
  sync
}

cleanup() {
  STATUS=$?
  trap - 0
  trap '' HUP INT TERM

  if [ "$STATUS" -ne 0 ] && [ "$ROOT_RW" -eq 1 ] &&
     [ "$ROLLBACK_COMPLETE" -eq 0 ]; then
    restore_pre_rollback_jars
  fi

  rm -f "$SDK_STAGE" "$READER_STAGE" \
    "$SDK_RESTORE_STAGE" "$READER_RESTORE_STAGE"

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
    echo "[$TS] rollback-ai failed"
  fi
  exit "$STATUS"
}

mkdir -p "$BACKUP_DIR" || exit 1
exec >> "$LOG" 2>&1
trap cleanup 0
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

echo "[$TS] rollback-ai start"
verify_sha256 "original SDK jar" "$SDK_ORIGINAL" "$EXPECTED_ORIGINAL_SDK_SHA256"
verify_sha256 "stable Reader plugin" "$READER_STABLE" "$EXPECTED_STABLE_READER_SHA256"
[ -f "$SDK_TARGET" ] || fail "missing system SDK jar: $SDK_TARGET"
[ -f "$READER_TARGET" ] || fail "missing system reader jar: $READER_TARGET"

cp -p "$SDK_TARGET" "$SDK_BACKUP" || fail "failed to back up ReaderSDK-impl.jar"
cp -p "$READER_TARGET" "$READER_BACKUP" || fail "failed to back up Reader-plugin.jar"
verify_backup_copy "ReaderSDK-impl.jar" "$SDK_TARGET" "$SDK_BACKUP"
verify_backup_copy "Reader-plugin.jar" "$READER_TARGET" "$READER_BACKUP"
sha256sum "$SDK_TARGET" "$READER_TARGET" "$SDK_ORIGINAL" "$READER_STABLE" \
  "$SDK_BACKUP" "$READER_BACKUP" || fail "failed to record pre-rollback hashes"

ROOT_RW=1
if ! mntroot rw; then
  ROOT_RW=0
  fail "failed to remount root read-write"
fi

cp "$SDK_ORIGINAL" "$SDK_STAGE" || fail "failed to stage original ReaderSDK-impl.jar"
cp "$READER_STABLE" "$READER_STAGE" || fail "failed to stage stable Reader-plugin.jar"
chown root:root "$SDK_STAGE" "$READER_STAGE" || fail "failed to set staged jar ownership"
chmod 0644 "$SDK_STAGE" "$READER_STAGE" || fail "failed to set staged jar permissions"

verify_sha256 "staged original SDK jar" "$SDK_STAGE" "$EXPECTED_ORIGINAL_SDK_SHA256"
verify_sha256 "staged stable Reader plugin" "$READER_STAGE" "$EXPECTED_STABLE_READER_SHA256"

mv -f "$READER_STAGE" "$READER_TARGET" || fail "failed to restore Reader-plugin.jar"
mv -f "$SDK_STAGE" "$SDK_TARGET" || fail "failed to restore ReaderSDK-impl.jar"

verify_sha256 "restored original SDK jar" "$SDK_TARGET" "$EXPECTED_ORIGINAL_SDK_SHA256"
verify_sha256 "restored stable Reader plugin" "$READER_TARGET" "$EXPECTED_STABLE_READER_SHA256"
sync || fail "failed to sync restored jars"
ROLLBACK_COMPLETE=1

if mntroot ro; then
  ROOT_RW=0
else
  echo "WARNING: root remount read-only failed; reboot will restore mount state"
fi

echo "[$TS] rollback-ai done; rebooting"
reboot
exit 0
