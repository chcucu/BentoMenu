import { execFileSync } from 'node:child_process';

const [, , url, dest] = process.argv;
if (!url || !dest) {
  console.error('usage: node download.mjs <url> <dest>');
  process.exit(2);
}
execFileSync('curl.exe', [
  '-L', '--retry', '5', '--retry-delay', '2', '--retry-all-errors',
  '--ssl-no-revoke', '--http1.1', '-o', dest, url
], { stdio: 'inherit' });
console.log('downloaded', dest);
