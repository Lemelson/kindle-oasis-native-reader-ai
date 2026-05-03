#!/bin/sh

LOG="/mnt/us/native-reader-research/nativepatch.log"
PATCH="/mnt/us/native-reader-research/patched/translator.ru.jar"
TARGET="/opt/amazon/ebook/lib/translator.jar"
BACKUP_DIR="/mnt/us/native-reader-research/backups"
TS="$(date +%Y%m%d-%H%M%S)"

mkdir -p "$BACKUP_DIR"
{
  echo "[$TS] install-translator-ru start"
  if [ ! -f "$PATCH" ]; then
    echo "missing patch: $PATCH"
    exit 1
  fi

  cp -p "$TARGET" "$BACKUP_DIR/translator.jar.before-ru.$TS" || exit 1
  sha256sum "$TARGET" "$PATCH" >> "$LOG" 2>&1

  mntroot rw || exit 1
  cp "$PATCH" "$TARGET" || exit 1
  chown root:root "$TARGET"
  chmod 0644 "$TARGET"
  sync
  sha256sum "$TARGET" >> "$LOG" 2>&1
  mntroot ro
  echo "[$TS] install-translator-ru done; rebooting"
} >> "$LOG" 2>&1

reboot
