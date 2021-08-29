package legend.core.input;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;

public class DigitalController extends Controller {
  private static final Logger LOGGER = LogManager.getFormatterLogger(DigitalController.class);

  private enum Mode {
    IDLE,
    CONNECTED,
    TRANSFERRING,
  }

  private Mode mode = Mode.IDLE;

  @Nullable
  private Responder responder;

  @Override
  public byte process(final byte b) {
    switch(this.mode) {
      case IDLE:
        switch(b) {
          case 0x01:
            LOGGER.error("[Controller] Idle Process 0x01");
            this.mode = Mode.CONNECTED;
            this.ack = true;
            return (byte)0xff;
          default:
            LOGGER.error("[Controller] Idle Process Warning: %02x", b);
            assert false;
            this.responder = null;
            this.ack = false;
            return (byte)0xff;
        }

      case CONNECTED:
        switch(b) {
          case 0x42:
            LOGGER.error("[Controller] Connected Init Transfer Process 0x42");
            this.mode = Mode.TRANSFERRING;
            this.respondToCommand42Init();
            this.ack = true;
            return this.responder.get(b);
          case 0x43:
            LOGGER.error("[Controller] Entering config mode 0x43");
            this.mode = Mode.TRANSFERRING;
            this.respondToCommand43Config();
            this.ack = true;
            return this.responder.get(b);
          case 0x44: //TODO this doesn't actually do anything
            LOGGER.error("[Controller] Set analog state 0x44");
            this.mode = Mode.TRANSFERRING;
            this.respondToCommand44SetAnalogState();
            this.ack = true;
            return this.responder.get(b);
          case 0x45:
            LOGGER.error("[Controller] Get controller state 0x45");
            this.mode = Mode.TRANSFERRING;
            this.respondToCommand45GetControllerState();
            this.ack = true;
            return this.responder.get(b);
          case 0x46:
            LOGGER.error("[Controller] Get controller state 0x46");
            this.mode = Mode.TRANSFERRING;
            this.respondToCommand46Unknown();
            this.ack = true;
            return this.responder.get(b);
          case 0x47:
            LOGGER.error("[Controller] Unknown 0x47");
            this.mode = Mode.TRANSFERRING;
            this.respondToCommand47Unknown();
            this.ack = true;
            return this.responder.get(b);
          case 0x4c:
            LOGGER.error("[Controller] Unknown 0x4c");
            this.mode = Mode.TRANSFERRING;
            this.respondToCommand4cUnknown();
            this.ack = true;
            return this.responder.get(b);
          case 0x4d: //TODO this doesn't actually do anything
            LOGGER.error("[Controller] Configuring rumble motors");
            this.mode = Mode.TRANSFERRING;
            this.respondToCommand4dConfigureRumbleMotors();
            this.ack = true;
            return this.responder.get(b);
          default:
            LOGGER.error("[Controller] Connected Transfer Process unknown command %02x RESET TO IDLE", b);
            assert false;
            this.mode = Mode.IDLE;
            this.responder = null;
            this.ack = false;
            return (byte)0xff;
        }

      case TRANSFERRING:
        final byte data = this.responder.get(b);
        this.ack = this.responder.hasMore();
        if(!this.ack) {
          LOGGER.error("[Controller] Changing to idle");
          this.mode = Mode.IDLE;
          this.responder = null;
        }
        LOGGER.error("[Controller] Transfer Process value: %02x response: %02x ack: %b", b, data, this.ack);
        return data;
      default:
        LOGGER.error("[Controller] This should be unreachable");
        assert false;
        return (byte)0xff;
    }
  }

  //TODO support digital
  private void respondToCommand42Init() {
    this.simpleResponse(
      0x41, // Digital
      0x5a,
      this.buttons & 0xff,
      this.buttons >>> 8 & 0xff,
      0x00,
      0x00,
      0x00,
      0x00
    );
  }

