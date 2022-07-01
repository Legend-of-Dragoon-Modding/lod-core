package legend.core.input;

import legend.core.DebugHelper;
import legend.core.Hardware;
import legend.core.InterruptType;
import legend.core.IoHelper;
import legend.core.memory.Memory;
import legend.core.memory.MisalignedAccessException;
import legend.core.memory.Segment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import static legend.core.Hardware.INTERRUPTS;

public class Joypad implements Runnable {
  private static final Logger LOGGER = LogManager.getFormatterLogger(Joypad.class);

  private byte JOY_TX_DATA; //1F801040h JOY_TX_DATA(W)
  private byte JOY_RX_DATA; //1F801040h JOY_RX_DATA(R) FIFO
  private boolean fifoFull;

  private boolean edgeTrigger;

  //1F801044 JOY_STAT(R)
  boolean txReadyFlag1 = true;
  boolean txReadyFlag2 = true;
  boolean rxParityError;
  boolean ackInputLevel;
  boolean interruptRequest;
  int baudrateTimer;

  //1F801048 JOY_MODE(R/W)
  int baudrateReloadFactor;
  int characterLength;
  boolean parityEnable;
  boolean parityTypeOdd;
  boolean clkOutputPolarity;

  //1F80104Ah JOY_CTRL (R/W) (usually 1003h,3003h,0000h)
  boolean txEnable;
  boolean JoyOutput;
  boolean rxEnable;
  boolean joyControlUnknownBit3;
  boolean controlAck;
  boolean joyControlUnknownBit5;
  boolean controlReset;
  int rxInterruptMode;
  boolean txInterruptEnable;
  boolean rxInterruptEnable;
  boolean ackInterruptEnable;
  int desiredSlotNumber;

  private short JOY_BAUD;    //1F80104Eh JOY_BAUD(R/W) (usually 0088h, i.e. circa 250kHz, when Factor = MUL1)

  private enum JoypadDevice {
    NONE,
    CONTROLLER,
    MEMORY_CARD,
  }

  private JoypadDevice joypadDevice = JoypadDevice.NONE;

  private final Controller controller;
  private final MemoryCard memoryCard;

  private boolean running;

  public Joypad(final Memory memory, final Controller controller, final MemoryCard memoryCard) {
    memory.addSegment(new JoypadSegment(0x1f80_1040L));
    this.controller = controller;
    this.memoryCard = memoryCard;
  }

  int counter;
  int counter2;

  private final Object registerLock = new Object();

  @Override
  public void run() {
    this.running = true;

    final long timeout = 500L;
    long timer = System.nanoTime() + timeout;

    while(this.running) {
      synchronized(this.registerLock) {
        if(this.counter > 0) {
          this.counter -= 100;
          if(this.counter == 0) {
            LOGGER.debug("[IRQ] TICK Triggering JOYPAD");
            this.ackInputLevel = false;
            this.interruptRequest = true;
            this.edgeTrigger = true;
          }
        }

        if(this.counter2 > 0) {
          this.counter2 -= 100;
          if(this.counter2 == 0) {
            LOGGER.debug("[IRQ] TICK Triggering JOYPAD (memcard)");
            this.ackInputLevel = false;
            this.interruptRequest = true;
            this.edgeTrigger = true;
          }
        }

        if(this.interruptRequest && this.edgeTrigger) {
          INTERRUPTS.set(InterruptType.CONTROLLER);
          this.edgeTrigger = false;
        }
      }

      while(timer > System.nanoTime()) {
        DebugHelper.sleep(0);
      }

      timer = System.nanoTime() + timeout;

      while(Hardware.dumping) {
        Hardware.joyWaiting = true;
        DebugHelper.sleep(1);
      }

      Hardware.joyWaiting = false;
    }
  }

  public void stop() {
    this.running = false;
  }

  private void reloadTimer() {
    LOGGER.debug("[JOYPAD] RELOAD TIMER");
    this.baudrateTimer = this.JOY_BAUD * this.baudrateReloadFactor & ~0x1;
  }

