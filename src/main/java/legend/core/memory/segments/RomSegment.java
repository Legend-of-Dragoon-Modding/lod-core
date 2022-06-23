package legend.core.memory.segments;

import legend.core.MathHelper;
import legend.core.memory.Segment;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class RomSegment extends Segment {
  public static RomSegment fromFile(final long address, final int length, final Path path) {
    try {
      final byte[] data = Files.readAllBytes(path);
      return new RomSegment(address, length, data);
    } catch(final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private final byte[] data;

  public RomSegment(final long address, final int length, final byte[] data) {
    super(address, length);
    this.data = new byte[length];
    System.arraycopy(data, 0, this.data, 0, Math.min(length, data.length));
  }

  @Override
  public byte get(final int offset) {
    return this.data[offset];
  }

  @Override
  public long get(final int offset, final int size) {
    // Use more efficient method to get a single byte
    if(size == 1) {
      return this.get(offset) & 0xffL;
    }

    return MathHelper.get(this.data, offset, size);
  }

  @Override
  public void set(final int offset, final byte value) {
    throw new ReadOnlyMemoryException("Memory at " + Long.toHexString(offset) + " is read only");
  }

  @Override
  public void set(final int offset, final int size, final long value) {
    throw new ReadOnlyMemoryException("Memory at " + Long.toHexString(offset) + " is read only");
  }

  @Override
  public void dump(final OutputStream stream) throws IOException {
    // Don't need to dump ROM
  }

  @Override
  public void load(final InputStream stream) throws IOException {
    // Don't need to load ROM
  }

  public static class ReadOnlyMemoryException extends RuntimeException {
    public ReadOnlyMemoryException() {
      super();
    }

    public ReadOnlyMemoryException(final String message) {
      super(message);
    }

    public ReadOnlyMemoryException(final String message, final Throwable cause) {
      super(message, cause);
    }

    public ReadOnlyMemoryException(final Throwable cause) {
      super(cause);
    }

    protected ReadOnlyMemoryException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace) {
      super(message, cause, enableSuppression, writableStackTrace);
    }
  }
}
