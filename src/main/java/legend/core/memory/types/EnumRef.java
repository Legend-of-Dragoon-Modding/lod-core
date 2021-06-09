package legend.core.memory.types;

import legend.core.memory.Value;

import java.util.function.Function;

public final class EnumRef<T extends Enum<T>> implements MemoryRef {
  @SafeVarargs
  public static <T extends Enum<T>> Function<Value, EnumRef<T>> of(final T... values) {
    return ref -> new EnumRef<>(ref, values);
  }

  private final Value ref;
  private final T[] values;

  private EnumRef(final Value ref, final T[] values) {
    this.ref = ref;
    this.values = values;
  }

  public T get() {
    return this.values[(int)this.ref.get()];
  }

  public void set(final T val) {
    for(int i = 0; i < this.values.length; i++) {
      if(this.values[i] == val) {
        this.ref.setu(i);
        return;
      }
    }

    throw new IllegalArgumentException(val + " is not a valid value for this enum ref");
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
