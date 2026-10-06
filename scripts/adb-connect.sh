#!/usr/bin/env bash
# Reconnect adb to the phone over the WireGuard tunnel after Wireless debugging
# was toggled or the phone rebooted (the connect port changes every time).
# mDNS doesn't work over the VPN, so scan the tunnel IP for the TLS adb port.
# Pairing is remembered by the phone; this does NOT re-pair (that needs the
# 6-digit code: adb pair <ip>:<pairing-port> <code>).
# Usage: scripts/adb-connect.sh [phone-ip]   (default 10.200.200.2)
set -u
IP="${1:-10.200.200.2}"
ADB="${ADB:-adb}"
command -v "$ADB" >/dev/null || ADB="$HOME/Android/Sdk/platform-tools/adb"

if "$ADB" devices | grep -q "^$IP:[0-9]*[[:space:]]*device"; then
  echo "Already connected:"; "$ADB" devices | grep "^$IP"; exit 0
fi
"$ADB" disconnect >/dev/null 2>&1   # drop stale offline entries
ping -c 1 -W 2 "$IP" >/dev/null || { echo "Phone $IP unreachable (tunnel up? WireGuard keepalive?)"; exit 1; }

echo "Scanning $IP for open ports..."
PORTS=$(python3 -I - "$IP" <<'PY'
import socket, sys
from concurrent.futures import ThreadPoolExecutor
ip = sys.argv[1]
def probe(p):
    s = socket.socket(); s.settimeout(1.5)
    try:
        s.connect((ip, p)); return p
    except OSError:
        return None
    finally:
        s.close()
# Wireless debugging picks a random port; try the usual range first, then the rest.
for rng in (range(30000, 50001), list(range(1024, 30000)) + list(range(50001, 65536))):
    with ThreadPoolExecutor(400) as ex:
        found = [p for p in ex.map(probe, rng) if p]
    if found:
        print(*found); break
PY
)
[ -z "$PORTS" ] && { echo "No open ports found. Is Wireless debugging on, and is the phone on Wi-Fi?"; exit 1; }

for p in $PORTS; do
  out=$("$ADB" connect "$IP:$p" 2>&1)
  if echo "$out" | grep -q "^connected"; then echo "$out"; "$ADB" devices -l | grep "^$IP"; exit 0; fi
done
echo "Open ports ($PORTS) but adb connect failed on all: $out"
echo "The phone probably needs re-pairing: Wireless debugging -> Pair device with pairing code,"
echo "then: adb pair $IP:<pairing-port> <code>  and rerun this script."
exit 1
