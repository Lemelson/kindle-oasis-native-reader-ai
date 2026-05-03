#!/bin/sh

LOG="/mnt/us/native-reader-research/nativepatch.log"
TARGET="/opt/amazon/ebook/lib/translator.jar"
ORIGINAL="/mnt/us/native-reader-research/jars/translator.jar"
BACKUP_DIR="/mnt/us/native-reader-research/backups"
TS="$(date +%Y%m%d-%H%M%S)"

mkdir -p "$BACKUP_DIR"
{
  echo "[$TS] rollback-translator start"
  if [ ! -f "$ORIGINAL" ]; then
    echo "missing original translator jar: $ORIGINAL"
    exit 1
  fi

  cp -p "$TARGET" "$BACKUP_DIR/translator.jar.before-rollback.$TS" || exit 1
  sha256sum "$TARGET" "$ORIGINAL" >> "$LOG" 2>&1

  mntroot rw || exit 1
  cp "$ORIGINAL" "$TARGET" || exit 1
  chown root:root "$TARGET"
  chmod 0644 "$TARGET"
  sync
  sha256sum "$TARGET" >> "$LOG" 2>&1
  mntroot ro
  echo "[$TS] rollback-translator done; rebooting"
} >> "$LOG" 2>&1

reboot
