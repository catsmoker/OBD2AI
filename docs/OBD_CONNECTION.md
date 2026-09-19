# OBD Connection

Transports, ELM327 initialization, DTC handling and demo mode — the rules
that keep OBD2AI talking to real adapters and clones alike.

## Transports

| Transport | API | Notes |
| --------- | --- | ----- |
| Bluetooth SPP | `setupObd` — RFCOMM socket, SPP UUID `00001101-0000-1000-8000-00805F9B34FB` | Most common; pair in Android settings first (classic PINs are often `1234`/`0000`) |
| WiFi TCP | `setupWifi(host, port)` — plain TCP streams | Same stream pipeline as Bluetooth via `ObdDeviceConnection`; many WiFi adapters are unencrypted — use a trusted network |
| Demo | `setupDemo()` — `DemoObdSource` simulation | No radio/network; sliders write `ObdDataHolder` flows directly, no polling loop |

## ELM327 init sequence

`ObdHelper.initializeObd` sends these raw AT strings in order:

```
ATZ, ATE0, ATL0, ATS0, ATH0, ATSP0, ATAT1
```

- `ATS0` (spaces off) and `ATH0` (headers off) make responses
  **deterministic across ELM327 clones** — the parsers assume both off.
  Don't drop them.
- `ATSP0` enables automatic protocol detection; `ATAT1` enables adaptive
  timing for flaky clones.

## Reading DTCs

- Stored, pending and permanent codes are read after init and mapped to
  `DtpCodeDTO` with SAE J2012 system/generic-origin classification.
- Results are cached to the internal file `dtc_results.json` (`DtcStore`).
  `ErrorDetailFragment` falls back to that cache when `ObdDataHolder` is
  empty (e.g. after process death or navigation back).
- Each code is then assessed by the configured AI provider in parallel; see
  [AI_PROVIDERS.md](AI_PROVIDERS.md). Codes assessed without AI carry
  `offline=true`.

## Live data

Speed, RPM and coolant temperature are polled via custom commands
(`MySpeedCommand`, …) into `ObdDataHolder` flows, rendered as
SpeedView gauges. Speed source is switchable (`speed_source` pref):

- **OBD** — vehicle speed from the adapter.
- **GPS** — phone location speed (stays in memory, never persisted).

## demoMode ownership (important)

Only `setupObd` / `setupWifi` / `setupDemo` own `ObdHelper.demoMode`.
`disconnectFromObdDevice()` must **not** reset it — LiveData teardown calls
disconnect, and resetting there silently kills demo so every later read
fails. All reads branch on `demoMode`; keep that branching when adding new
commands.

## Permissions & receivers

- Android 12+: `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT`; older devices: legacy
  `BLUETOOTH` / `BLUETOOTH_ADMIN` (with `maxSdkVersion` where appropriate).
- Location permission is required by Android for Bluetooth discovery on
  older versions — it is not location tracking.
- Runtime receivers must use `ContextCompat.registerReceiver(...,
  RECEIVER_NOT_EXPORTED)` — plain `registerReceiver` throws on API 33+
  (targetSdk 36).

## Troubleshooting

- **Can't find adapter** — enable Bluetooth, grant scan/connect +
  location permissions, pair in system settings first.
- **Connects but no data** — clone not answering init; check ignition is
  on, try re-seating the adapter, watch logcat for the AT exchange.
- **WiFi adapter unreachable** — join the adapter's network, verify
  host/port, ensure no VPN routes traffic away from it.
- **Demo "stops working" after leaving Live Data** — a teardown reset
  `demoMode`; see the ownership rule above.
- **Emulator** — no Bluetooth hardware; use demo mode or WiFi to a
  reachable adapter/bridge.

## See also

- [ARCHITECTURE.md](ARCHITECTURE.md)
- [AI_PROVIDERS.md](AI_PROVIDERS.md)
- [FAQ.md](FAQ.md)
- [../SECURITY.md](../SECURITY.md)
