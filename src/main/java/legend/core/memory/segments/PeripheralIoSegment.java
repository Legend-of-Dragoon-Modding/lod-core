package legend.core.memory.segments;

import legend.core.memory.MisalignedAccessException;
import legend.core.memory.Segment;
import legend.core.memory.Value;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static legend.core.Hardware.MEMORY;

public class PeripheralIoSegment extends Segment {
  private static final Logger LOGGER = LogManager.getFormatterLogger(PeripheralIoSegment.class);

  public static final Value JOY_DATA_32 = MEMORY.ref(4, 0x1f801040L);
  public static final Value JOY_DATA_8 = MEMORY.ref(1, 0x1f801040L);
  public static final Value JOY_STAT = MEMORY.ref(4, 0x1f801044L);
  public static final Value JOY_MODE = MEMORY.ref(2, 0x1f801048L);
  public static final Value JOY_CTRL = MEMORY.ref(2, 0x1f80104aL);
  public static final Value JOY_BAUD = MEMORY.ref(2, 0x1f80104eL);
  public static final Value SIO_DATA_32 = MEMORY.ref(4, 0x1f801050L);
  public static final Value SIO_DATA_8 = MEMORY.ref(1, 0x1f801050L);
  public static final Value SIO_STAT = MEMORY.ref(4, 0x1f801054L);
  public static final Value SIO_MODE = MEMORY.ref(2, 0x1f801058L);
  public static final Value SIO_CTRL = MEMORY.ref(2, 0x1f80105aL);
  public static final Value SIO_MISC = MEMORY.ref(2, 0x1f80105cL);
  public static final Value SIO_BAUD = MEMORY.ref(2, 0x1f80105eL);

  private long joyData;
  private long joyStat;
  private long joyMode;
  private long joyCtrl;
  private long joyBaud;
  private long sioData;
  private long sioStat;
  private long sioMode;
  private long sioCtrl;
  private long sioMisc;
  private long sioBaud;

  public PeripheralIoSegment(final long address) {
    super(address, 0x20);
  }

  @Override
  public byte get(final int offset) {
    return switch(offset & 0x1f) {
      case 0x00 -> (byte)this.joyData;
      case 0x10 -> (byte)this.sioData;
      default -> throw new MisalignedAccessException("Peripheral IO port " + Long.toHexString(offset) + " may not be accessed with 8-bit reads or writes");
    };
  }

  @Override
  public long get(final int offset, final int size) {
    if(size == 1) {
      return this.get(offset) & 0xffL;
    }

    if(size == 2) {
      return switch(offset & 0x1e) {
        case 0x08 -> this.joyMode;
        case 0x0a -> this.joyCtrl;
        case 0x0e -> this.joyBaud;
        case 0x18 -> this.sioMode;
        case 0x1a -> this.sioCtrl;
        case 0x1c -> this.sioMisc;
        case 0x1e -> this.sioBaud;
        default -> throw new MisalignedAccessException("Peripheral IO port " + Long.toHexString(offset) + " may not be accessed with 16-bit reads or writes");
      };
    }

    return switch(offset & 0x1c) {
      case 0x00 -> this.joyData;
      case 0x04 -> this.joyStat;
      case 0x10 -> this.sioData;
      case 0x14 -> this.sioStat;
      default -> throw new MisalignedAccessException("Peripheral IO port " + Long.toHexString(offset) + " may not be accessed with 32-bit reads or writes");
    };
  }

  @Override
  public void set(final int offset, final byte value) {
    switch(offset & 0x1f) {
      case 0x00 -> {
        LOGGER.info("Setting joypad/memcard data to %02x", value);
        this.joyData = value & 0xffL;
      }

      case 0x10 -> {
        LOGGER.info("Setting serial port data to %02x", value);
        this.sioData = value & 0xffL;
      }

      default -> throw new MisalignedAccessException("Peripheral IO port " + Long.toHexString(offset) + " may not be accessed with 8-bit reads or writes");
    }
  }

  @Override
  public void set(final int offset, final int size, final long value) {
    if(size == 1) {
      this.set(offset, (byte)value);
    }

    if(size == 2) {
      switch(offset & 0x1e) {
        case 0x08 -> {
          LOGGER.info("Setting joypad/memcard mode to %04x", value);
          this.joyMode = value & 0xffffL;
        }
        case 0x0a -> {
          LOGGER.info("Setting joypad/memcard control to %04x", value);
          this.joyCtrl = value & 0xffffL;
        }
        case 0x0e -> {
          LOGGER.info("Setting joypad/memcard baud rate to %04x", value);
          this.joyBaud = value & 0xffffL;
        }
        case 0x18 -> {
          LOGGER.info("Setting serial port mode to %04x", value);
          this.sioMode = value & 0xffffL;
        }
        case 0x1a -> {
          LOGGER.info("Setting serial port control to %04x", value);
          this.sioCtrl = value & 0xffffL;
        }
        case 0x1c -> {
          LOGGER.info("Setting serial port misc to %04x", value);
          this.sioMisc = value & 0xffffL;
        }
        case 0x1e -> {
          LOGGER.info("Setting serial port baud rate to %04x", value);
          this.sioBaud = value & 0xffffL;
        }

        default -> throw new MisalignedAccessException("Peripheral IO port " + Long.toHexString(offset) + " may not be accessed with 16-bit reads or writes");
      }

      return;
    }

    switch(offset & 0x1c) {
      case 0x00 -> {
        LOGGER.info("Setting joypad/memcard data to %08x", value);
        this.joyData = value & 0xffff_ffffL;
      }

      case 0x04 -> {
        LOGGER.info("Setting joypad/memcard status to %08x", value);
        this.joyStat = value & 0xffff_ffffL;
      }

      case 0x10 -> {
        LOGGER.info("Setting serial port data to %08x", value);
        this.sioData = value & 0xffff_ffffL;
      }

      case 0x14 -> {
        LOGGER.info("Setting serial port status to %08x", value);
        this.sioStat = value & 0xffff_ffffL;
      }

      default -> throw new MisalignedAccessException("Peripheral IO port " + Long.toHexString(offset) + " may not be accessed with 32-bit reads or writes");
    }
  }
}
