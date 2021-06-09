package legend.core.memory.types;

import legend.core.memory.Value;

import java.util.function.Function;

public class FixedString implements MemoryRef {
  public static Function<Value, FixedString> length(final int length) {
    return ref -> new FixedString(ref.offset(length, 0x0L));
  }

  private final Value ref;

  public FixedString(final Value ref) {
    this.ref = ref;
  }

  public boolean isEmpty() {
    return this.ref.offset(1, 0x0L).get() == 0;
  }

  public String get() {
    final StringBuilder sb = new StringBuilder();

    for(int offset = 0; offset < this.ref.getSize(); offset++) {
      final char ascii = (char)this.ref.offset(1, offset).get();

      if(ascii == 0) {
        break;
      }

      sb.append(ascii);
    }

    return sb.toString();
  }

  public void set(final String string) {
    if(string.length() >= this.ref.getSize()) {
      throw new IndexOutOfBoundsException("String buffer overrun - string of length " + string.length() + " can't fit within " + this.ref.getSize() + " bytes");
    }

    for(int offset = 0; offset < string.length(); offset++) {
      this.ref.offset(1, offset).set((byte)string.charAt(offset));
    }

    for(int offset = string.length(); offset < this.ref.getSize(); offset++) {
      this.ref.offset(1, offset).setu(0);
    }
  }

  public void set(final FixedString string) {
    this.set(string.get());
  }

  @Override
  public long getAddress() {
    return this.ref.getAddress();
  }
}
