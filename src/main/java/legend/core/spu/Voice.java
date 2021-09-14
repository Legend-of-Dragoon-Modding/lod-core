package legend.core.spu;

import legend.core.MathHelper;
import legend.core.memory.Memory;
import legend.core.memory.MisalignedAccessException;
import legend.core.memory.Segment;
import legend.core.memory.Value;

public class Voice {
  private static final int[] positiveXaAdpcmTable = {0, 60, 115, 98, 122};
  private static final int[] negativeXaAdpcmTable = {0, 0, -52, -55, -60};

  public final Value LEFT;
  public final Value RIGHT;
  public final Value ADPCM_SAMPLE_RATE;
  public final Value ADPCM_START_ADDR;
  public final Value ADSR;
  public final Value REG_5;
  public final Value ADSR_CURR_VOL;
  public final Value ADPCM_REPEAT_ADDR;

  public Volume volumeLeft = new Volume();           //0
  public Volume volumeRight = new Volume();          //2

  public short pitch;                //4
  public short startAddress;         //6
  public short currentAddress;       //6 Internal

  public ADSR adsr = new ADSR();

  public short adsrVolume;           //C
  public short adpcmRepeatAddress;   //E

  public final Counter counter = new Counter();

  public Phase adsrPhase;

  public short old;
  public short older;

  public short lastBlockSample26;
  public short lastBlockSample27;
  public short lastBlockSample28;

  public short latest;

  public boolean hasSamples;

  public boolean readRamIrq;

  public Voice(final Memory memory, final int voiceIndex) {
    memory.addSegment(new VoiceSegment(0x1f801c00L + voiceIndex * 0x10L));

    this.LEFT = memory.ref(2, 0x1f801c00L).offset(voiceIndex * 0x10L);
    this.RIGHT = memory.ref(2, 0x1f801c02L).offset(voiceIndex * 0x10L);
    this.ADPCM_SAMPLE_RATE = memory.ref(2, 0x1f801c04L).offset(voiceIndex * 0x10L);
    this.ADPCM_START_ADDR = memory.ref(2, 0x1f801c06L).offset(voiceIndex * 0x10L);
    this.ADSR = memory.ref(2, 0x1f801c08L).offset(voiceIndex * 0x10L);
    this.REG_5 = memory.ref(2, 0x1f801c0aL).offset(voiceIndex * 0x10L);
    this.ADSR_CURR_VOL = memory.ref(2, 0x1f801c0cL).offset(voiceIndex * 0x10L);
    this.ADPCM_REPEAT_ADDR = memory.ref(2, 0x1f801c0eL).offset(voiceIndex * 0x10L);

    this.adsrPhase = Phase.Off;
  }

  public void reset() {
    this.volumeLeft.set(0);
    this.volumeRight.set(0);
    this.pitch = 0;
    this.startAddress = 0;
    this.currentAddress = 0;
    this.adsr.hi = 0;
    this.adsr.lo = 0;
    this.adsrVolume = 0;
    this.adpcmRepeatAddress = 0;
  }

  public void keyOn() {
    this.hasSamples = false;
    this.old = 0;
    this.older = 0;
    this.currentAddress = this.startAddress;
    this.adsrCounter = 0;
    this.adsrVolume = 0;
    this.adsrPhase = Phase.Attack;
  }

  public void keyOff() {
    this.adsrCounter = 0;
    this.adsrPhase = Phase.Release;
  }

  public byte[] spuAdpcm = new byte[16];
  public short[] decodedSamples = new short[28];

