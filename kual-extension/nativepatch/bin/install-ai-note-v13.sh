#!/bin/sh

LOG="/mnt/us/native-reader-research/nativepatch.log"
READER_PATCH="/mnt/us/native-reader-research/patched/Reader-plugin.v4.jar"
SDK_PATCH="/mnt/us/native-reader-research/patched/ReaderSDK-impl.ai-v13.jar"
TRANSLATOR_ORIGINAL="/mnt/us/native-reader-research/jars/translator.jar"
READER_TARGET="/opt/amazon/ebook/lib/Reader-plugin.jar"
SDK_TARGET="/opt/amazon/ebook/lib/ReaderSDK-impl.jar"
TRANSLATOR_TARGET="/opt/amazon/ebook/lib/translator.jar"
CONFIG="/mnt/us/native-reader-research/ai-assistant.properties"
BACKUP_DIR="/mnt/us/native-reader-research/backups"
TS="$(date +%Y%m%d-%H%M%S)"

mkdir -p "$BACKUP_DIR"
{
  echo "[$TS] install-ai-note-v13 start"
  if [ ! -f "$READER_PATCH" ]; then
    echo "missing reader patch: $READER_PATCH"
    exit 1
  fi
  if [ ! -f "$SDK_PATCH" ]; then
    echo "missing SDK patch: $SDK_PATCH"
    exit 1
  fi
  if [ ! -f "$TRANSLATOR_ORIGINAL" ]; then
    echo "missing original translator jar: $TRANSLATOR_ORIGINAL"
    exit 1
  fi
  if [ ! -f "$CONFIG" ]; then
    echo "missing AI config: $CONFIG"
    exit 1
  fi

  cp -p "$READER_TARGET" "$BACKUP_DIR/Reader-plugin.jar.before-ai-note-v13.$TS" || exit 1
  cp -p "$SDK_TARGET" "$BACKUP_DIR/ReaderSDK-impl.jar.before-ai-note-v13.$TS" || exit 1
  cp -p "$TRANSLATOR_TARGET" "$BACKUP_DIR/translator.jar.before-ai-note-v13.$TS" || exit 1
  sha256sum "$READER_TARGET" "$READER_PATCH" "$SDK_TARGET" "$SDK_PATCH" \
    "$TRANSLATOR_TARGET" "$TRANSLATOR_ORIGINAL" >> "$LOG" 2>&1

  mntroot rw || exit 1
  cp "$READER_PATCH" "$READER_TARGET" || exit 1
  cp "$SDK_PATCH" "$SDK_TARGET" || exit 1
  cp "$TRANSLATOR_ORIGINAL" "$TRANSLATOR_TARGET" || exit 1
  chown root:root "$READER_TARGET" "$SDK_TARGET" "$TRANSLATOR_TARGET"
  chmod 0644 "$READER_TARGET" "$SDK_TARGET" "$TRANSLATOR_TARGET"
  sync
  sha256sum "$READER_TARGET" "$SDK_TARGET" "$TRANSLATOR_TARGET" >> "$LOG" 2>&1
  mntroot ro
  echo "[$TS] install-ai-note-v13 done; rebooting"
} >> "$LOG" 2>&1

reboot
