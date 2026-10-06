# Connecting to the phone from the server

The server reaches the phone through WireGuard (phone `10.200.200.2`, server `10.200.200.1`) using classic `adb tcpip` on port 5555. This works on Wi-Fi and mobile data alike.

## Connect (normal case)

On the server:

```
adb connect 10.200.200.2:5555
adb devices -l        # must say "device"
```

WireGuard must be connected on the phone. If `adb devices` already lists the phone, there is nothing to do.

## Disconnect

```
adb disconnect 10.200.200.2:5555
```

Optional, since leaving it connected is harmless. For better security, also turn **USB debugging off** on the phone when you are not developing: that closes port 5555 entirely.

## After a phone reboot

A reboot resets TCP mode, so `adb connect` will say "connection refused". Repeat the one-time step:

1. Connect the phone to the laptop by USB. USB debugging must be on; accept the RSA prompt if it appears.
2. In PowerShell on the laptop:
   ```powershell
   adb devices
   adb -d tcpip 5555
   ```
   `adb devices` must show the phone as `device`. The second command should print `restarting in TCP mode port: 5555`.
3. Unplug the cable, make sure WireGuard is connected on the phone, then connect from the server (see above).

## If it fails

| Symptom | Cause / fix |
| --- | --- |
| `connection refused` | The phone rebooted or USB debugging was toggled: repeat the USB step above. |
| Timeout | VPN/routing problem: `ping 10.200.200.2`, then check WireGuard is connected on the phone. |
| `offline` / `unauthorized` | `adb disconnect`, `adb kill-server`, connect again, accept the prompt on the phone ("Always allow from this computer"). |

Troubleshooting order: ping the tunnel IP, then `sudo wg show` on the server (the phone peer needs a recent handshake and `allowed ips: 10.200.200.2/32`), then `PersistentKeepalive = 25` in the phone's WireGuard config, then exempt the WireGuard app from battery optimization (ideally Always-on VPN), then `adb kill-server` and reconnect.

Never port-forward 5555 or expose it on a public interface.
