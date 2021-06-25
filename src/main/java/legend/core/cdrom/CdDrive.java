package legend.core.cdrom;

import legend.core.InterruptType;
import legend.core.MathHelper;
import legend.core.dma.DmaChannel;
import legend.core.dma.DmaInterface;
import legend.core.memory.Memory;
import legend.core.memory.Segment;
import legend.core.memory.Value;
import legend.core.spu.XaAdpcm;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static legend.core.Hardware.DMA;
import static legend.core.Hardware.INTERRUPTS;
import static legend.core.Hardware.MEMORY;
import static legend.core.Hardware.SPU;
import static legend.core.MathHelper.fromBcd;

public class CdDrive {
  public static final Value CDROM_REG0 = MEMORY.ref(1, 0x1f801800L);
  public static final Value CDROM_REG1 = MEMORY.ref(1, 0x1f801801L);
  public static final Value CDROM_REG2 = MEMORY.ref(1, 0x1f801802L);
  public static final Value CDROM_REG3 = MEMORY.ref(1, 0x1f801803L);

  private static final Logger LOGGER = LogManager.getFormatterLogger(CdDrive.class);

  private final IsoReader diskAsync;
  private final IsoReader diskSync;

  private final Status status = new Status();
  private final CdlMODE mode = new CdlMODE();

  private final CdlLOC seekLoc = new CdlLOC();
  private final CdlLOC readLoc = new CdlLOC();

  private final Deque<Integer> parameterBuffer = new ArrayDeque<>(16);
  private final Deque<Integer> responseBuffer = new ArrayDeque<>(16);
  private final Deque<Byte> interruptQueue = new ArrayDeque<>();

  private int interruptEnable = 0b11111;
  private int interruptFlag;

  private boolean isBusy;

  private INDEX index = INDEX.INDEX_0;
  private final Register[] registers = {
    new Register(0x1f80_1800L, this::onRegister0Read, this::onRegister0Read, this::onRegister0Read, this::onRegister0Read, this::onRegister0Write, this::onRegister0Write, this::onRegister0Write, this::onRegister0Write),
    new Register(0x1f80_1801L, this::onRegister1Read, this::onRegister1Read, this::onRegister1Read, this::onRegister1Read, this::onRegister1Index0Write, this::onRegister1Index1Write, this::onRegister1Index2Write, this::onRegister1Index3Write),
    new Register(0x1f80_1802L, this::onRegister2Read, this::onRegister2Read, this::onRegister2Read, this::onRegister2Read, this::onRegister2Index0Write, this::onRegister2Index1Write, this::onRegister2Index2Write, this::onRegister2Index3Write),
    new Register(0x1f80_1803L, this::onRegister3ReadInterruptEnable, this::onRegister3ReadInterruptFlags, this::onRegister3ReadInterruptEnable, this::onRegister3ReadInterruptFlags, this::onRegister3Index0Write, this::onRegister3Index1Write, this::onRegister3Index2Write, this::onRegister3Index3Write),
  };

  private final EnumMap<CdlCOMMAND, Runnable> commandCallbacks = new EnumMap<>(CdlCOMMAND.class);

  private enum State {
    IDLE,
    SEEK,
    READ,
    PLAY,
    TOC,
  }

  private State state = State.IDLE;
  private int counter;

  /**
   * 0 = mute, 80 = normal, ff = double
   */
  private int audioCdLeftToSpuLeft = 0x80;
  /**
   * 0 = mute, 80 = normal, ff = double
   */
  private int audioCdLeftToSpuRight;
  /**
   * 0 = mute, 80 = normal, ff = double
   */
  private int audioCdRightToSpuRight = 0x80;
  /**
   * 0 = mute, 80 = normal, ff = double
   */
  private int audioCdRightToSpuLeft;

  /**
   * 0 = mute, 80 = normal, ff = double
   */
  private int audioCdLeftToSpuLeftUncommitted = this.audioCdLeftToSpuLeft;
  /**
   * 0 = mute, 80 = normal, ff = double
   */
  private int audioCdLeftToSpuRightUncommitted = this.audioCdLeftToSpuRight;
  /**
   * 0 = mute, 80 = normal, ff = double
   */
  private int audioCdRightToSpuRightUncommitted = this.audioCdRightToSpuRight;
  /**
   * 0 = mute, 80 = normal, ff = double
   */
  private int audioCdRightToSpuLeftUncommitted = this.audioCdRightToSpuLeft;

  private boolean mutedAudio;
  private boolean mutedXAADPCM;

