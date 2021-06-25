package legend.core.gte;

import legend.core.memory.Value;
import legend.core.memory.types.MemoryRef;
import legend.core.memory.types.ShortRef;

import javax.annotation.Nullable;

public class SVECTOR implements MemoryRef {
  @Nullable
  private final Value ref;

  public final ShortRef x;
  public final ShortRef y;
  public final ShortRef z;
  public final ShortRef pad;

  public SVECTOR() {
    this.ref = null;
    this.x = new ShortRef();
    this.y = new ShortRef();
    this.z = new ShortRef();
    this.pad = new ShortRef();
  }

  public SVECTOR(final Value ref) {
    this.ref = ref;
    this.x = new ShortRef(ref.offset(2, 0x0L));
    this.y = new ShortRef(ref.offset(2, 0x2L));
    this.z = new ShortRef(ref.offset(2, 0x4L));
    this.pad = new ShortRef(ref.offset(2, 0x6L));
  }

  public SVECTOR set(final SVECTOR other) {
    this.setX(other.getX());
    this.setY(other.getY());
    this.setZ(other.getZ());
    this.setPad(other.getPad());
    return this;
  }

  public short getX() {
    return this.x.get();
  }

  public void setX(final short x) {
    this.x.set(x);
  }

  public short getY() {
    return this.y.get();
  }

  public void setY(final short y) {
    this.y.set(y);
  }

  public short getZ() {
    return this.z.get();
  }

  public void setZ(final short z) {
    this.z.set(z);
  }

  public short getPad() {
    return this.pad.get();
  }

  public void setPad(final short pad) {
    this.pad.set(pad);
  }

  @Override
  public long getAddress() {
    if(this.ref == null) {
      throw new NullPointerException("Can't get address of non-heap object");
    }

    return this.ref.getAddress();
  }
}
