package legend.core.memory.types;

import legend.core.Hardware;
import legend.core.memory.Value;

import java.util.function.Function;

public interface MemoryRef {
  long getAddress();

  default <T extends MemoryRef> T reinterpret(final Function<Value, T> constructor) {
    return constructor.apply(Hardware.MEMORY.ref(1, this.getAddress()));
  }
}
