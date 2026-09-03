from pymavlink import mavutil

LOGFILE = 'fixtures/sitl-session.tlog'
WANTED = ('HEARTBEAT', 'GLOBAL_POSITION_INT', 'ATTITUDE', 'SYS_STATUS')

m = mavutil.mavlink_connection(LOGFILE, notimestamps=True)

counts = {}
first_pos = None
last_pos = None

while True:
    msg = m.recv_match()
    if msg is None:
        break
    name = msg.get_type()
    if name == 'BAD_DATA':
        continue
    counts[name] = counts.get(name, 0) + 1
    if name == 'GLOBAL_POSITION_INT':
        if first_pos is None:
            first_pos = msg
        last_pos = msg

total = sum(counts.values())
print(f'{total} messages, {len(counts)} distinct types\n')

for name, n in sorted(counts.items(), key=lambda kv: -kv[1]):
    marker = ' <--' if name in WANTED else ''
    print(f'{n:7d}  {name}{marker}')

if first_pos and last_pos:
    print()
    print(f'first fix: lat {first_pos.lat / 1e7:.6f}  '
          f'lon {first_pos.lon / 1e7:.6f}  '
          f'alt {first_pos.relative_alt / 1000:.1f} m')
    print(f'last fix:  lat {last_pos.lat / 1e7:.6f}  '
          f'lon {last_pos.lon / 1e7:.6f}  '
          f'alt {last_pos.relative_alt / 1000:.1f} m')
    span = (last_pos.time_boot_ms - first_pos.time_boot_ms) / 1000
    print(f'span: {span:.0f} seconds')
else:
    print('\nNO POSITION DATA — recording is not usable, capture again')
