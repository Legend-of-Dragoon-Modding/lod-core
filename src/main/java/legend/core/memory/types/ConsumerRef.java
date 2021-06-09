package legend.core.memory.types;

import legend.core.memory.Value;

import java.util.function.Consumer;

public class ConsumerRef<T> implements MemoryRef {
  private final Value ref;

  public ConsumerRef(final Value ref) {
    this.ref = ref;

    if(ref.getSize() != 4) {
      throw new IllegalArgumentException("Size of callback refs must be 4");
    }
  }

  public void run(final T val) {
    this.ref.call(val);
  }

  public void set(final Consumer<T> val) {
    this.ref.set(val);
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