  private final Deque<Byte> dataBuffer = new ArrayDeque<>();
  private final Deque<Byte> cdBuffer = new ArrayDeque<>();

  public CdDrive(final Memory memory) {
    try {
      this.diskAsync = new IsoReader(Paths.get("isos/1.iso"));
      this.diskSync = new IsoReader(Paths.get("isos/1.iso"));
    } catch(final IOException e) {
      throw new RuntimeException("Failed to load disk 1", e);
    }

    this.commandCallbacks.put(CdlCOMMAND.GET_STAT_01, this::getStat);
    this.commandCallbacks.put(CdlCOMMAND.SET_LOC_02, this::setLoc);
    this.commandCallbacks.put(CdlCOMMAND.READ_N_06, this::read);
    this.commandCallbacks.put(CdlCOMMAND.PAUSE_09, this::pause);
    this.commandCallbacks.put(CdlCOMMAND.INIT_0A, this::init);
    this.commandCallbacks.put(CdlCOMMAND.DEMUTE_0C, this::demute);
    this.commandCallbacks.put(CdlCOMMAND.SET_MODE_0E, this::setMode);
    this.commandCallbacks.put(CdlCOMMAND.GET_TN_13, this::getTN);
    this.commandCallbacks.put(CdlCOMMAND.SEEK_L_15, this::seekL);
    this.commandCallbacks.put(CdlCOMMAND.READ_S_1B, this::read);

    DMA.cdrom.setDmaInterface(new DmaInterface() {
      @Override
      public void blockCopy(final int size) {
        if(DMA.cdrom.channelControl.getTransferDirection() == DmaChannel.ChannelControl.TRANSFER_DIRECTION.TO_MAIN_RAM) {
          final byte[] data = CdDrive.this.processDmaLoad(size);
          MEMORY.setBytes(DMA.cdrom.MADR.get(), data);
        } else {
          assert false;
        }

        DMA.cdrom.MADR.addu(DMA.cdrom.channelControl.getAddressStep().step * size);
      }

      @Override
      public void linkedList() {
        assert false;
      }
    });

    for(final Register register : this.registers) {
      memory.addSegment(register);
    }
  }

  private byte[] processDmaLoad(final int size) {
    LOGGER.info("[CDROM] DMA transferring %d words", size);

    final byte[] data = new byte[size * 4];

    synchronized(this.dataBuffer) {
      for(int i = 0; i < data.length; i++) {
        data[i] = this.dataBuffer.remove();
      }
    }

    return data;
  }

  public void readFromDisk(final CdlLOC pos, final int sectorCount, final long dest) {
    LOGGER.info("Performing direct read from disk: %d sectors from %s to %08x", sectorCount, pos, dest);

    final CdlLOC loc = new CdlLOC().set(pos);

    for(int i = 0; i < sectorCount; i++) {
      final byte[] data = new byte[0x800];

      try {
        this.diskSync.seekSector(loc);
        this.diskSync.advance(0xc);
        this.diskSync.read(data);
      } catch(final IOException e) {
        throw new RuntimeException(e);
      }

      MEMORY.setBytes(dest + i * data.length, data);

      loc.advance(1);

      INTERRUPTS.set(InterruptType.CDROM);
    }
  }

