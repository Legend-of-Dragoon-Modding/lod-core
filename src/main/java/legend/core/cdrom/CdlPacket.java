package legend.core.cdrom;

import legend.core.memory.Value;
import legend.core.memory.types.ArrayRef;
import legend.core.memory.types.BiConsumerRef;
import legend.core.memory.types.ByteRef;
import legend.core.memory.types.EnumRef;
import legend.core.memory.types.IntRef;
import legend.core.memory.types.MemoryRef;
import legend.core.memory.types.Pointer;
import legend.core.memory.types.UnsignedIntRef;

public class CdlPacket implements MemoryRef {
  private final Value ref;

  public final UnsignedIntRef batch;
  public final EnumRef<CdlCOMMAND> command;
  private final ArrayRef<ByteRef> args;
  private final Pointer<ArrayRef<ByteRef>> argsPtr;
  public final Pointer<BiConsumerRef<SyncCode, byte[]>> syncCallback;
  public final IntRef retries;

  public CdlPacket(final Value ref) {
    this.ref = ref;

    this.batch = ref.offset(4, 0x0L).cast(UnsignedIntRef::new);
    this.command = ref.offset(1, 0x4L).cast(EnumRef.of(CdlCOMMAND.values()));
    this.args = ref.offset(4, 0x5L).cast(ArrayRef.of(ByteRef.class, 4, 1, ByteRef::new));
    this.argsPtr = ref.offset(4, 0xcL).cast(Pointer.of(ArrayRef.of(ByteRef.class, 4, 1, ByteRef::new)));
    this.syncCallback = ref.offset(4, 0x10L).cast(Pointer.of(BiConsumerRef::new));
    this.retries = ref.offset(4, 0x14L).cast(IntRef::new);
  }

  public long getArgsAddress() {
    return this.argsPtr.isNull() ? 0 : this.argsPtr.deref().getAddress();
  }

  public void setArgs(final byte arg1, final byte arg2, final byte arg3, final byte arg4) {
    this.args.get(0).set(arg1);
    this.args.get(1).set(arg2);
    this.args.get(2).set(arg3);
    this.args.get(3).set(arg4);
    this.argsPtr.set(this.args);
  }

  public void clearArgs() {
    this.args.get(0).set((byte)0);
    this.args.get(1).set((byte)0);
    this.args.get(2).set((byte)0);
    this.args.get(3).set((byte)0);
    this.argsPtr.clear();
  }

  public void clear() {
    this.batch.set(0);
    this.command.set(CdlCOMMAND.GET_STAT_01);
    this.clearArgs();
    this.syncCallback.clear();
    this.retries.set(0);
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
