package legend.core;

public final class MathHelper {
  private MathHelper() {
  }

  public static short clamp(final short value, final short min, final short max) {
    return (short)Math.max(min, Math.min(value, max));
  }

  public static int clamp(final int value, final int min, final int max) {
    return Math.max(min, Math.min(value, max));
  }

  public static long colour15To24(final long colour) {
    final byte r = (byte)((colour        & 0b1_1111) * 8);
    final byte g = (byte)((colour >>>  5 & 0b1_1111) * 8);
    final byte b = (byte)((colour >>> 10 & 0b1_1111) * 8);
    final byte a = (byte)((colour >>> 15) * 255);

    return (a & 0xffL) << 24 | (b & 0xffL) << 16 | (g & 0xffL) << 8 | r & 0xffL;
  }

  public static int leadingZeroBits(final short num) {
    for(int i = 0; i < 16; i++) {
      if((num & 1 << 15 - i) != 0) {
        return i;
      }
    }

    return 16;
  }

  public static int assertPositive(final int val) {
    assert val >= 0 : "Value must be positive";
    return val;
  }

  public static long get(final byte[] data, final int offset, final int size) {
    long value = 0;

    for(int i = 0; i < size; i++) {
      value |= (long)(data[offset + i] & 0xff) << i * 8;
    }

    return value;
  }

  public static void set(final byte[] data, final int offset, final int size, final long value) {
    for(int i = 0; i < size; i++) {
      data[offset + i] = (byte)(value >>> i * 8 & 0xff);
    }
  }

  public static long sign(final long value, final int numberOfBytes) {
    if((value & 1L << numberOfBytes * 8 - 1) != 0) {
      return value | -(1L << numberOfBytes * 8);
    }

    return value;
  }

  public static long unsign(final long value, final int numberOfBytes) {
    return value & (1L << numberOfBytes * 8) - 1;
  }

  public static long fromBcd(final long x) {
    return (x >> 4L) * 10L + (x & 0xfL);
  }

  public static long toBcd(final long x) {
    return x / 10L << 4L | x % 10L;
  }
}
