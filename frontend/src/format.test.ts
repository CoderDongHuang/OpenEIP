import { describe, expect, it } from 'vitest';

import { errorMessage, formatBytes, formatDate, shortId } from './format';

describe('format helpers', () => {
  it('formats bounded sizes and identifiers', () => {
    expect(formatBytes(100)).toBe('100 B');
    expect(formatBytes(2048)).toBe('2.00 KB');
    expect(formatBytes(20 * 1024 * 1024)).toBe('20.0 MB');
    expect(shortId('1234567890')).toBe('12345678');
  });

  it('formats dates and stable errors', () => {
    expect(formatDate('2026-07-22T00:00:00Z')).toContain('2026');
    expect(errorMessage(new Error('stable'))).toBe('stable');
    expect(errorMessage(null)).toBe('Request failed');
  });
});
