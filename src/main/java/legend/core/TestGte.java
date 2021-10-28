package legend.core;

import legend.core.gte.Gte;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;

public final class TestGte {
  private TestGte() { }

  private static final Logger LOGGER = LogManager.getFormatterLogger(TestGte.class);

  public static void main(final String[] args) throws IOException {
    final Gte gte = new Gte();

    final FileInputStream input = new FileInputStream("gte_tests");
    final byte[] data = new byte[129*4];

    while(input.read(data) >= data.length) {
      final long command = MathHelper.get(data, 0, 4);

      LOGGER.error("Testing command %08x...", command);

      for(int i = 0; i < 32; i++) {
        if(i != 15 && i != 28 && i != 29 && i != 31) {
          gte.writeData(i, (int)MathHelper.get(data, (1 + i) * 4, 4));
        }

        gte.writeControl(i, (int)MathHelper.get(data, (33 + i) * 4, 4));
      }

      gte.execute((int)command);

      for(int i = 0; i < 32; i++) {
        if(i != 28 && i != 29 && i != 31) {
          final int expected = (int)MathHelper.get(data, (65 + i) * 4, 4);
          final int actual = gte.loadData(i);

          if(actual != expected) {
            LOGGER.error("%08x - data register %d expected %08x, got %08x", command, i, expected, actual);
          }
        }

        final int expected = (int)MathHelper.get(data, (97 + i) * 4, 4);
        final int actual = (int)gte.loadControl(i);

        if(actual != expected) {
          LOGGER.error("%08x - control register %d expected %08x, got %08x", command, i, expected, actual);
        }
      }
    }
  }
}
