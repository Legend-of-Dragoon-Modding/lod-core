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

  public ShortRef set(final short val) {
    if(this.ref != null) {
      this.ref.setu(val);
    } else {
      this.val = val;
    }

    return this;
  }

  public ShortRef set(final ShortRef val) {
    return this.set(val.get());
  }

  public ShortRef add(final short val) {
    return this.set((short)(this.get() + val));
  }

  public ShortRef add(final ShortRef val) {
    return this.set(val.get());
  }

  public ShortRef sub(final short val) {
    return this.set((short)(this.get() - val));
  }

  public ShortRef sub(final ShortRef val) {
    return this.set(val.get());
  }

  public ShortRef incr() {
    return this.add((short)1);
  }

  public ShortRef decr() {
    return this.sub((short)1);
  }

  public ShortRef not() {
    return this.set((short)~this.get());
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
