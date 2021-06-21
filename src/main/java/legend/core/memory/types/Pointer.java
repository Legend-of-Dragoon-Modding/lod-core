package legend.core.memory.types;

import legend.core.memory.Value;

import javax.annotation.Nullable;
import java.util.function.Function;

public class Pointer<T extends MemoryRef> implements MemoryRef {
  public static <T extends MemoryRef> Function<Value, Pointer<T>> of(final Function<Value, T> constructor) {
    return ref -> new Pointer<>(ref, constructor);
  }

  private final Value ref;
  private final Function<Value, T> constructor;
  @Nullable
  private T cache;

  public Pointer(final Value ref, final Function<Value, T> constructor) {
    this.ref = ref;

    if(ref.getSize() != 4) {
      throw new IllegalArgumentException("Pointers must be 4 bytes");
    }

    this.constructor = constructor;

    try {
      this.updateCache();
    } catch(final IllegalArgumentException ignored) { }
  }

  private void updateCache() {
    if(this.isNull()) {
      this.cache = null;
      return;
    }

    this.cache = this.constructor.apply(this.ref.deref(4));
  }

  public boolean isNull() {
    return this.ref.get() == 0;
  }

  public T deref() {
    final T value = this.derefNullable();

    if(value == null) {
      throw new NullPointerException("Pointer " + Long.toHexString(this.getAddress()) + " is null");
    }

    return value;
  }

  @Nullable
  public T derefNullable() {
    if(this.isNull()) {
      this.cache = null;
      return null;
    }

    if(this.cache == null || this.ref.get() != this.cache.getAddress()) {
      this.updateCache();
    }

    return this.cache;
  }

  public void set(final T ref) {
    this.ref.setu(ref.getAddress());
    this.cache = ref;
  }

  public void clear() {
    this.ref.setu(0);
    this.cache = null;
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
