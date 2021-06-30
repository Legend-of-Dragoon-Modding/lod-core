package legend.core.memory.types;

import legend.core.memory.Value;

import javax.annotation.Nullable;

public class ShortRef implements MemoryRef {
  @Nullable
  private final Value ref;

  private short val;

  public ShortRef() {
    this.ref = null;
  }

  public ShortRef(final Value ref) {
    this.ref = ref;

    if(ref.getSize() != 2) {
      throw new IllegalArgumentException("Size of short refs must be 2");
    }
  }

  public short get() {
    if(this.ref != null) {
      return (short)this.ref.get();
    }

    return this.val;
  }

  public void set(final short val) {
    if(this.ref != null) {
      this.ref.setu(val);
      return;
    }

    this.val = val;
  }

  public void set(final ShortRef val) {
    this.set(val.get());
  }

  public void add(final short val) {
    this.set((short)(this.get() + val));
  }

  public void sub(final short val) {
    this.set((short)(this.get() - val));
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
