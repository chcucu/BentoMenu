import mineflayer from 'mineflayer';

const host = process.argv[2] || '127.0.0.1';
const port = Number(process.argv[3] || 25565);
const version = process.argv[4] || '1.18.2';

const bot = mineflayer.createBot({ host, port, username: 'InspectBot', auth: 'offline', version, physicsEnabled: false });
function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }
function slotName(window, slot) {
  const item = window && window.slots[slot];
  return item ? item.name : null;
}
function content(window) {
  if (!window) return [];
  return [10,11,12,13,14,15,16,19,20,21,22,23,24,25].map((s) => slotName(window, s)).filter(Boolean);
}
function countItem(name) {
  return bot.inventory.items().filter((i) => i.name === name).reduce((sum, i) => sum + i.count, 0);
}
bot.on('windowOpen', (w) => console.log('WINDOW', JSON.stringify(w.title)));
bot.on('message', (msg) => {
  const text = msg.toString();
  if (text.includes('Bento') || text.includes('购买')) console.log('CHAT', text);
});
bot.on('error', (e) => console.error('BOT_ERROR', e.message));

bot.once('spawn', async () => {
  await sleep(800);
  bot.chat('/bento open shop');
  await sleep(2200);
  console.log('PAGE1', JSON.stringify(content(bot.currentWindow)));
  bot.clickWindow(41, 0, 0);
  await sleep(900);
  console.log('PAGE2', JSON.stringify(content(bot.currentWindow)));
  bot.clickWindow(39, 0, 0);
  await sleep(900);
  console.log('PAGE1_BACK', JSON.stringify(content(bot.currentWindow)));
  bot.clickWindow(29, 0, 0);
  await sleep(1200);
  console.log('CATEGORY_FOOD', JSON.stringify(content(bot.currentWindow)));
  console.log('TORCH_BEFORE', countItem('torch'));
  const first = content(bot.currentWindow)[0];
  console.log('BUY_TARGET', first);
  bot.clickWindow(10, 0, 0);
  await sleep(1200);
  console.log('COOKIE_AFTER', countItem('cookie'));
  console.log('TORCH_AFTER', countItem('torch'));
  console.log('BALANCE_LORE', JSON.stringify(bot.currentWindow && bot.currentWindow.slots[4] && bot.currentWindow.slots[4].customName));
  bot.quit('inspect complete');
  process.exit(0);
});
setTimeout(() => { console.error('TIMEOUT'); process.exit(2); }, 30000);
