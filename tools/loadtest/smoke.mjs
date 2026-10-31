import mineflayer from 'mineflayer';

const host = process.argv[2] || '127.0.0.1';
const port = Number(process.argv[3] || 25565);
const version = process.argv[4] || '1.18.2';
const username = process.argv[5] || 'SmokeBot';

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

const bot = mineflayer.createBot({
  host, port, username, auth: 'offline', version, physicsEnabled: false, hideErrors: false,
});

let lastWindowAt = 0;
bot.on('windowOpen', (window) => {
  lastWindowAt = Date.now();
  const top = window.slots.slice(0, Math.min(window.slots.length, 45));
  const filled = top.filter((slot) => slot).length;
  const names = top.filter((slot) => slot).slice(0, 6).map((slot) => slot.customName || slot.name);
  console.log(`WINDOW title=${JSON.stringify(window.title)} type=${window.type} slots=${window.slots.length} filled=${filled} names=${JSON.stringify(names)}`);
});

async function waitWindow(timeout = 4000) {
  const started = Date.now();
  while (Date.now() - started < timeout) {
    if (Date.now() - lastWindowAt < 500 && bot.currentWindow) return bot.currentWindow;
    await sleep(100);
  }
  return bot.currentWindow;
}

async function click(slot, mode = 0) {
  lastWindowAt = 0;
  bot.clickWindow(slot, mode, 0);
  await waitWindow();
  await sleep(400);
}

bot.on('error', (error) => console.error('BOT_ERROR', error.message));
bot.on('kicked', (reason) => console.error('KICKED', reason));

bot.once('spawn', async () => {
  await sleep(1000);
  bot.chat('/bento open main');
  await waitWindow();
  await sleep(500);

  console.log('--- main -> profile');
  await click(13);
  console.log('--- profile -> back');
  await click(20);
  console.log('--- main -> shop');
  await click(11);
  console.log('--- shop next page');
  await click(41);
  console.log('--- shop category food');
  await click(29);
  console.log('--- shop search (anvil)');
  await click(37);
  await sleep(800);
  if (bot.currentWindow) {
    console.log(`ANVIL type=${bot.currentWindow.type} title=${JSON.stringify(bot.currentWindow.title)}`);
    bot.closeWindow(bot.currentWindow);
    await sleep(1200);
  }
  console.log('--- reopen themes');
  bot.chat('/bento open themes');
  await waitWindow();
  await sleep(500);
  console.log('--- right-click aurora preview');
  await click(10, 1);
  console.log('--- apply preview theme');
  await click(11);
  await sleep(800);

  const api = 'smoke complete';
  console.log(api);
  bot.quit('smoke complete');
  process.exit(0);
});

setTimeout(() => {
  console.error('SMOKE_TIMEOUT');
  process.exit(2);
}, 40000);
