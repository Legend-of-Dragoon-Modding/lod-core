package legend.core.cdrom;

public enum CdlCOMMAND {
  GET_STAT_01(0x01L),
  SET_LOC_02(0x02L, 3),
  PLAY_03(0x03L, 0),
  FORWARD_04(0x04L),
  BACKWARD_05(0x05L),
  READ_N_06(0x06L),
  STANDBY_07(0x07L),
  STOP_08(0x08L),
  PAUSE_09(0x09L),
  INIT_0A(0x0aL),
  MUTE_0B(0x0bL),
  DEMUTE_0C(0x0cL),
  SET_FILTER_0D(0x0dL, 2),
  SET_MODE_0E(0x0eL, 1),
  GET_PARAM_0F(0x0fL),
  GET_LOC_L_10(0x10L),
  GET_LOC_P_11(0x11L),
  SET_SESSION_12(0x12L, 1),
  GET_TN_13(0x13L),
  GET_TD_14(0x14L, 1),
  SEEK_L_15(0x15L),
  SEEK_P_16(0x16L),
  READ_S_1B(0x1bL),
  ;

  public final long command;
  public final int paramCount;

  CdlCOMMAND(final long command) {
    this(command, 0);
  }

  CdlCOMMAND(final long command, final int paramCount) {
    this.command = command;
    this.paramCount = paramCount;
  }

  public static CdlCOMMAND fromLong(final long command) {
    for(final CdlCOMMAND c : values()) {
      if(c.command == command) {
        return c;
      }
    }

    throw new IllegalArgumentException("There is no CdlCOMMAND " + Long.toString(command, 16));
  }
}
