import { RelativeTimePipe } from './relative-time.pipe';

describe('RelativeTimePipe', () => {
  const pipe = new RelativeTimePipe();

  it('returns "just now" for < 1 minute ago', () => {
    const recent = new Date(Date.now() - 30_000).toISOString();
    expect(pipe.transform(recent)).toBe('just now');
  });

  it('returns minutes for < 1 hour ago', () => {
    const ago = new Date(Date.now() - 5 * 60_000).toISOString();
    expect(pipe.transform(ago)).toBe('5m ago');
  });

  it('returns hours for < 24 hours ago', () => {
    const ago = new Date(Date.now() - 3 * 3600_000).toISOString();
    expect(pipe.transform(ago)).toBe('3h ago');
  });
});
