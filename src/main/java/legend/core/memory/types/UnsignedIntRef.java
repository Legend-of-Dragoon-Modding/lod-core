package legend.core.memory.types;

import legend.core.memory.Value;

import javax.annotation.Nullable;

public class UnsignedIntRef implements MemoryRef {
  @Nullable
  private final Value ref;

  private int val;

  public UnsignedIntRef() {
    this.ref = null;
  }

  public UnsignedIntRef(final Value ref) {
    this.ref = ref;

    if(ref.getSize() != 4) {
      throw new IllegalArgumentException("Size of int refs must be 4");
    }
  }

  public long get() {
    if(this.ref != null) {
      return this.ref.get();
    }

    return this.val & 0xffff_ffffL;
  }

  public void set(final int val) {
    if(this.ref != null) {
      this.ref.setu(val);
      return;
    }

    this.val = val;
  }

  public void set(final long val) {
    if((val & 0xffff_ffff_0000_0000L) != 0) {
      throw new IllegalArgumentException("Value must fit within 32 bits");
    }

    this.set((int)val);
  }

  public void set(final UnsignedIntRef val) {
    this.set((int)val.get());
  }

  @Override
  public long getAddress() {
    if(this.ref == null) {
      return 0;
    }

    return this.ref.getAddress();
  }
}
