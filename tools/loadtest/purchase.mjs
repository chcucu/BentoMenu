import mineflayer from 'mineflayer';

const host = process.argv[2] || '127.0.0.1';
const port = Number(process.argv[3] || 25565);
const version = process.argv[4] || '1.18.2';
const rounds = Number(process.argv[5] || 3);
const username = process.argv[6] || `Buy${Date.now() % 100000}`;

const bot = mineflayer.createBot({ host, port, username, auth: 'offline', version, physicsEnabled: false });
function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }
function countItem() {
  return bot.inventory.items().reduce((sum, i) => sum + i.count, 0);
}
let messages = 0;
bot.on('message', (msg) => {
  const text = msg.toString();
  if (text.includes('购买成功')) messages++;
});
bot.on('error', (e) => console.error('BOT_ERROR', e.message));

bot.once('spawn', async () => {
  await sleep(800);
  bot.chat('/bento open shop');
  await sleep(2200);
  const before = countItem();
  for (let i = 0; i < rounds; i++) {
    bot.clickWindow(10, 0, 0);
    await sleep(1500);
  }
  if (bot.currentWindow) {
    bot.closeWindow(bot.currentWindow);
    await sleep(1200);
  }
  const after = countItem();
  console.log(JSON.stringify({ username, rounds, before, after, delta: after - before, messages }));
  bot.quit('purchase test');
  process.exit(0);
});
setTimeout(() => { console.error('TIMEOUT'); process.exit(2); }, 30000);
