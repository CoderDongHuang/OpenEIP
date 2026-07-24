import { ApiError } from './api';

export interface Citation {
  documentId: string;
  chunkId: string;
  sourceSha256: string;
  score: number;
  excerpt: string;
  pages: number[];
  startChar: number;
  endChar: number;
}

export type ChatEvent =
  | { event: 'token'; requestId: string; sessionId: string; sequence: number; token: string }
  | { event: 'done'; requestId: string; sessionId: string; finishReason: 'stop'; citations: Citation[] }
  | { event: 'error'; requestId: string; sessionId: string; code: string; message: string };

export class SseParser {
  private buffer = '';

  push(chunk: string): ChatEvent[] {
    this.buffer += chunk;
    const events: ChatEvent[] = [];
    while (true) {
      const separator = this.findSeparator();
      if (!separator) break;
      const block = this.buffer.slice(0, separator.index);
      this.buffer = this.buffer.slice(separator.index + separator.length);
      if (block) events.push(this.parseBlock(block));
    }
    return events;
  }

  finish(): void {
    if (this.buffer.trim()) throw new Error('Incomplete SSE event');
    this.buffer = '';
  }

  private findSeparator(): { index: number; length: number } | null {
    const lf = this.buffer.indexOf('\n\n');
    const crlf = this.buffer.indexOf('\r\n\r\n');
    if (lf < 0 && crlf < 0) return null;
    if (crlf >= 0 && (lf < 0 || crlf < lf)) return { index: crlf, length: 4 };
    return { index: lf, length: 2 };
  }

  private parseBlock(block: string): ChatEvent {
    const lines = block.split(/\r?\n/);
    const eventLines = lines.filter((line) => line.startsWith('event: '));
    const dataLines = lines.filter((line) => line.startsWith('data: '));
    if (eventLines.length !== 1 || dataLines.length !== 1 || lines.length !== 2) {
      throw new Error('Invalid SSE frame');
    }
    const event = eventLines[0].slice(7);
    if (event !== 'token' && event !== 'done' && event !== 'error') {
      throw new Error('Unsupported SSE event');
    }
    const data = JSON.parse(dataLines[0].slice(6)) as Record<string, unknown>;
    const requestId = requiredString(data.requestId);
    const sessionId = requiredString(data.sessionId);
    if (event === 'token') {
      if (!Number.isInteger(data.sequence) || (data.sequence as number) < 0) throw new Error('Invalid token sequence');
      const token = requiredString(data.token);
      if (token.length > 1024) throw new Error('Token exceeds limit');
      return { event, requestId, sessionId, sequence: data.sequence as number, token };
    }
    if (event === 'done') {
      if (data.finishReason !== 'stop' || !Array.isArray(data.citations) || data.citations.length > 20) {
        throw new Error('Invalid done event');
      }
      const citations = data.citations.map(parseCitation);
      if (new Set(citations.map((citation) => citation.chunkId)).size !== citations.length) {
        throw new Error('Duplicate citation');
      }
      return { event, requestId, sessionId, finishReason: 'stop', citations };
    }
    return {
      event,
      requestId,
      sessionId,
      code: requiredString(data.code),
      message: requiredString(data.message),
    };
  }
}

function parseCitation(value: unknown): Citation {
  if (!isRecord(value) || Object.keys(value).length !== 8) throw new Error('Invalid citation');
  const documentId = requiredString(value.documentId);
  const chunkId = requiredString(value.chunkId);
  const sourceSha256 = requiredString(value.sourceSha256);
  const score = value.score;
  const excerpt = requiredString(value.excerpt);
  const pages = value.pages;
  const startChar = value.startChar;
  const endChar = value.endChar;
  if (
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(documentId) ||
    !/^chk_[a-f0-9]{32}$/.test(chunkId) ||
    !/^[a-f0-9]{64}$/.test(sourceSha256) ||
    typeof score !== 'number' ||
    !Number.isFinite(score) ||
    score < -1 ||
    score > 1 ||
    excerpt.length > 500 ||
    !Array.isArray(pages) ||
    pages.length > 100 ||
    pages.some((page) => !Number.isInteger(page) || page < 1) ||
    !Number.isInteger(startChar) ||
    !Number.isInteger(endChar) ||
    (startChar as number) < 0 ||
    (endChar as number) <= (startChar as number)
  ) {
    throw new Error('Invalid citation');
  }
  return {
    documentId,
    chunkId,
    sourceSha256,
    score,
    excerpt,
    pages: pages as number[],
    startChar: startChar as number,
    endChar: endChar as number,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function requiredString(value: unknown): string {
  if (typeof value !== 'string' || !value) throw new Error('Invalid SSE data');
  return value;
}

export async function streamMessage(
  token: string,
  sessionId: string,
  message: string,
  signal: AbortSignal,
  onEvent: (event: ChatEvent) => void,
): Promise<void> {
  const response = await fetch(`/api/v1/chat/sessions/${encodeURIComponent(sessionId)}/messages:stream`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, topK: 5 }),
    signal,
  });
  if (!response.ok || !response.body) {
    const error = (await response.json().catch(() => null)) as { message?: string; code?: string } | null;
    throw new ApiError(error?.message || `Stream failed (${response.status})`, response.status, error?.code);
  }
  if (!response.headers.get('content-type')?.startsWith('text/event-stream')) {
    throw new Error('Invalid stream media type');
  }

  const parser = new SseParser();
  const decoder = new TextDecoder('utf-8', { fatal: true });
  const reader = response.body.getReader();
  let expectedSequence = 0;
  let terminal = false;
  try {
    while (true) {
      const { value, done } = await reader.read();
      const text = decoder.decode(value || new Uint8Array(), { stream: !done });
      for (const event of parser.push(text)) {
        if (event.sessionId !== sessionId || terminal) throw new Error('Invalid stream identity or lifecycle');
        if (event.event === 'token') {
          if (event.sequence !== expectedSequence++) throw new Error('Out-of-order token');
        } else {
          terminal = true;
        }
        onEvent(event);
      }
      if (done) break;
    }
    parser.finish();
    if (!terminal) throw new Error('Stream ended without terminal event');
  } finally {
    reader.releaseLock();
  }
}
