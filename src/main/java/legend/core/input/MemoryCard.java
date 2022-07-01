package legend.core.input;

import legend.core.IoHelper;
import legend.core.memory.Value;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static legend.core.Hardware.MEMORY;

public class MemoryCard {
  private static final Logger LOGGER = LogManager.getFormatterLogger(MemoryCard.class);

  public static final Value JOY_MCD_DATA = MEMORY.ref(4, 0x1f801040L);
  public static final Value JOY_MCD_STAT = MEMORY.ref(4, 0x1f801044L);
  public static final Value JOY_MCD_MODE = MEMORY.ref(2, 0x1f801048L);
  public static final Value JOY_MCD_CTRL = MEMORY.ref(2, 0x1f80104aL);
  public static final Value JOY_MCD_BAUD = MEMORY.ref(2, 0x1f80104eL);

  //emulating a 3rd party one as it seems easier to and 0x3FF bad address than to handle the
  //original memcard badAddress 0xFFFF error and the IdCommand
  final byte MEMORY_CARD_ID_1 = 0x5A;
  final byte MEMORY_CARD_ID_2 = 0x5D;
  final byte MEMORY_CARD_COMMAND_ACK_1 = 0x5C;
  final byte MEMORY_CARD_COMMAND_ACK_2 = 0x5D;
  private byte[] memory = new byte[128 * 1024]; //Standard memcard 128KB
  public boolean ack;

  //FLAG
  //only bit 2 (isError) and 3 (isNotReaded) seems documented
  //bit 5 is useless for non sony memcards, default value is 0x80
  private byte flag = 0x8;
  private static final byte FLAG_ERROR = 0x4;
  private static final byte FLAG_NOT_READ = 0x8;

  private byte addressMSB;
  private byte addressLSB;
  private short address;

  private byte checksum;
  private int readPointer;
  private byte endTransfer;

  private final String memCardFilePath = "./memcard.mcr";

  private enum Mode {
    IDLE,
    TRANSFER,
  }

  private Mode mode = Mode.IDLE;

  private enum TransferMode {
    READ,
    WRITE,
    ID,
    UNDEFINED,
  }

  private TransferMode transferMode = TransferMode.UNDEFINED;

  public MemoryCard() {
    try {
      this.memory = Files.readAllBytes(Path.of(this.memCardFilePath));
      LOGGER.info("[MEMCARD] File found. Loaded Contents.");
    } catch(final Exception e) {
      LOGGER.info("[MEMCARD] No Card found - will try to generate one on save");
    }
  }

  //This should be handled with some early response and post address queues but atm its easier to handle as a state machine
  public byte process(final byte value) {
    LOGGER.info("[MemCard] rawProcess %02x previous ack %b", value, this.ack);
    switch(this.transferMode) {
      case READ:
        return this.readMemory(value);
      case WRITE:
        return this.writeMemory(value);
      case ID:
        return (byte)0xff;
    }

    switch(this.mode) {
      case IDLE:
        switch(value) {
          case (byte)0x81 -> {
            LOGGER.info("[MemCard] Idle Process 0x81");
            this.mode = Mode.TRANSFER;
            this.ack = true;
            return (byte)0xff;
          }
          default -> {
            LOGGER.warn("[MemCard] Idle value WARNING %02x", value);
            this.ack = false;
            return (byte)0xff;
          }
        }

      case TRANSFER:
        switch(value) {
          case 0x52 -> { //Read
            LOGGER.info("[MemCard] Read Process 0x52");
            this.transferMode = TransferMode.READ;
          }
          case 0x57 -> { //Write
            LOGGER.info("[MemCard] Write Process 0x57");
            this.transferMode = TransferMode.WRITE;
          }
          case 0x53 -> { //ID
            LOGGER.info("[MemCard] ID Process 0x53");
            this.transferMode = TransferMode.UNDEFINED;
          }
          default -> {
            LOGGER.info("[MemCard] Unhandled Transfer Process %02x", value);
            this.transferMode = TransferMode.UNDEFINED;
            this.ack = false;
            return (byte)0xff;
          }
        }
        final byte prevFlag = this.flag;
        this.ack = true;
        this.flag &= ~FLAG_ERROR;
        return prevFlag;

      default:
        LOGGER.warn("[[MemCard]] Unreachable Mode Warning");
        this.ack = false;
        return (byte)0xff;
    }
  }

  public void resetToIdle() {
    this.readPointer = 0;
    this.transferMode = TransferMode.UNDEFINED;
    this.mode = Mode.IDLE;
  }

