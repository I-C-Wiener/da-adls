import { FileSizePipe } from './file-size.pipe';

describe('FileSizePipe', () => {
  const pipe = new FileSizePipe();

  it('formats bytes', () => expect(pipe.transform(500)).toBe('500 B'));
  it('formats kilobytes', () => expect(pipe.transform(2048)).toBe('2.0 KB'));
  it('formats megabytes', () => expect(pipe.transform(3 * 1024 * 1024)).toBe('3.0 MB'));
});
