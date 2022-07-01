package legend.core.input;

import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap;
import legend.core.IoHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

public class DigitalController extends Controller {
  private static final Logger LOGGER = LogManager.getFormatterLogger(DigitalController.class);

  private final Byte2ObjectMap<Supplier<Responder>> responders = new Byte2ObjectOpenHashMap<>();
  {
    this.responders.put((byte)1, SimpleResponder::new);
    this.responders.put((byte)0x43, Command43Responder::new);
    this.responders.put((byte)0x44, Command44Responder::new);
    this.responders.put((byte)0x46, Command46Responder::new);
    this.responders.put((byte)0x47, Command47Responder::new);
    this.responders.put((byte)0x4c, Command4cResponder::new);
  }

  private enum Mode {
    IDLE,
    CONNECTED,
    TRANSFERRING,
  }

  private Mode mode = Mode.IDLE;

  @Nullable
  private Responder responder;

  private boolean inConfig;

  @Override
  public void dump(final ByteBuffer stream) {
    super.dump(stream);

    IoHelper.write(stream, this.mode);

    if(this.responder == null) {
      IoHelper.write(stream, (byte)0);
    } else {
      this.responder.dump(stream);
    }

    IoHelper.write(stream, this.inConfig);
  }

  @Override
  public void load(final ByteBuffer stream) {
    super.load(stream);

    this.mode = IoHelper.readEnum(stream, Mode.class);

    final byte responderId = stream.get();
    if(responderId == 0) {
      this.responder = null;
    } else {
      this.responder = this.responders.get(responderId).get();
      this.responder.load(stream);
    }

    this.inConfig = IoHelper.readBool(stream);
  }

  @Override
  public byte process(final byte b) {
    switch(this.mode) {
      case IDLE:
        if(b == 0x01) {
          LOGGER.debug("[Controller] Idle Process 0x01");
          this.mode = Mode.CONNECTED;
          this.ack = true;
          return (byte)0xff;
        }
        LOGGER.error("[Controller] Idle Process Warning: %02x", b);
        assert false;
        this.responder = null;
        this.ack = false;
        return (byte)0xff;

      case CONNECTED:
        switch(b) {
          case 0x42:
            LOGGER.debug("[Controller] Connected Init Transfer Process 0x42");
            this.mode = Mode.TRANSFERRING;
            this.respondToCommand42Init();
            this.ack = true;
            return this.responder.get(b);
          case 0x43:
            LOGGER.debug("[Controller] Entering config mode 0x43");
            this.mode = Mode.TRANSFERRING;
            this.respondToCommand43Config();
            this.ack = true;
            return this.responder.get(b);
          case 0x44: //TODO this doesn't actually do anything
            LOGGER.debug("[Controller] Set analog state 0x44");
            this.mode = Mode.TRANSFERRING;
            this.respondToCommand44SetAnalogState();
            this.ack = true;
            return this.responder.get(b);
          case 0x45:
            LOGGER.debug("[Controller] Get analog state 0x45");
            this.mode = Mode.TRANSFERRING;
            this.respondToCommand45GetAnalogState();
            this.ack = true;
            return this.responder.get(b);
          case 0x46:
            LOGGER.debug("[Controller] Get controller state 0x46");
            this.mode = Mode.TRANSFERRING;
            this.respondToCommand46Unknown();
            this.ack = true;
            return this.responder.get(b);
          case 0x47:
            LOGGER.debug("[Controller] Unknown 0x47");
            this.mode = Mode.TRANSFERRING;
            this.respondToCommand47Unknown();
            this.ack = true;
            return this.responder.get(b);
          case 0x4c:
            LOGGER.debug("[Controller] Unknown 0x4c");
            this.mode = Mode.TRANSFERRING;
            this.respondToCommand4cUnknown();
            this.ack = true;
            return this.responder.get(b);
          case 0x4d: //TODO this doesn't actually do anything
            LOGGER.debug("[Controller] Configuring rumble motors");
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
          LOGGER.debug("[Controller] Changing to idle");
          this.mode = Mode.IDLE;
          this.responder = null;
        }
        LOGGER.debug("[Controller] Transfer Process value: %02x response: %02x ack: %b", b, data, this.ack);
        return data;
      default:
        LOGGER.error("[Controller] This should be unreachable");
        assert false;
        return (byte)0xff;
    }
  }

  //TODO support analog
  private void respondToCommand42Init() {
    this.simpleResponse(
      this.inConfig ? 0xf3 : 0x41, // Digital
      0x5a,
      this.buttons & 0xff,
      this.buttons >>> 8 & 0xff,
      0x00,
      0x00,
      0x00,
      0x00
    );
  }

