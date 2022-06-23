package legend.core.memory.segments;

import legend.core.IoHelper;
import legend.core.memory.MisalignedAccessException;
import legend.core.memory.Segment;
import legend.core.memory.Value;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static legend.core.Hardware.MEMORY;

public class MemoryControl2Segment extends Segment {
  private static final Logger LOGGER = LogManager.getFormatterLogger(MemoryControl2Segment.class);

  public static final Value RAM_SIZE = MEMORY.ref(4, 0x1f801060L);

  private long ramSize;

  public MemoryControl2Segment(final long address) {
    super(address, 4);
  }

  @Override
  public byte get(final int offset) {
    throw new MisalignedAccessException("Memory control 2 ports may not be accessed with 8-bit reads or writes");
  }

  @Override
  public long get(final int offset, final int size) {
    if(size == 1) {
      return this.get(offset);
    }

    if(size == 2) {
      return this.ramSize & 0xffffL;
    }

    return this.ramSize;
  }

  @Override
  public void set(final int offset, final byte value) {
    throw new MisalignedAccessException("Memory control 2 ports may not be accessed with 8-bit reads or writes");
  }

  @Override
  public void set(final int offset, final int size, final long value) {
    if(size == 1) {
      this.set(offset, (byte)value);
      return;
    }

    if(size == 2) {
      LOGGER.info("Setting RAM size to %04x", value);
      this.ramSize = value & 0xffffL;
      return;
    }

    LOGGER.info("Setting RAM size to %08x", value);
    this.ramSize = value & 0xffff_ffffL;
  }

  @Override
  public void dump(final OutputStream stream) throws IOException {
    IoHelper.write(stream, this.ramSize);
  }

  @Override
  public void load(final InputStream stream) throws IOException {
    this.ramSize = IoHelper.readLong(stream);
  }
}