  public void decodeSamples(final byte[] ram, final short ramIrqAddress) {
    //save the last 3 samples from the last decoded block
    //this are needed for interpolation in case the index is 0 1 or 2
    this.lastBlockSample28 = this.decodedSamples[this.decodedSamples.length - 1];
    this.lastBlockSample27 = this.decodedSamples[this.decodedSamples.length - 2];
    this.lastBlockSample26 = this.decodedSamples[this.decodedSamples.length - 3];

    System.arraycopy(ram, this.currentAddress * 8, this.spuAdpcm, 0, 16);

    //ramIrqAddress is >> 8 so we only need to check for currentAddress and + 1
    this.readRamIrq |= this.currentAddress == ramIrqAddress || this.currentAddress + 1 == ramIrqAddress;

    final int shift = 12 - (this.spuAdpcm[0] & 0x0F);
    int filter = (this.spuAdpcm[0] & 0x70) >> 4; //filter on SPU adpcm is 0-4 vs XA which is 0-3
    if(filter > 4) {
      filter = 4; //Crash Bandicoot sets this to 7 at the end of the first level and overflows the filter
    }

    final int f0 = positiveXaAdpcmTable[filter];
    final int f1 = negativeXaAdpcmTable[filter];

    //Actual ADPCM decoding is the same as on XA but the layout here is sequential by nibble where on XA in grouped by nibble line
    int position = 2; //skip shift and flags
    int nibble = 1;
    for(int i = 0; i < 28; i++) {
      nibble = nibble + 1 & 0x1;

      final int t = signed4bit((byte)(this.spuAdpcm[position] >> nibble * 4 & 0x0F));
      final int s = (t << shift) + (this.old * f0 + this.older * f1 + 32) / 64;
      final short sample = (short)MathHelper.clamp(s, -0x8000, 0x7FFF);

      this.decodedSamples[i] = sample;

      this.older = this.old;
      this.old = sample;

      position += nibble;
    }
  }

  public static int signed4bit(final byte value) {
    return value << 28 >> 28;
  }

  public short processVolume(final Volume volume) {
    if(volume.isSweepMode()) {
      return 0; //todo handle sweep mode volume envelope
    }

    return volume.fixedVolume();
  }

  public short getSample(final int i) {
    if(i == -3) {
      return this.lastBlockSample26;
    }

    if(i == -2) {
      return this.lastBlockSample27;
    }

    if(i == -1) {
      return this.lastBlockSample28;
    }

    return this.decodedSamples[i];
  }

  int adsrCounter;

  public void tickAdsr(final int v) {
    if(this.adsrPhase == Phase.Off) {
      this.adsrVolume = 0;
      return;
    }

    final int adsrTarget;
    final int adsrShift;
    final int adsrStep;
    final boolean isDecreasing;
    final boolean isExponential;

    //Todo move out of tick the actual change of phase
    switch(this.adsrPhase) {
      case Attack -> {
        adsrTarget = 0x7FFF;
        adsrShift = this.adsr.attackShift();
        adsrStep = 7 - this.adsr.attackStep(); // reg is 0-3 but values are "+7,+6,+5,+4"
        isDecreasing = false; // Always increase till 0x7FFF
        isExponential = this.adsr.isAttackModeExponential();
      }
      case Decay -> {
        adsrTarget = (this.adsr.sustainLevel() + 1) * 0x800;
        adsrShift = this.adsr.decayShift();
        adsrStep = -8;
        isDecreasing = true; // Always decreases (until target)
        isExponential = true; // Always exponential
      }
      case Sustain -> {
        adsrTarget = 0;
        adsrShift = this.adsr.sustainShift();
        adsrStep = this.adsr.isSustainDirectionDecrease() ? -8 + this.adsr.sustainStep() : 7 - this.adsr.sustainStep();
        isDecreasing = this.adsr.isSustainDirectionDecrease(); // Until keyoff
        isExponential = this.adsr.isSustainModeExponential();
      }
      case Release -> {
        adsrTarget = 0;
        adsrShift = this.adsr.releaseShift();
        adsrStep = -8;
        isDecreasing = true; // Always decrease till 0
        isExponential = this.adsr.isReleaseModeExponential();
      }
      default -> {
        adsrTarget = 0;
        adsrShift = 0;
        adsrStep = 0;
        isDecreasing = false;
        isExponential = false;
      }
    }

    //Envelope Operation depending on Shift/Step/Mode/Direction
    //AdsrCycles = 1 SHL Max(0, ShiftValue-11)
    //AdsrStep = StepValue SHL Max(0,11-ShiftValue)
    //IF exponential AND increase AND AdsrLevel>6000h THEN AdsrCycles=AdsrCycles*4
    //IF exponential AND decrease THEN AdsrStep = AdsrStep * AdsrLevel / 8000h
    //Wait(AdsrCycles); cycles counted at 44.1kHz clock
    //AdsrLevel=AdsrLevel+AdsrStep  ;saturated to 0..+7FFFh

    if(this.adsrCounter > 0) {
      this.adsrCounter--;
      return;
    }

    int envelopeCycles = 1 << Math.max(0, adsrShift - 11);
    int envelopeStep = adsrStep << Math.max(0, 11 - adsrShift);
    if(isExponential && !isDecreasing && this.adsrVolume > 0x6000) {
      envelopeCycles *= 4;
    }
    if(isExponential && isDecreasing) {
      envelopeStep = envelopeStep * this.adsrVolume >> 15;
    }

    this.adsrVolume = (short)MathHelper.clamp(this.adsrVolume + envelopeStep, 0, 0x7FFF);
    this.adsrCounter = envelopeCycles;

    final boolean nextPhase = isDecreasing ? this.adsrVolume <= adsrTarget : this.adsrVolume >= adsrTarget;
    if(nextPhase && this.adsrPhase != Phase.Sustain) {
      this.adsrPhase = this.adsrPhase.next();
      this.adsrCounter = 0;
    }
  }

