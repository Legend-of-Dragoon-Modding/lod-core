package legend.core.gte;

import legend.core.memory.Value;
import legend.core.memory.types.MemoryRef;
import legend.core.memory.types.Pointer;
import legend.core.memory.types.UnsignedIntRef;

public class GsDOBJ2 implements MemoryRef {
  private final Value ref;

  /** perspective, translation, rotate, display */
  public final UnsignedIntRef attribute;
  /** local dmatrix */
  public final Pointer<GsCOORDINATE2> coord2;
  public final UnsignedIntRef tmd;
  public final UnsignedIntRef id;

  public GsDOBJ2(final Value ref) {
    this.ref = ref;

    this.attribute = ref.offset(4, 0x0L).cast(UnsignedIntRef::new);
    this.coord2 = ref.offset(4, 0x4L).cast(Pointer.deferred(4, GsCOORDINATE2::new));
    this.tmd = ref.offset(4, 0x8L).cast(UnsignedIntRef::new);
    this.id = ref.offset(4, 0xcL).cast(UnsignedIntRef::new);
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
