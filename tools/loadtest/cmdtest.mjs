import mineflayer from 'mineflayer';

const host = process.argv[2] || '127.0.0.1';
const port = Number(process.argv[3] || 25565);
const version = process.argv[4] || '1.18.2';
const username = process.argv[5] || 'CmdCheck';
const bot = mineflayer.createBot({ host, port, username, auth: 'offline', version, physicsEnabled: false, hideErrors: true });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let lines = [];
bot.on('message', (m) => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(600);
  const run = async (cmd) => {
    lines = [];
    bot.chat(cmd);
    await sleep(1400);
    console.log(`>> ${cmd}`);
    lines.forEach((l) => console.log('   ' + l));
  };
  await run('/bento info');
  await run('/bento list');
  await run('/bento theme sakura');
  await run('/bento language en_US');
  await run('/bento language zh_CN');
  await run('/bento validate');
  await run('/bento reload');
  await sleep(1500);
  bot.quit('cmdtest');
  process.exit(0);
});
bot.on('error', (e) => console.error('ERR', e.message));
setTimeout(() => { console.error('TIMEOUT'); process.exit(2); }, 30000);