  //TODO support digital
  private void respondToCommand43Config() {
    this.simpleResponse(
      0xf3,
      0x5a,
      this.buttons & 0xff,
      this.buttons >>> 8 & 0xff,
      0x00,
      0x00,
      0x00,
      0x00
    );
  }

  private void respondToCommand44SetAnalogState() {
    this.responder = new Command44Responder();
  }

  private void respondToCommand45GetControllerState() {
    this.simpleResponse(
      0xf3,
      0x5a,
      0x01,
      0x02,
      0x01, // LED on (dunno if this should be on or off or if it even matters)
      0x02,
      0x01,
      0x00
    );
  }

  private void respondToCommand46Unknown() {
    this.responder = new Command46Responder();
  }

  private void respondToCommand47Unknown() {
    this.simpleResponse(
      0xf3,
      0x5a,
      0x00,
      0x00,
      0x02,
      0x00,
      0x01,
      0x00
    );
  }

  private void respondToCommand4cUnknown() {
    this.responder = new Command4cResponder();
  }

  private void respondToCommand4dConfigureRumbleMotors() {
    this.simpleResponse(
      0xf3,
      0x5a,
      0x00,
      0x00,
      0x00,
      0x00,
      0x00,
      0x00
    );
  }

  @Override
  public void resetToIdle() {
    this.mode = Mode.IDLE;
  }

  private void simpleResponse(final int... responses) {
    this.responder = new SimpleResponder(responses);
  }

  private interface Responder {
    byte get(final byte input);
    boolean hasMore();
  }

  private static class SimpleResponder implements Responder {
    protected final int[] responses;
    protected int index;

    public SimpleResponder(final int... responses) {
      this.responses = responses;
    }

    @Override
    public byte get(final byte input) {
      return (byte)this.responses[this.index++];
    }

    @Override
    public boolean hasMore() {
      return this.index < this.responses.length;
    }
  }

  private static class Command44Responder extends SimpleResponder {
    private boolean analog;

    public Command44Responder() {
      super(
        0xf3,
        0x5a,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00
      );
    }

    @Override
    public byte get(final byte input) {
      if(this.index == 2) {
        this.analog = input != 0;
      }

      if(this.index == 3) {
        LOGGER.info("[CONTROLLER] Analog mode %b", this.analog);
      }

      return super.get(input);
    }
  }

  private static class Command46Responder extends SimpleResponder {
    private int response4;
    private int response5;
    private int response6;
    private int response7;

    public Command46Responder() {
      super(
        0xf3,
        0x5a,
        0x00,
        0x00,
        0x00, // Variable
        0x00, // Variable
        0x00, // Variable
        0x00  // Variable
      );
    }

    @Override
    public byte get(final byte input) {
      if(this.index == 2) {
        if(input == 0) {
          this.response4 = 0x01;
          this.response5 = 0x02;
          this.response6 = 0x00;
          this.response7 = 0x0a;
        } else {
          this.response4 = 0x01;
          this.response5 = 0x01;
          this.response6 = 0x01;
          this.response7 = 0x14;
        }
      }

      if(this.index == 4) {
        return (byte)this.response4;
      }

      if(this.index == 5) {
        return (byte)this.response5;
      }

      if(this.index == 6) {
        return (byte)this.response6;
      }

      if(this.index == 7) {
        return (byte)this.response7;
      }

      return super.get(input);
    }
  }

  private static class Command4cResponder extends SimpleResponder {
    private int response5;

    public Command4cResponder() {
      super(
        0xf3,
        0x5a,
        0x00,
        0x00,
        0x00,
        0x00, // Variable
        0x00,
        0x00
      );
    }

    @Override
    public byte get(final byte input) {
      if(this.index == 2) {
        this.response5 = input == 0 ? 0x4 : 0x7;
      }

      if(this.index == 5) {
        return (byte)this.response5;
      }

      return super.get(input);
    }
  }
}
