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

  public void set(final int val) {
    if(this.ref != null) {
      this.ref.setu(val);
      return;
    }

    this.val = val;
  }

  public void set(final IntRef val) {
    this.set(val.get());
  }

  @Override
  public long getAddress() {
    if(this.ref == null) {
      return 0;
    }

    return this.ref.getAddress();
  }
}
