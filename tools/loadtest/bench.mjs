// Phased benchmark: isolates login, menu-open burst and click pressure.
// Usage: node bench.mjs <host> <port> <rconPort> <bots> <version>
import mineflayer from 'mineflayer';
import { rconCommand } from './rcon.mjs';

const host = process.argv[2] || '127.0.0.1';
const port = Number(process.argv[3] || 25565);
const rconPort = Number(process.argv[4] || 25575);
const count = Number(process.argv[5] || 200);
const version = process.argv[6] || '1.18.2';
const prefix = process.argv[7] || 'BN';
const rconPassword = process.env.BENTO_RCON_PASSWORD || 'bento';
const slots = [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25];
const bots = [];
let stopping = false;
let clicks = 0;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const tps = () => rconCommand(host, rconPort, rconPassword, 'bentotest status').catch(() => 'rcon-failed');

async function spawn(index) {
  const username = `${prefix}${String(index).padStart(3, '0')}`.slice(0, 16);
  const bot = mineflayer.createBot({ host, port, username, auth: 'offline', version, physicsEnabled: false, hideErrors: true });
  bots.push(bot);
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`spawn timeout ${username}`)), 30000);
    bot.once('spawn', () => { clearTimeout(timer); resolve(); });
    bot.once('error', (e) => { clearTimeout(timer); reject(e); });
  });
  bot.on('error', () => {});
  return bot;
}

async function clickLoop(bot) {
  while (!stopping) {
    await sleep(2500 + Math.floor(Math.random() * 1500));
    if (!bot.currentWindow) continue;
    try {
      bot.clickWindow(slots[Math.floor(Math.random() * slots.length)], 0, 0);
      clicks++;
    } catch { /* ignore */ }
  }
}

async function sample(phase, seconds) {
  const started = Date.now();
  while (Date.now() - started < seconds * 1000) {
    await sleep(5000);
    console.log(`[${phase}] ${(await tps()).trim()}`);
  }
}

async function main() {
  console.log(`Spawning ${count} bots...`);
  for (let i = 0; i < count; i++) {
    try { await spawn(i); } catch (e) { console.error(`bot ${i}: ${e.message}`); }
    if (i % 20 === 19) console.log(`  ${i + 1}/${count}`);
    await sleep(40);
  }
  console.log('All spawned; settling 30s with menus closed.');
  await sample('idle-no-menu', 30);

  await rconCommand(host, rconPort, rconPassword, 'bentotest giveall 1000').catch(() => {});
  console.log('Opening menus...');
  await rconCommand(host, rconPort, rconPassword, 'bentotest openall shop').catch(() => {});
  await sample('idle-menu-open', 25);

  console.log('Closing menus...');
  await rconCommand(host, rconPort, rconPassword, 'bentotest closeall').catch(() => {});
  await sample('idle-closed', 15);

  console.log('Reopening and starting click pressure...');
  await rconCommand(host, rconPort, rconPassword, 'bentotest openall shop').catch(() => {});
  bots.forEach((bot) => clickLoop(bot));
  await sample('click-pressure', 30);
  stopping = true;
  const stats = await rconCommand(host, rconPort, rconPassword, 'bento stats').catch(() => '');
  console.log(`Clicks sent: ${clicks}`);
  console.log(stats);
  for (const bot of bots) { try { bot.quit('bench done'); } catch { /* ignore */ } }
  process.exit(0);
}

main().catch((e) => { console.error(e); process.exit(1); });
setTimeout(() => { console.error('BENCH TIMEOUT'); process.exit(2); }, 300000);
