package legend.core.memory.types;

import legend.core.memory.Value;

public class UnsignedByteRef implements MemoryRef {
  private final Value ref;

  public UnsignedByteRef(final Value ref) {
    this.ref = ref;

    if(ref.getSize() != 1) {
      throw new IllegalArgumentException("Size of byte refs must be 1");
    }
  }

  public int get() {
    return (int)this.ref.get();
  }

  public UnsignedByteRef set(final int val) {
    if((val & ~0xff) != 0) {
      throw new IllegalArgumentException("Overflow: " + val);
    }

    this.ref.setu(val);
    return this;
  }

  public UnsignedByteRef set(final UnsignedByteRef val) {
    this.set(val.get());
    return this;
  }

  public UnsignedByteRef add(final int amount) {
    return this.set(this.get() + amount);
  }

  public UnsignedByteRef add(final UnsignedByteRef amount) {
    return this.set(amount.get());
  }

  public UnsignedByteRef sub(final int amount) {
    return this.set(this.get() - amount);
  }

  public UnsignedByteRef sub(final UnsignedByteRef amount) {
    return this.set(amount.get());
  }

  public UnsignedByteRef incr() {
    return this.add(1);
  }

  public UnsignedByteRef decr() {
    return this.sub(1);
  }

  public UnsignedByteRef not() {
    return this.set(~this.get() & 0xff);
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }

  @Override
  public String toString() {
    return this.get() + (this.ref == null ? " (local)" : " @ " + Long.toHexString(this.getAddress()));
  }
}