  public boolean tick(final int cycles) {
    this.counter += cycles;

    if(this.processInterrupt()) {
      return true;
    }

    switch(this.state) {
      case IDLE:
        this.counter = 0;
        return false;

      case SEEK:
        synchronized(this.interruptQueue) {
          if(this.counter < 33868800 / 3 || !this.interruptQueue.isEmpty()) {
            return false;
          }
        }

        this.counter = 0;
        this.state = State.IDLE;
        this.status.seeking = false;

        this.queueResponse(0x2, this.status.pack());
        break;

      case READ:
      case PLAY:
        synchronized(this.interruptQueue) {
          if(this.counter < 33868800 / (this.mode.isDoubleSpeed() ? 150 : 75) || !this.interruptQueue.isEmpty()) {
            return false;
          }
        }

        this.counter = 0;

        LOGGER.info("[CDROM] Performing read @ %s", this.readLoc);

        byte[] rawSector = new byte[2352];
        try {
          this.diskAsync.read(rawSector);
        } catch(final IOException e) {
          throw new RuntimeException(e);
        }

        this.readLoc.advance(1);

        //TODO do we need to handle CDDA?

        final SectorSubHeader sectorSubHeader = new SectorSubHeader();
        sectorSubHeader.file = rawSector[16];
        sectorSubHeader.channel = rawSector[17];
        sectorSubHeader.subMode = rawSector[18];
        sectorSubHeader.codingInfo = rawSector[19];

        //if (sectorSubHeader.isVideo) Console.WriteLine("is video");
        //if (sectorSubHeader.isData) Console.WriteLine("is data");
        //if (sectorSubHeader.isAudio) Console.WriteLine("is audio");

        if(this.mode.isSendingAdpcmToSpu() && sectorSubHeader.isForm2()) {
          if(sectorSubHeader.isEndOfFile()) {
            LOGGER.info("[CDROM] XA ON: End of File!");
            //is this even needed? There seems to not be an AutoPause flag like on CDDA
            //RR4, Castlevania and others hang sound here if hard stopped to STAT 0x2
          }

          if(sectorSubHeader.isRealTime() && sectorSubHeader.isAudio()) {
            if(this.mode.isXaFilter() /*TODO && (filterFile != sectorSubHeader.file || filterChannel != sectorSubHeader.channel)*/) {
              LOGGER.info("[CDROM] XA Filter: file || channel");
              assert false : "CDDA not yet supported";
              return false;
            }

            if(!this.mutedAudio && !this.mutedXAADPCM) {
              LOGGER.info("[CDROM] Sending realtime XA data to SPU"); //TODO Flag to pass to SPU?

              final byte[] decodedXaAdpcm = XaAdpcm.decode(rawSector, sectorSubHeader.codingInfo);
              this.applyVolume(decodedXaAdpcm);
              SPU.pushCdBufferSamples(decodedXaAdpcm);
            }

            return false;
          }
        }

        //If we arrived here the sector should be delivered to CPU so slice out sync and header based on flag
        final byte[] sector;

        if(this.mode.isEntireSector()) {
          sector = new byte[rawSector.length - 12];
          System.arraycopy(rawSector, 12, sector, 0, sector.length);
        } else {
          sector = new byte[0x800];
          System.arraycopy(rawSector, 24, sector, 0, sector.length);
        }

        rawSector = sector;

        this.cdBuffer.clear();
        for(final byte b : rawSector) {
          this.cdBuffer.add(b);
        }

        this.queueResponse(0x1, this.status.pack());
        break;

      case TOC:
        assert false;
    }

    return false;
  }

  private void onRegister0Write(final long value) {
    if(value > 0b11L) {
      throw new RuntimeException("Bits 2-7 of reg0 are read-only");
    }

    this.index = INDEX.from(value);

    for(final Register register : this.registers) {
      register.setIndex(this.index);
    }
  }

  private void onRegister1Index0Write(final long value) {
    LOGGER.debug("Got CDROM command %02x", value);

    this.isBusy = true;
    synchronized(this.interruptQueue) {
      this.interruptQueue.clear();
    }
    this.responseBuffer.clear();

    final Runnable callback = this.commandCallbacks.computeIfAbsent(CdlCOMMAND.fromCommand((int)value), key -> {
      throw new RuntimeException("Cannot write to reg1.0 - unknown command " + Long.toString(value, 16));
    });

    callback.run();
  }

  private void onRegister1Index1Write(final long value) {
    throw new RuntimeException("Cannot write to reg1.1 - unknown command " + Long.toString(value, 16));
  }

  private void onRegister1Index2Write(final long value) {
    throw new RuntimeException("Cannot write to reg1.2 - unknown command " + Long.toString(value, 16));
  }

  private void onRegister1Index3Write(final long value) {
    LOGGER.info("Setting CDROM audio right -> SPU right to %02x", value);
    this.audioCdRightToSpuRightUncommitted = (int)value;
  }

  private void onRegister2Index0Write(final long value) {
    LOGGER.debug("Queued CDROM argument %02x", value);
    this.parameterBuffer.add((int)value);
  }

  private void onRegister2Index1Write(final long value) {
    LOGGER.debug("Set CDROM interruptMask to %s", Long.toString(value, 2));
    this.interruptEnable = (int)value & 0b11111;
  }

  private void onRegister2Index2Write(final long value) {
    LOGGER.info("Setting CDROM audio left -> SPU left to %02x", value);
    this.audioCdLeftToSpuLeftUncommitted = (int)value;
  }

  private void onRegister2Index3Write(final long value) {
    LOGGER.info("Setting CDROM audio right -> SPU left to %02x", value);
    this.audioCdRightToSpuLeftUncommitted = (int)value;
  }

