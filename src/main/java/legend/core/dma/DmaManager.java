package legend.core.dma;

import legend.core.memory.IllegalAddressException;
import legend.core.memory.Memory;
import legend.core.memory.MisalignedAccessException;
import legend.core.memory.Segment;
import legend.core.memory.Value;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumMap;

import static legend.core.Hardware.MEMORY;

public class DmaManager {
  private static final Logger LOGGER = LogManager.getFormatterLogger(DmaManager.class);

  public static final Value DMA_DPCR = MEMORY.ref(4, 0x1f8010f0L);
  public static final Value DMA_DICR = MEMORY.ref(4, 0x1f8010f4L);

  private final EnumMap<DmaChannelType, DmaChannel> channels = new EnumMap<>(DmaChannelType.class);
  private final EnumMap<DmaChannelType, Boolean> dicrIrqEnable = new EnumMap<>(DmaChannelType.class);
  private final EnumMap<DmaChannelType, Boolean> dicrIrqFlag = new EnumMap<>(DmaChannelType.class);

  public final DmaChannel mdecIn;
  public final DmaChannel mdecOut;
  public final DmaChannel gpu;
  public final DmaChannel cdrom;
  public final DmaChannel spu;
  public final DmaChannel pio;
  public final DmaChannel otc;

  private long dpcr;

  private boolean dicrForceIrq;
  private boolean dicrMasterEnable;
  private boolean dicrMasterFlag;

  private boolean edgeTrigger;

  public DmaManager(final Memory memory) {
    memory.addSegment(new DmaSegment(0x1f80_10f0L));
    this.mdecIn = this.addChannel(memory, DmaChannelType.MDEC_IN, 1);
    this.mdecOut = this.addChannel(memory, DmaChannelType.MDEC_OUT, 2);
    this.gpu = this.addChannel(memory, DmaChannelType.GPU, 3);
    this.cdrom = this.addChannel(memory, DmaChannelType.CDROM, 4);
    this.spu = this.addChannel(memory, DmaChannelType.SPU, 5);
    this.pio = this.addChannel(memory, DmaChannelType.PIO, 6);
    this.otc = this.addChannel(memory, DmaChannelType.OTC, 7);

  }

  public boolean tick() {
    if(this.edgeTrigger) {
      this.edgeTrigger = false;
      return true;
    }

    return false;
  }

  private DmaChannel addChannel(final Memory memory, final DmaChannelType channel, final int priority) {
    final DmaChannel dma = new DmaChannel(memory, channel);
    dma.setPriority(priority);
    this.channels.put(channel, dma);
    this.disableInterrupt(channel);
    this.acknowledgeInterrupt(channel);
    return dma;
  }

  public DmaChannel channel(final DmaChannelType channel) {
    return this.channels.get(channel);
  }

  private void updateInterruptFlag() {
    final boolean oldMasterFlag = this.dicrMasterFlag;

    this.dicrMasterFlag = this.dicrForceIrq;

    for(final DmaChannelType channel : DmaChannelType.values()) {
      if(this.dicrMasterEnable && this.isInterruptEnabled(channel) && this.isInterruptPending(channel)) {
        this.dicrMasterFlag = true;
        break;
      }
    }

    if(this.dicrMasterFlag && !oldMasterFlag) {
      this.edgeTrigger = true;
    }
  }

  void transferFinished(final DmaChannelType channel) {
    if(this.dicrMasterEnable && this.isInterruptEnabled(channel)) {
      this.dicrIrqFlag.put(channel, true);
    }

    this.updateInterruptFlag();
  }

  public boolean isInterruptForced() {
    return this.dicrForceIrq;
  }

  public void masterEnableInterrupts() {
    LOGGER.info("[DMA Manager] Master enable interrupts");
    this.dicrMasterEnable = true;
  }

  public void masterDisableInterrupts() {
    LOGGER.info("[DMA Manager] Master disable interrupts");
    this.dicrMasterEnable = false;
  }

