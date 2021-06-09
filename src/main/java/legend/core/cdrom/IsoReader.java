package legend.core.cdrom;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

public class IsoReader {
  private static final long SECTOR_SIZE = 2352L;
  private static final long SYNC_PATTER_SIZE = 12L;

  private final RandomAccessFile file;

  public IsoReader(final Path path) throws IOException {
    this.file = new RandomAccessFile(path.toFile(), "r");
  }

  public long getPos() throws IOException {
    return this.file.getFilePointer();
  }

  public void seekSector(final long sector) throws IOException {
    this.file.seek(sector * SECTOR_SIZE + SYNC_PATTER_SIZE);
  }

  public void seekSector(final CdlLOC loc) throws IOException {
    this.seekSector(loc.pack());
  }

  public void seekSectorRaw(final long sector) throws IOException {
    this.file.seek(sector * SECTOR_SIZE);
  }

  public void seekSectorRaw(final CdlLOC loc) throws IOException {
    this.seekSectorRaw(loc.pack());
  }

  public void advance(final int amount) throws IOException {
    final int skipped = this.file.skipBytes(amount);

    if(skipped != amount) {
      throw new RuntimeException("Skipped the wrong number of bytes. End of file? Negative amount? (requested: " + amount + ", actual: " + skipped + ')');
    }
  }

  public long readByte() throws IOException {
    return this.file.readByte() & 0xffL;
  }

  public void read(final byte[] out) throws IOException {
    this.file.read(out);
  }
}
