package legend.core.memory.types;

import legend.core.memory.Value;

public class ByteRef implements MemoryRef {
  private final Value ref;

  public ByteRef(final Value ref) {
    this.ref = ref;

    if(ref.getSize() != 1) {
      throw new IllegalArgumentException("Size of byte refs must be 1");
    }
  }

  public byte get() {
    return (byte)this.ref.get();
  }

  public ByteRef set(final int val) {
    this.ref.setu(val);
    return this;
  }

  public ByteRef set(final ByteRef val) {
    this.set(val.get());
    return this;
  }

  public long getUnsigned() {
    return this.ref.get() & 0xffL;
  }

  public ByteRef setUnsigned(final long val) {
    this.ref.setu(val);
    return this;
  }

  public ByteRef add(final int amount) {
    return this.set((byte)(this.get() + amount));
  }

  public ByteRef addUnsigned(final long amount) {
    return this.setUnsigned(this.getUnsigned() + amount);
  }

  public ByteRef sub(final int amount) {
    return this.set((byte)(this.get() - amount));
  }

  public ByteRef subUnsigned(final long amount) {
    return this.setUnsigned(this.getUnsigned() - amount);
  }

  public ByteRef incr() {
    return this.add(1);
  }

  public ByteRef decr() {
    return this.sub(1);
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
