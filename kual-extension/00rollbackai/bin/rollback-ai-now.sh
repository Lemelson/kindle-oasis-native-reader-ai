#!/bin/sh

LOG="/mnt/us/native-reader-research/nativepatch.log"
SDK_TARGET="/opt/amazon/ebook/lib/ReaderSDK-impl.jar"
SDK_ORIGINAL="/mnt/us/native-reader-research/jars/ReaderSDK-impl.jar"
BACKUP_DIR="/mnt/us/native-reader-research/backups"
TS="$(date +%Y%m%d-%H%M%S)"

mkdir -p "$BACKUP_DIR"
{
  echo "[$TS] emergency rollback-ai start"
  if [ ! -f "$SDK_ORIGINAL" ]; then
    echo "missing original SDK jar: $SDK_ORIGINAL"
    exit 1
  fi

  cp -p "$SDK_TARGET" "$BACKUP_DIR/ReaderSDK-impl.jar.before-emergency-rollback-ai.$TS" || exit 1
  sha256sum "$SDK_TARGET" "$SDK_ORIGINAL" >> "$LOG" 2>&1

  mntroot rw || exit 1
  cp "$SDK_ORIGINAL" "$SDK_TARGET" || exit 1
  chown root:root "$SDK_TARGET"
  chmod 0644 "$SDK_TARGET"
  sync
  sha256sum "$SDK_TARGET" >> "$LOG" 2>&1
  mntroot ro
  echo "[$TS] emergency rollback-ai done; rebooting"
} >> "$LOG" 2>&1

reboot
