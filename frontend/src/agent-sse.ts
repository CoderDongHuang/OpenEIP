import { ApiError } from './api';

export type AgentEvent =
  | { event: 'execution.started'; requestId: string; sequence: number; executionId: string; agentId: string }
  | {
      event: 'tool.started';
      requestId: string;
      sequence: number;
      executionId: string;
      toolCallId: string;
      toolName: string;
      step: number;
    }
  | {
      event: 'tool.completed';
      requestId: string;
      sequence: number;
      executionId: string;
      toolCallId: string;
      toolName: string;
      step: number;
      durationMs: number;
      resultChars: number;
    }
  | { event: 'answer.delta'; requestId: string; sequence: number; executionId: string; text: string }
  | {
      event: 'execution.completed';
      requestId: string;
      sequence: number;
      executionId: string;
      finishReason: 'stop';
      steps: number;
    }
  | {
      event: 'execution.error';
      requestId: string;
      sequence: number;
      executionId: string;
      code: string;
      message: string;
    };

export class AgentSseParser {
  private buffer = '';

  push(chunk: string): AgentEvent[] {
    this.buffer += chunk;
    const events: AgentEvent[] = [];
    while (true) {
      const match = /\r?\n\r?\n/.exec(this.buffer);
      if (!match || match.index === undefined) break;
      const block = this.buffer.slice(0, match.index);
      this.buffer = this.buffer.slice(match.index + match[0].length);
      if (block) events.push(this.parse(block));
    }
    return events;
  }

  finish(): void {
    if (this.buffer.trim()) throw new Error('Incomplete Agent event');
  }

  private parse(block: string): AgentEvent {
    const lines = block.split(/\r?\n/);
    if (lines.length !== 2 || !lines[0].startsWith('event: ') || !lines[1].startsWith('data: ')) {
      throw new Error('Invalid Agent event');
    }
    const event = lines[0].slice(7) as AgentEvent['event'];
    if (
      ![
        'execution.started',
        'tool.started',
        'tool.completed',
        'answer.delta',
        'execution.completed',
        'execution.error',
      ].includes(event)
    ) {
      throw new Error('Unsupported Agent event');
    }
    const data = JSON.parse(lines[1].slice(6)) as unknown;
    if (!isRecord(data)) throw new Error('Invalid Agent data');
    const requestId = requiredString(data.requestId);
    const executionId = requiredString(data.executionId);
    const sequence = requiredInteger(data.sequence, 0);
    const base = { requestId, executionId, sequence };
    if (event === 'execution.started') {
      exactKeys(data, ['requestId', 'executionId', 'sequence', 'agentId']);
      return { event, ...base, agentId: requiredString(data.agentId) };
    }
    if (event === 'tool.started') {
      exactKeys(data, ['requestId', 'executionId', 'sequence', 'toolCallId', 'toolName', 'step']);
      return {
        event,
        ...base,
        toolCallId: requiredString(data.toolCallId),
        toolName: requiredString(data.toolName),
        step: requiredInteger(data.step, 1),
      };
    }
    if (event === 'tool.completed') {
      exactKeys(data, [
        'requestId',
        'executionId',
        'sequence',
        'toolCallId',
        'toolName',
        'step',
        'durationMs',
        'resultChars',
      ]);
      return {
        event,
        ...base,
        toolCallId: requiredString(data.toolCallId),
        toolName: requiredString(data.toolName),
        step: requiredInteger(data.step, 1),
        durationMs: requiredNumber(data.durationMs, 0),
        resultChars: requiredInteger(data.resultChars, 1),
      };
    }
    if (event === 'answer.delta') {
      exactKeys(data, ['requestId', 'executionId', 'sequence', 'text']);
      const text = requiredString(data.text);
      if (text.length > 1024) throw new Error('Invalid Agent data');
      return { event, ...base, text };
    }
    if (event === 'execution.completed') {
      exactKeys(data, ['requestId', 'executionId', 'sequence', 'finishReason', 'steps']);
      if (data.finishReason !== 'stop') throw new Error('Invalid Agent data');
      return { event, ...base, finishReason: 'stop', steps: requiredInteger(data.steps, 0) };
    }
    exactKeys(data, ['requestId', 'executionId', 'sequence', 'code', 'message']);
    return { event, ...base, code: requiredString(data.code), message: requiredString(data.message) };
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function exactKeys(data: Record<string, unknown>, expected: string[]): void {
  const actual = Object.keys(data).sort();
  if (actual.length !== expected.length || actual.some((key, index) => key !== [...expected].sort()[index])) {
    throw new Error('Invalid Agent data');
  }
}

function requiredString(value: unknown): string {
  if (typeof value !== 'string' || !value) throw new Error('Invalid Agent data');
  return value;
}

function requiredInteger(value: unknown, minimum: number): number {
  if (!Number.isInteger(value) || (value as number) < minimum) throw new Error('Invalid Agent data');
  return value as number;
}

function requiredNumber(value: unknown, minimum: number): number {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < minimum) throw new Error('Invalid Agent data');
  return value;
}

export async function streamAgent(
  token: string,
  agentId: string,
  input: string,
  knowledgeBaseId: string | undefined,
  allowedTools: string[],
  maxSteps: number,
  signal: AbortSignal,
  onEvent: (event: AgentEvent) => void,
): Promise<void> {
  const response = await fetch(`/api/v1/agents/${encodeURIComponent(agentId)}/executions:stream`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ input, knowledgeBaseId, allowedTools, maxSteps }),
    signal,
  });
  if (!response.ok || !response.body) {
    const body = (await response.json().catch(() => null)) as { message?: string; code?: string } | null;
    throw new ApiError(body?.message || `Agent failed (${response.status})`, response.status, body?.code);
  }
  if (!response.headers.get('content-type')?.startsWith('text/event-stream')) throw new Error('Invalid Agent stream');
  const parser = new AgentSseParser();
  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8', { fatal: true });
  let sequence = 0;
  let terminal = false;
  try {
    while (true) {
      const { value, done } = await reader.read();
      for (const event of parser.push(decoder.decode(value || new Uint8Array(), { stream: !done }))) {
        if (terminal || event.sequence !== sequence++) throw new Error('Invalid Agent event order');
        terminal = event.event === 'execution.completed' || event.event === 'execution.error';
        onEvent(event);
      }
      if (done) break;
    }
    parser.finish();
    if (!terminal) throw new Error('Agent stream ended unexpectedly');
  } finally {
    reader.releaseLock();
  }
}
