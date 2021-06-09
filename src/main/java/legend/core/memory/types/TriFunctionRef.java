package legend.core.memory.types;

import legend.core.memory.Value;
import legend.core.memory.types.MemoryRef;

public class TriFunctionRef<T, U, V, R> implements MemoryRef {
  private final Value ref;

  public TriFunctionRef(final Value ref) {
    this.ref = ref;

    if(ref.getSize() != 4) {
      throw new IllegalArgumentException("Size of callback refs must be 4");
    }
  }

  public R run(final T t, final U u, final V v) {
    //noinspection unchecked
    return (R)this.ref.call(t, u, v);
  }

  public void set(final Value.TriFunction<T, U, V, R> val) {
    this.ref.set(val);
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
