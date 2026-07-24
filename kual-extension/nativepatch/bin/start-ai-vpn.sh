#!/bin/sh
BASE="/mnt/us/native-reader-research/vpn"
XRAY="$BASE/bin/xray"
CONFIG="$BASE/xray-config.json"
PIDFILE="$BASE/xray.pid"
LOG="$BASE/xray.log"

is_managed_xray() {
  CHECK_PID="$1"
  EXE="$(readlink "/proc/$CHECK_PID/exe" 2>/dev/null)"
  case "$EXE" in
    "$XRAY"|*/xray) return 0 ;;
    *) return 1 ;;
  esac
}

stop_managed_xray() {
  STOP_PID="$1"
  kill "$STOP_PID" 2>/dev/null || true
  WAIT_COUNT=0
  while kill -0 "$STOP_PID" 2>/dev/null && [ "$WAIT_COUNT" -lt 3 ]; do
    sleep 1
    WAIT_COUNT=$((WAIT_COUNT + 1))
  done
  if kill -0 "$STOP_PID" 2>/dev/null; then
    kill -9 "$STOP_PID" 2>/dev/null || true
  fi
}

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
    if is_managed_xray "$PID"; then
      echo "restarting unready xray pid=$PID" >> "$LOG"
      stop_managed_xray "$PID"
    else
      echo "ignoring stale non-xray pid=$PID" >> "$LOG"
    fi
  fi
  rm -f "$PIDFILE"
fi
nohup "$XRAY" run -config "$CONFIG" >> "$LOG" 2>&1 &
echo $! > "$PIDFILE"
exit 0
