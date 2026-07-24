#!/bin/sh

LOG="/mnt/us/native-reader-research/nativepatch.log"
READER_PATCH="/mnt/us/native-reader-research/patched/Reader-plugin.v3.jar"
SDK_PATCH="/mnt/us/native-reader-research/patched/ReaderSDK-impl.ai-v24.jar"
TRANSLATOR_ORIGINAL="/mnt/us/native-reader-research/jars/translator.jar"
READER_TARGET="/opt/amazon/ebook/lib/Reader-plugin.jar"
SDK_TARGET="/opt/amazon/ebook/lib/ReaderSDK-impl.jar"
TRANSLATOR_TARGET="/opt/amazon/ebook/lib/translator.jar"
CONFIG="/mnt/us/native-reader-research/ai-assistant.properties"
VPN_DIR="/mnt/us/native-reader-research/vpn"
SCRIPT_DIR="$(dirname "$0")"
BACKUP_DIR="/mnt/us/native-reader-research/backups"
TS="$(date +%Y%m%d-%H%M%S)"

set_prop() {
  KEY="$1"
  VALUE="$2"
  TMP="$CONFIG.tmp"
  if grep -q "^$KEY=" "$CONFIG"; then
    sed "s|^$KEY=.*|$KEY=$VALUE|" "$CONFIG" > "$TMP" && mv "$TMP" "$CONFIG"
  else
    echo "$KEY=$VALUE" >> "$CONFIG"
  fi
}

mkdir -p "$BACKUP_DIR" "$VPN_DIR"
{
  echo "[$TS] install-ai-note-v24 start"
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
  if [ ! -f "$SCRIPT_DIR/start-ai-vpn.sh" ] || [ ! -f "$SCRIPT_DIR/stop-ai-vpn.sh" ]; then
    echo "missing VPN lifecycle scripts in $SCRIPT_DIR"
    exit 1
  fi

  cp -p "$READER_TARGET" "$BACKUP_DIR/Reader-plugin.jar.before-ai-note-v24.$TS" || exit 1
  cp -p "$SDK_TARGET" "$BACKUP_DIR/ReaderSDK-impl.jar.before-ai-note-v24.$TS" || exit 1
  cp -p "$TRANSLATOR_TARGET" "$BACKUP_DIR/translator.jar.before-ai-note-v24.$TS" || exit 1
  cp -p "$CONFIG" "$BACKUP_DIR/ai-assistant.properties.before-ai-note-v24.$TS" || exit 1
  if [ -f "$VPN_DIR/start-ai-vpn.sh" ]; then
    cp -p "$VPN_DIR/start-ai-vpn.sh" "$BACKUP_DIR/start-ai-vpn.sh.before-ai-note-v24.$TS" || exit 1
  fi
  if [ -f "$VPN_DIR/stop-ai-vpn.sh" ]; then
    cp -p "$VPN_DIR/stop-ai-vpn.sh" "$BACKUP_DIR/stop-ai-vpn.sh.before-ai-note-v24.$TS" || exit 1
  fi

  set_prop gemini_models gemini-3.6-flash || exit 1
  set_prop gemini_thinking_level high || exit 1
  set_prop max_tokens 6750 || exit 1
  cp "$SCRIPT_DIR/start-ai-vpn.sh" "$VPN_DIR/start-ai-vpn.sh" || exit 1
  cp "$SCRIPT_DIR/stop-ai-vpn.sh" "$VPN_DIR/stop-ai-vpn.sh" || exit 1
  chmod 0755 "$VPN_DIR/start-ai-vpn.sh" "$VPN_DIR/stop-ai-vpn.sh" || exit 1

  sha256sum "$READER_TARGET" "$READER_PATCH" "$SDK_TARGET" "$SDK_PATCH" \
    "$TRANSLATOR_TARGET" "$TRANSLATOR_ORIGINAL" "$CONFIG" \
    "$VPN_DIR/start-ai-vpn.sh" "$VPN_DIR/stop-ai-vpn.sh" >> "$LOG" 2>&1

  mntroot rw || exit 1
  cp "$READER_PATCH" "$READER_TARGET" || exit 1
  cp "$SDK_PATCH" "$SDK_TARGET" || exit 1
  cp "$TRANSLATOR_ORIGINAL" "$TRANSLATOR_TARGET" || exit 1
  chown root:root "$READER_TARGET" "$SDK_TARGET" "$TRANSLATOR_TARGET"
  chmod 0644 "$READER_TARGET" "$SDK_TARGET" "$TRANSLATOR_TARGET"
  sync
  sha256sum "$READER_TARGET" "$SDK_TARGET" "$TRANSLATOR_TARGET" >> "$LOG" 2>&1
  mntroot ro
  echo "[$TS] install-ai-note-v24 done; rebooting"
} >> "$LOG" 2>&1

reboot
