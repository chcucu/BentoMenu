import fs from 'node:fs';

const project = process.argv[2] || 'paper';
const version = process.argv[3] || '1.21.11';
const dest = process.argv[4];

async function main() {
  const metaUrl = `https://fill.papermc.io/v3/projects/${project}/versions/${version}/builds/latest`;
  const response = await fetch(metaUrl, { headers: { 'User-Agent': 'BentoMenu-Tests' } });
  if (!response.ok) {
    throw new Error(`metadata ${response.status} ${metaUrl}`);
  }
  const json = await response.json();
  const url = json.downloads?.['server:default']?.url;
  if (!url) {
    console.log(JSON.stringify(json).slice(0, 1000));
    throw new Error('no server:default download');
  }
  console.log(`${project} ${version} build=${json.id}`);
  console.log(url);
  if (dest) {
    const download = await fetch(url, { headers: { 'User-Agent': 'BentoMenu-Tests' } });
    if (!download.ok) {
      throw new Error(`download ${download.status}`);
    }
    const buffer = Buffer.from(await download.arrayBuffer());
    fs.writeFileSync(dest, buffer);
    console.log('saved', dest, buffer.length);
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
