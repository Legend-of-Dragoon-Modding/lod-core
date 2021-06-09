package legend.core.memory.segments;

import legend.core.memory.Value;

import static legend.core.Hardware.MEMORY;

public class ExpansionRegion1Segment extends RamSegment {
  public static final Value EXPANSION_REGION_1 = MEMORY.ref(4, 0x1f00_0000L);

  public ExpansionRegion1Segment(final long address) {
    super(address, 0x8_0000);
  }
}
