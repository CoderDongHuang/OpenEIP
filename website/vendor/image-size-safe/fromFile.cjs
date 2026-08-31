'use strict';

const fs = require('fs');
const path = require('path');
const { imageSize } = require('./index.cjs');

const MAX_INPUT_SIZE = 512 * 1024;
let concurrency = 100;

function setConcurrency(value) {
  if (!Number.isInteger(value) || value < 1) throw new TypeError('concurrency must be positive');
  concurrency = value;
}

async function imageSizeFromFile(filePath) {
  const handle = await fs.promises.open(path.resolve(filePath), 'r');
  try {
    const { size } = await handle.stat();
    if (size <= 0) throw new Error('Empty file');
    const input = Buffer.alloc(Math.min(size, MAX_INPUT_SIZE));
    await handle.read(input, 0, input.length, 0);
    return imageSize(input);
  } finally {
    await handle.close();
  }
}

module.exports = { imageSizeFromFile, setConcurrency, get concurrency() { return concurrency; } };
