#!/bin/sh
BASE="/mnt/us/native-reader-research/vpn"
XRAY="$BASE/bin/xray"
CONFIG="$BASE/xray-config.json"
PIDFILE="$BASE/xray.pid"
LOG="$BASE/xray.log"
PORT="18080"

mkdir -p "$BASE"
if [ ! -x "$XRAY" ]; then
  echo "missing executable: $XRAY" >> "$LOG"
  exit 1
fi
if [ ! -f "$CONFIG" ]; then
  echo "missing config: $CONFIG" >> "$LOG"
  exit 1
fi
if [ -f "$PIDFILE" ]; then
  PID="$(cat "$PIDFILE" 2>/dev/null)"
  if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
    exit 0
  fi
  rm -f "$PIDFILE"
fi
if netstat -an 2>/dev/null | grep -q "127.0.0.1:$PORT"; then
  exit 0
fi
nohup "$XRAY" run -config "$CONFIG" >> "$LOG" 2>&1 &
echo $! > "$PIDFILE"
exit 0
