import net from 'node:net';

/**
 * Minimal RCON client used by the load-test scripts.
 */
export function rconCommand(host, port, password, command, timeoutMs = 4000) {
  return new Promise((resolve, reject) => {
    let id = 1;
    let authed = false;
    let pending = Buffer.alloc(0);
    let output = '';
    let done = false;

    const socket = net.createConnection({ host, port });
    const timer = setTimeout(() => finish(), timeoutMs);

    function finish() {
      if (done) return;
      done = true;
      clearTimeout(timer);
      try { socket.destroy(); } catch {}
      resolve(output.trim());
    }

    function packet(type, payload) {
      const body = Buffer.alloc(Buffer.byteLength(payload) + 2);
      body.write(payload, 0, 'utf8');
      body[body.length - 1] = 0;
      const buffer = Buffer.alloc(12 + body.length);
      buffer.writeInt32LE(4 + 4 + body.length, 0);
      buffer.writeInt32LE(id, 4);
      buffer.writeInt32LE(type, 8);
      body.copy(buffer, 12);
      return buffer;
    }

    socket.on('connect', () => socket.write(packet(3, password)));
    socket.on('data', (chunk) => {
      pending = Buffer.concat([pending, chunk]);
      while (pending.length >= 4) {
        const length = pending.readInt32LE(0);
        if (pending.length < 4 + length) break;
        const data = pending.subarray(4, 4 + length);
        pending = pending.subarray(4 + length);
        const responseId = data.readInt32LE(0);
        const type = data.readInt32LE(4);
        const body = data.subarray(8, data.length - 2).toString('utf8');
        if (type === 2 && !authed) {
          if (responseId === -1) {
            clearTimeout(timer);
            reject(new Error('RCON authentication failed'));
            return;
          }
          authed = true;
          id++;
          socket.write(packet(2, command));
          continue;
        }
        if (type === 0) {
          output += body + '\n';
        }
      }
    });
    socket.on('error', (error) => {
      clearTimeout(timer);
      reject(error);
    });
    socket.on('close', () => finish());
  });
}
