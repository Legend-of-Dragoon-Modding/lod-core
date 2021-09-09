package legend.core.memory.types;

import legend.core.memory.Value;

public class ProcessControlBlock implements MemoryRef {
  private final Value ref;

  public final Pointer<ThreadControlBlock> threadControlBlockPtr;

  public ProcessControlBlock(final Value ref) {
    this.ref = ref;

    this.threadControlBlockPtr = ref.offset(4, 0x0L).cast(Pointer.of(4, ThreadControlBlock::new));
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
