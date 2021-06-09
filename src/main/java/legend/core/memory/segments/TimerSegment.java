package legend.core.memory.segments;

import legend.core.memory.IllegalAddressException;
import legend.core.memory.MisalignedAccessException;
import legend.core.memory.Segment;
import legend.core.memory.Value;

import static legend.core.Hardware.MEMORY;

public class TimerSegment extends Segment {
  public static final Value TMR_DOTCLOCK_VAL = MEMORY.ref(4, 0x1f801100L);
  public static final Value TMR_DOTCLOCK_MODE = MEMORY.ref(4, 0x1f801104L);
  public static final Value TMR_DOTCLOCK_MAX = MEMORY.ref(4, 0x1f801108L);
  public static final Value TMR_HRETRACE_VAL = MEMORY.ref(4, 0x1f801110L);
  public static final Value TMR_HRETRACE_MODE = MEMORY.ref(4, 0x1f801114L);
  public static final Value TMR_HRETRACE_MAX = MEMORY.ref(4, 0x1f801118L);
  public static final Value TMR_SYSCLOCK_VAL = MEMORY.ref(4, 0x1f801120L);
  public static final Value TMR_SYSCLOCK_MODE = MEMORY.ref(4, 0x1f801124L);
  public static final Value TMR_SYSCLOCK_MAX = MEMORY.ref(4, 0x1f801128L);

  private long dotclockVal;
  private long dotclockMode;
  private long dotclockMax;
  private long hretraceVal;
  private long hretraceMode;
  private long hretraceMax;
  private long sysclockVal;
  private long sysclockMode;
  private long sysclockMax;

  public TimerSegment(final long address) {
    super(address, 0x30);
  }

  @Override
  public byte get(final int offset) {
    throw new MisalignedAccessException("Timer ports may only be accessed with 32-bit reads and writes");
  }

  @Override
  public long get(final int offset, final int size) {
    if(size != 4) {
      throw new MisalignedAccessException("Timer ports may only be accessed with 32-bit reads and writes");
    }

    assert false : "Timers not yet supported";

    return switch(offset & 0x3c) {
      case 0x00 -> this.dotclockVal;
      case 0x04 -> this.dotclockMode;
      case 0x08 -> this.dotclockMax;
      case 0x10 -> this.hretraceVal;
      case 0x14 -> this.hretraceMode;
      case 0x18 -> this.hretraceMax;
      case 0x20 -> this.sysclockVal;
      case 0x24 -> this.sysclockMode;
      case 0x28 -> this.sysclockMax;
      default -> throw new IllegalAddressException("There is no timer port at " + Long.toHexString(this.getAddress() + offset));
    };
  }

  @Override
  public void set(final int offset, final byte value) {
    throw new MisalignedAccessException("Timer ports may only be accessed with 32-bit reads and writes");
  }

  @Override
  public void set(final int offset, final int size, final long value) {
    if(size != 4) {
      throw new MisalignedAccessException("Timer ports may only be accessed with 32-bit reads and writes");
    }

    assert value == 0 : "Timers not yet supported";
  }
}