  public void directRead(final int sector, final long dest, final int sectors) {
    MEMORY.setBytes(dest, this.memory, sector * 0x80, sectors * 0x80);
  }

  public void directRead(final int sector, final long dest) {
    this.directRead(sector, dest, 1);
  }

  public void directWrite(final int sector, final long src) {
    MEMORY.getBytes(src, this.memory, sector * 0x80, 0x80);
  }

  /*  Reading Data from Memory Card
      Send Reply Comment
      81h  N/A   Memory Card Access (unlike 01h=Controller access), dummy response
      52h  FLAG  Send Read Command (ASCII "R"), Receive FLAG Byte

      00h  5Ah   Receive Memory Card ID1
      00h  5Dh   Receive Memory Card ID2
      MSB  (00h) Send Address MSB  ;\sector number (0..3FFh)
      LSB  (pre) Send Address LSB  ;/
      00h  5Ch   Receive Command Acknowledge 1  ;<-- late /ACK after this byte-pair
      00h  5Dh   Receive Command Acknowledge 2
      00h  MSB   Receive Confirmed Address MSB
      00h  LSB   Receive Confirmed Address LSB
      00h  ...   Receive Data Sector (128 bytes)
      00h  CHK   Receive Checksum (MSB xor LSB xor Data bytes)
      00h  47h   Receive Memory End Byte (should be always 47h="G"=Good for Read)
  */
  private byte readMemory(final byte value) {
    LOGGER.info("[MemCard] readMemory pointer: %x value: %02x ack %b", this.readPointer, value, this.ack);
    this.ack = true;
    switch(this.readPointer++) {
      case 0:
        return this.MEMORY_CARD_ID_1;
      case 1:
        return this.MEMORY_CARD_ID_2;
      case 2:
        this.addressMSB = (byte)(value & 0x3);
        return 0;
      case 3:
        this.addressLSB = value;
        this.address = (short)((this.addressMSB & 0xff) << 8 | this.addressLSB & 0xff);
        this.checksum = (byte)(this.addressMSB & 0xff ^ this.addressLSB & 0xff);
        return 0;
      case 4:
        return this.MEMORY_CARD_COMMAND_ACK_1;
      case 5:
        return this.MEMORY_CARD_COMMAND_ACK_2;
      case 6:
        return this.addressMSB;
      case 7:
        return this.addressLSB;
      //sector frame ended after 128 bytes, handle checksum and finish
      case 8 + 128:
        return this.checksum;
      case 9 + 128:
        this.transferMode = TransferMode.UNDEFINED;
        this.mode = Mode.IDLE;
        this.readPointer = 0;
        this.ack = false;
        return 0x47;
      default:
        //from here handle the 128 bytes of the read sector frame
        if(this.readPointer - 1 >= 8 && this.readPointer - 1 < 8 + 128) {
          final byte data = this.memory[(this.address & 0xffff) * 128 + this.readPointer - 1 - 8];
          LOGGER.info("Read readPointer %x data %x", this.readPointer - 1, data);
          this.checksum ^= data;
          return data;
        }

        LOGGER.info("[MemCard] Unreachable! %08x", this.readPointer - 1);
        this.transferMode = TransferMode.UNDEFINED;
        this.mode = Mode.IDLE;
        this.readPointer = 0;
        this.ack = false;
        return (byte)0xff;
    }
  }

