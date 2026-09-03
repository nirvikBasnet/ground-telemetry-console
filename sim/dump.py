from pymavlink import mavutil

m = mavutil.mavlink_connection('tcp:127.0.0.1:14551')
print('waiting for heartbeat...')
m.wait_heartbeat()
print(f'connected: system {m.target_system} component {m.target_component}')

WANTED = ('HEARTBEAT', 'GLOBAL_POSITION_INT', 'ATTITUDE', 'SYS_STATUS')

while True:
    msg = m.recv_match(blocking=True)
    if msg and msg.get_type() in WANTED:
        print(msg.get_type(), msg.to_dict())
