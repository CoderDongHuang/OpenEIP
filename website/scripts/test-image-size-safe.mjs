import assert from 'node:assert/strict';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { imageSizeFromFile } from 'image-size/fromFile';

const directory = await mkdtemp(join(process.cwd(), '.tmp-image-size-'));
try {
  const png = Buffer.from([
    0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 2
  ]);
  const safePath = join(directory, 'safe.png');
  await writeFile(safePath, png);
  assert.deepEqual(await imageSizeFromFile(safePath), { width: 3, height: 2, type: 'png' });

  const hostilePath = join(directory, 'hostile.icns');
  await writeFile(hostilePath, Buffer.from('icns\x00\x00\x00\x08', 'binary'));
  await assert.rejects(imageSizeFromFile(hostilePath), /icns parser disabled/);
} finally {
  await rm(directory, { recursive: true, force: true });
}

console.log('safe image-size parser checks passed');
