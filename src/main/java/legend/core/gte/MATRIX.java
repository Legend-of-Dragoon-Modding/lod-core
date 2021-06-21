package legend.core.gte;

import legend.core.memory.Value;
import legend.core.memory.types.ArrayRef;
import legend.core.memory.types.IntRef;
import legend.core.memory.types.MemoryRef;
import legend.core.memory.types.ShortRef;

public class MATRIX implements MemoryRef {
  private final Value ref;
  // 0h-11h
  private final ArrayRef<ShortRef> data;
  // 12h-13h skipped to align
  // 14h-1fh
  private final ArrayRef<IntRef> transferVector;

  private final short[] data2 = new short[9];
  private final int[] transferVector2 = new int[3];

  public MATRIX() {
    this.ref = null;
    this.data = null;
    this.transferVector = null;
  }

  public MATRIX(final Value ref) {
    this.ref = ref;
    this.data = ref.cast(ArrayRef.of(ShortRef.class, 9, 2, ShortRef::new));
    this.transferVector = ref.offset(0x14L).cast(ArrayRef.of(IntRef.class, 3, 4, IntRef::new));
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

  public void set(final int x, final int y, final short val) {
    this.set(x * 3 + y, val);
  }

  public void set(final int index, final short val) {
    if(this.data != null) {
      this.data.get(index).set(val);
      return;
    }

    this.data2[index] = val;
  }

  public int getTransferVector(final int index) {
    if(this.transferVector != null) {
      return this.transferVector.get(index).get();
    }

    return this.transferVector2[index];
  }

  public void setTransferVector(final int index, final int val) {
    if(this.transferVector != null) {
      this.transferVector.get(index).set(val);
      return;
    }

    this.transferVector2[index] = val;
  }

  public void set(final MATRIX other) {
    for(int x = 0; x < 3; x++) {
      for(int y = 0; y < 3; y++) {
        this.set(x, y, other.get(x, y));
      }
    }

    for(int i = 0; i < 3; i++) {
      this.setTransferVector(i, other.getTransferVector(i));
    }
  }

  public void clear() {
    for(int x = 0; x < 3; x++) {
      for(int y = 0; y < 3; y++) {
        this.set(x, y, (short)0);
      }
    }

    for(int i = 0; i < 3; i++) {
      this.setTransferVector(i, 0);
    }
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
