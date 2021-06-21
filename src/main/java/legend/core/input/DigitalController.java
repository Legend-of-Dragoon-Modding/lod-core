package legend.core.input;

public class DigitalController extends Controller {
  private final short CONTROLLER_TYPE = 0x5A41; //digital

  private enum Mode {
    Idle,
    Connected,
    Transferring,
  }

  private Mode mode = Mode.Idle;

  @Override
  public byte process(final byte b) {
    switch(this.mode) {
      case Idle:
        switch(b) {
          case 0x01:
            //Console.WriteLine("[Controller] Idle Process 0x01");
            this.mode = Mode.Connected;
            this.ack = true;
            return (byte)0xFF;
          default:
            //Console.WriteLine($"[Controller] Idle Process Warning: {b:x2}");
            this.transferDataFifo.clear();
            this.ack = false;
            return (byte)0xFF;
        }

      case Connected:
        switch(b) {
          case 0x42:
            //Console.WriteLine("[Controller] Connected Init Transfer Process 0x42");
            this.mode = Mode.Transferring;
            this.generateResponse();
            this.ack = true;
            return this.transferDataFifo.remove();
          default:
            //Console.WriteLine("[Controller] Connected Transfer Process unknow command {b:x2} RESET TO IDLE");
            this.mode = Mode.Idle;
            this.transferDataFifo.clear();
            this.ack = false;
            return (byte)0xFF;
        }

      case Transferring:
        final byte data = this.transferDataFifo.remove();
        this.ack = !this.transferDataFifo.isEmpty();
        if(!this.ack) {
          //Console.WriteLine("[Controller] Changing to idle");
          this.mode = Mode.Idle;
        }
        //Console.WriteLine($"[Controller] Transfer Process value:{b:x2} response: {data:x2} queueCount: {transferDataFifo.Count} ack: {ack}");
        return data;
      default:
        //Console.WriteLine("[Controller] This should be unreachable");
        return (byte)0xFF;
    }
  }

  public void generateResponse() {
    final byte b0 = (byte)(this.CONTROLLER_TYPE & 0xFF);
    final byte b1 = (byte)(this.CONTROLLER_TYPE >>> 8 & 0xFF);

    final byte b2 = (byte)(this.buttons & 0xFF);
    final byte b3 = (byte)(this.buttons >>> 8 & 0xFF);

    this.transferDataFifo.add(b0);
    this.transferDataFifo.add(b1);
    this.transferDataFifo.add(b2);
    this.transferDataFifo.add(b3);
  }

  @Override
  public void resetToIdle() {
    this.mode = Mode.Idle;
  }
}
