package legend.core.memory.types;

import legend.core.memory.Value;

import javax.annotation.Nullable;

public class UnsignedShortRef implements MemoryRef {
  @Nullable
  private final Value ref;

  private int val;

  public UnsignedShortRef() {
    this.ref = null;
  }

  public UnsignedShortRef(final Value ref) {
    this.ref = ref;

    if(ref.getSize() != 2) {
      throw new IllegalArgumentException("Size of short refs must be 2");
    }
  }

  public int get() {
    if(this.ref != null) {
      return (int)this.ref.get();
    }

    return this.val;
  }

  public UnsignedShortRef set(final int val) {
    if((val & ~0xffff) != 0) {
      throw new IllegalArgumentException("Overflow: " + val);
    }

    if(this.ref != null) {
      this.ref.setu(val);
    } else {
      this.val = val & 0xffff;
    }

    return this;
  }

  public UnsignedShortRef set(final UnsignedShortRef val) {
    return this.set(val.get());
  }

  public UnsignedShortRef add(final int val) {
    return this.set(this.get() + val);
  }

  public UnsignedShortRef add(final UnsignedShortRef val) {
    return this.set(val.get());
  }

  public UnsignedShortRef sub(final int val) {
    return this.set(this.get() - val);
  }

  public UnsignedShortRef sub(final UnsignedShortRef val) {
    return this.set(val.get());
  }

  public UnsignedShortRef incr() {
    return this.add(1);
  }

  public UnsignedShortRef decr() {
    return this.sub(1);
  }

  public UnsignedShortRef not() {
    return this.set(~this.get() & 0xffff);
  }

  @Override
  public long getAddress() {
    if(this.ref == null) {
      return 0;
    }

    return this.ref.getAddress();
  }

  @Override
  public String toString() {
    return this.get() + (this.ref == null ? " (local)" : " @ " + Long.toHexString(this.getAddress()));
  }
}
