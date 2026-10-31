import mineflayer from 'mineflayer';

const host = process.argv[2] || '127.0.0.1';
const port = Number(process.argv[3] || 25565);
const version = process.argv[4] || '1.18.2';
const menu = process.argv[5] || 'example-addon';
const username = process.argv[6] || `Menu${Date.now() % 100000}`;
const bot = mineflayer.createBot({ host, port, username, auth: 'offline', version, physicsEnabled: false, hideErrors: true });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
bot.on('windowOpen', (w) => console.log('WINDOW', w.type, w.slots.length, JSON.stringify(w.title)));
bot.on('message', (m) => console.log('CHAT', m.toString()));
bot.once('spawn', async () => {
  await sleep(600);
  bot.chat(`/bento open ${menu}`);
  await sleep(2500);
  if (!bot.currentWindow) { console.log('NO_WINDOW'); bot.quit('fail'); process.exit(1); }
  const items = [];
  for (let i = 0; i < bot.currentWindow.slots.length; i++) {
    const item = bot.currentWindow.slots[i];
    if (item) items.push({ slot: i, name: item.name, display: item.customName ? JSON.parse(item.customName).extra?.map((e) => e.text).join('') : null });
  }
  console.log('ITEMS', JSON.stringify(items));
  bot.quit('menutest');
  process.exit(0);
});
bot.on('error', (e) => console.error('ERR', e.message));
setTimeout(() => { console.error('TIMEOUT'); process.exit(2); }, 20000);
