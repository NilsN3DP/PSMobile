# Remote slicing (work in progress)

Slicing big, complex plates takes time and memory on a tablet. Remote slicing
lets the app hand the job to a server you run yourself – a home lab, a NAS, a
Docker host. There is **no central service**: the app talks to the address you
configure, nothing else.

## How it works

The core (`psmobile_core`) needs no GL/viewport sources for pure slicing, so
the same code that slices on the iPad is built natively for Linux and runs in
a container. No second slicer, no format divergence.

```
App (iOS/Android)                          Your server (Docker)
-----------------                          --------------------
export project as 3MF (profiles embedded)
POST /jobs  ------------------------------>  queue job, run the headless slicer
GET  /jobs/{id}  (poll progress)  <--------  status JSON
GET  /jobs/{id}/gcode  <-------------------  finished G-code
load G-code into the local preview
```

| Part | Where | State |
| --- | --- | --- |
| Headless CLI: takes a `.3mf` with embedded printer/filament/print profile, writes G-code + statistics JSON | `core/test/psm_slice_cli.c` (`PSM_BUILD_SLICE_CLI=ON`) | in the repo |
| App side: `RemoteSliceClient` (URLSession / OkHttp), the "Remote Slicing" entry on the start screen, the toggle next to the slice button; the result is loaded into the same preview path as a local slice | `ios/PSMobile/Networking`, `android/app/.../net` | in the repo, behind the plug-in switch in the app settings |
| Container: compose file with the token wiring | `docker/remote-slice/docker-compose.yml` | in the repo |
| Dockerfile, HTTP wrapper, native Linux build script | – | not published yet |

The app sends the file **and** the currently selected print settings (they are
embedded in the 3MF), so the server needs no profiles of its own.

## Security

The server authenticates with a bearer token (`PSM_AUTH_TOKEN`, set in the
container environment – see `docker/remote-slice/docker-compose.yml`). The app
stores the token in the Keychain / Android Keystore and sends it as
`Authorization: Bearer …`. Intended for a private network or a VPN; do not
expose it to the internet without TLS in front of it.

## Status

Prepared, not released. Server and app core must speak the same
`psm_abi_version()`; `/health` reports it and the app refuses a mismatch with a
clear message. Open points: multi-bed projects against a real project, several
workers for several devices, packaging and publishing of the image.
