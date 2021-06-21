package legend.core.input;

import legend.core.memory.Memory;
import legend.core.memory.MisalignedAccessException;
import legend.core.memory.Segment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Joypad {
  private static final Logger LOGGER = LogManager.getFormatterLogger(Joypad.class);

  private byte JOY_TX_DATA; //1F801040h JOY_TX_DATA(W)
  private byte JOY_RX_DATA; //1F801040h JOY_RX_DATA(R) FIFO
  private boolean fifoFull;

  //1F801044 JOY_STAT(R)
  boolean TXreadyFlag1 = true;
  boolean TXreadyFlag2 = true;
  boolean RXparityError;
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
  boolean TXenable;
  boolean JoyOutput;
  boolean RXenable;
  boolean joyControl_unknow_bit3;
  boolean controlAck;
  boolean joyControl_unknow_bit5;
  boolean controlReset;
  int RXinterruptMode;
  boolean TXinterruptEnable;
  boolean RXinterruptEnable;
  boolean ACKinterruptEnable;
  int desiredSlotNumber;

  private short JOY_BAUD;    //1F80104Eh JOY_BAUD(R/W) (usually 0088h, ie.circa 250kHz, when Factor = MUL1)

  private enum JoypadDevice {
    None,
    Controller,
    MemoryCard
  }

  private JoypadDevice joypadDevice = JoypadDevice.None;

  private final Controller controller;
  private final MemoryCard memoryCard;

  public Joypad(final Memory memory, final Controller controller, final MemoryCard memoryCard) {
    memory.addSegment(new JoypadSegment(0x1f80_1040L));
    this.controller = controller;
    this.memoryCard = memoryCard;
  }

  int counter;

  public boolean tick() {
    if(this.counter > 0) {
      this.counter -= 100;
      if(this.counter == 0) {
        //Console.WriteLine("[IRQ] TICK Triggering JOYPAD");
        this.ackInputLevel = false;
        this.interruptRequest = true;
      }
    }

    return this.interruptRequest;
  }

  private void reloadTimer() {
    //Console.WriteLine("[JOYPAD] RELOAD TIMER");
    this.baudrateTimer = this.JOY_BAUD * this.baudrateReloadFactor & ~0x1;
  }

  private void setJOY_DATA(final int value) {
    //Console.WriteLine("[JOYPAD] TX DATA ENQUEUE " + value.ToString("x2"));
    this.JOY_TX_DATA = (byte)value;
    this.JOY_RX_DATA = (byte)0xff;
    this.fifoFull = true;
    this.TXreadyFlag1 = true;
    this.TXreadyFlag2 = false;
    if(this.JoyOutput) {
      this.TXreadyFlag2 = true;

      //Console.WriteLine("[JOYPAD] DesiredSlot == " + desiredSlotNumber);
      if(this.desiredSlotNumber == 1) {
        this.JOY_RX_DATA = (byte)0xff;
        this.ackInputLevel = false;
        return;
      }

      if(this.joypadDevice == JoypadDevice.None) {
        //Console.ForegroundColor = ConsoleColor.Red;
        if(value == 0x01) {
          //Console.ForegroundColor = ConsoleColor.Green;
          this.joypadDevice = JoypadDevice.Controller;
        } else if(value == 0x81) {
          //Console.ForegroundColor = ConsoleColor.Blue;
          this.joypadDevice = JoypadDevice.MemoryCard;
        }
      }

      if(this.joypadDevice == JoypadDevice.Controller) {
        this.JOY_RX_DATA = this.controller.process(this.JOY_TX_DATA);
        this.ackInputLevel = this.controller.ack;
        if(this.ackInputLevel) {
          this.counter = 500;
        }
        //Console.WriteLine($"[JOYPAD] Conroller TICK Enqueued RX response {JOY_RX_DATA:x2} ack: {ackInputLevel}");
        //Console.ReadLine();
      } else if(this.joypadDevice == JoypadDevice.MemoryCard) {
        this.JOY_RX_DATA = this.memoryCard.process(this.JOY_TX_DATA);
        this.ackInputLevel = this.memoryCard.ack;
        if(this.ackInputLevel) {
          this.counter = 500;
        }
        //Console.WriteLine($"[JOYPAD] MemCard TICK Enqueued RX response {JOY_RX_DATA:x2} ack: {ackInputLevel}");
        //Console.ReadLine();
      } else {
        this.ackInputLevel = false;
      }
      if(!this.ackInputLevel) {
        this.joypadDevice = JoypadDevice.None;
      }
    } else {
      this.joypadDevice = JoypadDevice.None;
      this.memoryCard.resetToIdle();
      this.controller.resetToIdle();

      this.ackInputLevel = false;
    }
  }

  private void setJOY_CTRL(final int value) {
    this.TXenable = (value & 0x1) != 0;
    this.JoyOutput = (value >> 1 & 0x1) != 0;
    this.RXenable = (value >> 2 & 0x1) != 0;
    this.joyControl_unknow_bit3 = (value >> 3 & 0x1) != 0;
    this.controlAck = (value >> 4 & 0x1) != 0;
    this.joyControl_unknow_bit5 = (value >> 5 & 0x1) != 0;
    this.controlReset = (value >> 6 & 0x1) != 0;
    this.RXinterruptMode = value >> 8 & 0x3;
    this.TXinterruptEnable = (value >> 10 & 0x1) != 0;
    this.RXinterruptEnable = (value >> 11 & 0x1) != 0;
    this.ACKinterruptEnable = (value >> 12 & 0x1) != 0;
    this.desiredSlotNumber = value >> 13 & 0x1;

    if(this.controlAck) {
      //Console.WriteLine("[JOYPAD] CONTROL ACK");
      this.RXparityError = false;
      this.interruptRequest = false;
      this.controlAck = false;
    }

    if(this.controlReset) {
      //Console.WriteLine("[JOYPAD] CONTROL RESET");
      this.joypadDevice = JoypadDevice.None;
      this.controller.resetToIdle();
      this.memoryCard.resetToIdle();
      this.fifoFull = false;

      this.setJOY_MODE(0);
      this.setJOY_CTRL(0);
      this.JOY_BAUD = 0;

      this.JOY_RX_DATA = (byte)0xFF;
      this.JOY_TX_DATA = (byte)0xFF;

      this.TXreadyFlag1 = true;
      this.TXreadyFlag2 = true;

      this.controlReset = false;
    }

    if(!this.JoyOutput) {
      this.joypadDevice = JoypadDevice.None;
      this.memoryCard.resetToIdle();
      this.controller.resetToIdle();
    }
  }

  private void setJOY_MODE(final int value) {
    this.baudrateReloadFactor = value & 0x3;
    this.characterLength = value >> 2 & 0x3;
    this.parityEnable = (value >> 4 & 0x1) != 0;
    this.parityTypeOdd = (value >> 5 & 0x1) != 0;
    this.clkOutputPolarity = (value >> 8 & 0x1) != 0;
  }

  private void setJOY_BAUD(final int value) {
    this.JOY_BAUD = (short)value;
    this.reloadTimer();
  }

  private byte getJOY_DATA() {
    Joypad.this.fifoFull = false;
    return Joypad.this.JOY_RX_DATA;
  }

  private int getJOY_CTRL() {
    int joy_ctrl = 0;
    joy_ctrl |= this.TXenable ? 1 : 0;
    joy_ctrl |= (this.JoyOutput ? 1 : 0) << 1;
    joy_ctrl |= (this.RXenable ? 1 : 0) << 2;
    joy_ctrl |= (this.joyControl_unknow_bit3 ? 1 : 0) << 3;
    //joy_ctrl |= (ack ? 1u : 0u) << 4; // only writeable
    joy_ctrl |= (this.joyControl_unknow_bit5 ? 1 : 0) << 5;
    //joy_ctrl |= (reset ? 1u : 0u) << 6; // only writeable
    //bit 7 allways 0
    joy_ctrl |= this.RXinterruptMode << 8;
    joy_ctrl |= (this.TXinterruptEnable ? 1 : 0) << 10;
    joy_ctrl |= (this.RXinterruptEnable ? 1 : 0) << 11;
    joy_ctrl |= (this.ACKinterruptEnable ? 1 : 0) << 12;
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
    joy_stat |= this.TXreadyFlag1 ? 1 : 0;
    joy_stat |= (this.fifoFull ? 1 : 0) << 1;
    joy_stat |= (this.TXreadyFlag2 ? 1 : 0) << 2;
    joy_stat |= (this.RXparityError ? 1 : 0) << 3;
    joy_stat |= (this.ackInputLevel ? 1 : 0) << 7;
    joy_stat |= (this.interruptRequest ? 1 : 0) << 9;
    joy_stat |= this.baudrateTimer << 11;

    this.ackInputLevel = false;

    return joy_stat;
  }

  private int getJOY_BAUD() {
    return this.JOY_BAUD;
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
          case 0x08 -> Joypad.this.getJOY_MODE();
          case 0x0a -> Joypad.this.getJOY_CTRL();
          case 0x0e -> Joypad.this.getJOY_BAUD();
          default -> throw new MisalignedAccessException("Peripheral IO port " + Long.toHexString(this.getAddress() + offset) + " may not be accessed with 16-bit reads or writes");
        };
      }

      return switch(offset & 0x4) {
        case 0x00 -> Joypad.this.getJOY_DATA();
        case 0x04 -> Joypad.this.getJOY_STAT();
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

      switch(offset & 0x4) {
        case 0x00 -> Joypad.this.setJOY_DATA((int)value);
        default -> throw new MisalignedAccessException("Peripheral IO port " + Long.toHexString(this.getAddress() + offset) + " may not be accessed with 32-bit reads or writes");
      };
    }
  }
}
