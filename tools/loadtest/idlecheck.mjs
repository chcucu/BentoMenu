import mineflayer from 'mineflayer';

const host = process.argv[2] || '127.0.0.1';
const port = Number(process.argv[3] || 25565);
const version = process.argv[4] || '1.18.2';
const username = process.argv[5] || `Idle${Date.now() % 100000}`;
const bot = mineflayer.createBot({ host, port, username, auth: 'offline', version, physicsEnabled: false, hideErrors: true });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const loreOf = (window, slot) => {
  const item = window && window.slots[slot];
  return item ? JSON.stringify(item.customLore || item.nbt || null) : null;
};
const nameOf = (window, slot) => {
  const item = window && window.slots[slot];
  return item ? JSON.stringify(item.customName || item.name) : null;
};
let slotPackets = 0;
bot.on('packet', (data, meta) => {
  if (meta.name === 'window_items' || meta.name === 'set_slot') slotPackets++;
});
bot.once('spawn', async () => {
  await sleep(600);
  bot.chat('/bento open shop');
  await sleep(2200);
  console.log('BEFORE_BALANCE_LORE', loreOf(bot.currentWindow, 4));
  console.log('BEFORE_COOKIE_LORE', loreOf(bot.currentWindow, 10));
  slotPackets = 0;
  bot.clickWindow(10, 0, 0);
  await sleep(1000);
  console.log('AFTER_1S_BALANCE_LORE', loreOf(bot.currentWindow, 4));
  console.log('AFTER_1S_COOKIE_LORE', loreOf(bot.currentWindow, 10));
  console.log('AFTER_1S_SLOT_PACKETS', slotPackets);
  await sleep(6000);
  console.log('AFTER_7S_BALANCE_LORE', loreOf(bot.currentWindow, 4));
  console.log('AFTER_7S_SLOT_PACKETS', slotPackets);
  console.log('COOKIES', bot.inventory.items().filter((i) => i.name === 'cookie').reduce((s, i) => s + i.count, 0));
  bot.quit('idlecheck');
  process.exit(0);
});
bot.on('error', (e) => console.error('ERR', e.message));
setTimeout(() => { console.error('TIMEOUT'); process.exit(2); }, 25000);
