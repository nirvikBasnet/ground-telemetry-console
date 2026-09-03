# Simulator environment

ArduPilot SITL produces the telemetry the console consumes. This document
describes how to bring the simulator up, get the vehicle into a state that emits
useful telemetry, and capture and verify a fixture.

## Running SITL

SITL runs from the prebuilt Docker image `orthuk/ardupilot-sitl-debian`, not from
a source build. ArduPilot crashes when built natively on Apple Silicon, so the
container is the only reliable path on this machine.

Create the container:

```
docker run -it --name sitl \
  -p 14550:14550 -p 14551:14551 \
  orthuk/ardupilot-sitl-debian \
  ./Tools/autotest/sim_vehicle.py -v ArduCopter -w \
    '--mavproxy-args=--out tcpin:0.0.0.0:14550 --out tcpin:0.0.0.0:14551 --state-basedir=/tmp/mavlink-sitl'
```

The two `--out tcpin:0.0.0.0:<port>` arguments make MAVProxy listen for inbound
TCP connections rather than pushing UDP to a fixed address. Binding to `0.0.0.0`
rather than loopback is what lets the published ports reach the host. The Android
emulator reaches the host loopback at `10.0.2.2`, so from inside the emulator
the feed is at `10.0.2.2:14550` over TCP.

Two ports are exposed because each `tcpin` output serves exactly one client. One
port is for the app, the other for a test script (`dump.py`, `record.py`) so both
can be connected at the same time.

### Resuming versus recreating

```
docker start -ai sitl     # resume the existing container
```

Use `docker start -ai` every time after the first. Running `docker run` again
creates a *new* container, which triggers a full ArduCopter rebuild taking 10–20
minutes. Only recreate when the container is genuinely broken.

### Parameters

`params/mav.parm` is the working simulator parameter set. Restore it at the
MAVProxy prompt with:

```
param load params/mav.parm
```

(the file must be readable from inside the container, or its contents pasted in;
the path above is relative to this repository on the host).

## Startup sequence

Enter these at the MAVProxy (`STABILIZE>`) prompt, in order:

```
mode guided                 # position-controlled mode that accepts takeoff and ignores RC
param set DISARM_DELAY 0    # stop the auto-disarm timer from cutting the flight short
param set BATT_FS_LOW_ACT 0 # battery low failsafe takes no action
param set BATT_FS_CRT_ACT 0 # battery critical failsafe takes no action
arm throttle                # arm the motors
takeoff 30                  # climb to 30 m above home
rc 3 1500                   # centre the throttle stick before leaving GUIDED
mode circle                 # orbit, producing continuously changing position and attitude
```

The battery failsafe parameters are set to 0 because SITL's simulated battery
drains over a long session and would otherwise trigger an RTL or land partway
through a capture.

### Why `rc 3 1500` is required

CIRCLE reads the throttle stick as an altitude *rate* command, not as thrust.
SITL's default RC input sits at minimum, so on entering CIRCLE the vehicle is
commanded to descend continuously and flies into the ground. Centring channel 3
at 1500 gives a zero climb rate and holds altitude.

GUIDED ignores RC input entirely, which is why `takeoff 30` works fine without
touching the sticks and only the mode change to CIRCLE crashes the vehicle.

### Why `DISARM_DELAY 0` is required

An armed copter sitting idle on the ground auto-disarms after roughly 10 seconds.
Setting `DISARM_DELAY` to 0 disables that timer, so the vehicle stays armed
between `arm throttle` and `takeoff`.

## Capturing a fixture

The scripts are run from the repository root, since their paths are relative to
it. They need `pymavlink` installed.

Watch the live feed to confirm the vehicle is producing telemetry (connects to
port 14551):

```
python sim/dump.py
```

Record a session to `fixtures/sitl-session.tlog` (connects to port 14550,
Ctrl+C to stop):

```
python sim/record.py
```

Verify the recording:

```
python sim/verify.py
```

### The `.tlog` extension is load-bearing

pymavlink infers its parser from the file extension. `.tlog` selects the
telemetry-log parser, which is what the capture actually is. A `.bin` suffix
selects the DataFlash parser and fails, and an unrecognised extension makes
pymavlink treat the path as a serial device.

`verify.py` passes `notimestamps=True` because `record.py` writes the raw byte
stream straight off the socket, with no per-message timestamp prefixes.

### What a good result looks like

`verify.py` prints message counts, the first and last position fixes, and the
span of the recording. A usable fixture has:

- Roughly equal counts of `ATTITUDE`, `GLOBAL_POSITION_INT` and `SYS_STATUS` —
  these stream at the same rate.
- `HEARTBEAT` at about a quarter of that count — it is a 1 Hz message where the
  others are around 4 Hz.
- A stable altitude across the recording (near the 30 m commanded at takeoff),
  confirming the vehicle held its orbit rather than descending.
- First and last positions that *differ*. Identical fixes mean the vehicle never
  moved and the fixture exercises nothing.

If `verify.py` reports no position data, the recording is unusable — capture
again.

## Field scaling

`GLOBAL_POSITION_INT` carries scaled integers, not floats:

- `lat`, `lon` — degrees × 1e7
- `relative_alt` — millimetres above the home position
- `alt` — millimetres above mean sea level

## Files

- `dump.py` — connect to the live feed and print decoded messages
- `record.py` — write the raw telemetry byte stream to `fixtures/sitl-session.tlog`
- `verify.py` — summarise a recorded fixture and sanity-check it
- `params/mav.parm` — working simulator parameter set
