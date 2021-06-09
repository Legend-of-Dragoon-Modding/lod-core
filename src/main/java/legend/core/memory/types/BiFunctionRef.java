package legend.core.memory.types;

import legend.core.memory.Value;

import java.util.function.BiFunction;

public class BiFunctionRef<T, U, R> implements MemoryRef {
  private final Value ref;

  public BiFunctionRef(final Value ref) {
    this.ref = ref;

    if(ref.getSize() != 4) {
      throw new IllegalArgumentException("Size of callback refs must be 4");
    }
  }

  public R run(final T t, final U u) {
    //noinspection unchecked
    return (R)this.ref.call(t, u);
  }

  public void set(final BiFunction<T, U, R> val) {
    this.ref.set(val);
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
