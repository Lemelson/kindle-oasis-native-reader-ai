#!/bin/sh

LOG="/mnt/us/native-reader-research/nativepatch.log"
READER_PATCH="/mnt/us/native-reader-research/patched/Reader-plugin.v2.jar"
TRANSLATOR_ORIGINAL="/mnt/us/native-reader-research/jars/translator.jar"
READER_TARGET="/opt/amazon/ebook/lib/Reader-plugin.jar"
TRANSLATOR_TARGET="/opt/amazon/ebook/lib/translator.jar"
BACKUP_DIR="/mnt/us/native-reader-research/backups"
TS="$(date +%Y%m%d-%H%M%S)"

mkdir -p "$BACKUP_DIR"
{
  echo "[$TS] install-stable-trans-only start"
  if [ ! -f "$READER_PATCH" ]; then
    echo "missing reader patch: $READER_PATCH"
    exit 1
  fi
  if [ ! -f "$TRANSLATOR_ORIGINAL" ]; then
    echo "missing original translator jar: $TRANSLATOR_ORIGINAL"
    exit 1
  fi

  cp -p "$READER_TARGET" "$BACKUP_DIR/Reader-plugin.jar.before-stable-trans-only.$TS" || exit 1
  cp -p "$TRANSLATOR_TARGET" "$BACKUP_DIR/translator.jar.before-stable-trans-only.$TS" || exit 1
  sha256sum "$READER_TARGET" "$READER_PATCH" "$TRANSLATOR_TARGET" "$TRANSLATOR_ORIGINAL" >> "$LOG" 2>&1

  mntroot rw || exit 1
  cp "$READER_PATCH" "$READER_TARGET" || exit 1
  cp "$TRANSLATOR_ORIGINAL" "$TRANSLATOR_TARGET" || exit 1
  chown root:root "$READER_TARGET" "$TRANSLATOR_TARGET"
  chmod 0644 "$READER_TARGET" "$TRANSLATOR_TARGET"
  sync
  sha256sum "$READER_TARGET" "$TRANSLATOR_TARGET" >> "$LOG" 2>&1
  mntroot ro
  echo "[$TS] install-stable-trans-only done; rebooting"
} >> "$LOG" 2>&1

reboot