  //TODO support analog
  private void respondToCommand43Config() {
    this.responder = new Command43Responder();
  }

  private void respondToCommand44SetAnalogState() {
    assert this.inConfig : "Can't use this command when not in config mode";
    this.responder = new Command44Responder();
  }

  private void respondToCommand45GetAnalogState() {
    assert this.inConfig : "Can't use this command when not in config mode";

    this.simpleResponse(
      0xf3,
      0x5a,
      0x01,
      0x02,
      0x00, // TODO analog state
      0x02,
      0x01,
      0x00
    );
  }

  private void respondToCommand46Unknown() {
    assert this.inConfig : "Can't use this command when not in config mode";
    this.responder = new Command46Responder();
  }

  private void respondToCommand47Unknown() {
    assert this.inConfig : "Can't use this command when not in config mode";
    this.responder = new Command47Responder();
  }

  private void respondToCommand4cUnknown() {
    assert this.inConfig : "Can't use this command when not in config mode";
    this.responder = new Command4cResponder();
  }

  private void respondToCommand4dConfigureRumbleMotors() {
    assert this.inConfig : "Can't use this command when not in config mode";

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
    byte id();
    byte get(final byte input);
    boolean hasMore();
    void dump(final ByteBuffer stream);
    void load(final ByteBuffer stream);
  }

  private static class SimpleResponder implements Responder {
    protected int[] responses;
    protected int index;

    @Override
    public byte id() {
      return 1;
    }

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

    @Override
    public void dump(final ByteBuffer stream) {
      IoHelper.write(stream, this.id());
      IoHelper.write(stream, this.responses.length);

      for(final int response : this.responses) {
        IoHelper.write(stream, response);
      }

      IoHelper.write(stream, this.index);
    }

    @Override
    public void load(final ByteBuffer stream) {
      this.responses = new int[IoHelper.readInt(stream)];

      for(int i = 0; i < this.responses.length; i++) {
        this.responses[i] = IoHelper.readInt(stream);
      }

      this.index = IoHelper.readInt(stream);
    }
  }

  private class Command43Responder extends SimpleResponder {
    @Override
    public byte id() {
      return 0x43;
    }

    public Command43Responder() {
      super(
        DigitalController.this.inConfig ? 0xf3 : 0x41, // Digital
        0x5a,
        DigitalController.this.inConfig ? 0x00 : DigitalController.this.buttons & 0xff,
        DigitalController.this.inConfig ? 0x00 : DigitalController.this.buttons >>> 8 & 0xff,
        0x00,
        0x00,
        0x00,
        0x00
      );
    }

    @Override
    public byte get(final byte input) {
      if(this.index == 2) {
        if(input == 0) {
          LOGGER.debug("Exiting config mode");
          DigitalController.this.inConfig = false;
        } else {
          LOGGER.debug("Entering config mode");
          DigitalController.this.inConfig = true;
        }
      }

      return super.get(input);
    }
  }

  private static class Command44Responder extends SimpleResponder {
    @Override
    public byte id() {
      return 0x44;
    }

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
        //TODO
        LOGGER.info("[CONTROLLER] Analog mode %b", input == 1);
      }

      if(this.index == 3 && (input == 2 || input == 3)) {
        //TODO
        LOGGER.info("[CONTROLLER] Analog locked %b", input == 3);
      }

      return super.get(input);
    }
  }

  private static class Command46Responder extends SimpleResponder {
    @Override
    public byte id() {
      return 0x46;
    }

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
          this.responses[4] = 0x01;
          this.responses[5] = 0x02;
          this.responses[6] = 0x00;
          this.responses[7] = 0x0a;
        } else {
          this.responses[4] = 0x01;
          this.responses[5] = 0x01;
          this.responses[6] = 0x01;
          this.responses[7] = 0x14;
        }
      }

      return super.get(input);
    }
  }

  private static class Command47Responder extends SimpleResponder {
    @Override
    public byte id() {
      return 0x47;
    }

    public Command47Responder() {
      super(
        0xf3,
        0x5a,
        0x00,
        0x00,
        0x02, // Variable
        0x00, // Variable
        0x01, // Variable
        0x00  // Variable
      );
    }

    @Override
    public byte get(final byte input) {
      if(this.index == 2) {
        if(input != 0) {
          this.responses[4] = 0x00;
          this.responses[5] = 0x00;
          this.responses[6] = 0x00;
          this.responses[7] = 0x00;
        }
      }

      return super.get(input);
    }
  }

  private static class Command4cResponder extends SimpleResponder {
    @Override
    public byte id() {
      return 0x4c;
    }

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
        this.responses[5] = input == 0 ? 0x4 : 0x7;
      }

      return super.get(input);
    }
  }
}
