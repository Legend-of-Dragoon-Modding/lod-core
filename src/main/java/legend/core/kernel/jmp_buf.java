package legend.core.kernel;

import legend.core.memory.Value;
import legend.core.memory.types.ConsumerRef;
import legend.core.memory.types.MemoryRef;
import legend.core.memory.types.UnsignedIntRef;

import java.util.function.Consumer;

/**
 * 0x30 bytes total, normally contains values of many registers
 */
public class jmp_buf implements MemoryRef {
  private final Value ref;

  private final ConsumerRef<Long> ra;
  public final UnsignedIntRef sp;
  public final UnsignedIntRef fp;
  // More...

  public jmp_buf(final Value ref) {
    this.ref = ref;

    this.ra = ref.offset(4, 0x0L).cast(ConsumerRef::new);
    this.sp = ref.offset(4, 0x4L).cast(UnsignedIntRef::new);
    this.fp = ref.offset(4, 0x8L).cast(UnsignedIntRef::new);
  }

  public void run(final long val) {
    this.ra.run(val);
  }

  public void set(final Consumer<Long> callback) {
    this.ra.set(callback);
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
