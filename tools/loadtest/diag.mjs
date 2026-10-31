import mineflayer from 'mineflayer';

const host = process.argv[2] || '127.0.0.1';
const port = Number(process.argv[3] || 25565);
const version = process.argv[4] || '1.18.2';
const username = process.argv[5] || `Diag${Date.now() % 100000}`;
const clicks = Number(process.argv[6] || 1);

const bot = mineflayer.createBot({ host, port, username, auth: 'offline', version, physicsEnabled: false });
function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }
function snapshot() {
  const map = {};
  for (const item of bot.inventory.items()) {
    map[item.name] = (map[item.name] || 0) + item.count;
  }
  return map;
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
  await sleep(1000);
  bot.chat('/bento open shop');
  await sleep(2200);
  console.log('BEFORE', JSON.stringify(snapshot()));
  for (let i = 0; i < clicks; i++) {
    bot.clickWindow(10, 0, 0);
    await sleep(i === 0 ? 150 : 1500);
  }
  await sleep(1600);
  if (bot.currentWindow) {
    bot.closeWindow(bot.currentWindow);
    await sleep(1200);
  }
  console.log('AFTER', JSON.stringify(snapshot()));
  console.log(JSON.stringify({ username, clicks, success, cooldown }));
  bot.quit('diag');
  process.exit(0);
});
setTimeout(() => { console.error('TIMEOUT'); process.exit(2); }, 30000);
