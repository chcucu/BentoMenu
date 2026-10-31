import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

const outRoot = process.argv[2] || path.resolve('resourcepack');
const assets = path.join(outRoot, 'assets', 'minecraft');
const itemModels = path.join(assets, 'models', 'item');
const textures = path.join(assets, 'textures', 'item', 'bento');

fs.rmSync(outRoot, { recursive: true, force: true });
fs.mkdirSync(itemModels, { recursive: true });
fs.mkdirSync(textures, { recursive: true });

function crcTable() {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c;
  }
  return table;
}
const CRC = crcTable();

function crc32(buffer) {
  let c = 0xffffffff;
  for (const byte of buffer) c = CRC[(c ^ byte) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const typeBuffer = Buffer.from(type, 'ascii');
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuffer, data])));
  return Buffer.concat([length, typeBuffer, data, crc]);
}

function png(width, height, pixelFn) {
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0;
    for (let x = 0; x < width; x++) {
      const rgba = pixelFn(x, y, width, height);
      const offset = y * (width * 4 + 1) + 1 + x * 4;
      raw[offset] = rgba[0];
      raw[offset + 1] = rgba[1];
      raw[offset + 2] = rgba[2];
      raw[offset + 3] = rgba[3];
    }
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8;
  header[9] = 6;
  header[10] = 0;
  header[11] = 0;
  header[12] = 0;
  const idat = zlib.deflateSync(raw, { level: 9 });
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', idat),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

const size = 64;
const clear = [0, 0, 0, 0];

function roundedRect(x, y, left, top, right, bottom, radius) {
  const cx = Math.min(Math.max(x + 0.5, left + radius), right - radius);
  const cy = Math.min(Math.max(y + 0.5, top + radius), bottom - radius);
  const dx = x + 0.5 - cx;
  const dy = y + 0.5 - cy;
  return dx * dx + dy * dy <= radius * radius + 0.5;
}

function mix(a, b, t) {
  return [
    Math.round(a[0] + (b[0] - a[0]) * t),
    Math.round(a[1] + (b[1] - a[1]) * t),
    Math.round(a[2] + (b[2] - a[2]) * t),
    Math.round(a[3] + (b[3] - a[3]) * t),
  ];
}

function borderTexture() {
  return png(size, size, (x, y) => {
    const t = (x + y) / (size * 2 - 2);
    const base = mix([28, 40, 68, 210], [96, 82, 148, 210], t);
    const stripe = Math.sin((x - y) / 6.0) * 6;
    const alpha = roundedRect(x, y, 1, 1, size - 1, size - 1, 3) ? 235 : 0;
    return [base[0] + stripe, base[1] + stripe, base[2] + stripe, alpha];
  });
}

function iconBase(inner) {
  return png(size, size, (x, y) => {
    if (Math.abs(x + 0.5 - size / 2) > size / 2 - 3 || Math.abs(y + 0.5 - size / 2) > size / 2 - 3) {
      return clear;
    }
    return inner(x, y);
  });
}

function circleFill(x, y, cx, cy, radius) {
  const dx = x + 0.5 - cx;
  const dy = y + 0.5 - cy;
  return dx * dx + dy * dy <= radius * radius;
}

function lineDistance(px, py, ax, ay, bx, by) {
  const dx = bx - ax;
  const dy = by - ay;
  const t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
  const qx = ax + t * dx;
  const qy = ay + t * dy;
  return Math.hypot(px - qx, py - qy);
}

function searchTexture() {
  return iconBase((x, y) => {
    const rim = circleFill(x, y, 30, 30, 20) && !circleFill(x, y, 30, 30, 16);
    const handle = lineDistance(x + 0.5, y + 0.5, 44, 44, 56, 56) < 3;
    const glint = lineDistance(x + 0.5, y + 0.5, 22, 22, 30, 18) < 2;
    if (handle) return [210, 225, 255, 255];
    if (rim) return [122, 215, 255, 255];
    if (glint) return [180, 235, 255, 220];
    return clear;
  });
}

function arrowTexture(direction) {
  return iconBase((x, y) => {
    const cx = direction === 'left' ? 34 : 30;
    const tipX = direction === 'left' ? 20 : 44;
    const d = lineDistance(x + 0.5, y + 0.5, tipX, 32, cx, 18)
      + lineDistance(x + 0.5, y + 0.5, tipX, 32, cx, 46);
    if (d < 3) return [234, 242, 255, 255];
    return clear;
  });
}

function pageTexture() {
  return iconBase((x, y) => {
    if (roundedRect(x, y, 18, 10, 46, 54, 4)) {
      const fold = x > 36 && y < 60 && Math.abs(x - y + 4) < 18 && y < 22;
      const textLine = (y % 8 === 0) && x > 24 && x < 40 && y > 24 && y < 46;
      if (textLine) return [122, 215, 255, 255];
      if (fold) return [140, 190, 230, 255];
      return [235, 241, 250, 255];
    }
    return clear;
  });
}

function tagTexture() {
  return iconBase((x, y) => {
    if (roundedRect(x, y, 14, 20, 50, 44, 6)) {
      const hole = circleFill(x, y, 22, 32, 4);
      const line = x > 30 && x < 44 && Math.abs(y - 32) < 1.5;
      if (hole) return clear;
      if (line) return [122, 215, 255, 255];
      return [255, 209, 102, 255];
    }
    return clear;
  });
}

function closeTexture() {
  return iconBase((x, y) => {
    const first = lineDistance(x + 0.5, y + 0.5, 20, 20, 44, 44) < 3;
    const second = lineDistance(x + 0.5, y + 0.5, 44, 20, 20, 44) < 3;
    if (first || second) return [255, 107, 107, 255];
    return clear;
  });
}

function clockTexture() {
  return iconBase((x, y) => {
    const rim = circleFill(x, y, 32, 32, 22) && !circleFill(x, y, 32, 32, 18);
    const hand1 = lineDistance(x + 0.5, y + 0.5, 32, 32, 32, 18) < 2;
    const hand2 = lineDistance(x + 0.5, y + 0.5, 32, 32, 42, 36) < 2;
    if (rim || hand1 || hand2) return [255, 200, 87, 255];
    return clear;
  });
}

function errorTexture() {
  return iconBase((x, y) => {
    const triangle = y + 0.5 > 14
      && y + 0.5 < 52
      && Math.abs(x + 0.5 - 32) < (y + 0.5 - 14) * 0.52;
    const bang = Math.abs(x - 32) < 2.5 && y > 22 && y < 38;
    const dot = Math.abs(x - 32) < 2.5 && y > 42 && y < 46;
    if (triangle && (bang || dot)) return [255, 190, 90, 255];
    if (triangle) return [120, 40, 40, 255];
    return clear;
  });
}

const texturesWritten = {
  border: borderTexture(),
  search: searchTexture(),
  previous: arrowTexture('left'),
  next: arrowTexture('right'),
  page: pageTexture(),
  category: tagTexture(),
  close: closeTexture(),
  loading: clockTexture(),
  error: errorTexture(),
};

for (const [name, buffer] of Object.entries(texturesWritten)) {
  fs.writeFileSync(path.join(textures, `${name}.png`), buffer);
}

function model(name) {
  return {
    parent: 'item/generated',
    textures: { layer0: `item/bento/${name}` },
    display: {
      gui: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] },
    },
  };
}

