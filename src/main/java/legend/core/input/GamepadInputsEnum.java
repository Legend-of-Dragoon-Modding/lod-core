package legend.core.input;

public enum GamepadInputsEnum {
  SELECT(0x1),
  L3(0x2),
  R3(0x4),
  START(0x8),
  UP(0x10),
  RIGHT(0x20),
  DOWN(0x40),
  LEFT(0x80),
  L2(0x100),
  R2(0x200),
  L1(0x400),
  R1(0x800),
  TRIANGLE(0x1000),
  CIRCLE(0x2000),
  CROSS(0x4000),
  SQUARE(0x8000),
  ;

  public final int value;

  GamepadInputsEnum(final int value) {
    this.value = value;
  }
}
