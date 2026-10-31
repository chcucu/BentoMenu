import net from 'node:net';

/**
 * TCP latency proxy: delays each direction by DELAY_MS, simulating a client
 * with roughly 2xDELAY_MS round-trip latency. Used for the 200ms-latency test.
 *
 * usage: node latency-proxy.mjs <listenPort> <targetHost> <targetPort> [delayMs]
 */
const listenPort = Number(process.argv[2] || 25566);
const targetHost = process.argv[3] || '127.0.0.1';
const targetPort = Number(process.argv[4] || 25565);
const delayMs = Number(process.argv[5] || 100);

function delayedWrite(socket, chunk) {
  setTimeout(() => {
    if (!socket.destroyed) socket.write(chunk);
  }, delayMs);
}

const server = net.createServer((client) => {
  const upstream = net.createConnection({ host: targetHost, port: targetPort });
  client.on('data', (chunk) => delayedWrite(upstream, chunk));
  upstream.on('data', (chunk) => delayedWrite(client, chunk));
  client.on('error', () => upstream.destroy());
  upstream.on('error', () => client.destroy());
  client.on('close', () => upstream.destroy());
  upstream.on('close', () => client.destroy());
});

server.listen(listenPort, '0.0.0.0', () => {
  console.log(`Latency proxy ${listenPort} -> ${targetHost}:${targetPort} (+${delayMs}ms each way, ~${delayMs * 2}ms RTT)`);
});
