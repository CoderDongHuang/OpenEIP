'use strict';

const decoder = new TextDecoder();
const MAX_INPUT_SIZE = 512 * 1024;

function fail(message) {
  throw new TypeError(`Unsupported or invalid image: ${message}`);
}

function ascii(input, start, end) {
  return decoder.decode(input.subarray(start, end));
}

function uint16le(input, offset) {
  return input[offset] | (input[offset + 1] << 8);
}

function uint32be(input, offset) {
  return (input[offset] * 0x1000000) +
    ((input[offset + 1] << 16) | (input[offset + 2] << 8) | input[offset + 3]);
}

function png(input) {
  if (input.length < 24 || input[0] !== 0x89 || ascii(input, 1, 8) !== 'PNG\r\n\x1a\n') return null;
  return { width: uint32be(input, 16), height: uint32be(input, 20), type: 'png' };
}

function gif(input) {
  if (input.length < 10 || !/^GIF8[79]a$/.test(ascii(input, 0, 6))) return null;
  return { width: uint16le(input, 6), height: uint16le(input, 8), type: 'gif' };
}

function jpeg(input) {
  if (input.length < 4 || input[0] !== 0xff || input[1] !== 0xd8) return null;
  let offset = 2;
  while (offset + 1 < input.length) {
    while (offset < input.length && input[offset] !== 0xff) offset += 1;
    while (offset < input.length && input[offset] === 0xff) offset += 1;
    if (offset >= input.length) break;
    const marker = input[offset++];
    if (marker === 0xd8 || marker === 0xd9 || (marker >= 0xd0 && marker <= 0xd7)) continue;
    if (marker === 0xda) break;
    if (offset + 1 >= input.length) break;
    const segmentLength = (input[offset] << 8) | input[offset + 1];
    if (segmentLength < 2 || offset + segmentLength > input.length) break;
    const isStartOfFrame = (marker >= 0xc0 && marker <= 0xc3) ||
      (marker >= 0xc5 && marker <= 0xc7) ||
      (marker >= 0xc9 && marker <= 0xcb) ||
      (marker >= 0xcd && marker <= 0xcf);
    if (isStartOfFrame && segmentLength >= 7) {
      return {
        width: (input[offset + 5] << 8) | input[offset + 6],
        height: (input[offset + 3] << 8) | input[offset + 4],
        type: 'jpg'
      };
    }
    offset += segmentLength;
  }
  fail('jpeg dimensions not found');
}

function webp(input) {
  if (input.length < 30 || ascii(input, 0, 4) !== 'RIFF' || ascii(input, 8, 12) !== 'WEBP') return null;
  if (ascii(input, 12, 16) !== 'VP8X') fail('webp variant is not supported');
  const width = 1 + input[24] + (input[25] << 8) + (input[26] << 16);
  const height = 1 + input[27] + (input[28] << 8) + (input[29] << 16);
  return { width, height, type: 'webp' };
}

function imageSize(input) {
  if (!Buffer.isBuffer(input) && !(input instanceof Uint8Array)) fail('input must be bytes');
  if (input.length === 0 || input.length > MAX_INPUT_SIZE) fail('input exceeds bounded parser limit');
  const bytes = Buffer.from(input.buffer, input.byteOffset, input.byteLength);
  const result = png(bytes) || gif(bytes) || webp(bytes);
  if (result) return result;
  if (bytes[0] === 0xff && bytes[1] === 0xd8) return jpeg(bytes);
  if (ascii(bytes, 0, 4) === 'icns') fail('icns parser disabled');
  if (ascii(bytes, 4, 8) === 'JXL ' || (bytes[0] === 0xff && bytes[1] === 0x0a)) fail('jxl parser disabled');
  if (ascii(bytes, 4, 8) === 'ftyp') fail('heif parser disabled');
  fail('format is not enabled for documentation assets');
}

module.exports = { default: imageSize, disableTypes: () => {}, imageSize, types: ['gif', 'jpg', 'png', 'webp'] };
