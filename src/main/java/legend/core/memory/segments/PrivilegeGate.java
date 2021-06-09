package legend.core.memory.segments;

import legend.core.memory.PrivilegeNotAcquiredException;
import legend.core.memory.Segment;

public class PrivilegeGate {
  private int acquisitions;

  public void acquire() {
    this.acquisitions++;
  }

  public void release() {
    this.acquisitions--;

    if(this.acquisitions < 0) {
      throw new RuntimeException("Privilege gate released more times than acquired");
    }
  }

  public void test() {
    if(this.acquisitions == 0) {
      throw new PrivilegeNotAcquiredException("Attempted to access privileged memory");
    }
  }

  public Segment wrap(final Segment segment) {
    return new PrivilegedSegment(segment, this, false);
  }

  public Segment readonly(final Segment segment) {
    return new PrivilegedSegment(segment, this, true);
  }
}
