package legend.core.spu;

import legend.core.IoHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Counter {            //internal
  public int register;

  public int currentSampleIndex() {
    return this.register >> 12 & 0x1F;
  }

  public void currentSampleIndex(final int value) {
    this.register = (short)(this.register & 0xFFF);
    this.register |= value << 12;
  }

  public int interpolationIndex() {
    return this.register >> 3 & 0xFF;
  }

  public void dump(final OutputStream stream) throws IOException {
    IoHelper.write(stream, this.register);
  }

  public void load(final InputStream stream) throws IOException {
    this.register = IoHelper.readInt(stream);
  }
}
