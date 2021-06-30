package legend.core.gte;

class ShortVector3 {
  public short x;
  public short y;
  public short z;

  public ShortVector3() { }

  public ShortVector3(final short x, final short y, final short z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  public void setXY(final int xy) {
    this.x = (short)(xy & 0xffff);
    this.y = (short)(xy >>> 16);
  }

  public int getXY() {
    return (this.y & 0xffff) << 16 | this.x & 0xffff;
  }

  public ShortVector3 copy() {
    return new ShortVector3(this.x, this.y, this.z);
  }
}
