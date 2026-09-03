# telemetry-console

An Android operator console for a single uncrewed vehicle. It connects to a live
telemetry feed, decodes the vehicle's state as it arrives, and presents attitude,
position, altitude and system health to an operator in real time. Telemetry is
produced by an ArduPilot SITL simulator and carried over MAVLink; the simulator
setup, the capture tooling and a recorded session fixture live in `sim/` and
`fixtures/` so the console can be developed and tested without a running
simulator.

## Architecture

Placeholder — to be filled in as the application takes shape.

## Telemetry as an external source

The application treats telemetry as an abstract external real-time source rather
than coupling to MAVLink throughout. MAVLink decoding is confined to a transport
boundary that emits domain-level vehicle state; the domain model, presentation
layer and tests know nothing about MAVLink message names, field scaling or the
underlying socket. The transport can change — a different protocol, a replayed
fixture, a synthetic feed — without touching the domain model.

## Repository layout

```
sim/         simulator setup notes and capture/verification scripts
sim/params/  simulator parameter set (mav.parm), restorable with `param load`
fixtures/    recorded telemetry sessions used as test data
android/     Android application (not yet started)
```

## What I'd do next
