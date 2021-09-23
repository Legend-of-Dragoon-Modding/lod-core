package legend.core.memory.types;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import legend.core.memory.Value;

import java.util.function.Function;

public class UnboundedArrayRef<T extends MemoryRef> implements MemoryRef {
  public static <T extends MemoryRef> Function<Value, UnboundedArrayRef<T>> of(final int stride, final Function<Value, T> constructor) {
    return ref -> new UnboundedArrayRef<>(ref, stride, constructor);
  }

  private final Value ref;
  private final Int2ObjectMap<T> elements;
  private final int stride;
  private final Function<Value, T> constructor;

  public UnboundedArrayRef(final Value ref, final int stride, final Function<Value, T> constructor) {
    this.ref = ref;

    this.elements = new Int2ObjectArrayMap<>();
    this.stride = stride;
    this.constructor = constructor;
  }

  public T get(final int index) {
    return this.elements.computeIfAbsent(index, key -> this.constructor.apply(this.ref.offset(this.stride, (long)key * this.stride)));
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
