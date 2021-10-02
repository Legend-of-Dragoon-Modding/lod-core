package legend.core.gte;

import legend.core.memory.Value;
import legend.core.memory.types.ArrayRef;
import legend.core.memory.types.MemoryRef;
import legend.core.memory.types.ShortRef;

public class MATRIX implements MemoryRef {
  private final Value ref;

  // 0h-11h
  private final ArrayRef<ShortRef> data;
  // 12h-13h skipped to align
  // 14h-1fh
  public final VECTOR transfer;

  private final short[] data2 = new short[9];

  public MATRIX() {
    this.ref = null;
    this.data = null;
    this.transfer = new VECTOR();
  }

  public MATRIX(final Value ref) {
    this.ref = ref;
    this.data = ref.cast(ArrayRef.of(ShortRef.class, 9, 2, ShortRef::new));
    this.transfer = ref.offset(0x14L).cast(VECTOR::new);
  }

  public short get(final int x, final int y) {
    return this.get(x * 3 + y);
  }

  public short get(final int index) {
    if(this.data != null) {
      return this.data.get(index).get();
    }

    return this.data2[index];
  }

  public MATRIX set(final int x, final int y, final short val) {
    this.set(x * 3 + y, val);
    return this;
  }

  public MATRIX set(final int index, final short val) {
    if(this.data != null) {
      this.data.get(index).set(val);
      return this;
    }

    this.data2[index] = val;
    return this;
  }

  public MATRIX set(final MATRIX other) {
    for(int x = 0; x < 3; x++) {
      for(int y = 0; y < 3; y++) {
        this.set(x, y, other.get(x, y));
      }
    }

    for(int i = 0; i < 3; i++) {
      this.transfer.set(other.transfer);
    }

    return this;
  }

  public MATRIX clear() {
    for(int x = 0; x < 3; x++) {
      for(int y = 0; y < 3; y++) {
        this.set(x, y, (short)0);
      }
    }

    this.transfer.x.set(0);
    this.transfer.y.set(0);
    this.transfer.z.set(0);

    return this;
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
