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

  public void set(final byte val) {
    this.ref.setu(val);
  }

  public long getUnsigned() {
    return (byte)this.ref.get();
  }

  public void setUnsigned(final long val) {
    this.ref.setu(val);
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
