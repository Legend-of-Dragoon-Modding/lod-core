package legend.core.cdrom;

public enum CdlCOMMAND {
  NONE_00(0x00),
  GET_STAT_01(0x01),
  SET_LOC_02(0x02, 3),
  PLAY_03(0x03, 0),
  FORWARD_04(0x04),
  BACKWARD_05(0x05),
  READ_N_06(0x06),
  STANDBY_07(0x07),
  STOP_08(0x08),
  PAUSE_09(0x09),
  INIT_0A(0x0a),
  MUTE_0B(0x0b),
  DEMUTE_0C(0x0c),
  SET_FILTER_0D(0x0d, 2),
  SET_MODE_0E(0x0e, 1),
  GET_PARAM_0F(0x0f),
  GET_LOC_L_10(0x10),
  GET_LOC_P_11(0x11),
  SET_SESSION_12(0x12, 1),
  GET_TN_13(0x13),
  GET_TD_14(0x14, 1),
  SEEK_L_15(0x15),
  SEEK_P_16(0x16),
  READ_S_1B(0x1b),
  ;

  public final int command;
  public final int paramCount;

  CdlCOMMAND(final int command) {
    this(command, 0);
  }

  CdlCOMMAND(final int command, final int paramCount) {
    this.command = command;
    this.paramCount = paramCount;
  }

  public static CdlCOMMAND fromCommand(final int command) {
    for(final CdlCOMMAND c : values()) {
      if(c.command == command) {
        return c;
      }
    }

    throw new IllegalArgumentException("There is no CdlCOMMAND " + Long.toString(command, 16));
  }

  public int getCommand() {
    return this.command;
  }
}
