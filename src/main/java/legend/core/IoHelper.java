package legend.core;

import legend.core.gpu.RECT;
import legend.core.memory.Value;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.BufferUtils.createByteBuffer;
import static org.lwjgl.system.MemoryUtil.memSlice;

public final class IoHelper {
  private IoHelper() { }

  /**
   * Reads the specified resource and returns the raw data as a ByteBuffer.
   *
   * @param path   the resource to read
   * @return the resource data
   * @throws IOException if an IO error occurs
   */
  public static ByteBuffer pathToByteBuffer(final Path path) throws IOException {
    final ByteBuffer buffer;

    try(final SeekableByteChannel fc = Files.newByteChannel(path)) {
      buffer = createByteBuffer((int)fc.size() + 1);
      while(fc.read(buffer) != -1) {
      }
    }

    buffer.flip();
    return memSlice(buffer);
  }

  public static void write(final OutputStream stream, final Value value) throws IOException {
    for(int i = 0; i < value.getSize(); i++) {
      stream.write((int)value.offset(1, i).get());
    }
  }

  public static void write(final OutputStream stream, final boolean value) throws IOException {
    stream.write(value ? 1 : 0);
  }

  public static void write(final OutputStream stream, final Enum<?> value) throws IOException {
    stream.write((byte)value.ordinal());
  }

  public static void write(final OutputStream stream, final byte value) throws IOException {
    stream.write(value);
  }

  public static void write(final OutputStream stream, final short value) throws IOException {
    for(int i = 0; i < 2; i++) {
      stream.write(value >>> i * 8 & 0xff);
    }
  }

  public static void write(final OutputStream stream, final int value) throws IOException {
    for(int i = 0; i < 4; i++) {
      stream.write(value >>> i * 8 & 0xff);
    }
  }

  public static void write(final OutputStream stream, final long value) throws IOException {
    write(stream, (int)value);
  }

  public static void write(final OutputStream stream, final String value) throws IOException {
    write(stream, value.length());
    stream.write(value.getBytes());
  }

  public static void write(final OutputStream stream, final RECT value) throws IOException {
    write(stream, value.x.get());
    write(stream, value.y.get());
    write(stream, value.w.get());
    write(stream, value.h.get());
  }

  public static void read(final InputStream stream, final Value value) throws IOException {
    for(int i = 0; i < value.getSize(); i++) {
      value.offset(1, i).setu(stream.read());
    }
  }

  public static boolean readBool(final InputStream stream) throws IOException {
    return stream.read() != 0;
  }

  public static <T extends Enum<T>> T readEnum(final InputStream stream, final Class<T> cls) throws IOException {
    return cls.getEnumConstants()[stream.read()];
  }

  public static byte readByte(final InputStream stream) throws IOException {
    return (byte)stream.read();
  }

  public static short readShort(final InputStream stream) throws IOException {
    return (short)(stream.read() | stream.read() << 8);
  }

  public static int readInt(final InputStream stream) throws IOException {
    return stream.read() | stream.read() << 8 | stream.read() << 16 | stream.read() << 24;
  }

  public static long readLong(final InputStream stream) throws IOException {
    return readInt(stream) & 0xffff_ffffL;
  }

  public static String readString(final InputStream stream) throws IOException {
    final int length = readInt(stream);
    final byte[] data = new byte[length];
    stream.read(data);
    return new String(data);
  }

  public static void readRect(final InputStream stream, final RECT rect) throws IOException {
    rect.set(readShort(stream), readShort(stream), readShort(stream), readShort(stream));
  }
}