  public void enableInterrupt(final DmaChannelType channel) {
    LOGGER.debug("[DMA Manager] Enabling interrupt %s", channel);
    this.dicrIrqEnable.put(channel, true);
  }

  public void disableInterrupt(final DmaChannelType channel) {
    LOGGER.debug("[DMA Manager] Disabling interrupt %s", channel);
    this.dicrIrqEnable.put(channel, false);
  }

  public boolean isInterruptEnabled(final DmaChannelType channel) {
    return this.dicrIrqEnable.get(channel);
  }

  public boolean isInterruptPending(final DmaChannelType channel) {
    return this.dicrIrqFlag.get(channel);
  }

  public void acknowledgeInterrupt(final DmaChannelType channel) {
    this.dicrIrqFlag.put(channel, false);
    this.updateInterruptFlag();
  }

  public class DmaSegment extends Segment {
    public DmaSegment(final long address) {
      super(address, 0x8);
    }

    @Override
    public byte get(final int offset) {
      throw new MisalignedAccessException("DMA manager ports may only be accessed with 32-bit reads and writes");
    }

    @Override
    public long get(final int offset, final int size) {
      if(size != 4) {
        throw new MisalignedAccessException("DMA manager ports may only be accessed with 32-bit reads and writes");
      }

      return switch(offset & 0x4) {
        case 0x0 -> DmaManager.this.dpcr;
        case 0x4 -> {
          long value = 0;

          if(DmaManager.this.dicrForceIrq) {
            value |= 1L << 15;
          }

          if(DmaManager.this.dicrMasterEnable) {
            value |= 1L << 23;
          }

          if(DmaManager.this.dicrMasterFlag) {
            value |= 1L << 31;
          }

          for(final DmaChannelType channel : DmaChannelType.values()) {
            if(DmaManager.this.isInterruptEnabled(channel)) {
              value |= 1L << 16 + channel.ordinal();
            }

            if(DmaManager.this.isInterruptPending(channel)) {
              value |= 1L << 24 + channel.ordinal();
            }
          }

          yield value;
        }
        default -> throw new IllegalAddressException("There is no DMA control port at address " + Long.toHexString(this.getAddress() + offset));
      };
    }

    @Override
    public void set(final int offset, final byte value) {
      throw new MisalignedAccessException("DMA manager ports may only be accessed with 32-bit reads and writes");
    }

    @Override
    public void set(final int offset, final int size, final long value) {
      if(size != 4) {
        throw new MisalignedAccessException("DMA manager ports may only be accessed with 32-bit reads and writes");
      }

      switch(offset & 0x4) {
        case 0x0 -> {
          LOGGER.debug("Set DPCR to %08x", value);

          for(final DmaChannel channel : DmaManager.this.channels.values()) {
            final int channelOffset = channel.channel.ordinal() * 4;

            if((value & 0b1000L << channelOffset) == 0) {
              channel.disable();
            } else {
              channel.enable();
            }

            channel.setPriority((int)(value >>> channelOffset) & 0b111);
            DmaManager.this.dpcr = value;
          }
        }

        case 0x4 -> {
          if((value >>> 31 & 0b1) != 0) {
            throw new RuntimeException("Can't set IRQ master flag {value: " + Long.toString(value, 16) + '}');
          }

          if((value & 0b111_1111_0000_0000_0000_0000_0000_0000) != 0) {
            for(final DmaChannelType channel : DmaChannelType.values()) {
              if((value & 1L << channel.ordinal() + 24) != 0) {
                DmaManager.this.acknowledgeInterrupt(channel);
              }
            }
          }

          DmaManager.this.dicrForceIrq = (value & 1L << 15) != 0;
          DmaManager.this.dicrMasterEnable = (value & 1L << 23) != 0;

          for(final DmaChannelType channel : DmaChannelType.values()) {
            if((value & 1L << 16 + channel.ordinal()) == 0) {
              DmaManager.this.disableInterrupt(channel);
            } else {
              DmaManager.this.enableInterrupt(channel);
            }
          }

          DmaManager.this.updateInterruptFlag();
        }
      }
    }
  }
}
