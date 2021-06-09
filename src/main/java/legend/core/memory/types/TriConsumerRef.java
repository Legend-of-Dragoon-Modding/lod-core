package legend.core.memory.types;

import legend.core.memory.Value;
import legend.core.memory.types.MemoryRef;
import org.apache.logging.log4j.util.TriConsumer;

public class TriConsumerRef<T, U, V> implements MemoryRef {
  private final Value ref;

  public TriConsumerRef(final Value ref) {
    this.ref = ref;

    if(ref.getSize() != 4) {
      throw new IllegalArgumentException("Size of callback refs must be 4");
    }
  }

  public void run(final T t, final U u, final V v) {
    this.ref.call(t, u, v);
  }

  public void set(final TriConsumer<T, U, V> val) {
    this.ref.set(val);
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
