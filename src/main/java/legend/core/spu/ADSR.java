package legend.core.spu;

public class ADSR {
  public short lo;               //8
  public short hi;               //A

  public boolean isAttackModeExponential() {
    return (this.lo >> 15 & 0x1) != 0;
  }

  public int attackShift() {
    return this.lo >> 10 & 0x1F;
  }

  public int attackStep() {
    return this.lo >> 8 & 0x3; //"+7,+6,+5,+4"
  }

  public int decayShift() {
    return this.lo >> 4 & 0xF;
  }

  public int sustainLevel() {
    return this.lo & 0xF; //Level=(N+1)*800h
  }

  public boolean isSustainModeExponential() {
    return (this.hi >> 15 & 0x1) != 0;
  }

  public boolean isSustainDirectionDecrease() {
    return (this.hi >> 14 & 0x1) != 0;
  }

  public int sustainShift() {
    return this.hi >> 8 & 0x1F;
  }

  public int sustainStep() {
    return this.hi >> 6 & 0x3;
  }

  public boolean isReleaseModeExponential() {
    return (this.hi >> 5 & 0x1) != 0;
  }

  public int releaseShift() {
    return this.hi & 0x1F;
  }
}
