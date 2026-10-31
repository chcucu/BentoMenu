import mineflayer from 'mineflayer';

const host = process.argv[2] || '127.0.0.1';
const port = Number(process.argv[3] || 25565);
const version = process.argv[4] || '1.18.2';
const term = process.argv[5] || '苹果';

const bot = mineflayer.createBot({ host, port, username: 'SearchBot', auth: 'offline', version, physicsEnabled: false });

function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }
let lastOpen = 0;
bot.on('windowOpen', (w) => {
  lastOpen = Date.now();
  console.log('WINDOW', JSON.stringify(w.title), 'type=' + w.type);
});
bot.on('error', (e) => console.error('BOT_ERROR', e.message));

function contentNames(window) {
  if (!window) return [];
  const slots = [10,11,12,13,14,15,16,19,20,21,22,23,24,25];
  return slots.map((slot) => {
    const item = window.slots[slot];
    return item ? (item.customName || item.name) : null;
  }).filter(Boolean);
}

bot.once('spawn', async () => {
  await sleep(800);
  bot.chat('/bento open shop');
  await sleep(2000);
  console.log('BEFORE', contentNames(bot.currentWindow).length);
  bot.clickWindow(37, 0, 0);
  await sleep(1000);
  console.log('ANVIL', bot.currentWindow && bot.currentWindow.type);
  try {
    bot._client.write('name_item', { name: term });
  } catch (error) {
    console.error('NAME_ITEM_FAIL', error.message);
  }
  await sleep(800);
  console.log('RESULT_ITEM', bot.currentWindow && bot.currentWindow.slots[2] && (bot.currentWindow.slots[2].customName || bot.currentWindow.slots[2].name));
  try {
    await bot.clickWindow(2, 0, 0);
    console.log('RESULT_CLICK_SENT');
  } catch (error) {
    console.error('RESULT_CLICK_FAIL', error.message);
  }
  await sleep(2200);
  console.log('AFTER_WINDOW', bot.currentWindow && bot.currentWindow.type);
  const names = contentNames(bot.currentWindow);
  console.log('AFTER_COUNT', names.length);
  console.log('AFTER_NAMES', JSON.stringify(names));
  bot.quit('search test complete');
  process.exit(0);
});

setTimeout(() => { console.error('TIMEOUT'); process.exit(2); }, 25000);
