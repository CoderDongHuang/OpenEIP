import { describe, expect, it } from 'vitest';

import { SseParser } from './sse';

const request = '11111111-1111-4111-8111-111111111111';
const session = '22222222-2222-4222-8222-222222222222';

describe('SseParser', () => {
  it('parses arbitrarily fragmented LF and CRLF frames', () => {
    const parser = new SseParser();
    const stream =
      `event: token\ndata: {"requestId":"${request}","sessionId":"${session}","sequence":0,"token":"safe\\ntext"}\n\n` +
      `event: done\r\ndata: {"requestId":"${request}","sessionId":"${session}","finishReason":"stop","citations":[]}\r\n\r\n`;
    const events = [
      ...parser.push(stream.slice(0, 9)),
      ...parser.push(stream.slice(9, 47)),
      ...parser.push(stream.slice(47)),
    ];
    parser.finish();
    expect(events).toHaveLength(2);
    expect(events[0]).toMatchObject({ event: 'token', sequence: 0, token: 'safe\ntext' });
    expect(events[1]).toMatchObject({ event: 'done', finishReason: 'stop' });
  });

  it.each([
    'event: message\ndata: {}\n\n',
    'event: token\nevent: error\ndata: {}\n\n',
    'event: token\ndata: {}\ndata: {}\n\n',
    `event: token\ndata: {"requestId":"${request}","sessionId":"${session}","sequence":-1,"token":"x"}\n\n`,
  ])('rejects invalid event framing and data', (frame) => {
    expect(() => new SseParser().push(frame)).toThrow();
  });

  it('rejects incomplete terminal framing', () => {
    const parser = new SseParser();
    parser.push('event: token\ndata: {}');
    expect(() => parser.finish()).toThrow('Incomplete');
  });

  it.each([
    { documentId: request, chunkId: 'bad', sourceSha256: 'a'.repeat(64), score: 0.9 },
    { documentId: 'bad', chunkId: `chk_${'a'.repeat(32)}`, sourceSha256: 'a'.repeat(64), score: 0.9 },
    { documentId: request, chunkId: `chk_${'a'.repeat(32)}`, sourceSha256: 'bad', score: 0.9 },
    { documentId: request, chunkId: `chk_${'a'.repeat(32)}`, sourceSha256: 'a'.repeat(64), score: 2 },
    {
      documentId: request,
      chunkId: `chk_${'a'.repeat(32)}`,
      sourceSha256: 'a'.repeat(64),
      score: 0.9,
      unexpected: true,
    },
  ])('rejects malformed citation data', (citation) => {
    const frame = `event: done\ndata: ${JSON.stringify({
      requestId: request,
      sessionId: session,
      finishReason: 'stop',
      citations: [citation],
    })}\n\n`;
    expect(() => new SseParser().push(frame)).toThrow('Invalid citation');
  });

  it('rejects duplicate citations', () => {
    const citation = {
      documentId: request,
      chunkId: `chk_${'a'.repeat(32)}`,
      sourceSha256: 'a'.repeat(64),
      score: 0.9,
    };
    const frame = `event: done\ndata: ${JSON.stringify({
      requestId: request,
      sessionId: session,
      finishReason: 'stop',
      citations: [citation, citation],
    })}\n\n`;
    expect(() => new SseParser().push(frame)).toThrow('Duplicate citation');
  });
});
