package legend.core.memory.types;

import legend.core.memory.Value;
import legend.core.memory.types.MemoryRef;

import java.util.function.Function;

public class VariadicFunctionRef<R> implements MemoryRef {
  private final Value ref;

  public VariadicFunctionRef(final Value ref) {
    this.ref = ref;

    if(ref.getSize() != 4) {
      throw new IllegalArgumentException("Size of callback refs must be 4");
    }
  }

  public R run(final Object... params) {
    //noinspection unchecked
    return (R)this.ref.call(params);
  }

  public void set(final Function<Object[], R> val) {
    this.ref.set(val);
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