for (const name of Object.keys(texturesWritten)) {
  fs.writeFileSync(path.join(itemModels, `bento_${name}.json`), JSON.stringify(model(name), null, 2));
}

function overrides(baseModel, entries) {
  const json = {
    parent: baseModel,
    overrides: entries.map(([predicate, modelName]) => ({
      predicate: { custom_model_data: predicate },
      model: `item/bento_${modelName}`,
    })),
  };
  return JSON.stringify(json, null, 2);
}

fs.writeFileSync(path.join(itemModels, 'leather_horse_armor.json'),
  overrides('item/leather_horse_armor', [[9001, 'border']]));
fs.writeFileSync(path.join(itemModels, 'compass.json'),
  overrides('item/compass', [[9101, 'search']]));
fs.writeFileSync(path.join(itemModels, 'arrow.json'), JSON.stringify({
  parent: 'item/generated',
  textures: { layer0: 'item/arrow' },
  overrides: [
    { predicate: { custom_model_data: 9102 }, model: 'item/bento_previous' },
    { predicate: { custom_model_data: 9103 }, model: 'item/bento_next' },
  ],
}, null, 2));
fs.writeFileSync(path.join(itemModels, 'paper.json'),
  overrides('item/paper', [[9104, 'page']]));
fs.writeFileSync(path.join(itemModels, 'name_tag.json'),
  overrides('item/name_tag', [[9105, 'category']]));
fs.writeFileSync(path.join(itemModels, 'barrier.json'), JSON.stringify({
  parent: 'item/generated',
  textures: { layer0: 'item/barrier' },
  overrides: [
    { predicate: { custom_model_data: 9106 }, model: 'item/bento_close' },
    { predicate: { custom_model_data: 9108 }, model: 'item/bento_error' },
  ],
}, null, 2));
fs.writeFileSync(path.join(itemModels, 'clock.json'),
  overrides('item/clock', [[9107, 'loading']]));

const pack = {
  pack: {
    pack_format: 8,
    description: 'BentoMenu optional UI pack — gradient glass borders and icons',
    supported_formats: { min_inclusive: 8, max_inclusive: 100 },
  },
};
fs.writeFileSync(path.join(outRoot, 'pack.mcmeta'), JSON.stringify(pack, null, 2));
fs.writeFileSync(path.join(outRoot, 'README.txt'), [
  'BentoMenu optional resource pack',
  '',
  '1. Put this folder (or zip) on your web server / CDN.',
  '2. Set ui.resource-pack: true in BentoMenu config.yml.',
  '3. Themes use resource-pack-material: LEATHER_HORSE_ARMOR with model data 9001,',
  '   which keeps per-slot RGB tinting for true gradients.',
  '4. Icons use custom-model-data 9101-9108 on vanilla materials.',
  '',
  'Without this pack BentoMenu falls back to vanilla stained glass; all menus remain usable.',
].join('\n'));

console.log('resource pack written to', outRoot);