  private void setJOY_DATA(final int value) {
    synchronized(this.registerLock) {
      LOGGER.debug("[JOYPAD] TX DATA ENQUEUE %02x", value);
      this.JOY_TX_DATA = (byte)value;
      this.JOY_RX_DATA = (byte)0xff;
      this.fifoFull = true;
      this.txReadyFlag1 = true;
      this.txReadyFlag2 = false;
      if(this.JoyOutput) {
        this.txReadyFlag2 = true;

        LOGGER.debug("[JOYPAD] DesiredSlot == %d", this.desiredSlotNumber);
        if(this.desiredSlotNumber == 1) {
          this.JOY_RX_DATA = (byte)0xff;
          this.ackInputLevel = false;
          return;
        }

        if(this.joypadDevice == JoypadDevice.NONE) {
          if(value == 0x01) {
            this.joypadDevice = JoypadDevice.CONTROLLER;
          } else if(value == 0x81) {
            this.joypadDevice = JoypadDevice.MEMORY_CARD;
          }
        }

        if(this.joypadDevice == JoypadDevice.CONTROLLER) {
          this.JOY_RX_DATA = this.controller.process(this.JOY_TX_DATA);
          this.ackInputLevel = this.controller.ack;
          if(this.ackInputLevel) {
            this.counter = 500;
          }
          LOGGER.debug("[JOYPAD] Controller TICK Enqueued RX response %02x ack: %b", this.JOY_RX_DATA, this.ackInputLevel);
          //Console.ReadLine();
        } else if(this.joypadDevice == JoypadDevice.MEMORY_CARD) {
          this.JOY_RX_DATA = this.memoryCard.process(this.JOY_TX_DATA);
          this.ackInputLevel = this.memoryCard.ack;
          if(this.ackInputLevel) {
            this.counter2 = 100000;
          }
          LOGGER.debug("[JOYPAD] MemCard TICK Enqueued RX response %02x ack: %b", this.JOY_RX_DATA, this.ackInputLevel);
          //Console.ReadLine();
        } else {
          this.ackInputLevel = false;
        }
        if(!this.ackInputLevel) {
          this.joypadDevice = JoypadDevice.NONE;
        }
      } else {
        this.joypadDevice = JoypadDevice.NONE;
        this.memoryCard.resetToIdle();
        this.controller.resetToIdle();

        this.ackInputLevel = false;
      }
    }
  }

  private void setJOY_CTRL(final int value) {
    synchronized(this.registerLock) {
      LOGGER.debug("[JOYPAD] Set CTRL %04x", value);

      this.txEnable = (value & 0x1) != 0;
      this.JoyOutput = (value >> 1 & 0x1) != 0;
      this.rxEnable = (value >> 2 & 0x1) != 0;
      this.joyControlUnknownBit3 = (value >> 3 & 0x1) != 0;
      this.controlAck = (value >> 4 & 0x1) != 0;
      this.joyControlUnknownBit5 = (value >> 5 & 0x1) != 0;
      this.controlReset = (value >> 6 & 0x1) != 0;
      this.rxInterruptMode = value >> 8 & 0x3;
      this.txInterruptEnable = (value >> 10 & 0x1) != 0;
      this.rxInterruptEnable = (value >> 11 & 0x1) != 0;
      this.ackInterruptEnable = (value >> 12 & 0x1) != 0;
      this.desiredSlotNumber = value >> 13 & 0x1;

      if(this.controlAck) {
        LOGGER.debug("[JOYPAD] CONTROL ACK");
        this.rxParityError = false;
        this.interruptRequest = false;
        this.controlAck = false;
      }

      if(this.controlReset) {
        LOGGER.debug("[JOYPAD] CONTROL RESET");
        this.joypadDevice = JoypadDevice.NONE;
        this.controller.resetToIdle();
        this.memoryCard.resetToIdle();
        this.fifoFull = false;

        this.setJOY_MODE(0);
        this.setJOY_CTRL(0);
        this.JOY_BAUD = 0;

        this.JOY_RX_DATA = (byte)0xff;
        this.JOY_TX_DATA = (byte)0xff;

        this.txReadyFlag1 = true;
        this.txReadyFlag2 = true;

        this.controlReset = false;
      }

      if(!this.JoyOutput) {
        this.joypadDevice = JoypadDevice.NONE;
        this.memoryCard.resetToIdle();
        this.controller.resetToIdle();
      }
    }
  }

  private void setJOY_MODE(final int value) {
    synchronized(this.registerLock) {
      this.baudrateReloadFactor = value & 0x3;
      this.characterLength = value >> 2 & 0x3;
      this.parityEnable = (value >> 4 & 0x1) != 0;
      this.parityTypeOdd = (value >> 5 & 0x1) != 0;
      this.clkOutputPolarity = (value >> 8 & 0x1) != 0;
    }
  }

  private void setJOY_BAUD(final int value) {
    synchronized(this.registerLock) {
      this.JOY_BAUD = (short)value;
      this.reloadTimer();
    }
  }

  private byte getJOY_DATA() {
    Joypad.this.fifoFull = false;
    return Joypad.this.JOY_RX_DATA;
  }

