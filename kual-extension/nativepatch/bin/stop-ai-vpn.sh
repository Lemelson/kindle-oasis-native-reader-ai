#!/bin/sh
BASE="/mnt/us/native-reader-research/vpn"
XRAY="$BASE/bin/xray"
PIDFILE="$BASE/xray.pid"

is_managed_xray() {
  CHECK_PID="$1"
  EXE="$(readlink "/proc/$CHECK_PID/exe" 2>/dev/null)"
  case "$EXE" in
    "$XRAY"|*/xray) return 0 ;;
    *) return 1 ;;
  esac
}

if [ -f "$PIDFILE" ]; then
  PID="$(cat "$PIDFILE" 2>/dev/null)"
  if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null && is_managed_xray "$PID"; then
    kill "$PID" 2>/dev/null || true
    WAIT_COUNT=0
    while kill -0 "$PID" 2>/dev/null && [ "$WAIT_COUNT" -lt 3 ]; do
      sleep 1
      WAIT_COUNT=$((WAIT_COUNT + 1))
    done
    if kill -0 "$PID" 2>/dev/null; then
      kill -9 "$PID" 2>/dev/null || true
    fi
  fi
  rm -f "$PIDFILE"
fi
exit 0
