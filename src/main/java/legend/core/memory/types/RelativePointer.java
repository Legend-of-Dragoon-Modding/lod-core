package legend.core.memory.types;

import legend.core.memory.Value;

import javax.annotation.Nullable;
import java.util.function.Function;

public class RelativePointer<T extends MemoryRef> implements MemoryRef {
  public static <T extends MemoryRef> Function<Value, RelativePointer<T>> of(final int size, final Function<Value, T> constructor) {
    return ref -> new RelativePointer<>(ref, constructor, size, true);
  }

  /**
   * Lazy mode - don't resolve pointer until used
   */
  public static <T extends MemoryRef> Function<Value, RelativePointer<T>> deferred(final int size, final Function<Value, T> constructor) {
    return ref -> new RelativePointer<>(ref, constructor, size, false);
  }

  public static <T extends MemoryRef> Class<RelativePointer<T>> classFor(final Class<T> t) {
    //noinspection unchecked
    return (Class<RelativePointer<T>>)(Class<?>)RelativePointer.class;
  }

  private final Value ref;
  private final Function<Value, T> constructor;
  private final int size;
  @Nullable
  private T cache;

  public RelativePointer(final Value ref, final Function<Value, T> constructor, final int size, final boolean precache) {
    this.ref = ref;

    if(ref.getSize() != 4) {
      throw new IllegalArgumentException("Pointers must be 4 bytes");
    }

    this.constructor = constructor;
    this.size = size;

    if(precache) {
      try {
        this.updateCache();
      } catch(final IllegalArgumentException ignored) {}
    }
  }

  private void updateCache() {
    this.cache = this.constructor.apply(this.ref.offset(this.size, this.ref.get()));
  }

  public T deref() {
    if(this.cache == null || this.ref.offset(this.ref.get()).getAddress() != this.cache.getAddress()) {
      this.updateCache();
    }

    return this.cache;
  }

  public <U> U derefAs(final Class<U> cls) {
    return cls.cast(this.deref());
  }

  public RelativePointer<T> set(final T ref) {
    this.ref.setu(ref.getAddress() - this.ref.getAddress());
    this.cache = ref;
    return this;
  }

  public RelativePointer<T> add(final long amount) {
    this.ref.addu(amount);
    this.cache = null;
    return this;
  }

  public RelativePointer<T> sub(final long amount) {
    this.ref.subu(amount);
    this.cache = null;
    return this;
  }

  public RelativePointer<T> incr() {
    this.add(this.size);
    this.cache = null;
    return this;
  }

  public RelativePointer<T> decr() {
    this.sub(this.size);
    this.cache = null;
    return this;
  }

  public long getPointer() {
    return this.ref.get();
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
