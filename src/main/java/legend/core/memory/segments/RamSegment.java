package legend.core.memory.segments;

import legend.core.MathHelper;
import legend.core.memory.Segment;

import java.nio.ByteBuffer;

public class RamSegment extends Segment {
  private final byte[] data;

  public RamSegment(final long address, final int length) {
    super(address, length);
    this.data = new byte[length];
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
    this.data[offset] = value;
  }

  @Override
  public void set(final int offset, final int size, final long value) {
    // Use more efficient method to set a single byte
    if(size == 1) {
      this.set(offset, (byte)value);
      return;
    }

    MathHelper.set(this.data, offset, size, value);
  }

  @Override
  public byte[] getBytes(final int offset, final int size) {
    final byte[] data = new byte[size];
    System.arraycopy(this.data, offset, data, 0, size);
    return data;
  }

  @Override
  public void getBytes(final int offset, final byte[] dest, final int dataOffset, final int dataSize) {
    System.arraycopy(this.data, offset, dest, dataOffset, dataSize);
  }

  @Override
  public void setBytes(final int offset, final byte[] data) {
    System.arraycopy(data, 0, this.data, offset, data.length);
  }

  @Override
  public void setBytes(final int offset, final byte[] data, final int dataOffset, final int dataLength) {
    System.arraycopy(data, dataOffset, this.data, offset, dataLength);
  }

  @Override
  public void dump(final ByteBuffer stream) {
    stream.put(this.data);
  }

  @Override
  public void load(final ByteBuffer stream) {
    stream.get(this.data);
  }
}
