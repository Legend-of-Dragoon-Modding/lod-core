package legend.core.memory.types;

import legend.core.memory.Value;
import legend.core.memory.types.MemoryRef;

public class QuadFunctionRef<T, U, V, W, R> implements MemoryRef {
  private final Value ref;

  public QuadFunctionRef(final Value ref) {
    this.ref = ref;

    if(ref.getSize() != 4) {
      throw new IllegalArgumentException("Size of callback refs must be 4");
    }
  }

  public R run(final T t, final U u, final V v, final W w) {
    //noinspection unchecked
    return (R)this.ref.call(t, u, v, w);
  }

  public void set(final Value.QuadFunction<T, U, V, W, R> val) {
    this.ref.set(val);
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
