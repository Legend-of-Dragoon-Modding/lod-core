package legend.core.cdrom;

import legend.core.memory.Value;
import legend.core.memory.types.BoolRef;
import legend.core.memory.types.CString;
import legend.core.memory.types.IntRef;
import legend.core.memory.types.MemoryRef;
import legend.core.memory.types.Pointer;
import legend.core.memory.types.ShortRef;
import legend.core.memory.types.TriConsumerRef;

public class FileLoadingInfo implements MemoryRef {
  private final Value ref;

  /**
   * 0x0 - 4 bytes
   */
  public final CdlLOC pos;
  /**
   * 0x4 - 4 bytes
   */
  public final IntRef size;
  /**
   * 0x8 - 4 bytes
   */
  public final Pointer<TriConsumerRef<Value, Long, Long>> callback;
  /**
   * 0xc - 4 bytes
   */
  public final Value transferDest;
  /**
   * 0x10 - 4 bytes
   */
  public final Pointer<CString> namePtr;
  /**
   * 0x14 - 4 bytes
   */
  public final IntRef unknown1;
  /**
   * 0x18 - 2 bytes
   */
  public final ShortRef unknown2;
  /**
   * 0x1a - 2 bytes
   */
  public ShortRef unknown3;
  /**
   * 0x1c - 1 byte
   */
  public BoolRef used;

  public FileLoadingInfo(final Value ref) {
    this.ref = ref;
    this.pos = new CdlLOC(ref.offset(4, 0x0L));
    this.size = ref.offset(4, 0x4L).cast(IntRef::new);
    this.callback = ref.offset(4, 0x8L).cast(Pointer.of(4, TriConsumerRef::new));
    this.transferDest = ref.offset(4, 0xcL);
    this.namePtr = ref.offset(4, 0x10L).cast(Pointer.of(16, CString.maxLength(16)));
    this.unknown1 = ref.offset(4, 0x14L).cast(IntRef::new);
    this.unknown2 = ref.offset(2, 0x18L).cast(ShortRef::new);
    this.unknown3 = ref.offset(2, 0x1aL).cast(ShortRef::new);
    this.used = ref.offset(1, 0x1cL).cast(BoolRef::new);
  }

  public void set(final FileLoadingInfo other) {
    this.pos.set(other.pos);
    this.size.set(other.size);
    this.callback.set(other.callback.deref());
    this.transferDest.setu(other.transferDest);
    this.namePtr.set(other.namePtr.deref());
    this.unknown1.set(other.unknown1);
    this.unknown2.set(other.unknown2);
    this.unknown3.set(other.unknown3);
    this.used.set(other.used);
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }

  @Override
  public String toString() {
    if(!this.used.get()) {
      return "FileLoadingInfo: unused";
    }

    if(this.namePtr.isNull()) {
      return "FileLoadingInfo: no name";
    }

    return "FileLoadingInfo {name: " + this.namePtr.deref().get() + ", pos: " + this.pos + ", size: " + this.size.get() + ", transfer dest: " + Long.toString(this.transferDest.get(), 16) + ", callback: " + Long.toString(this.callback.getAddress(), 16) + '}';
  }
}