  private int getJOY_CTRL() {
    int joy_ctrl = 0;
    joy_ctrl |= this.txEnable ? 1 : 0;
    joy_ctrl |= (this.JoyOutput ? 1 : 0) << 1;
    joy_ctrl |= (this.rxEnable ? 1 : 0) << 2;
    joy_ctrl |= (this.joyControlUnknownBit3 ? 1 : 0) << 3;
    //joy_ctrl |= (ack ? 1u : 0u) << 4; // only writeable
    joy_ctrl |= (this.joyControlUnknownBit5 ? 1 : 0) << 5;
    //joy_ctrl |= (reset ? 1u : 0u) << 6; // only writeable
    //bit 7 allways 0
    joy_ctrl |= this.rxInterruptMode << 8;
    joy_ctrl |= (this.txInterruptEnable ? 1 : 0) << 10;
    joy_ctrl |= (this.rxInterruptEnable ? 1 : 0) << 11;
    joy_ctrl |= (this.ackInterruptEnable ? 1 : 0) << 12;
    joy_ctrl |= this.desiredSlotNumber << 13;
    return joy_ctrl;
  }

  private int getJOY_MODE() {
    int joy_mode = 0;
    joy_mode |= this.baudrateReloadFactor;
    joy_mode |= this.characterLength << 2;
    joy_mode |= (this.parityEnable ? 1 : 0) << 4;
    joy_mode |= (this.parityTypeOdd ? 1 : 0) << 5;
    joy_mode |= (this.clkOutputPolarity ? 1 : 0) << 4;
    return joy_mode;
  }

  private int getJOY_STAT() {
    int joy_stat = 0;
    joy_stat |= this.txReadyFlag1 ? 1 : 0;
    joy_stat |= (this.fifoFull ? 1 : 0) << 1;
    joy_stat |= (this.txReadyFlag2 ? 1 : 0) << 2;
    joy_stat |= (this.rxParityError ? 1 : 0) << 3;
    joy_stat |= (this.ackInputLevel ? 1 : 0) << 7;
    joy_stat |= (this.interruptRequest ? 1 : 0) << 9;
    joy_stat |= this.baudrateTimer << 11;

    this.ackInputLevel = false;

    return joy_stat;
  }

  private int getJOY_BAUD() {
    return this.JOY_BAUD;
  }

  public void dump(final ByteBuffer stream) {
    IoHelper.write(stream, this.JOY_TX_DATA);
    IoHelper.write(stream, this.JOY_RX_DATA);

    IoHelper.write(stream, this.fifoFull);

    IoHelper.write(stream, this.edgeTrigger);
    IoHelper.write(stream, this.txReadyFlag1);
    IoHelper.write(stream, this.txReadyFlag2);
    IoHelper.write(stream, this.rxParityError);
    IoHelper.write(stream, this.ackInputLevel);
    IoHelper.write(stream, this.interruptRequest);
    IoHelper.write(stream, this.baudrateTimer);

    IoHelper.write(stream, this.baudrateReloadFactor);
    IoHelper.write(stream, this.characterLength);
    IoHelper.write(stream, this.parityEnable);
    IoHelper.write(stream, this.parityTypeOdd);
    IoHelper.write(stream, this.clkOutputPolarity);

    IoHelper.write(stream, this.txEnable);
    IoHelper.write(stream, this.JoyOutput);
    IoHelper.write(stream, this.rxEnable);
    IoHelper.write(stream, this.joyControlUnknownBit3);
    IoHelper.write(stream, this.controlAck);
    IoHelper.write(stream, this.joyControlUnknownBit5);
    IoHelper.write(stream, this.controlReset);
    IoHelper.write(stream, this.rxInterruptMode);
    IoHelper.write(stream, this.txInterruptEnable);
    IoHelper.write(stream, this.rxInterruptEnable);
    IoHelper.write(stream, this.ackInterruptEnable);
    IoHelper.write(stream, this.desiredSlotNumber);

    IoHelper.write(stream, this.JOY_BAUD);

    IoHelper.write(stream, this.joypadDevice);

    this.controller.dump(stream);
    this.memoryCard.dump(stream);

    IoHelper.write(stream, this.running);

    IoHelper.write(stream, this.counter);
    IoHelper.write(stream, this.counter2);
  }

