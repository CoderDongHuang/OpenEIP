import { afterEach, describe, expect, it, vi } from 'vitest';

import { AgentSseParser, streamAgent } from './agent-sse';

describe('AgentSseParser', () => {
  afterEach(() => vi.unstubAllGlobals());
  it('parses fragmented lifecycle events', () => {
    const parser = new AgentSseParser();
    const stream =
      'event: execution.started\ndata: {"requestId":"r","executionId":"e","sequence":0,"agentId":"openeip.constrained-v1"}\n\n' +
      'event: execution.completed\r\ndata: {"requestId":"r","executionId":"e","sequence":1,"finishReason":"stop","steps":0}\r\n\r\n';
    const events = [...parser.push(stream.slice(0, 30)), ...parser.push(stream.slice(30))];
    parser.finish();
    expect(events.map((event) => event.event)).toEqual(['execution.started', 'execution.completed']);
  });

  it.each([
    'event: unknown\ndata: {"executionId":"e","sequence":0}\n\n',
    'event: execution.started\ndata: {"executionId":"e","sequence":"0"}\n\n',
    'event: execution.started\nevent: execution.started\ndata: {}\n\n',
    'event: execution.started\ndata: {"requestId":"r","executionId":"e","sequence":0,"agentId":"agent","event":"execution.completed"}\n\n',
    'event: execution.completed\ndata: {"requestId":"r","executionId":"e","sequence":1,"finishReason":"other","steps":0}\n\n',
  ])('rejects malformed events', (frame) => {
    expect(() => new AgentSseParser().push(frame)).toThrow();
  });

  it('streams one ordered Agent execution', async () => {
    const stream =
      'event: execution.started\ndata: {"requestId":"r","executionId":"e","sequence":0,"agentId":"openeip.constrained-v1"}\n\n' +
      'event: answer.delta\ndata: {"requestId":"r","executionId":"e","sequence":1,"text":"answer"}\n\n' +
      'event: execution.completed\ndata: {"requestId":"r","executionId":"e","sequence":2,"finishReason":"stop","steps":0}\n\n';
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(stream, { headers: { 'Content-Type': 'text/event-stream' } })),
    );
    const events: string[] = [];
    await streamAgent(
      'token',
      'agent',
      'input',
      undefined,
      ['document.inspect'],
      2,
      new AbortController().signal,
      (event) => events.push(event.event),
    );
    expect(events).toEqual(['execution.started', 'answer.delta', 'execution.completed']);
  });

  it('rejects upstream errors and out-of-order events', async () => {
    const invalid =
      'event: execution.started\ndata: {"requestId":"r","executionId":"e","sequence":1,"agentId":"agent"}\n\n';
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: 'Failed', code: 'AGENT-S-001' }), { status: 503 }))
      .mockResolvedValueOnce(new Response(invalid, { headers: { 'Content-Type': 'text/event-stream' } }));
    vi.stubGlobal('fetch', fetchMock);
    await expect(
      streamAgent(
        'token',
        'agent',
        'input',
        undefined,
        ['document.inspect'],
        1,
        new AbortController().signal,
        () => undefined,
      ),
    ).rejects.toMatchObject({ status: 503 });
    await expect(
      streamAgent(
        'token',
        'agent',
        'input',
        undefined,
        ['document.inspect'],
        1,
        new AbortController().signal,
        () => undefined,
      ),
    ).rejects.toThrow('order');
  });
});
