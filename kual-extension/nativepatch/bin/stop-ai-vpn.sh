#!/bin/sh
BASE="/mnt/us/native-reader-research/vpn"
PIDFILE="$BASE/xray.pid"
if [ -f "$PIDFILE" ]; then
  PID="$(cat "$PIDFILE" 2>/dev/null)"
  if [ -n "$PID" ]; then
    kill "$PID" 2>/dev/null || true
  fi
  rm -f "$PIDFILE"
fi
exit 0