  public class VoiceSegment extends Segment {
    public VoiceSegment(final long address) {
      super(address, 0x10);
    }

    @Override
    public byte get(final int offset) {
      throw new MisalignedAccessException("SPU registers may only be accessed via 16-bit reads or writes");
    }

    @Override
    public long get(final int offset, final int size) {
      if(size != 2) {
        throw new MisalignedAccessException("SPU registers may only be accessed via 16-bit reads or writes");
      }

      return switch(offset & 0xe) {
        case 0x0 -> Voice.this.volumeLeft.get();
        case 0x2 -> Voice.this.volumeRight.get();
        case 0x4 -> Voice.this.pitch & 0xffffL;
        case 0x6 -> Voice.this.startAddress & 0xffffL;
        case 0x8 -> Voice.this.adsr.lo & 0xffffL;
        case 0xa -> Voice.this.adsr.hi & 0xffffL;
        case 0xc -> Voice.this.adsrVolume & 0xffffL;
        case 0xe -> Voice.this.adpcmRepeatAddress & 0xffffL;
        default -> throw new MisalignedAccessException("SPU voice port " + Long.toHexString(offset) + " does not exist");
      };
    }

    @Override
    public void set(final int offset, final byte value) {
      throw new MisalignedAccessException("SPU registers may only be accessed via 16-bit reads or writes");
    }

    @Override
    public void set(final int offset, final int size, final long value) {
      if(size != 2) {
        throw new MisalignedAccessException("SPU registers may only be accessed via 16-bit reads or writes");
      }

      switch(offset & 0xe) {
        case 0x0 -> Voice.this.volumeLeft.set(value);
        case 0x2 -> Voice.this.volumeRight.set(value);
        case 0x4 -> Voice.this.pitch = (short)value;
        case 0x6 -> Voice.this.startAddress = (short)value;
        case 0x8 -> Voice.this.adsr.lo = (short)value;
        case 0xa -> Voice.this.adsr.hi = (short)value;
        case 0xc -> Voice.this.adsrVolume = (short)value;
        case 0xe -> Voice.this.adpcmRepeatAddress = (short)value;
        default -> throw new MisalignedAccessException("SPU voice port " + Long.toHexString(offset) + " does not exist");
      };
    }
  }
}
