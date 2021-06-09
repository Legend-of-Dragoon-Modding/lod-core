package legend.core.cdrom;

public enum SyncCode {
  NO_INTERRUPT("NoIntr"),
  DATA_READY("DataReady"),
  COMPLETE("Complete"),
  ACKNOWLEDGE("Acknowledge"),
  DATA_END("DataEnd"),
  DISK_ERROR("DiskError"),
  NO_RESULT("NoResult"),
  ;

  public static SyncCode fromLong(final long val) {
    return SyncCode.values()[(int)val];
  }

  public final String name;

  SyncCode(final String name) {
    this.name = name;
  }
}