  private void onRegister3Index0Write(final long value) {
    if((value & 0b0001_1111) != 0) {
      throw new RuntimeException("Bits 0-4 of reg3.0 are read only");
    }

    // SMEN
    if((value & 0b0010_0000) != 0) {
      throw new RuntimeException("reg3.0 want command start interrupt not yet implemented"); //TODO
    }

    // BFWR
    if((value & 0b0100_0000) != 0) {
      throw new RuntimeException("reg3.0 bit 6 unknown"); //TODO
    }

    // BFRD
    if((value & 0b1000_0000) == 0) {
      LOGGER.debug("CDROM reg3.0 bfrd 0 - clear data");

      synchronized(this.dataBuffer) {
        this.dataBuffer.clear();
      }
    } else {
      LOGGER.debug("CDROM ready to read");

      synchronized(this.dataBuffer) {
        if(!this.dataBuffer.isEmpty()) {
          /*Console.WriteLine(">>>>>>> CDROM BUFFER WAS NOT EMPTY <<<<<<<<<");*/
          return;
        }

        this.dataBuffer.addAll(this.cdBuffer);
      }
    }
  }

  private void onRegister3Index1Write(final long value) {
    LOGGER.debug("Acknowledging CDROM interrupt (%d)", value);

    this.interruptFlag &= ~(value & 0b11111);

    synchronized(this.interruptQueue) {
      if(!this.interruptQueue.isEmpty()) {
        this.interruptFlag |= this.interruptQueue.remove();
      }
    }

    if((value & 0b100_0000) != 0) {
      this.parameterBuffer.clear();
    }
  }

  private void onRegister3Index2Write(final long value) {
    LOGGER.info("Setting CDROM audio left -> SPU right to %02x", value);
    this.audioCdLeftToSpuRightUncommitted = (int)value;
  }

  private void onRegister3Index3Write(final long value) {
    this.mutedXAADPCM = (value & 0b1L) != 0;

    LOGGER.info("[CDROM] XA-ADPCM muted: %b", this.mutedXAADPCM);

    if((value & 0b100000) != 0) {
      LOGGER.info("Committing CDROM audio volume settings {LL: %x, LR: %x, RL: %x, RR: %x}", this.audioCdLeftToSpuLeftUncommitted, this.audioCdLeftToSpuRightUncommitted, this.audioCdRightToSpuLeftUncommitted, this.audioCdRightToSpuRightUncommitted);
      this.audioCdLeftToSpuLeft = this.audioCdLeftToSpuLeftUncommitted;
      this.audioCdLeftToSpuRight = this.audioCdLeftToSpuRightUncommitted;
      this.audioCdRightToSpuRight = this.audioCdRightToSpuRightUncommitted;
      this.audioCdRightToSpuLeft = this.audioCdRightToSpuLeftUncommitted;
    }
  }

  private long onRegister0Read() {
    return
      this.index.ordinal() |
      (this.mode.isSendingAdpcmToSpu() ? 1 : 0) << 2 |
      (this.parameterBuffer.isEmpty() ? 1 : 0) << 3 |
      (this.parameterBuffer.size() < 16 ? 1 : 0) << 4 |
      (this.responseBuffer.isEmpty() ? 0 : 1) << 5 |
      (this.dataBuffer.isEmpty() ? 0 : 1) << 6 |
      (this.isBusy ? 1 : 0) << 7;
  }

  private long onRegister1Read() {
    if(this.responseBuffer.isEmpty()) {
      throw new RuntimeException("No response available");
    }

    return this.responseBuffer.remove();
  }

  private long onRegister2Read() {
    synchronized(this.dataBuffer) {
      return this.dataBuffer.remove();
    }
  }

  private long onRegister3ReadInterruptEnable() {
    return this.interruptEnable | 0b1110_0000L;
  }

  private long onRegister3ReadInterruptFlags() {
    return this.interruptFlag | 0b1110_0000L;
  }

  private void getStat() {
    LOGGER.info("Running GetSTAT...");

    this.status.shellOpen = false;
    this.queueResponse(0x3, this.status.pack());
  }

  private void setLoc() {
    LOGGER.info("Running SetLoc...");

    this.seekLoc.setMinute(fromBcd(this.parameterBuffer.remove()));
    this.seekLoc.setSecond(fromBcd(this.parameterBuffer.remove()));
    this.seekLoc.setSector(fromBcd(this.parameterBuffer.remove()));

    LOGGER.info("Seeking to %s", this.seekLoc);

    try {
      this.diskAsync.seekSectorRaw(this.seekLoc);
    } catch(final IOException e) {
      throw new RuntimeException("Failed to seek to " + this.seekLoc, e);
    }

    this.queueResponse(0x3, this.status.pack());
  }

