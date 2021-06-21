package legend.core.input;

public enum GamepadInputsEnum {
  L2(0x1),
  R2(0x2),
  L1(0x4),
  R1(0x8),
  TRIANGLE(0x10),
  CIRCLE(0x20),
  CROSS(0x40),
  SQUARE(0x80),
  SELECT(0x100),
  PADi(0x200),
  PADj(0x400),
  START(0x800),
  UP(0x1000),
  RIGHT(0x2000),
  DOWN(0x4000),
  LEFT(0x8000),
  ;

  public final int value;

  GamepadInputsEnum(final int value) {
    this.value = value;
  }
}
