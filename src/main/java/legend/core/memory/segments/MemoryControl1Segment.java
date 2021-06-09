package legend.core.memory.segments;

import legend.core.memory.MisalignedAccessException;
import legend.core.memory.Segment;
import legend.core.memory.Value;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static legend.core.Hardware.MEMORY;

public class MemoryControl1Segment extends Segment {
  private static final Logger LOGGER = LogManager.getFormatterLogger(MemoryControl1Segment.class);

  public static final Value EXP1_BASE_ADDR = MEMORY.ref(4, 0x1f801000L);
  public static final Value EXP2_BASE_ADDR = MEMORY.ref(4, 0x1f801004L);
  public static final Value EXP1_DELAY_SIZE = MEMORY.ref(4, 0x1f801008L);
  public static final Value EXP3_DELAY_SIZE = MEMORY.ref(4, 0x1f80100cL);
  public static final Value BIOS_ROM = MEMORY.ref(4, 0x1f801010L);
  public static final Value SPU_DELAY = MEMORY.ref(4, 0x1f801014L);
  public static final Value CDROM_DELAY = MEMORY.ref(4, 0x1f801018L);
  public static final Value EXP2_DELAY_SIZE = MEMORY.ref(4, 0x1f80101cL);
  public static final Value COMMON_DELAY = MEMORY.ref(4, 0x1f801020L);

  private long expansion1BaseAddress;
  private long expansion2BaseAddress;
  private long expansion1Delay;
  private long expansion3Delay;
  private long biosRomDelay;
  private long spuDelay;
  private long cdromDelay;
  private long expansion2Delay;
  private long comDelay;

  public MemoryControl1Segment(final long address) {
    super(address, 0x24);
  }

  @Override
  public byte get(final int offset) {
    throw new MisalignedAccessException("Memory control 1 ports may only be accessed with 32-bit reads and writes");
  }

  @Override
  public long get(final int offset, final int size) {
    if(size != 4) {
      throw new MisalignedAccessException("Memory control 1 ports may only be accessed with 32-bit reads and writes");
    }

    return switch(offset & 0x3c) {
      case 0x00 -> this.expansion1BaseAddress;
      case 0x04 -> this.expansion2BaseAddress;
      case 0x08 -> this.expansion1Delay;
      case 0x0c -> this.expansion3Delay;
      case 0x10 -> this.biosRomDelay;
      case 0x14 -> this.spuDelay;
      case 0x18 -> this.cdromDelay;
      case 0x1c -> this.expansion2Delay;
      case 0x20 -> this.comDelay;
      default -> throw new RuntimeException("This should be impossible. Offset " + Long.toHexString(offset) + ", size " + size);
    };
  }

  @Override
  public void set(final int offset, final byte value) {
    throw new MisalignedAccessException("Memory control 1 ports may only be accessed with 32-bit reads and writes");
  }

  @Override
  public void set(final int offset, final int size, final long value) {
    if(size != 4) {
      throw new MisalignedAccessException("Memory control 1 ports may only be accessed with 32-bit reads and writes");
    }

    switch(offset & 0x3c) {
      case 0x00 -> {
        LOGGER.info("Setting expansion 1 base address to %08x", value);
        this.expansion1BaseAddress = value;
      }

      case 0x04 -> {
        LOGGER.info("Setting expansion 2 base address to %08x", value);
        this.expansion2BaseAddress = value;
      }

      case 0x08 -> {
        LOGGER.info("Setting expansion 1 delay/size to %08x", value);
        this.expansion1Delay = value;
      }

      case 0x0c -> {
        LOGGER.info("Setting expansion 3 delay/size to %08x", value);
        this.expansion3Delay = value;
      }

      case 0x10 -> {
        LOGGER.info("Setting BIOS ROM delay/size to %08x", value);
        this.biosRomDelay = value;
      }

      case 0x14 -> {
        LOGGER.info("Setting SPU delay/size to %08x", value);
        this.spuDelay = value;
      }

      case 0x18 -> {
        LOGGER.info("Setting CDROM delay/size to %08x", value);
        this.cdromDelay = value;
      }

      case 0x1c -> {
        LOGGER.info("Setting expansion 2 delay/size to %08x", value);
        this.expansion2Delay = value;
      }

      case 0x20 -> {
        LOGGER.info("Setting com delay to %08x", value);
        this.comDelay = value;
      }
    }
  }
}
