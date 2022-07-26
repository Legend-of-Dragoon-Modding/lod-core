package legend.core.memory.segments;

import legend.core.IoHelper;
import legend.core.memory.Segment;

import java.nio.ByteBuffer;

public class IntSegment extends Segment {
  private int value;

  public IntSegment(final long address) {
    super(address, 4);
  }

  @Override
  public byte get(final int offset) {
    return (byte)(this.value >> offset * 8);
  }

  @Override
  public long get(final int offset, final int size) {
    if(size == 1) {
      return this.get(offset) & 0xffL;
    }

    if(size == 2) {
      return this.value >> offset * 8 & 0xffffL;
    }

    return this.value & 0xffff_ffffL;
  }

  @Override
  public void set(final int offset, final byte value) {
    this.value = this.value & ~0xff | value & 0xff;
  }

  @Override
  public void set(final int offset, final int size, final long value) {
    if(size == 1) {
      this.set(offset, (byte)value);
    } else if(size == 2) {
      this.value = this.value & ~0xffff | (int)value & 0xffff;
    } else {
      this.value = (int)value;
    }
  }

  @Override
  public void dump(final ByteBuffer stream) {
    super.dump(stream);
    IoHelper.write(stream, this.value);
  }

  @Override
  public void load(final ByteBuffer stream) throws ClassNotFoundException {
    super.load(stream);
    this.value = IoHelper.readInt(stream);
  }
}