  /*  Writing Data to Memory Card
      Send Reply Comment
      81h  N/A   Memory Card Access (unlike 01h=Controller access), dummy response
      57h  FLAG  Send Write Command (ASCII "W"), Receive FLAG Byte

      00h  5Ah   Receive Memory Card ID1
      00h  5Dh   Receive Memory Card ID2
      MSB  (00h) Send Address MSB  ;\sector number (0..3FFh)
      LSB  (pre) Send Address LSB  ;/
      ...  (pre) Send Data Sector (128 bytes)
      CHK  (pre) Send Checksum (MSB xor LSB xor Data bytes)
      00h  5Ch   Receive Command Acknowledge 1
      00h  5Dh   Receive Command Acknowledge 2
      00h  4xh   Receive Memory End Byte (47h=Good, 4Eh=BadChecksum, FFh=BadSector)
  */
  private byte writeMemory(final byte value) {
    LOGGER.info("[MemCard] writeMemory pointer: %x value: %02x ack %b", this.readPointer, value, this.ack);
    switch(this.readPointer++) {
      case 0:
        return this.MEMORY_CARD_ID_1;
      case 1:
        return this.MEMORY_CARD_ID_2;
      case 2:
        this.addressMSB = value;
        return 0;
      case 3:
        this.addressLSB = value;
        this.address = (short)((this.addressMSB & 0xff) << 8 | this.addressLSB & 0xff);
        this.endTransfer = 0x47; //47h=Good

        if(this.address > 0x3ff) {
          this.flag |= FLAG_ERROR;
          this.endTransfer = (byte)0xff; //FFh = BadSector
          this.address &= 0x3ff;
          this.addressMSB &= 0x3;
        }
        this.checksum = (byte)(this.addressMSB & 0xff ^ this.addressLSB & 0xff);
        return 0;
      //sector frame ended after 128 bytes, handle checksum and finish
      case 4 + 128:
        if(this.checksum == value) {
          LOGGER.info("MemCard Write CHECKSUM OK was: %02x expected: %02x", this.checksum, value);
        } else {
          LOGGER.info("MemCard Write CHECKSUM WRONG was: %02x expected: %02x", this.checksum, value);
          this.flag |= FLAG_ERROR;
        }
        return 0;
      case 5 + 128:
        return this.MEMORY_CARD_COMMAND_ACK_1;
      case 6 + 128:
        return this.MEMORY_CARD_COMMAND_ACK_2;
      case 7 + 128:
        LOGGER.info("End WRITE Transfer with code %02x", this.endTransfer);
        this.transferMode = TransferMode.UNDEFINED;
        this.mode = Mode.IDLE;
        this.readPointer = 0;
        this.ack = false;
        this.flag &= ~FLAG_NOT_READ;
        this.handleSave();
        return this.endTransfer;

      default:
        //from here handle the 128 bytes of the read sector frame
        if(this.readPointer - 1 >= 4 && this.readPointer - 1 < 4 + 128) {
          LOGGER.info("Write readPointer %x value %02x", this.readPointer - 1, value);
          this.memory[(this.address & 0xffff) * 128 + this.readPointer - 1 - 4] = value;
          this.checksum ^= value;
          return 0;
        }

        LOGGER.warn("WARNING DEFAULT Write Memory read pointer ws %x", this.readPointer);
        this.transferMode = TransferMode.UNDEFINED;
        this.mode = Mode.IDLE;
        this.readPointer = 0;
        this.ack = false;
        return (byte)0xff;
    }
  }

  private void handleSave() {
    try {
      Files.write(Path.of(this.memCardFilePath), this.memory);
      LOGGER.info("[MemCard] Saved");
    } catch(final Exception e) {
      LOGGER.error("[MemCard] Error trying to save memCard file", e);
    }
  }

  /*  Get Memory Card ID Command
      Send Reply Comment
      81h  N/A   Memory Card Access (unlike 01h=Controller access), dummy response
      53h  FLAG  Send Get ID Command (ASCII "S"), Receive FLAG Byte

      00h  5Ah   Receive Memory Card ID1
      00h  5Dh   Receive Memory Card ID2
      00h  5Ch   Receive Command Acknowledge 1
      00h  5Dh   Receive Command Acknowledge 2
      00h  04h   Receive 04h
      00h  00h   Receive 00h
      00h  00h   Receive 00h
      00h  80h   Receive 80h
  */
  private byte idMemory(final byte value) {
    LOGGER.warn("[MEMORY CARD] WARNING Id UNHANDLED COMMAND");
    //Console.ReadLine();
    this.transferMode = TransferMode.UNDEFINED;
    this.mode = Mode.IDLE;
    return (byte)0xff;
  }

  public void dump(final ByteBuffer stream) {
    stream.put(this.memory);

    IoHelper.write(stream, this.ack);

    IoHelper.write(stream, this.flag);

    IoHelper.write(stream, this.addressMSB);
    IoHelper.write(stream, this.addressLSB);
    IoHelper.write(stream, this.address);

    IoHelper.write(stream, this.checksum);
    IoHelper.write(stream, this.readPointer);
    IoHelper.write(stream, this.endTransfer);

    IoHelper.write(stream, this.mode);

    IoHelper.write(stream, this.transferMode);
  }

  public void load(final ByteBuffer stream) {
    stream.get(this.memory);

    this.ack = IoHelper.readBool(stream);

    this.flag = IoHelper.readByte(stream);

    this.addressMSB = IoHelper.readByte(stream);
    this.addressLSB = IoHelper.readByte(stream);
    this.address = IoHelper.readShort(stream);

    this.checksum = IoHelper.readByte(stream);
    this.readPointer = IoHelper.readInt(stream);
    this.endTransfer = IoHelper.readByte(stream);

    this.mode = IoHelper.readEnum(stream, Mode.class);

    this.transferMode = IoHelper.readEnum(stream, TransferMode.class);
  }
}
