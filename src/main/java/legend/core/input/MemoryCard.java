package legend.core.input;

import legend.core.memory.Value;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
  byte MEMORY_CARD_ID_1 = 0x5A;
  byte MEMORY_CARD_ID_2 = 0x5D;
  byte MEMORY_CARD_COMMAND_ACK_1 = 0x5C;
  byte MEMORY_CARD_COMMAND_ACK_2 = 0x5D;
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
    Idle,
    Transfer,
  }

  private Mode mode = Mode.Idle;

  private enum TransferMode {
    Read,
    Write,
    Id,
    Undefined,
  }

  private TransferMode transferMode = TransferMode.Undefined;

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
    //return 0xFF;
    //Console.WriteLine($"[MemCard] rawProcess {value:x2} previous ack {ack}");
    switch(this.transferMode) {
      case Read:
        return this.readMemory(value);
      case Write:
        return this.writeMemory(value);
      case Id:
        return (byte)0xff;
    }

    switch(this.mode) {
      case Idle:
        switch(value) {
          case (byte)0x81 -> {
            //Console.WriteLine("[MemCard] Idle Process 0x81");
            this.mode = Mode.Transfer;
            this.ack = true;
            return (byte)0xFF;
          }
          default -> {
            //Console.WriteLine("[MemCard] Idle value WARNING " + value);
            this.ack = false;
            return (byte)0xFF;
          }
        }

      case Transfer:
        switch(value) {
          case 0x52 -> //Read
            //Console.WriteLine("[MemCard] Read Process 0x52");
            this.transferMode = TransferMode.Read;
          case 0x57 -> //Write
            //Console.WriteLine("[MemCard] Write Process 0x57");
            this.transferMode = TransferMode.Write;
          case 0x53 -> //ID
            //Console.WriteLine("[MemCard] ID Process 0x53");
            this.transferMode = TransferMode.Undefined;
          default -> {
            //Console.WriteLine($"[MemCard] Unhandled Transfer Process {value:x2}");
            this.transferMode = TransferMode.Undefined;
            this.ack = false;
            return (byte)0xFF;
          }
        }
        final byte prevFlag = this.flag;
        this.ack = true;
        this.flag &= ~FLAG_ERROR;
        return prevFlag;

      default:
        //Console.WriteLine("[[MemCard]] Unreachable Mode Warning");
        this.ack = false;
        return (byte)0xFF;
    }
  }

  public void resetToIdle() {
    this.readPointer = 0;
    this.transferMode = TransferMode.Undefined;
    this.mode = Mode.Idle;
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
    //Console.WriteLine($"[MemCard] readMemory pointer: {readPointer} value: {value:x2} ack {ack}");
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
        this.address = (short)(this.addressMSB << 8 | this.addressLSB);
        this.checksum = (byte)(this.addressMSB ^ this.addressLSB);
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
        this.transferMode = TransferMode.Undefined;
        this.mode = Mode.Idle;
        this.readPointer = 0;
        this.ack = false;
        return 0x47;
      default:
        //from here handle the 128 bytes of the read sector frame
        if(this.readPointer - 1 >= 8 && this.readPointer - 1 < 8 + 128) {
          //Console.WriteLine($"Read readPointer {readPointer - 1} index {index}");
          final byte data = this.memory[this.address * 128 + this.readPointer - 1 - 8];
          this.checksum ^= data;
          return data;
        }

        LOGGER.info("[MemCard] Unreachable! %08x", this.readPointer - 1);
        this.transferMode = TransferMode.Undefined;
        this.mode = Mode.Idle;
        this.readPointer = 0;
        this.ack = false;
        return (byte)0xFF;
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
    //Console.WriteLine($"[MemCard] writeMemory pointer: {readPointer} value: {value:x2} ack {ack}");
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
        this.address = (short)(this.addressMSB << 8 | this.addressLSB);
        this.endTransfer = 0x47; //47h=Good

        if(this.address > 0x3FF) {
          this.flag |= FLAG_ERROR;
          this.endTransfer = (byte)0xFF; //FFh = BadSector
          this.address &= 0x3FF;
          this.addressMSB &= 0x3;
        }
        this.checksum = (byte)(this.addressMSB ^ this.addressLSB);
        return 0;
      //sector frame ended after 128 bytes, handle checksum and finish
      case 4 + 128:
        if(this.checksum == value) {
          //Console.WriteLine($"MemCard Write CHECKSUM OK was: {checksum:x2} expected: {value:x2}");
        } else {
          //Console.WriteLine($"MemCard Write CHECKSUM WRONG was: {checksum:x2} expected: {value:x2}");
          this.flag |= FLAG_ERROR;
        }
        return 0;
      case 5 + 128:
        return this.MEMORY_CARD_COMMAND_ACK_1;
      case 6 + 128:
        return this.MEMORY_CARD_COMMAND_ACK_2;
      case 7 + 128:
        //Console.WriteLine($"End WRITE Transfer with code {endTransfer:x2}");
        this.transferMode = TransferMode.Undefined;
        this.mode = Mode.Idle;
        this.readPointer = 0;
        this.ack = false;
        this.flag &= ~FLAG_NOT_READ;
        this.handleSave();
        return this.endTransfer;

      default:
        //from here handle the 128 bytes of the read sector frame
        if(this.readPointer - 1 >= 4 && this.readPointer - 1 < 4 + 128) {
          //Console.WriteLine($"Write readPointer {readPointer - 1} index {index} value {value:x2}");
          this.memory[this.address * 128 + this.readPointer - 1 - 4] = value;
          this.checksum ^= value;
          return 0;
        }

        //Console.WriteLine($"WARNING DEFAULT Write Memory read pointer ws {readPointer}");
        this.transferMode = TransferMode.Undefined;
        this.mode = Mode.Idle;
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
      LOGGER.info("[MemCard] Error trying to save memCard file", e);
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
    LOGGER.info("[MEMORY CARD] WARNING Id UNHANDLED COMMAND");
    //Console.ReadLine();
    this.transferMode = TransferMode.Undefined;
    this.mode = Mode.Idle;
    return (byte)0xff;
  }
}