  public void load(final ByteBuffer stream) {
    this.JOY_TX_DATA = IoHelper.readByte(stream);
    this.JOY_RX_DATA = IoHelper.readByte(stream);

    this.fifoFull = IoHelper.readBool(stream);

    this.edgeTrigger = IoHelper.readBool(stream);
    this.txReadyFlag1 = IoHelper.readBool(stream);
    this.txReadyFlag2 = IoHelper.readBool(stream);
    this.rxParityError = IoHelper.readBool(stream);
    this.ackInputLevel = IoHelper.readBool(stream);
    this.interruptRequest = IoHelper.readBool(stream);
    this.baudrateTimer = IoHelper.readInt(stream);

    this.baudrateReloadFactor = IoHelper.readInt(stream);
    this.characterLength = IoHelper.readInt(stream);
    this.parityEnable = IoHelper.readBool(stream);
    this.parityTypeOdd = IoHelper.readBool(stream);
    this.clkOutputPolarity = IoHelper.readBool(stream);

    this.txEnable = IoHelper.readBool(stream);
    this.JoyOutput = IoHelper.readBool(stream);
    this.rxEnable = IoHelper.readBool(stream);
    this.joyControlUnknownBit3 = IoHelper.readBool(stream);
    this.controlAck = IoHelper.readBool(stream);
    this.joyControlUnknownBit5 = IoHelper.readBool(stream);
    this.controlReset = IoHelper.readBool(stream);
    this.rxInterruptMode = IoHelper.readInt(stream);
    this.txInterruptEnable = IoHelper.readBool(stream);
    this.rxInterruptEnable = IoHelper.readBool(stream);
    this.ackInterruptEnable = IoHelper.readBool(stream);
    this.desiredSlotNumber = IoHelper.readInt(stream);

    this.JOY_BAUD = IoHelper.readShort(stream);

    this.joypadDevice = IoHelper.readEnum(stream, JoypadDevice.class);

    this.controller.load(stream);
    this.memoryCard.load(stream);

    this.running = IoHelper.readBool(stream);

    this.counter = IoHelper.readInt(stream);
    this.counter2 = IoHelper.readInt(stream);
  }

  public class JoypadSegment extends Segment {
    public JoypadSegment(final long address) {
      super(address, 0x10);
    }

    @Override
    public byte get(final int offset) {
      if(offset == 0x0) {
        return Joypad.this.getJOY_DATA();
      }

      throw new MisalignedAccessException("Peripheral IO port " + Long.toHexString(this.getAddress() + offset) + " may not be accessed with 8-bit reads or writes");
    }

    @Override
    public long get(final int offset, final int size) {
      if(size == 1) {
        return this.get(offset) & 0xffL;
      }

      if(size == 2) {
        return switch(offset & 0xe) {
          case 0x08 -> Joypad.this.getJOY_MODE() & 0xffffL;
          case 0x0a -> Joypad.this.getJOY_CTRL() & 0xffffL;
          case 0x0e -> Joypad.this.getJOY_BAUD() & 0xffffL;
          default -> throw new MisalignedAccessException("Peripheral IO port " + Long.toHexString(this.getAddress() + offset) + " may not be accessed with 16-bit reads or writes");
        };
      }

      return switch(offset & 0x4) {
        case 0x00 -> Joypad.this.getJOY_DATA() & 0xffff_ffffL;
        case 0x04 -> Joypad.this.getJOY_STAT() & 0xffff_ffffL;
        default -> throw new MisalignedAccessException("Peripheral IO port " + Long.toHexString(this.getAddress() + offset) + " may not be accessed with 32-bit reads or writes");
      };
    }

    @Override
    public void set(final int offset, final byte value) {
      if(offset == 0x0) {
        Joypad.this.setJOY_DATA(value & 0xff);
      }

      throw new MisalignedAccessException("Peripheral IO port " + Long.toHexString(this.getAddress() + offset) + " may not be accessed with 8-bit reads or writes");
    }

    @Override
    public void set(final int offset, final int size, final long value) {
      if(size == 1) {
        this.set(offset, (byte)value);
        return;
      }

      if(size == 2) {
        switch(offset & 0xe) {
          case 0x08 -> Joypad.this.setJOY_MODE((int)value);
          case 0x0a -> Joypad.this.setJOY_CTRL((int)value);
          case 0x0e -> Joypad.this.setJOY_BAUD((int)value);
          default -> throw new MisalignedAccessException("Peripheral IO port " + Long.toHexString(this.getAddress() + offset) + " may not be accessed with 16-bit reads or writes");
        }

        return;
      }

      if((offset & 0x4) == 0x00) {
        Joypad.this.setJOY_DATA((int)value);
      } else {
        throw new MisalignedAccessException("Peripheral IO port " + Long.toHexString(this.getAddress() + offset) + " may not be accessed with 32-bit reads or writes");
      }
    }

    @Override
    public void dump(final ByteBuffer stream) {

    }

    @Override
    public void load(final ByteBuffer stream) {

    }
  }
}
