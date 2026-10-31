import mineflayer from 'mineflayer';

const host = process.argv[2] || '127.0.0.1';
const port = Number(process.argv[3] || 25565);
const version = process.argv[4] || '1.18.2';
const username = process.argv[5] || 'Probe';
const rounds = Number(process.argv[6] || 5);

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

const bot = mineflayer.createBot({
  host,
  port,
  username,
  auth: 'offline',
  version,
  physicsEnabled: false,
  hideErrors: false,
});

let pendingOpen = null;
let pendingSound = null;
let pendingSlot = null;

bot.on('windowOpen', (window) => {
  if (pendingOpen) {
    const elapsed = Number(process.hrtime.bigint() - pendingOpen) / 1e6;
    console.log(`MENU_OPEN_RTT_MS=${elapsed.toFixed(2)} title=${JSON.stringify(window.title)} slots=${window.slots.length}`);
    pendingOpen = null;
  }
});

bot.on('soundEffectHeard', () => {
  if (pendingSound) {
    const elapsed = Number(process.hrtime.bigint() - pendingSound) / 1e6;
    console.log(`CLICK_TO_SOUND_RTT_MS=${elapsed.toFixed(2)}`);
    pendingSound = null;
  }
});

bot.on('packet', (data, meta) => {
  if (pendingSlot && (meta.name === 'window_items' || meta.name === 'set_slot')) {
    const elapsed = Number(process.hrtime.bigint() - pendingSlot) / 1e6;
    console.log(`CLICK_TO_SLOT_UPDATE_MS=${elapsed.toFixed(2)} packet=${meta.name}`);
    pendingSlot = null;
  }
});

bot.on('spawn', async () => {
  await sleep(1000);
  pendingOpen = process.hrtime.bigint();
  bot.chat('/bento open shop');
  await sleep(2500);
  if (!bot.currentWindow) {
    console.log('NO_WINDOW');
    bot.quit('probe failed');
    return;
  }
  for (let i = 0; i < rounds; i++) {
    await sleep(2000);
    pendingSound = process.hrtime.bigint();
    pendingSlot = process.hrtime.bigint();
    bot.clickWindow(10, 0, 0);
    await sleep(1200);
  }
  await sleep(500);
  bot.quit('probe complete');
  process.exit(0);
});

bot.on('error', (error) => console.error('BOT_ERROR', error.message));
bot.on('kicked', (reason) => console.error('KICKED', reason));

setTimeout(() => {
  console.error('PROBE_TIMEOUT');
  process.exit(2);
}, 30000);
