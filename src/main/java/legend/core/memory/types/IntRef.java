package legend.core.memory.types;

import legend.core.memory.Value;

import javax.annotation.Nullable;

public class IntRef implements MemoryRef {
  @Nullable
  private final Value ref;

  private int val;

  public IntRef() {
    this.ref = null;
  }

  public IntRef(final Value ref) {
    this.ref = ref;

    if(ref.getSize() != 4) {
      throw new IllegalArgumentException("Size of int refs must be 4");
    }
  }

  public int get() {
    if(this.ref != null) {
      return (int)this.ref.get();
    }

    return this.val;
  }

  public IntRef set(final int val) {
    if(this.ref != null) {
      this.ref.setu(val);
      return this;
    }

    this.val = val;
    return this;
  }

  public IntRef set(final IntRef val) {
    return this.set(val.get());
  }

  public IntRef add(final int amount) {
    return this.set(this.get() + amount);
  }

  public IntRef add(final IntRef amount) {
    return this.add(amount.get());
  }

  public IntRef sub(final int amount) {
    return this.set(this.get() - amount);
  }

  public IntRef sub(final IntRef amount) {
    return this.sub(amount.get());
  }

  public IntRef incr() {
    return this.add(1);
  }

  public IntRef decr() {
    return this.sub(1);
  }

  public IntRef not() {
    return this.set(~this.get());
  }

  public IntRef and(final int val) {
    return this.set(this.get() & val);
  }

  public IntRef and(final IntRef val) {
    return this.and(val.get());
  }

  public IntRef or(final int val) {
    return this.set(this.get() | val);
  }

  public IntRef or(final IntRef val) {
    return this.or(val.get());
  }

  public IntRef xor(final int val) {
    return this.set(this.get() ^ val);
  }

  public IntRef xor(final IntRef val) {
    return this.xor(val.get());
  }

  public IntRef shl(final int bits) {
    return this.set(this.get() << bits);
  }

  public IntRef shl(final IntRef bits) {
    return this.shl(bits.get());
  }

  public IntRef shr(final int bits) {
    return this.set(this.get() >>> bits);
  }

  public IntRef shr(final IntRef bits) {
    return this.shr(bits.get());
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
    return Integer.toHexString(this.get()) + (this.ref == null ? " (local)" : " @ " + Long.toHexString(this.getAddress()));
  }
}
