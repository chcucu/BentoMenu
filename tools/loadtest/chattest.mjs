import mineflayer from 'mineflayer';

const host = process.argv[2] || '127.0.0.1';
const port = Number(process.argv[3] || 25565);
const version = process.argv[4] || '1.18.2';
const term = process.argv[5] || '苹果';

const bot = mineflayer.createBot({ host, port, username: 'ChatSearchBot', auth: 'offline', version, physicsEnabled: false });
function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }
function contentNames(window) {
  if (!window) return [];
  const slots = [10,11,12,13,14,15,16,19,20,21,22,23,24,25];
  return slots.map((slot) => window.slots[slot]).filter(Boolean).map((item) => item.customName || item.name);
}
bot.on('windowOpen', (w) => console.log('WINDOW', JSON.stringify(w.title), 'type=' + w.type));
bot.on('windowClose', () => console.log('WINDOW_CLOSED'));
let lastMessage = '';
bot.on('message', (message) => {
  const text = message.toString();
  if (text.trim()) console.log('CHAT', text);
});
bot.on('error', (e) => console.error('BOT_ERROR', e.message));

bot.once('spawn', async () => {
  await sleep(800);
  bot.chat('/bento open shop');
  await sleep(2000);
  console.log('BEFORE', contentNames(bot.currentWindow).length);
  bot.clickWindow(37, 0, 0);
  await sleep(1000);
  console.log('AFTER_CLICK_WINDOW', bot.currentWindow && bot.currentWindow.type);
  bot.chat(term);
  await sleep(3000);
  console.log('AFTER_SEARCH_WINDOW', bot.currentWindow && bot.currentWindow.type);
  const names = contentNames(bot.currentWindow);
  console.log('AFTER_COUNT', names.length);
  console.log('AFTER_NAMES', JSON.stringify(names));
  bot.quit('chat search complete');
  process.exit(0);
});
setTimeout(() => { console.error('TIMEOUT'); process.exit(2); }, 25000);
