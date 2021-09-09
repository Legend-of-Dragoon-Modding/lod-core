package legend.core.gte;

import legend.core.memory.Value;
import legend.core.memory.types.MemoryRef;
import legend.core.memory.types.Pointer;
import legend.core.memory.types.UnsignedIntRef;

public class GsCOORDINATE2 implements MemoryRef {
  private final Value ref;

  public final UnsignedIntRef flg;
  public final MATRIX coord;
  public final MATRIX workm;
  public final Pointer<GsCOORD2PARAM> param;
  public final Pointer<GsCOORDINATE2> super_;
  public final Pointer<GsCOORDINATE2> sub;

  public GsCOORDINATE2(final Value ref) {
    this.ref = ref;

    this.flg    = ref.offset(0x04, 0x00L).cast(UnsignedIntRef::new);
    this.coord  = ref.offset(0x20, 0x04L).cast(MATRIX::new);
    this.workm  = ref.offset(0x20, 0x24L).cast(MATRIX::new);
    this.param  = ref.offset(0x04, 0x44L).cast(Pointer.deferred(4, GsCOORD2PARAM::new));
    this.super_ = ref.offset(0x04, 0x48L).cast(Pointer.deferred(4, GsCOORDINATE2::new));
    this.sub    = ref.offset(0x04, 0x4cL).cast(Pointer.deferred(4, GsCOORDINATE2::new));
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
