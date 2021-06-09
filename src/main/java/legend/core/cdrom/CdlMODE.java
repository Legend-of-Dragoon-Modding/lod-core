package legend.core.cdrom;

public class CdlMODE {
  /**
   * Unknown. Not used by CD drive. Used in SStrmBin. Not part of packed representation.
   */
  public boolean unknownExtraBit;

  /**
   * (0=Normal speed, 1=Double speed)
   */
  private boolean speed;
  /**
   * (0=Off, 1=Send XA-ADPCM sectors to SPU Audio Input)
   */
  private boolean xaAdpcm;
  /**
   * (0=800h=DataOnly, 1=924h=WholeSectorExceptSyncBytes)
   */
  private boolean sectorSize;
  /**
   * (0=Normal, 1=Ignore Sector Size and Setloc position)
   */
  private boolean ignoreBit;
  /**
   * (0=Off, 1=Process only XA-ADPCM sectors that match Setfilter)
   */
  private boolean xaFilter;
  /**
   * (0=Off, 1=Enable Report-Interrupts for Audio Play)
   */
  private boolean report;
  /**
   * (0=Off, 1=Auto Pause upon End of Track; for Audio Play)
   */
  private boolean autoPause;
  /**
   * (0=Off, 1=Allow to Read CD-DA Sectors; ignore missing EDC)
   */
  private boolean cdda;

  public CdlMODE() { }

  public CdlMODE(final long packed) {
    this.set(packed);
  }

  public CdlMODE unknownExtraBit() {
    this.unknownExtraBit = true;
    return this;
  }

  public CdlMODE normalSpeed() {
    this.speed = false;
    return this;
  }

  public boolean isNormalSpeed() {
    return !this.speed;
  }

  public CdlMODE doubleSpeed() {
    this.speed = true;
    return this;
  }

  public boolean isDoubleSpeed() {
    return this.speed;
  }

  public CdlMODE sendAdpcmToSpu() {
    this.xaAdpcm = true;
    return this;
  }

  public boolean isSendingAdpcmToSpu() {
    return this.xaAdpcm;
  }

  public CdlMODE readDataOnly() {
    this.sectorSize = false;
    return this;
  }

  public boolean isDataOnly() {
    return !this.sectorSize;
  }

  public CdlMODE readEntireSector() {
    this.sectorSize = true;
    return this;
  }

  public boolean isEntireSector() {
    return this.sectorSize;
  }

  public boolean isXaFilter() {
    return this.xaFilter;
  }

  public CdlMODE readCddaSectors() {
    this.cdda = true;
    return this;
  }

  public void clear() {
    this.speed = false;
    this.xaAdpcm = false;
    this.sectorSize = false;
    this.ignoreBit = false;
    this.xaFilter = false;
    this.report = false;
    this.autoPause = false;
    this.cdda = false;
  }

  public void set(final long packed) {
    this.speed = (packed >> 7 & 0b1) != 0;
    this.xaAdpcm = (packed >> 6 & 0b1) != 0;
    this.sectorSize = (packed >> 5 & 0b1) != 0;
    this.ignoreBit = (packed >> 4 & 0b1) != 0;
    this.xaFilter = (packed >> 3 & 0b1) != 0;
    this.report = (packed >> 2 & 0b1) != 0;
    this.autoPause = (packed >> 1 & 0b1) != 0;
    this.cdda = (packed & 0b1) != 0;
  }

  public long toLong() {
    return
      (this.speed ? 1 : 0) << 7 |
      (this.xaAdpcm ? 1 : 0) << 6 |
      (this.sectorSize ? 1 : 0) << 5 |
      (this.ignoreBit ? 1 : 0) << 4 |
      (this.xaFilter ? 1 : 0) << 3 |
      (this.report ? 1 : 0) << 2 |
      (this.autoPause ? 1 : 0) << 1 |
      (this.cdda ? 1 : 0);
  }

  @Override
  public String toString() {
    return "MODE {double speed: " + this.speed + ", XA-ADPCM: " + this.xaAdpcm + ", whole sector: " + this.sectorSize + ", XA-filter: " + this.xaFilter + ", report: " + this.report + ", auto-pause: " + this.autoPause + ", CDDA: " + this.cdda + '}';
  }
}
