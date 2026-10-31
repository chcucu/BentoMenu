import mineflayer from 'mineflayer';

const host = process.argv[2] || '127.0.0.1';
const port = Number(process.argv[3] || 25565);
const version = process.argv[4] || '1.18.2';
const username = process.argv[5] || `Cool${Date.now() % 100000}`;

const bot = mineflayer.createBot({ host, port, username, auth: 'offline', version, physicsEnabled: false });
function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }
function countAll() {
  return bot.inventory.items().reduce((sum, i) => sum + i.count, 0);
}
let success = 0;
let cooldown = 0;
bot.on('message', (msg) => {
  const text = msg.toString();
  if (text.includes('购买成功')) success++;
  if (text.includes('冷却')) cooldown++;
});
bot.on('error', (e) => console.error('BOT_ERROR', e.message));

bot.once('spawn', async () => {
  await sleep(800);
  bot.chat('/bento open shop');
  await sleep(2200);
  const before = countAll();
  bot.clickWindow(10, 0, 0);
  await sleep(150);
  bot.clickWindow(10, 0, 0);
  await sleep(1600);
  if (bot.currentWindow) {
    bot.closeWindow(bot.currentWindow);
    await sleep(1000);
  }
  const after = countAll();
  console.log(JSON.stringify({ username, before, after, delta: after - before, success, cooldown }));
  bot.quit('cooldown test');
  process.exit(0);
});
setTimeout(() => { console.error('TIMEOUT'); process.exit(2); }, 30000);
