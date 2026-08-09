# Experimental Local PrusaLink QR Pairing

## Scope

This side-build adds QR-based pairing for local physical PrusaLink printers in PS Mobile only. It does not modify firmware or the printer UI. The feature is explicitly labeled `Experimental` and is disabled by default.

## Architecture

- A shared, platform-neutral QR payload model and validator accepts only `type=prusalink-local`, `version=1`, `transport=http`, a valid printer model, a local host, a valid port, and a non-empty pairing token.
- Android and iOS provide native camera scanner adapters. The same shared validator is used for camera data and manual entry.
- Manual fallback accepts host, port, and pairing token when camera scanning is unavailable.
- Local printers are stored as a dedicated physical/local host type. Cloud discovery and automatic cloud fallback are never used.
- Printer metadata is persisted in the normal printer store; the pairing token is persisted only in Android Keystore-backed storage or iOS Keychain-backed storage.

## Pairing flow

1. The user opens `Physical printer → Experimental local connection → Scan QR code` or chooses manual entry.
2. PS Mobile parses and validates the payload before persisting anything.
3. The app normalizes the endpoint to `http://192.168.4.1` by default and rejects cloud/public endpoints.
4. It probes `/api/v1/status`, then reads the advertised PrusaLink capabilities.
5. Only after successful validation and probing is the printer saved as a local physical printer.
6. Reconnect uses the stored local endpoint and token without cloud fallback.

## Security and lifecycle

- No WLAN password is accepted, displayed, logged, or persisted by the pairing flow.
- Pairing tokens never appear in logs, error strings, analytics, or QR previews.
- Pairing reset deletes the local token immediately and removes or disables the local pairing record.
- Unknown payload versions, unsupported models, invalid tokens, public/cloud hosts, and malformed ports fail closed.
- Because this scope does not change firmware, true printer-side token expiry/revocation cannot be enforced unless the payload carries `expires_at` or the printer rejects the token through its documented API. The app therefore supports optional `expires_at`, rejects expired payloads, and treats a failed probe as inactive; it does not claim printer-side revocation where no such endpoint exists.

## UI

The add-printer flow exposes `Experimental local connection` and `Scan QR code`. The local printer detail shows `Experimental`, `Local printer`, `Printer hotspot`, IP address, connection state, pairing state, `Show QR code again` (when a payload is available locally), and `Reset local pairing`. If scanning is unavailable, the manual form is shown.

## Testing

- Shared parser/validator tests cover valid payloads, wrong type/version/model, public/cloud hosts, invalid ports, missing tokens, expired payloads, and capability filtering.
- Android and iOS tests cover manual fallback, secure token-store boundaries, reconnect, reset, offline local status, and the absence of cloud fallback.
- Tests assert that tokens and credentials are absent from logs and user-facing errors.
- Platform builds remain side-build only until the beta review is accepted.
