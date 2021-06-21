package legend.core.input;

import java.util.ArrayDeque;
import java.util.Queue;

public abstract class Controller {
  protected Queue<Byte> transferDataFifo = new ArrayDeque<>();
  protected short buttons = (short)0xffff;
  public boolean ack;

  public abstract byte process(byte b);
  public abstract void resetToIdle();

  public void handleJoyPadDown(final GamepadInputsEnum inputCode) {
    this.buttons &= (short)~(this.buttons & (short)inputCode.value);
    //Console.WriteLine(buttons.ToString("x8"));
  }

  public void handleJoyPadUp(final GamepadInputsEnum inputCode) {
    this.buttons |= (short)inputCode.value;
    //Console.WriteLine(buttons.ToString("x8"));
  }
}