  private void read() {
    LOGGER.info("Beginning data read...");

    this.readLoc.set(this.seekLoc);

    this.status.reading = true;
    this.status.spindleMotor = true;

    this.state = State.READ;

    this.queueResponse(0x3, this.status.pack());
  }

  private void pause() {
    LOGGER.info("Running pause...");

    this.queueResponse(0x3, this.status.pack());

    this.status.reading = false;
    this.state = State.IDLE;

    this.queueResponse(0x2, this.status.pack());
  }

  private void init() {
    LOGGER.info("Initializing CDROM...");

    this.status.clear();
    this.status.spindleMotor = true;

    this.mode.clear();

    this.responseBuffer.clear();
    this.parameterBuffer.clear();
    synchronized(this.dataBuffer) {
      this.dataBuffer.clear();
      this.cdBuffer.clear();
    }

    this.queueResponse(0x3, this.status.pack());
    this.queueResponse(0x2, this.status.pack());
  }

  private void demute() {
    LOGGER.info("Unmuting CDROM...");
    this.mode.sendAdpcmToSpu().readCddaSectors();
    this.mutedAudio = false;

    this.queueResponse(0x3, this.status.pack());
  }

  private void setMode() {
    LOGGER.info("Setting CDROM mode...");
    this.mode.set(this.parameterBuffer.remove());
    LOGGER.info("New mode: %s", this.mode);

    this.queueResponse(0x3, this.status.pack());
  }

  private void getTN() {
    LOGGER.info("Getting track number...");
    //TODO verify that there is, in fact, only one track

    this.queueResponse(0x3, this.status.pack(), 1, 1);
  }

  private void seekL() {
    LOGGER.info("Seeking to %s...", this.seekLoc);

    this.readLoc.set(this.seekLoc);

    try {
      this.diskAsync.seekSectorRaw(this.seekLoc);
    } catch(final IOException e) {
      throw new RuntimeException("Failed to seek to " + this.seekLoc, e);
    }

    this.status.spindleMotor = true;
    this.status.seeking = true;

    this.state = State.SEEK;

    this.queueResponse(0x3, this.status.pack());
  }

  private void queueResponse(final int interrupt, final int... responses) {
    LOGGER.debug("[CDROM] Queueing CDROM response interrupt %d with responses %s", interrupt, Arrays.toString(responses));

    synchronized(this.interruptQueue) {
      this.interruptQueue.add((byte)interrupt);
    }

    for(final int response : responses) {
      this.responseBuffer.add(response);
    }
  }

  private boolean processInterrupt() {
    synchronized(this.interruptQueue) {
      if(!this.interruptQueue.isEmpty() && this.interruptFlag == 0) {
        this.interruptFlag |= this.interruptQueue.remove();
      }
    }

    if((this.interruptFlag & this.interruptEnable) != 0) {
      this.isBusy = false;
      return true;
    }

    return false;
  }

  private void applyVolume(final byte[] rawSector) {
    if(this.mutedAudio) {
      return;
    }

    for(int i = 0; i < rawSector.length; i += 4) {
      final short l = (short)((rawSector[i + 1] & 0xff) << 8 | rawSector[i    ] & 0xff);
      final short r = (short)((rawSector[i + 3] & 0xff) << 8 | rawSector[i + 2] & 0xff);

      final int volumeL = MathHelper.clamp((l * this.audioCdLeftToSpuLeft  >> 7) + (r * this.audioCdRightToSpuLeft  >> 7), -0x8000, 0x7FFF);
      final int volumeR = MathHelper.clamp((l * this.audioCdLeftToSpuRight >> 7) + (r * this.audioCdRightToSpuRight >> 7), -0x8000, 0x7FFF);

      rawSector[i    ] = (byte)(volumeL       & 0xff);
      rawSector[i + 1] = (byte)(volumeL >>> 8 & 0xff);
      rawSector[i + 2] = (byte)(volumeR       & 0xff);
      rawSector[i + 3] = (byte)(volumeR >>> 8 & 0xff);
    }
  }

  private enum INDEX {
    INDEX_0,
    INDEX_1,
    INDEX_2,
    INDEX_3,
    ;

    public static INDEX from(final long index) {
      return INDEX.values()[(int)(index & 0b111)];
    }
  }

