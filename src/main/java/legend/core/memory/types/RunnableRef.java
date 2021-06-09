package legend.core.memory.types;

import legend.core.memory.Value;

public class RunnableRef implements MemoryRef {
  private final Value ref;

  public RunnableRef(final Value ref) {
    this.ref = ref;

    if(ref.getSize() != 4) {
      throw new IllegalArgumentException("Size of callback refs must be 4");
    }
  }

  public void run() {
    this.ref.call();
  }

  public void set(final Runnable val) {
    this.ref.set(val);
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
