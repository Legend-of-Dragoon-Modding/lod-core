package legend.core.input;

import legend.core.IoHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;

public abstract class Controller {
  private static final Logger LOGGER = LogManager.getFormatterLogger(Controller.class);

  protected short buttons = (short)0xffff;
  public boolean ack;

  public abstract byte process(byte b);
  public abstract void resetToIdle();

  public void handleJoyPadDown(final GamepadInputsEnum inputCode) {
    this.buttons &= (short)~(this.buttons & (short)inputCode.value);
    LOGGER.error("[JOYPAD] Button down %s (current button code: %08x)", inputCode, this.buttons);
  }

  public void handleJoyPadUp(final GamepadInputsEnum inputCode) {
    this.buttons |= (short)inputCode.value;
    LOGGER.error("[JOYPAD] Button up %s (current button code: %08x)", inputCode, this.buttons);
  }

  public void dump(final ByteBuffer stream) {
    IoHelper.write(stream, this.buttons);
    IoHelper.write(stream, this.ack);
  }

  public void load(final ByteBuffer stream) {
    this.buttons = IoHelper.readShort(stream);
    this.ack = IoHelper.readBool(stream);
  }
}
