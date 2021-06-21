package legend.core.cdrom;

import legend.core.memory.Value;
import legend.core.memory.types.ArrayRef;
import legend.core.memory.types.ByteRef;
import legend.core.memory.types.EnumRef;
import legend.core.memory.types.MemoryRef;

public class Response implements MemoryRef {
  private final Value ref;

  private final Value batch;
  private final EnumRef<SyncCode> syncCode;
  private final ArrayRef<ByteRef> responses;

  public Response(final Value ref) {
    this.ref = ref;
    this.batch = ref.offset(4, 0x0L);
    this.syncCode = ref.offset(1, 0x4L).cast(EnumRef.of(SyncCode.values()));
    this.responses = ref.offset(1, 0x5L).cast(ArrayRef.of(ByteRef.class, 8, 1, ByteRef::new));
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }

  public long getBatch() {
    return this.batch.get();
  }

  public void setBatch(final long batch) {
    this.batch.setu(batch);
  }

  public SyncCode getSyncCode() {
    return this.syncCode.get();
  }

  public void setSyncCode(final SyncCode syncCode) {
    this.syncCode.set(syncCode);
  }

  public byte getResponse(final int index) {
    return this.responses.get(index).get();
  }

  public void setResponse(final int index, final byte value) {
    this.responses.get(index).set(value);
  }

  public void setResponses(final byte[] responses) {
    if(responses.length != this.responses.length()) {
      throw new IllegalArgumentException("Array must contain " + this.responses.length() + " elements");
    }

    for(int i = 0; i < this.responses.length(); i++) {
      this.responses.get(i).set(responses[i]);
    }
  }
}
