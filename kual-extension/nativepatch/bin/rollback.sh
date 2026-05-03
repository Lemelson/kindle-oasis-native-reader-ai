#!/bin/sh

LOG="/mnt/us/native-reader-research/nativepatch.log"
TARGET="/opt/amazon/ebook/lib/Reader-plugin.jar"
ORIGINAL="/mnt/us/native-reader-research/backups/Reader-plugin.jar.orig.20260504-013002"
BACKUP_DIR="/mnt/us/native-reader-research/backups"
TS="$(date +%Y%m%d-%H%M%S)"

{
  echo "[$TS] rollback start"
  if [ ! -f "$ORIGINAL" ]; then
    ORIGINAL="$(ls -t "$BACKUP_DIR"/Reader-plugin.jar.orig.* 2>/dev/null | head -n 1)"
  fi
  if [ ! -f "$ORIGINAL" ]; then
    echo "missing original Reader-plugin backup"
    exit 1
  fi

  cp -p "$TARGET" "$BACKUP_DIR/Reader-plugin.jar.before-rollback.$TS" || exit 1
  sha256sum "$TARGET" "$ORIGINAL" >> "$LOG" 2>&1

  mntroot rw || exit 1
  cp "$ORIGINAL" "$TARGET" || exit 1
  chown root:root "$TARGET"
  chmod 0644 "$TARGET"
  sync
  sha256sum "$TARGET" >> "$LOG" 2>&1
  mntroot ro
  echo "[$TS] rollback done; rebooting"
} >> "$LOG" 2>&1

reboot
