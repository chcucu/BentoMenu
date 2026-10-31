import mineflayer from 'mineflayer';
import fs from 'node:fs';
import path from 'node:path';
import { rconCommand } from './rcon.mjs';

const host = process.argv[2] || '127.0.0.1';
const port = Number(process.argv[3] || 25565);
const rconPort = Number(process.argv[4] || 25575);
const count = Number(process.argv[5] || 20);
const durationSeconds = Number(process.argv[6] || 60);
const version = process.argv[7] || '1.18.2';
const prefix = process.argv[8] || 'BT';
const rconPassword = process.env.BENTO_RCON_PASSWORD || 'bento';
const clickEnabled = process.env.BENTO_CLICKS !== '0';

const contentSlots = [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25];
const bots = [];
const stats = { spawned: 0, windows: 0, clicks: 0, clickErrors: 0, botErrors: 0 };
let stopping = false;

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function spawnBot(index) {
  const username = `${prefix}${String(index).padStart(3, '0')}`.slice(0, 16);
  const bot = mineflayer.createBot({
    host,
    port,
    username,
    auth: 'offline',
    version,
    physicsEnabled: false,
    hideErrors: true,
  });
  bots.push(bot);
  bot.on('windowOpen', () => {
    stats.windows++;
  });
  bot.on('error', () => {
    stats.botErrors++;
  });
  bot.on('kicked', (reason) => {
    stats.botErrors++;
    if (bots.length < 5) console.error('kicked', username, reason);
  });
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`spawn timeout ${username}`)), 30000);
    bot.once('spawn', () => {
      clearTimeout(timer);
      stats.spawned++;
      resolve();
    });
    bot.once('error', (error) => {
      clearTimeout(timer);
      reject(error);
    });
  });
  return bot;
}

async function clickLoop(bot) {
  while (!stopping && bot.entity) {
    await sleep(2500 + Math.floor(Math.random() * 1500));
    if (!bot.currentWindow) continue;
    try {
      const slot = contentSlots[Math.floor(Math.random() * contentSlots.length)];
      bot.clickWindow(slot, 0, 0);
      stats.clicks++;
    } catch {
      stats.clickErrors++;
    }
  }
}

async function main() {
  console.log(`Spawning ${count} bots against ${host}:${port} (MC ${version})...`);
  for (let i = 0; i < count; i++) {
    try {
      const bot = await spawnBot(i);
      if (clickEnabled) clickLoop(bot);
    } catch (error) {
      console.error(`Bot ${i} failed: ${error.message}`);
    }
    if (i % 10 === 9) console.log(`  ${i + 1}/${count} spawned`);
    await sleep(40);
  }
  console.log(`Spawned ${stats.spawned}/${count}; waiting for server to settle...`);
  await sleep(3000);
  await rconCommand(host, rconPort, rconPassword, 'bentotest giveall 1000').catch(() => {});
  await rconCommand(host, rconPort, rconPassword, 'bentotest openall shop').catch((e) => console.error(e.message));
  console.log('Menus requested. Running load phase...');
  const started = Date.now();
  const tpsSamples = [];
  while (Date.now() - started < durationSeconds * 1000) {
    await sleep(10000);
    const status = await rconCommand(host, rconPort, rconPassword, 'bentotest status').catch(() => '');
    if (status) {
      tpsSamples.push(status.trim());
      console.log(status.trim());
    }
  }
  stopping = true;
  await rconCommand(host, rconPort, rconPassword, 'bentotest status').catch(() => {});
  const finalStatus = await rconCommand(host, rconPort, rconPassword, 'bentotest status').catch(() => '');
  const result = {
    timestamp: new Date().toISOString(),
    host,
    port,
    version,
    requested: count,
    ...stats,
    finalStatus: finalStatus.trim(),
    tpsSamples,
  };
  const dir = path.resolve('results');
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, `loadtest-${version}-${count}-${Date.now()}.json`);
  fs.writeFileSync(file, JSON.stringify(result, null, 2));
  console.log(`Saved ${file}`);
  for (const bot of bots) {
    try { bot.quit('test complete'); } catch {}
  }
  await sleep(1000);
  process.exit(0);
}

process.on('SIGINT', () => {
  stopping = true;
  for (const bot of bots) {
    try { bot.quit('interrupted'); } catch {}
  }
  process.exit(0);
});

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
