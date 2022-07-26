package legend.core.memory.segments;

import legend.core.IoHelper;
import legend.core.memory.IllegalAddressException;
import legend.core.memory.Segment;
import legend.core.memory.Value;

import java.nio.ByteBuffer;

import static legend.core.Hardware.MEMORY;

public class ExpansionRegion2Segment extends Segment {
  public static final Value EXPANSION_REGION_2 = MEMORY.ref(4, 0x1f80_2000L);

  public static final Value BOOT_STATUS = MEMORY.ref(1, 0x1f80_2041L);

  private byte bootStatus;

  public ExpansionRegion2Segment(final long address) {
    super(address, 0x43);
  }

  @Override
  public byte get(final int offset) {
    if(offset == 0x41) {
      return this.bootStatus;
    }

    throw new IllegalAddressException("Expansion region 2 not supported");
  }

  @Override
  public long get(final int offset, final int size) {
    if(size == 1) {
      return this.get(offset);
    }

    throw new IllegalAddressException("Expansion region 2 not supported");
  }

  @Override
  public void set(final int offset, final byte value) {
    if(offset == 0x41) {
      this.bootStatus = value;
      return;
    }

    throw new IllegalAddressException("Expansion region 2 not supported");
  }

  @Override
  public void set(final int offset, final int size, final long value) {
    if(size == 1) {
      this.set(offset, (byte)value);
      return;
    }

    throw new IllegalAddressException("Expansion region 2 not supported");
  }
}
