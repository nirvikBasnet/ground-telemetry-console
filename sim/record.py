import socket

OUT = 'fixtures/sitl-session.tlog'
s = socket.create_connection(('127.0.0.1', 14550))
print(f'recording to {OUT} — Ctrl+C to stop')
total = 0
with open(OUT, 'wb') as f:
    try:
        while True:
            data = s.recv(4096)
            if not data:
                break
            f.write(data)
            total += len(data)
    except KeyboardInterrupt:
        pass
print(f'wrote {total} bytes')
