package legend.core.memory;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import legend.core.MathHelper;
import legend.core.memory.types.QuadConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.TriConsumer;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Memory {
  private static final Logger LOGGER = LogManager.getFormatterLogger(Memory.class);

  private static final int[] REGION_MASK = {
    0xffff_ffff, 0xffff_ffff, 0xffff_ffff, 0xffff_ffff, // KUSEG: 2048MB
    0x7fff_ffff,                                        // KSEG0:  512MB
    0x1fff_ffff,                                        // KSEG1:  512MB
    0xffff_ffff, 0xffff_ffff,                           // KSEG2: 1024MB
  };

  private final Object lock = new Object();

  private final List<Segment> segments = new ArrayList<>();

  private boolean alignmentChecks = true;

  private static final long TEMP_FLAG = 0xffff_0000L;
  private static final long TEMP_MASK = 0x0000_ffffL;

  private final byte[] temp = new byte[0x1000];
  private final BitSet tempUsage = new BitSet(0x1000);

  public static final IntSet watches = new IntOpenHashSet();

  public void waitForLock(final Runnable callback) {
    synchronized(this.lock) {
      callback.run();
    }
  }

  public void disableAlignmentChecks() {
    this.alignmentChecks = false;
  }

  public void enableAlignmentChecks() {
    this.alignmentChecks = true;
  }

  private void checkAlignment(final long address, final int size) {
    if(!this.alignmentChecks) {
      return;
    }

    // Don't check alignment for temps - they use special storage
    if((address & TEMP_FLAG) == TEMP_FLAG) {
      return;
    }

    if((address & Math.min(size, 4) - 1) != 0) {
      throw new MisalignedAccessException("Misaligned memory access at address " + Long.toHexString(address) + " for size " + size);
    }
  }

  private long maskAddress(final long address) {
    final int region = (int)(address >>> 29 & 0b111);
    return address & REGION_MASK[region];
  }

  public void addSegment(final Segment segment) {
    synchronized(this.lock) {
      this.segments.add(segment);
    }
  }

  private Segment getSegment(final long address) {
    final long masked = this.maskAddress(address);

    for(final Segment segment : this.segments) {
      if(segment.accepts(masked)) {
        return segment;
      }
    }

    throw new RuntimeException("There is no memory segment at " + Long.toHexString(masked) + " (address: " + Long.toHexString(address) + ')');
  }

  public byte get(final long address) {
    synchronized(this.lock) {
      final Segment segment = this.getSegment(address);
      return segment.get((int)(this.maskAddress(address) - segment.getAddress()));
    }
  }

  public long get(final long address, final int size) {
    this.checkAlignment(address, size);

    synchronized(this.lock) {
      if((address & TEMP_FLAG) == TEMP_FLAG) {
        final int tempIndex = (int)(address & TEMP_MASK);
        if(!this.tempUsage.get(tempIndex)) {
          throw new IllegalAddressException("There's no temp value reserved at " + Integer.toHexString(tempIndex));
        }

        long value = 0;
        for(int i = 0; i < size; i++) {
          value |= (this.temp[tempIndex + i] & 0xffL) << i * 8;
        }

        return value;
      }

      final Segment segment = this.getSegment(address);
      return segment.get((int)(this.maskAddress(address) - segment.getAddress()), size);
    }
  }

  public void set(final long address, final byte data) {
    synchronized(this.lock) {
      final Segment segment = this.getSegment(address);
      segment.set((int)(this.maskAddress(address) - segment.getAddress()), data);
    }

    if(watches.contains((int)address & 0xffffff)) {
      LOGGER.error(Long.toHexString(address) + " set to " + Long.toHexString(data), new Throwable());
    }
  }

  public void set(final long address, final int size, final long data) {
    this.checkAlignment(address, size);

    synchronized(this.lock) {
      if((address & TEMP_FLAG) == TEMP_FLAG) {
        final int tempIndex = (int)(address & TEMP_MASK);
        if(!this.tempUsage.get(tempIndex)) {
          throw new IllegalAddressException("There's no temp value reserved at " + Integer.toHexString(tempIndex));
        }

        for(int i = 0; i < size; i++) {
          this.temp[tempIndex + i] = (byte)(data >> i * 8 & 0xff);
        }

        return;
      }

      final Segment segment = this.getSegment(address);
      segment.set((int)(this.maskAddress(address) - segment.getAddress()), size, data);
    }

    if(watches.contains((int)address & 0xffffff)) {
      LOGGER.error(Long.toHexString(address) + " set to " + Long.toHexString(data), new Throwable());
    }
  }

  public byte[] getBytes(final long address, final int size) {
    synchronized(this.lock) {
      final Segment segment = this.getSegment(address);
      return segment.getBytes((int)(this.maskAddress(address) - segment.getAddress()), size);
    }
  }

  public void getBytes(final long address, final byte[] dest, final int offset, final int size) {
    synchronized(this.lock) {
      final Segment segment = this.getSegment(address);
      segment.getBytes((int)(this.maskAddress(address) - segment.getAddress()), dest, offset, size);
    }
  }

  public void setBytes(final long address, final byte[] data) {
    this.setBytes(address, data, 0, data.length);
  }

  public void setBytes(final long address, final byte[] data, final int offset, final int size) {
    synchronized(this.lock) {
      final Segment segment = this.getSegment(address);
      segment.setBytes((int)(this.maskAddress(address) - segment.getAddress()), data, offset, size);
    }

    if(watches.contains((int)address & 0xffffff)) {
      LOGGER.error(Long.toHexString(address) + " set to " + Long.toHexString(MathHelper.get(data, 0, 4)), new Throwable());
    }
  }

  public Value ref(final int byteSize, final long address) {
    this.checkAlignment(address, byteSize);
    return new MemoryValue(byteSize, address);
  }

  public <T> T ref(final int byteSize, final long address, final Function<Value, T> constructor) {
    if(address == 0) {
      return null;
    }

    return constructor.apply(this.ref(byteSize, address));
  }

  public TemporaryReservation temp() {
    return this.temp(4);
  }

  public TemporaryReservation temp(final int length) {
    outer:
    for(int i = 0; i < this.temp.length; i++) {
      for(int n = 0; n < length; n++) {
        if(this.tempUsage.get(i + n)) {
          continue outer;
        }
      }

      this.tempUsage.set(i, i + length);
      return new TemporaryReservation(TEMP_FLAG | i, length);
    }

    throw new RuntimeException("Ran out of temporary space");
  }

  public void releaseTemp(final long address, final int length) {
    this.tempUsage.clear((int)(address & TEMP_MASK), (int)(address & TEMP_MASK) + length);
  }

  private static record MethodInfo(java.lang.reflect.Method method, boolean ignoreExtraParams) { }

  public void addFunctions(final Class<?> cls) {
    LOGGER.info("Adding function references from %s", cls);

    final Long2ObjectMap<MethodInfo> methods = new Long2ObjectOpenHashMap<>();

    for(final java.lang.reflect.Method method : cls.getMethods()) {
      if(method.isAnnotationPresent(Method.class)) {
        final Method address = method.getAnnotation(Method.class);
        final long addr = this.maskAddress(address.value());

        if(methods.containsKey(addr)) {
          throw new RuntimeException(cls + " contains two methods at address " + addr);
        }

        methods.put(addr, new MethodInfo(method, address.ignoreExtraParams()));
      }
    }

    if(methods.isEmpty()) {
      throw new RuntimeException(cls + " contained no methods with Method annotations");
    }

    synchronized(this.lock) {
      for(final Long2ObjectMap.Entry<MethodInfo> entry : methods.long2ObjectEntrySet()) {
        this.setFunction(entry.getLongKey(), entry.getValue().method, null, entry.getValue().ignoreExtraParams);
      }
    }
  }

  private void setFunction(final long address, final java.lang.reflect.Method function, @Nullable final Object instance, final boolean ignoreExtraParams) {
    this.checkAlignment(address, 4);

    final Segment segment = this.getSegment(address);
    segment.setFunction((int)(this.maskAddress(address) - segment.getAddress()), function, instance, ignoreExtraParams);
  }

  private MethodBinding getFunction(final long address) {
    this.checkAlignment(address, 4);

    final Segment segment = this.getSegment(address);
    return segment.getFunction((int)(this.maskAddress(address) - segment.getAddress()));
  }

  private boolean isFunction(final long address) {
    this.checkAlignment(address, 4);

    final Segment segment = this.getSegment(address);
    return segment.isFunction((int)(this.maskAddress(address) - segment.getAddress()));
  }

  public class MemoryValue extends Value {
    private final long address;

    public MemoryValue(final int byteSize, final long address) {
      super(byteSize);
      this.address = address;
    }

    private MemoryValue(final int byteSize, final long address, final long value) {
      this(byteSize, address);
      this.setu(value);
    }

    @Override
    public Value offset(final long offset) {
      if(offset == 0) {
        return this;
      }

      return new MemoryValue(this.getSize(), this.address + offset);
    }

    @Override
    public Value offset(final int size, final long offset) {
      if(offset == 0 && size == this.getSize()) {
        return this;
      }

      return new MemoryValue(size, this.address + offset);
    }

    @Override
    public Value deref(final int size) {
      if(this.getSize() != 4) {
        throw new UnsupportedOperationException("Can only dereference 4-byte values");
      }

      if((this.address & TEMP_FLAG) != TEMP_FLAG && Memory.this.isFunction(this.address)) {
        throw new UnsupportedOperationException("Can't dereference callback");
      }

      return Memory.this.ref(size, this.get());
    }

    @Override
    public long getAddress() {
      return this.address;
    }

    @Override
    public String toString() {
      return super.toString() + " @ " + Long.toString(this.address, 16);
    }

    @Override
    public long get() {
      return Memory.this.get(this.address, this.getSize());
    }

    @Override
    public long getSigned() {
      return MathHelper.sign(Memory.this.get(this.address, this.getSize()), this.getSize());
    }

    @Override
    public Object call(final Object... params) {
      try {
        final MethodBinding binding = Memory.this.getFunction(this.address);
        final java.lang.reflect.Method method = binding.method();
        final Object[] finalParams;

        if(method.getParameterCount() < params.length && binding.ignoreExtraParams()) {
          finalParams = new Object[method.getParameterCount()];
          System.arraycopy(params, 0, finalParams, 0, finalParams.length);
        } else {
          finalParams = params;
        }

        return binding.method().invoke(binding.instance(), finalParams);
      } catch(final IllegalArgumentException e) {
        throw new RuntimeException("Method " + Long.toHexString(this.address), e);
      } catch(final IllegalAccessException e) {
        throw new RuntimeException(e);
      } catch(final InvocationTargetException e) {
        if(e.getTargetException() instanceof RuntimeException) {
          throw (RuntimeException)e.getTargetException();
        }

        if(e.getTargetException() instanceof Error) {
          throw (Error)e.getTargetException();
        }

        throw new RuntimeException(e.getTargetException());
      }
    }

    @Override
    public Value set(final long value) {
      this.validateSigned(value);
      Memory.this.set(this.address, this.getSize(), value);
      return this;
    }

    @Override
    public Value setu(long value) {
      value &= (0b1L << this.getSize() * 8) - 1;
      this.validateUnsigned(value);
      Memory.this.set(this.address, this.getSize(), value);
      return this;
    }

    @Override
    public Value set(final Runnable function) {
      try {
        Memory.this.setFunction(this.address, function.getClass().getMethod("run"), function, false);
      } catch(final NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    @Override
    public <T> Value set(final Consumer<T> function) {
      try {
        Memory.this.setFunction(this.address, function.getClass().getMethod("accept", Object.class), function, false);
      } catch(final NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    @Override
    public <T, U> Value set(final BiConsumer<T, U> function) {
      try {
        Memory.this.setFunction(this.address, function.getClass().getMethod("accept", Object.class, Object.class), function, false);
      } catch(final NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    @Override
    public <T, U, V> Value set(final TriConsumer<T, U, V> function) {
      try {
        Memory.this.setFunction(this.address, function.getClass().getMethod("accept", Object.class, Object.class, Object.class), function, false);
      } catch(final NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    @Override
    public <T, U, V, W> Value set(final QuadConsumer<T, U, V, W> function) {
      try {
        Memory.this.setFunction(this.address, function.getClass().getMethod("accept", Object.class, Object.class, Object.class, Object.class), function, false);
      } catch(final NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    @Override
    public Value set(final Function<?, ?> function) {
      try {
        Memory.this.setFunction(this.address, function.getClass().getMethod("apply", Object.class), function, false);
      } catch(final NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    @Override
    public Value set(final BiFunction<?, ?, ?> function) {
      try {
        Memory.this.setFunction(this.address, function.getClass().getMethod("apply", Object.class, Object.class), function, false);
      } catch(final NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    @Override
    public Value set(final TriFunction<?, ?, ?, ?> function) {
      try {
        Memory.this.setFunction(this.address, function.getClass().getMethod("apply", Object.class, Object.class, Object.class), function, false);
      } catch(final NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    @Override
    public Value set(final QuadFunction<?, ?, ?, ?, ?> function) {
      try {
        Memory.this.setFunction(this.address, function.getClass().getMethod("apply", Object.class, Object.class, Object.class, Object.class), function, false);
      } catch(final NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    @Override
    public <T> Value set(final Supplier<T> function) {
      try {
        Memory.this.setFunction(this.address, function.getClass().getMethod("get"), function, false);
      } catch(final NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
      return this;
    }
  }

  public final class TemporaryReservation {
    public final long address;
    public final int length;
    private boolean released;

    private TemporaryReservation(final long address, final int length) {
      this.address = address;
      this.length = length;
    }

    public Value get() {
      if(this.released) {
        throw new IllegalStateException("Can't get temporary reservation once released");
      }

      return Memory.this.ref(4, this.address);
    }

    public void release() {
      this.released = true;
      Memory.this.releaseTemp(this.address, this.length);
    }
  }
}
