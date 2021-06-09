package legend.core.memory.types;

import legend.core.memory.Value;
import legend.core.memory.types.MemoryRef;

import java.util.function.Supplier;

public class SupplierRef<T> implements MemoryRef {
  private final Value ref;

  public SupplierRef(final Value ref) {
    this.ref = ref;

    if(ref.getSize() != 4) {
      throw new IllegalArgumentException("Size of callback refs must be 4");
    }
  }

  public T run() {
    //noinspection unchecked
    return (T)this.ref.call();
  }

  public void set(final Supplier<T> val) {
    this.ref.set(val);
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