  private static class Status {
    /**
     * 0 - Invalid command/parameters (followed by error byte)
     */
    private boolean error;
    /**
     * 1 - 0 = off/spinup, 1 = on
     */
    private boolean spindleMotor = true;
    /**
     * 2 - Followed by error byte
     */
    private boolean seekError;
    /**
     * 3 - GetID denied (also set when Setmode.Bit4=1)
     */
    private boolean idError;
    /**
     * 4 - 0 = closed, 1 = is/was open
     */
    private boolean shellOpen;
    /**
     * 5 - Reading data sectors
     */
    private boolean reading;
    /**
     * 6 - Seeking
     */
    private boolean seeking;
    /**
     * 7 - Playing CD-DA
     */
    private boolean playing;

    private void clear() {
      this.error = false;
      this.spindleMotor = false;
      this.seekError = false;
      this.idError = false;
      this.shellOpen = false;
      this.reading = false;
      this.seeking = false;
      this.playing = false;
    }

    private int pack() {
      if((this.playing ? 1 : 0) + (this.seeking ? 1 : 0) + (this.reading ? 1 : 0) > 1) {
        throw new RuntimeException("Playing, seeking, and reading are mutually exclusive bits");
      }

      return
        (this.error ? 1 : 0) |
        (this.spindleMotor ? 1 : 0) << 1 |
        (this.seekError ? 1 : 0) << 2 |
        (this.idError ? 1 : 0) << 3 |
        (this.shellOpen ? 1 : 0) << 4 |
        (this.reading ? 1 : 0) << 5 |
        (this.seeking ? 1 : 0) << 6 |
        (this.playing ? 1 : 0) << 7;
    }
  }

  private static class SectorSubHeader {
    public byte file;
    public byte channel;
    public byte subMode;
    public byte codingInfo;

    public boolean isEndOfRecord() {
      return (this.subMode & 0x1) != 0;
    }

    public boolean isVideo() {
      return (this.subMode & 0x2) != 0;
    }

    public boolean isAudio() {
      return (this.subMode & 0x4) != 0;
    }

    public boolean isData() {
      return (this.subMode & 0x8) != 0;
    }

    public boolean isTrigger() {
      return (this.subMode & 0x10) != 0;
    }

    public boolean isForm2() {
      return (this.subMode & 0x20) != 0;
    }

    public boolean isRealTime() {
      return (this.subMode & 0x40) != 0;
    }

    public boolean isEndOfFile() {
      return (this.subMode & 0x80) != 0;
    }
  }

  private static final class Register extends Segment {
    private final Map<INDEX, LongSupplier> readCallbacks = new EnumMap<>(INDEX.class);
    private final Map<INDEX, LongConsumer> writeCallbacks = new EnumMap<>(INDEX.class);
    private INDEX index;

    private Register(final long address, final LongSupplier read0, final LongSupplier read1, final LongSupplier read2, final LongSupplier read3, final LongConsumer write0, final LongConsumer write1, final LongConsumer write2, final LongConsumer write3) {
      super(address, 1);
      this.readCallbacks.put(INDEX.INDEX_0, read0);
      this.readCallbacks.put(INDEX.INDEX_1, read1);
      this.readCallbacks.put(INDEX.INDEX_2, read2);
      this.readCallbacks.put(INDEX.INDEX_3, read3);
      this.writeCallbacks.put(INDEX.INDEX_0, write0);
      this.writeCallbacks.put(INDEX.INDEX_1, write1);
      this.writeCallbacks.put(INDEX.INDEX_2, write2);
      this.writeCallbacks.put(INDEX.INDEX_3, write3);
      this.setIndex(INDEX.INDEX_0);
    }

    private void setIndex(final INDEX index) {
      this.index = index;
    }

    @Override
    public byte get(final int offset) {
      return (byte)this.readCallbacks.get(this.index).getAsLong();
    }

    @Override
    public long get(final int offset, final int size) {
      if(size != 1) {
        throw new UnsupportedOperationException("CDROM registers may only be accessed with 8-bit reads and writes");
      }

      return this.get(offset);
    }

    @Override
    public void set(final int offset, final byte value) {
      this.writeCallbacks.get(this.index).accept(value & 0xffL);
    }

    @Override
    public void set(final int offset, final int size, final long value) {
      if(size != 1) {
        throw new UnsupportedOperationException("CDROM registers may only be accessed with 8-bit reads and writes");
      }

      this.set(offset, (byte)value);
    }
  }
}
