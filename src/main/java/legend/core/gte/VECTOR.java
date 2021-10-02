package legend.core.gte;

import legend.core.memory.Value;
import legend.core.memory.types.IntRef;
import legend.core.memory.types.MemoryRef;

import javax.annotation.Nullable;

public class VECTOR implements MemoryRef {
  @Nullable
  private final Value ref;

  public final IntRef x;
  public final IntRef y;
  public final IntRef z;
  public final IntRef pad;

  public VECTOR() {
    this.ref = null;
    this.x = new IntRef();
    this.y = new IntRef();
    this.z = new IntRef();
    this.pad = new IntRef();
  }

  public VECTOR(final Value ref) {
    this.ref = ref;
    this.x = new IntRef(ref.offset(4, 0x0L));
    this.y = new IntRef(ref.offset(4, 0x4L));
    this.z = new IntRef(ref.offset(4, 0x8L));
    this.pad = new IntRef(ref.offset(4, 0xcL));
  }

  /** NOTE: does NOT set pad */
  public VECTOR set(final VECTOR other) {
    this.setX(other.getX());
    this.setY(other.getY());
    this.setZ(other.getZ());
    return this;
  }

  public int getX() {
    return this.x.get();
  }

  public void setX(final int x) {
    this.x.set(x);
  }

  public int getY() {
    return this.y.get();
  }

  public void setY(final int y) {
    this.y.set(y);
  }

  public int getZ() {
    return this.z.get();
  }

  public void setZ(final int z) {
    this.z.set(z);
  }

  public int getPad() {
    return this.pad.get();
  }

  public void setPad(final int pad) {
    this.pad.set(pad);
  }

  public VECTOR add(final VECTOR other) {
    this.x.add(other.x);
    this.y.add(other.y);
    this.z.add(other.z);
    return this;
  }

  public VECTOR sub(final VECTOR other) {
    this.x.sub(other.x);
    this.y.sub(other.y);
    this.z.sub(other.z);
    return this;
  }

  public VECTOR div(final int divisor) {
    this.x.set(this.x.get() / divisor);
    this.y.set(this.y.get() / divisor);
    this.z.set(this.z.get() / divisor);
    return this;
  }

  public VECTOR negate() {
    this.x.set(-this.x.get());
    this.y.set(-this.y.get());
    this.z.set(-this.z.get());
    return this;
  }

  @Override
  public long getAddress() {
    if(this.ref == null) {
      throw new NullPointerException("Can't get address of non-heap object");
    }

    return this.ref.getAddress();
  }

  @Override
  public String toString() {
    return "VECTOR {x: " + this.x + ", y: " + this.y + ", z: " + this.z + '}' + (this.ref == null ? " (local)" : " @ " + Long.toHexString(this.getAddress()));
  }
}
