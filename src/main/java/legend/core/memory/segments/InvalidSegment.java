package legend.core.memory.segments;

import legend.core.memory.IllegalAddressException;
import legend.core.memory.Segment;

import java.nio.ByteBuffer;

public class InvalidSegment extends Segment {
  public InvalidSegment(final long address, final int length) {
    super(address, length);
  }

  @Override
  public byte get(final int offset) {
    throw new IllegalAddressException("Memory at " + Long.toHexString(this.getAddress() + offset) + " may not be used");
  }

  @Override
  public long get(final int offset, final int size) {
    throw new IllegalAddressException("Memory at " + Long.toHexString(this.getAddress() + offset) + " may not be used");
  }

  @Override
  public void set(final int offset, final byte value) {
    throw new IllegalAddressException("Memory at " + Long.toHexString(this.getAddress() + offset) + " may not be used");
  }

  @Override
  public void set(final int offset, final int size, final long value) {
    throw new IllegalAddressException("Memory at " + Long.toHexString(this.getAddress() + offset) + " may not be used");
  }

  @Override
  public void dump(final ByteBuffer stream) {

  }

  @Override
  public void load(final ByteBuffer stream) {

  }
}
