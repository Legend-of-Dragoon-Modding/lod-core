package legend.core.kernel;

import legend.core.DebugHelper;
import legend.core.cdrom.CdlLOC;
import legend.core.cdrom.SyncCode;
import legend.core.memory.Memory;
import legend.core.memory.Method;
import legend.core.memory.Value;
import legend.core.memory.types.ArrayRef;
import legend.core.memory.types.BiConsumerRef;
import legend.core.memory.types.BiFunctionRef;
import legend.core.memory.types.CString;
import legend.core.memory.types.ConsumerRef;
import legend.core.memory.types.DIRENTRY;
import legend.core.memory.types.IntRef;
import legend.core.memory.types.Pointer;
import legend.core.memory.types.ProcessControlBlock;
import legend.core.memory.types.RunnableRef;
import legend.core.memory.types.SupplierRef;
import legend.core.memory.types.ThreadControlBlock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;

import static legend.core.Hardware.CDROM;
import static legend.core.Hardware.CPU;
import static legend.core.Hardware.DMA;
import static legend.core.Hardware.ENTRY_POINT;
import static legend.core.Hardware.GATE;
import static legend.core.Hardware.MEMCARD;
import static legend.core.Hardware.MEMORY;
import static legend.core.Hardware.SPU;
import static legend.core.InterruptController.I_MASK;
import static legend.core.InterruptController.I_STAT;
import static legend.core.MathHelper.toBcd;
import static legend.core.MemoryHelper.getMethodAddress;
import static legend.core.cdrom.CdDrive.CDROM_REG0;
import static legend.core.cdrom.CdDrive.CDROM_REG1;
import static legend.core.cdrom.CdDrive.CDROM_REG2;
import static legend.core.cdrom.CdDrive.CDROM_REG3;
import static legend.core.dma.DmaManager.DMA_DICR;
import static legend.core.dma.DmaManager.DMA_DPCR;
import static legend.core.gpu.Gpu.GPU_REG0;
import static legend.core.gpu.Gpu.GPU_REG1;
import static legend.core.kernel.Kernel.EvMdNOINTR;
import static legend.core.kernel.Kernel.EvSpACK;
import static legend.core.kernel.Kernel.EvSpCOMP;
import static legend.core.kernel.Kernel.EvSpDE;
import static legend.core.kernel.Kernel.EvSpDR;
import static legend.core.kernel.Kernel.EvSpERROR;
import static legend.core.kernel.Kernel.EvSpIOE;
import static legend.core.kernel.Kernel.EvSpNEW;
import static legend.core.kernel.Kernel.EvSpTIMOUT;
import static legend.core.kernel.Kernel.EvSpUNKNOWN;
import static legend.core.kernel.Kernel.HwCdRom;
import static legend.core.kernel.Kernel.SwCARD;
import static legend.core.memory.segments.ExpansionRegion1Segment.EXPANSION_REGION_1;
import static legend.core.memory.segments.ExpansionRegion2Segment.BOOT_STATUS;
import static legend.core.memory.segments.ExpansionRegion2Segment.EXPANSION_REGION_2;
import static legend.core.memory.segments.MemoryControl1Segment.BIOS_ROM;
import static legend.core.memory.segments.MemoryControl1Segment.CDROM_DELAY;
import static legend.core.memory.segments.MemoryControl1Segment.COMMON_DELAY;
import static legend.core.memory.segments.MemoryControl1Segment.EXP1_BASE_ADDR;
import static legend.core.memory.segments.MemoryControl1Segment.EXP1_DELAY_SIZE;
import static legend.core.memory.segments.MemoryControl1Segment.EXP2_BASE_ADDR;
import static legend.core.memory.segments.MemoryControl1Segment.EXP2_DELAY_SIZE;
import static legend.core.memory.segments.MemoryControl1Segment.EXP3_DELAY_SIZE;
import static legend.core.memory.segments.MemoryControl1Segment.SPU_DELAY;
import static legend.core.memory.segments.MemoryControl2Segment.RAM_SIZE;

public final class Bios {
  private Bios() { }

  private static final Logger LOGGER = LogManager.getFormatterLogger(Bios.class);

  private static final Object[] EMPTY_OBJ_ARRAY = new Object[0];

  public static final BiFunctionRef<Long, Object[], Object> functionVectorA_000000a0 = MEMORY.ref(4, 0x000000a0L, BiFunctionRef::new);
  public static final BiFunctionRef<Long, Object[], Object> functionVectorB_000000b0 = MEMORY.ref(4, 0x000000b0L, BiFunctionRef::new);
  public static final BiFunctionRef<Long, Object[], Object> functionVectorC_000000c0 = MEMORY.ref(4, 0x000000c0L, BiFunctionRef::new);

  public static final Value argv_00000180 = MEMORY.ref(1, 0x00000180L);

  public static final Value jumpTableA_00000200 = MEMORY.ref(4, 0x00000200L);

  public static final Pointer<ArrayRef<Pointer<PriorityChainEntry>>> ExceptionChainPtr_a0000100 = MEMORY.ref(4, 0xa0000100L, Pointer.of(0x20, ArrayRef.of(Pointer.classFor(PriorityChainEntry.class), 4, 4, 8, Pointer.of(0x10, PriorityChainEntry::new))));
  public static final Value ExceptionChainSize_a0000104 = MEMORY.ref(4, 0xa0000104L);
  public static final Pointer<ProcessControlBlock> ProcessControlBlockPtr_a0000108 = MEMORY.ref(4, 0xa0000108L, Pointer.of(4, ProcessControlBlock::new));
  public static final Value ProcessControlBlockSize_a000010c = MEMORY.ref(4, 0xa000010cL);
  public static final Value ThreadControlBlockAddr_a0000110 = MEMORY.ref(4, 0xa0000110L);
  public static final Value ThreadControlBlockSize_a0000114 = MEMORY.ref(4, 0xa0000114L);

  public static final Value EventControlBlockAddr_a0000120 = MEMORY.ref(4, 0xa0000120L);
  public static final Value EventControlBlockSize_a0000124 = MEMORY.ref(4, 0xa0000124L);

  public static final Value kernelStart_a0000500 = MEMORY.ref(4, 0xa0000500L);
  public static final Value abcFunctionVectorsStart_a0000510 = MEMORY.ref(4, 0xa0000510L);

  public static final Value randSeed_a0009010 = MEMORY.ref(4, 0xa0009010L);

  public static final Value _a0009150 = MEMORY.ref(4, 0xa0009150L);
  public static final Value _a0009154 = MEMORY.ref(4, 0xa0009154L);
  public static final Value _a0009158 = MEMORY.ref(4, 0xa0009158L);
  public static final Value _a000915c = MEMORY.ref(4, 0xa000915cL);
  public static final Value _a0009160 = MEMORY.ref(4, 0xa0009160L);
  public static final Value _a0009164 = MEMORY.ref(4, 0xa0009164L);

  public static final Value _a000917c = MEMORY.ref(4, 0xa000917cL);
  public static final Value _a0009180 = MEMORY.ref(4, 0xa0009180L);

  public static final Value _a0009188 = MEMORY.ref(4, 0xa0009188L);
  public static final Value _a000918c = MEMORY.ref(4, 0xa000918cL);
  public static final Value _a0009190 = MEMORY.ref(4, 0xa0009190L);

  public static final Value _a0009198 = MEMORY.ref(4, 0xa0009198L);

  public static final Value _a00091ac = MEMORY.ref(4, 0xa00091acL);

  public static final Value _a00091c4 = MEMORY.ref(4, 0xa00091c4L);
  public static final Value _a00091c8 = MEMORY.ref(4, 0xa00091c8L);
  public static final Value _a00091cc = MEMORY.ref(4, 0xa00091ccL);
  public static final PriorityChainEntry _a00091d0 = MEMORY.ref(4, 0xa00091d0L, PriorityChainEntry::new);
  public static final PriorityChainEntry _a00091e0 = MEMORY.ref(4, 0xa00091e0L, PriorityChainEntry::new);
  public static final Value _a00091f0 = MEMORY.ref(4, 0xa00091f0L);

  public static final Value _a00091fc = MEMORY.ref(4, 0xa00091fcL);

  public static final Value _a000958c = MEMORY.ref(4, 0xa000958cL);

  public static final Value _a00095b0 = MEMORY.ref(4, 0xa00095b0L);

  public static final Value _a00095bc = MEMORY.ref(4, 0xa00095bcL);

  public static final Value _a0009d70 = MEMORY.ref(4, 0xa0009d70L);
  public static final Value _a0009d74 = MEMORY.ref(4, 0xa0009d74L);
  public static final Value _a0009d78 = MEMORY.ref(4, 0xa0009d78L);
  public static final Value _a0009d7c = MEMORY.ref(4, 0xa0009d7cL);
  public static final Value _a0009d80 = MEMORY.ref(1, 0xa0009d80L);

  public static final Value _a0009e00 = MEMORY.ref(4, 0xa0009e00L);
  public static final Value _a0009e04 = MEMORY.ref(4, 0xa0009e04L);
  public static final Value _a0009e08 = MEMORY.ref(4, 0xa0009e08L);

  public static final Value _a0009e10 = MEMORY.ref(4, 0xa0009e10L);

  public static final Value _a0009e18 = MEMORY.ref(1, 0xa0009e18L);

  public static final Value _a0009f20 = MEMORY.ref(4, 0xa0009f20L);
  public static final Value _a0009f24 = MEMORY.ref(4, 0xa0009f24L);
  public static final Value _a0009f28 = MEMORY.ref(4, 0xa0009f28L);
  public static final Value _a0009f30 = MEMORY.ref(4, 0xa0009f30L);

  public static final Value _a0009f50 = MEMORY.ref(4, 0xa0009f50L);

  public static final Value _a0009f58 = MEMORY.ref(4, 0xa0009f58L);

  public static final Value _a0009f60 = MEMORY.ref(4, 0xa0009f60L);

  public static final Value _a0009f68 = MEMORY.ref(4, 0xa0009f68L);

  public static final Value _a0009f70 = MEMORY.ref(4, 0xa0009f70L);

  public static final Value _a0009f78 = MEMORY.ref(4, 0xa0009f78L);

  public static final Value _a0009f80 = MEMORY.ref(4, 0xa0009f80L);

  public static final Value _a0009f88 = MEMORY.ref(2, 0xa0009f88L);

  public static final Value memcardFileIndex_a0009f8c = MEMORY.ref(4, 0xa0009f8cL);

  public static final Value _a0009f90 = MEMORY.ref(1, 0xa0009f90L);

  /** String */
  public static final Value _a0009f98 = MEMORY.ref(1, 0xa0009f98L);

  public static final Value _a0009fac = MEMORY.ref(4, 0xa0009facL);

  public static final Value _a000b068 = MEMORY.ref(4, 0xa000b068L);

  public static final Value _a000b070 = MEMORY.ref(4, 0xa000b070L);
  public static final Value _a000b071 = MEMORY.ref(1, 0xa000b071L);

  public static final Value _a000b078 = MEMORY.ref(4, 0xa000b078L);

  public static final Value _a000b080 = MEMORY.ref(1, 0xa000b080L);

  public static final Value _a000b0c0 = MEMORY.ref(4, 0xa000b0c0L);

  public static final Value _a000b0f4 = MEMORY.ref(4, 0xa000b0f4L);

  public static final Value _a000b0fc = MEMORY.ref(4, 0xa000b0fcL);

  public static final Value _a000b10e = MEMORY.ref(1, 0xa000b10eL);
  public static final Value _a000b10f = MEMORY.ref(1, 0xa000b10fL);
  public static final Value _a000b110 = MEMORY.ref(1, 0xa000b110L);
  public static final Value _a000b111 = MEMORY.ref(1, 0xa000b111L);

  public static final EXEC _a000b870 = MEMORY.ref(4, 0xa000b870L, EXEC::new);

  public static final Value _a000b890 = MEMORY.ref(4, 0xa000b890L);
  public static final Value _a000b894 = MEMORY.ref(4, 0xa000b894L);

  public static final Value _a000b8b0 = MEMORY.ref(1, 0xa000b8b0L);

  public static final Value _a000b938 = MEMORY.ref(4, 0xa000b938L);
  public static final Value _a000b93c = MEMORY.ref(4, 0xa000b93cL);
  public static final Value _a000b940 = MEMORY.ref(4, 0xa000b940L);
  public static final Value _a000b944 = MEMORY.ref(4, 0xa000b944L);
  public static final Value _a000b948 = MEMORY.ref(4, 0xa000b948L);

  public static final jmp_buf jmp_buf_a000b980 = MEMORY.ref(0x10, 0xa000b980L, jmp_buf::new);

  public static final IntRef ttyFlag_a000b9b0 = MEMORY.ref(4, 0xa000b9b0L, IntRef::new);

  public static final Value EventId_HwCdRom_EvSpACK_a000b9b8 = MEMORY.ref(1, 0xa000b9b8L);
  public static final Value EventId_HwCdRom_EvSpCOMP_a000b9bc = MEMORY.ref(1, 0xa000b9bcL);
  public static final Value EventId_HwCdRom_EvSpDR_a000b9c0 = MEMORY.ref(1, 0xa000b9c0L);
  public static final Value EventId_HwCdRom_EvSpDE_a000b9c4 = MEMORY.ref(1, 0xa000b9c4L);
  public static final Value EventId_HwCdRom_EvSpERROR_a000b9c8 = MEMORY.ref(1, 0xa000b9c8L);

  public static final Value memcardOkay_a000b9d0 = MEMORY.ref(4, 0xa000b9d0L);
  public static final Value memcardError_a000b9d4 = MEMORY.ref(4, 0xa000b9d4L);
  public static final Value memcardBusy_a000b9d8 = MEMORY.ref(4, 0xa000b9d8L);
  public static final Value memcardEjected_a000b9dc = MEMORY.ref(4, 0xa000b9dcL);
  public static final Value _a000b9e0 = MEMORY.ref(4, 0xa000b9e0L);

  public static final Value _a000b9e4 = MEMORY.ref(4, 0xa000b9e4L);
  public static final Value _a000b9e8 = MEMORY.ref(4, 0xa000b9e8L);

  public static final Value _a000ba88 = MEMORY.ref(4, 0xa000ba88L);
  public static final Value _a000ba8c = MEMORY.ref(4, 0xa000ba8cL);
  public static final Value _a000ba90 = MEMORY.ref(4, 0xa000ba90L);

  public static final Value _a000bc68 = MEMORY.ref(4, 0xa000bc68L);
  public static final Value _a000bc6c = MEMORY.ref(4, 0xa000bc6cL);
  public static final Value _a000bc70 = MEMORY.ref(4, 0xa000bc70L);

  public static final Value _a000be48 = MEMORY.ref(1, 0xa000be48L);

  public static final Value responseFromBootMenu_a000dffc = MEMORY.ref(1, 0xa000dffcL);
  public static final Value kernelMemoryStart_a000e000 = MEMORY.ref(1, 0xa000e000L);

  public static final Value userRamStart_a0010000 = MEMORY.ref(4, 0xa0010000L);

  public static final Value jumpTableARom_bfc04300 = MEMORY.ref(4, 0xbfc04300L);

  public static final Value _bfc0ddb1 = MEMORY.ref(1, 0xbfc0ddb1L);

  public static final Value _bfc0e14c = MEMORY.ref(1, 0xbfc0e14cL);

  public static final Value CdromDeviceInfo_bfc0e2f0 = MEMORY.ref(12, 0xbfc0e2f0L);

  public static final Value DummyTtyDeviceInfo_bfc0e350 = MEMORY.ref(12, 0xbfc0e350L);

  public static final Value MemCardDeviceInfo_bfc0e3e4 = MEMORY.ref(12, 0xbfc0e3e4L);

  public static final Value kernelStartRom_bfc10000 = MEMORY.ref(4, 0xbfc10000L);

  @Method(0xbfc00000L)
  public static void main() {
    LOGGER.info("Executing BIOS");

    BIOS_ROM.setu(0x13243fL);
    RAM_SIZE.setu(0xb88L);
    FUN_bfc00150();
  }

  @Method(0xbfc00150L)
  public static void FUN_bfc00150() {
    COMMON_DELAY.setu(0x3_1125L);
    EXP1_BASE_ADDR.setu(EXPANSION_REGION_1.getAddress());
    EXP2_BASE_ADDR.setu(EXPANSION_REGION_2.getAddress());
    EXP1_DELAY_SIZE.setu(0x13_243fL);
    SPU_DELAY.setu(0x2009_31e1L);
    CDROM_DELAY.setu(0x2_0843L);
    EXP3_DELAY_SIZE.setu(0x3022L);
    EXP2_DELAY_SIZE.setu(0x7_0777L);

    // Clear cache, clear RAM, clear CP0 registers...

    RAM_SIZE.setu(0xb88L);
    MEMORY.set(0x60L, 4, 0x02L);
    MEMORY.set(0x64L, 4, 0x00L);
    MEMORY.set(0x68L, 4, 0xffL);

    SPU.MAIN_VOL_L.set(0);
    SPU.MAIN_VOL_R.set(0);
    SPU.REVERB_OUT_L.set(0);
    SPU.REVERB_OUT_R.set(0);

    FUN_bfc06ec4();
  }

  /**
   * See no$ "BIOS Memory Map"
   */
  @Method(0xbfc00420L)
  public static void copyKernelSegment2() {
    //LAB_bfc00434
    MEMORY.memcpy(kernelStart_a0000500.getAddress(), kernelStartRom_bfc10000.getAddress(), 0x8bf0);
    MEMORY.addFunctions(Kernel.class);
    kernelStart_a0000500.cast(RunnableRef::new).run();
  }

  @Method(0xbfc008a0L)
  public static void loadCnf(final long a0, final long a1, final long a2) {
    //LAB_bfc008bc
    for(int i = 0; i < 3; i++) {
      MEMORY.ref(4, a1).offset(i * 4).setu(0);
    }

    MEMORY.ref(1, a2).setu(0);
    argv_00000180.setu(0);

    getCnfInt(a0, a1, "TCB");
    getCnfInt(a0, a1 + 0x4L, "EVENT");
    getCnfInt(a0, a1 + 0x8L, "STACK");
    getCnfString(a0, a2, argv_00000180.getAddress(), "BOOT");
  }

  @Method(0xbfc00944L)
  public static void getCnfInt(final long a0, final long dest, final String a2) {
    long s0 = a0;

    if(MEMORY.ref(1, s0).get() != 0) {
      //LAB_bfc00978
      //LAB_bfc009a4
      while(strncmp_Impl_A18(MEMORY.ref(1, s0).getString(), a2, a2.length()) != 0) {
        char c = (char)MEMORY.ref(1, s0).get();

        //LAB_bfc009b4
        while(c != '\n') {
          if(c == '\0') {
            return;
          }

          s0++;
          c = (char)MEMORY.ref(1, s0).get();
        }

        //LAB_bfc009cc
        if(c == '\0') {
          break;
        }

        s0++;
      }
    }

    //LAB_bfc009fc
    s0 += a2.length();
    char c = (char)MEMORY.ref(1, s0).get();

    //LAB_bfc00a30
    while(_bfc0ddb1.offset(c).get(0x8L) != 0) {
      if(c == '\n' || c == '\0') {
        return;
      }

      s0++;
      c = (char)MEMORY.ref(1, s0).get();
    }

    //LAB_bfc00a64
    if(c != '=' || c == '\n' || c == '\0') {
      return;
    }

    s0++;
    c = (char)MEMORY.ref(1, s0).get();
    long v0 = _bfc0ddb1.offset(c).get();

    //LAB_bfc00aa4
    while((v0 & 0x8L) != 0) {
      if(c == '\n' || c == '\0') {
        return;
      }

      s0++;
      c = (char)MEMORY.ref(1, s0).get();
      v0 = _bfc0ddb1.offset(c).get();
    }

    //LAB_bfc00ae0
    long s1 = 0;

    //LAB_bfc00aec
    while((v0 & 0x44L) != 0) {
      if((v0 & 0x4L) == 0) {
        //LAB_bfc00b04
        s1 = toupper_Impl_A25(c) + s1 * 16 - 0x37L;
      } else {
        s1 = c + s1 * 16 - 0x30L;
      }

      //LAB_bfc00b20
      s0++;
      c = (char)MEMORY.ref(1, s0).get();
      v0 = _bfc0ddb1.offset(c).get();
    }

    //LAB_bfc00b48
    MEMORY.ref(4, dest).setu(s1);
    LOGGER.info("%s\t%08x", a2, s1);

    //LAB_bfc00b68;
  }

  @Method(0xbfc00b7cL)
  public static void getCnfString(final long a0, final long a1, final long a2, final String a3) {
    if(MEMORY.ref(1, a0).get() == 0) {
      return;
    }

    //LAB_bfc00bc4
    long s0 = a0;
    do {
      if(strncmp_Impl_A18(MEMORY.ref(1, s0).getString(), a3, a3.length()) == 0) {
        s0 += a3.length();
        long v0 = MEMORY.ref(1, s0).get();

        //LAB_bfc00c04
        while(_bfc0ddb1.offset(v0).get(0x8L) != 0) {
          if(v0 == 0xaL || v0 == 0) {
            return;
          }

          s0++;
          v0 = MEMORY.ref(1, s0).get();
        }

        //LAB_bfc00c38
        if(v0 != 0x3dL || v0 == 0xaL || v0 == 0) {
          return;
        }

        s0++;
        v0 = MEMORY.ref(1, s0).get();

        //LAB_bfc00c74
        while(_bfc0ddb1.offset(v0).get(0x8L) != 0) {
          if(v0 == 0xaL || v0 == 0) {
            return;
          }

          s0++;
          v0 = MEMORY.ref(1, s0).get();
        }

        //LAB_bfc00ca8
        final long v = s0;

        //LAB_bfc00cb0
        while(v0 != 0xaL) {
          s0++;
          v0 = MEMORY.ref(1, s0).get();
        }

        //LAB_bfc00cc0
        MEMORY.ref(1, s0).setu(0);

        //LAB_bfc00ce4
        v0 = v;
        while(_bfc0ddb1.offset(MEMORY.ref(1, v0)).get(0x8L) == 0) {
          v0++;
        }

        //LAB_bfc00d04
        MEMORY.ref(1, v0).setu(0);
        MEMORY.ref(1, a1).set(MEMORY.ref(1, v).getString());
        MEMORY.ref(1, a2).set(MEMORY.ref(1, v0).offset(0x1L).getString(0x80));
        LOGGER.info("BOOT =\t%s", MEMORY.ref(1, a1).getString());
        LOGGER.info("argument =\t%s", MEMORY.ref(1, a2).getString());
        return;
      }

      //LAB_bfc00d50
      long v0 = MEMORY.ref(1, s0).get();

      //LAB_bfc00d60
      while(v0 != 0xaL) {
        if(v0 == 0) {
          return;
        }

        s0++;
        v0 = MEMORY.ref(1, s0).get();
      }

      //LAB_bfc00d78
      s0++;
    } while(MEMORY.ref(1, s0).get() != 0);

    //LAB_bfc00d8c
  }

  @Method(0xbfc01920L)
  public static void FlushCache_Impl_A44() {
    // Flush cache not necessary - we don't cache opcodes
  }

  @Method(0xbfc01a60L)
  public static void setBootStatus(final long bootCode) {
    LOGGER.info("Boot status: %02x", bootCode);

    BOOT_STATUS.setu(bootCode);
    FUN_bfc03990();
  }

  @Method(0xbfc01accL)
  public static long bzero_Impl_A28(final long dst, final int size) {
    if(dst == 0 || size <= 0) {
      return 0;
    }

    MEMORY.waitForLock(() -> {
      long dest = dst;
      long s = size;
      MEMORY.disableAlignmentChecks();
      for(; s >= 8; s -= 8, dest += 8) {
        MEMORY.set(dest, 8, 0);
      }
      MEMORY.enableAlignmentChecks();

      for(int i = 0; i < s; i++) {
        MEMORY.set(dst, (byte)0);
      }
    });

    return dst;
  }

  @Method(0xbfc02200L)
  public static int rand_Impl_A2f() {
    final long v1 = (randSeed_a0009010.get() * 0x41c6_4e6dL & 0xffff_ffffL) + 0x3039L;
    final long v0 = v1 >>> 16;
    randSeed_a0009010.setu(v1);
    return (int)(v0 & 0x7fff);
  }

  @Method(0xbfc02230L)
  public static void srand_Impl_A30(final long seed) {
    randSeed_a0009010.setu(seed);
  }

  @Method(0xbfc02240L)
  public static void setjmp_Impl_A13(final jmp_buf buffer, final RunnableRef callback) {
    buffer.set(callback);
  }

  @Method(0xbfc0227cL)
  public static int longjmp_Impl_A14(final jmp_buf buffer, final int value) {
    throw new RuntimeException("Not implemented");
  }

  @Method(0xbfc02918L)
  public static int abs_Impl_A0e(final int val) {
    return Math.abs(val);
  }

  @Method(0xbfc02b50L)
  public static long memcpy_Impl_A2a(final long dst, final long src, final int size) {
    if(dst == 0) {
      return 0;
    }

    if(size <= 0) {
      return dst;
    }

    MEMORY.memcpy(dst, src, size);
    return dst;
  }

  @Method(0xbfc02ea0L)
  public static char toupper_Impl_A25(final char c) {
    return Character.toUpperCase(c);
  }

  @Method(0xbfc03190L)
  @Nullable
  public static String strcat_Impl_15(@Nullable final CString dest, @Nullable final String src) {
    if(dest == null) {
      return null;
    }

    final String out = dest.get() + src;
    dest.set(out);
    return out;
  }

  @Method(0xbfc03288L)
  public static int strcmp_Impl_A17(final String s1, final String s2) {
    return s1.compareToIgnoreCase(s2);
  }

  @Method(0xbfc03310L)
  public static int strncmp_Impl_A18(final String s1, final String s2, final int n) {
    return s1.substring(0, Math.min(s1.length(), n)).compareToIgnoreCase(s2.substring(0, Math.min(s2.length(), n)));
  }

  @Method(0xbfc033c8L)
  @Nullable
  public static CString strcpy_Impl_A19(@Nullable final CString dest, @Nullable final String src) {
    if(dest == null || src == null) {
      return null;
    }

    dest.set(src);
    return dest;
  }

  @Method(0xbfc03418L)
  @Nullable
  public static CString strncpy_Impl_A1a(@Nullable final CString str1, @Nullable final CString str2, final int len) {
    if(str1 == null || str2 == null) {
      //LAB_bfc03428
      return null;
    }

    //LAB_bfc03430
    long a0 = str1.getAddress();
    long a1 = str2.getAddress();

    //LAB_bfc0343c
    for(int i = 0; i < len; i++) {
      final long a3 = MEMORY.ref(1, a1).getSigned();
      MEMORY.ref(1, a0).setu(a3);

      a0++;
      a1++;

      if(a3 == 0) {
        i++;
        //LAB_bfc03460
        while(i < len) {
          MEMORY.ref(1, a0).setu(0);
          i++;
          a0++;
        }

        //LAB_bfc03474
        return MEMORY.ref(len, str1.getAddress(), CString::new);
      }

      //LAB_bfc0347c
    }

    //LAB_bfc03488
    return MEMORY.ref(len, str1.getAddress(), CString::new);
  }

  @Method(0xbfc03990L)
  public static void FUN_bfc03990() {
    _a000b068.setu(0);
  }

  @Method(0xbfc03a18L)
  public static boolean LoadExeFile_Impl_A42(final String filename, final long header) {
    final int fd = open(filename, 1);
    if(fd < 0) {
      return false;
    }

    //LAB_bfc03a3c
    if(!FUN_bfc03c90(fd, header)) {
      close(fd);
      return false;
    }

    //LAB_bfc03a68
    read(fd, MEMORY.ref(4, header).offset(0x8L).get(), (int)MEMORY.ref(4, header).offset(0xcL).get());
    close(fd);
    FlushCache_Impl_A44();

    //LAB_bfc03a94
    return true;
  }

  @Method(0xbfc03c90L)
  public static boolean FUN_bfc03c90(final int fd, final long header) {
    final int bytesRead = read(fd, _a000b070.getAddress(), 0x800);

    if(bytesRead >= 0x800) {
      memcpy_Impl_A2a(header, _a000b080.getAddress(), 0x3c);
      return true;
    }

    return false;
  }

  private static boolean entrypointInitialized;

  @Method(0xbfc03cf0L)
  public static long Exec_Impl_A43(final EXEC header, final int argc, final long argv) {
    long b_size = header.b_size.get();
    long b_addr = header.b_addr.get();

    while((int)b_size > 0) {
      MEMORY.ref(4, b_addr).setu(0);
      b_addr += 0x4L;
      b_size -= 0x4L;
    }

    final Value entry = MEMORY.ref(4, header.pc0.get());

    GATE.release();

    if(ENTRY_POINT != null && !entrypointInitialized) {
      MEMORY.addFunctions(ENTRY_POINT);
      entrypointInitialized = true;
    }

    entry.cast(BiConsumerRef::new).run(argc, argv);

    GATE.acquire();

    return 0x1L;
  }

  @Method(0xbfc03facL)
  public static void GPU_cw_Impl_A49(final int gp0cmd) {
    gpu_sync_Impl_A4e();
    GPU_REG0.setu(gp0cmd);
  }

  @Method(0xbfc040ecL)
  public static void gpu_abort_dma_Impl_A4c() {
    DMA.gpu.CHCR.setu(0x401L);
    GPU_REG1.setu(0x400_0000L);
    GPU_REG1.setu(0x200_0000L);
    GPU_REG1.setu(0x100_0000L);
  }

  @Method(0xbfc04138L)
  public static int gpu_sync_Impl_A4e() {
    long v1 = 0x1000_0000L;
    if(GPU_REG1.get(0x6000_0000L) == 0) {
      //LAB_bfc04174
      while(GPU_REG1.get(0x1000_0000L) == 0) {
        if(v1 == 0) {
          FUN_bfc04260("GPU_sync(FG)");
          return 0xffff_ffff;
        }

        //LAB_bfc04190
        v1--;
      }

      //LAB_bfc041a4
      return 0;
    }

    //LAB_bfc041ac
    //LAB_bfc041d0
    while(DMA.gpu.CHCR.get(0x100_0000L) != 0) {
      if(v1 == 0) {
        FUN_bfc04260("GPU_sync(BG)");
        return 0xffff_ffff;
      }

      //LAB_bfc041ec
      v1--;
    }

    //LAB_bfc04200
    //LAB_bfc04214
    while(GPU_REG1.get(0x400_0000L) == 0) {
      DebugHelper.sleep(1);
    }

    //LAB_bfc04248
    GPU_REG1.setu(0x400_0000L);

    //LAB_bfc04250
    return 0;
  }

  @Method(0xbfc04260L)
  public static void FUN_bfc04260(final String log) {
    LOGGER.error("%s timeout: gp1=%08x", log, GPU_REG1.get());
    gpu_abort_dma_Impl_A4c();
  }

  @Method(0xbfc042a0L)
  public static void copyAbcFunctionVectors_Impl_A45() {
    MEMORY.memcpy(functionVectorA_000000a0.getAddress(), abcFunctionVectorsStart_a0000510.getAddress(), 0x30);
    MEMORY.addFunctions(FunctionVectors.class);
  }

  @Method(0xbfc042d0L)
  public static void copyJumpTableA() {
    MEMORY.memcpy(jumpTableA_00000200.getAddress(), jumpTableARom_bfc04300.getAddress(), 0x300);
  }

  @Method(0xbfc04610L)
  public static long allocateExceptionChain(final int count) {
    final int size = count * 8;
    final long mem = alloc_kernel_memory(size);

    if(mem == 0) {
      return 0;
    }

    //LAB_bfc04640
    bzero_Impl_A28(mem, size);
    ExceptionChainPtr_a0000100.set(MEMORY.ref(4, mem, ArrayRef.of(Pointer.classFor(PriorityChainEntry.class), 4, 4, 8, Pointer.of(0x10, PriorityChainEntry::new))));
    ExceptionChainSize_a0000104.setu(size);

    //LAB_bfc04668
    return size;
  }

  @Method(0xbfc04678L)
  public static long allocateEventControlBlock(final int count) {
    LOGGER.info("");
    LOGGER.info("Configuration : EvCB\t0x%02x\t\t", count);

    final int size = count * 28;
    final long addr = alloc_kernel_memory(size);
    if(addr == 0) {
      return 0;
    }

    EventControlBlockAddr_a0000120.setu(addr);
    EventControlBlockSize_a0000124.setu(size);

    for(int i = 0; i < count; i++) {
      MEMORY.ref(4, addr).offset(i * 28L).offset(0x4L).setu(0);
    }

    return size;
  }

  @Method(0xbfc0472cL)
  public static long allocateThreadControlBlock(final int processCount, final int threadCount) {
    LOGGER.info("TCB\t0x%02x", threadCount);

    final int processSize = processCount * 0x4;
    final int threadSize = threadCount * 0xc0;

    ProcessControlBlockSize_a000010c.setu(processSize);
    ThreadControlBlockSize_a0000114.setu(threadSize);

    final long processAddr = alloc_kernel_memory(processSize);
    if(processAddr == 0) {
      return 0;
    }

    //LAB_bfc04798
    final long threadAddr = alloc_kernel_memory(threadSize);
    if(threadAddr == 0) {
      return 0;
    }

    final ProcessControlBlock pcb = MEMORY.ref(4, processAddr, ProcessControlBlock::new);
    final ThreadControlBlock tcb = MEMORY.ref(4, threadAddr, ThreadControlBlock::new);

    //LAB_bfc047b8
    //LAB_bfc047d4
    for(int i = 0; i < processCount; i++) {
      MEMORY.ref(4, processAddr).offset(i * 4L).setu(0);
    }

    //LAB_bfc047e8
    //LAB_bfc0480c
    for(int i = 0; i < threadCount; i++) {
      MEMORY.ref(4, threadAddr).offset(i * 0xc0L).setu(0x1000L); // Uninitialized
    }

    //LAB_bfc0481c
    tcb.status.set(0x4000L); // Initialized
    pcb.threadControlBlockPtr.set(tcb);
    ProcessControlBlockPtr_a0000108.set(pcb);
    ThreadControlBlockAddr_a0000110.setu(threadAddr);

    //LAB_bfc0483c
    return processSize + threadSize;
  }

  @Method(0xbfc04850L)
  public static void EnqueueCdIntr_Impl_Aa2() {
    _a00091d0.next.clear();
    _a00091d0.secondFunction.set(MEMORY.ref(4, 0xbfc0506cL, ConsumerRef::new)); // CdromIoIrqFunc2_Impl_A92
    _a00091d0.firstFunction.set(MEMORY.ref(4, 0xbfc04decL, SupplierRef::new)); // CdromIoIrqFunc1_Impl_A90
    SysEnqIntRP(0, _a00091d0);

    _a00091e0.next.clear();
    _a00091e0.secondFunction.set(MEMORY.ref(4, 0xbfc050a4L, ConsumerRef::new)); // CdromDmaIrqFunc2_Impl_A93
    _a00091e0.firstFunction.set(MEMORY.ref(4, 0xbfc04fbcL, SupplierRef::new)); // CdromDmaIrqFunc1_Impl_A91
    SysEnqIntRP(0, _a00091e0);
  }

  @Method(0xbfc048d0L)
  public static void DequeueCdIntr_Impl_Aa3() {
    SysDeqIntRP(0, _a00091d0);
    SysDeqIntRP(0, _a00091e0);
  }

  @Method(0xbfc04910L)
  public static boolean CdInitSubFunc_Impl_A95() {
    _a00091c4.setu(0);
    _a00091c8.setu(0);
    _a00091cc.setu(0);

    EnterCriticalSection();

    I_MASK.and(0xfffffffbL);
    I_MASK.and(0xfffffff7L);
    I_STAT.setu(0xfffffffbL);
    I_STAT.setu(0xfffffff7L);

    _a000b938.setu(0x1L);
    _a000b93c.setu(0x1L);

    DMA_DPCR.setu(0x9099L);
    DMA_DICR.setu(0x0800_0000L | DMA_DICR.get(0xff_ffffL));

    _a0009154.setu(0xffffL);
    _a0009158.setu(0xffffL);
    _a000915c.setu(0);
    _a0009160.setu(0);

    acknowledgeCdromInterruptsAndClearParamBuffer();

    I_STAT.setu(0xfffffffbL);
    I_MASK.oru(0x4L);
    I_MASK.oru(0x8L);

    ExitCriticalSection();

    CDROM.init();
    return true;
  }

  @Method(0xbfc04abcL)
  public static int CdAsyncSeekL_Impl_A78(final CdlLOC src) {
    if(_a0009154.get() == 0xe6L || _a0009154.get() == 0xebL) {
      if(_a000915c.get() == 0) {
        return 0;
      }
    } else if(_a0009154.get() != 0xffffL) {
      return 0;
    }

    //LAB_bfc04b14
    if(CDROM_REG0.get(0x10L) != 0x10L) {
      return 0;
    }

    //LAB_bfc04b3c
    FUN_bfc064d8();

    if(_a0009154.get() == 0xe6L || _a0009154.get() == 0xebL) {
      //LAB_bfc04b60
      _a0009158.setu(_a0009154);
    }

    //LAB_bfc04b68
    CDROM_REG0.setu(0);
    CDROM_REG2.setu(toBcd(src.getMinute()));
    CDROM_REG2.setu(toBcd(src.getSecond()));
    CDROM_REG2.setu(toBcd(src.getSector()));
    CDROM_REG1.setu(0x2L);
    _a0009154.setu(0xf2L);

    //LAB_bfc04bb4
    return 1;
  }

  @Method(0xbfc04bc4L)
  public static int CdAsyncGetStatus_Impl_A7c(final long dest) {
    if(_a0009154.get() != 0xffffL) {
      return 0;
    }

    //LAB_bfc04be8
    FUN_bfc06548();
    _a0009164.setu(dest);
    CDROM_REG0.setu(0);
    _a0009154.setu(0x1L);
    CDROM_REG1.setu(0x1L);

    //LAB_bfc04c28
    return 1;
  }

  @Method(0xbfc04c38L)
  public static int CdAsyncReadSector_Impl_A7e(final int count, final long dest, final int mode) {
    if(_a0009154.get() != 0xffffL || count <= 0) {
      return 0;
    }

    //LAB_bfc04c64
    FUN_bfc06548();

    _a000917c.setu(count);
    _a0009180.setu(count);
    _a0009188.setu(dest);
    _a0009190.setu(mode);
    _a000915c.setu(0);

    if((mode & 0x10L) == 0) {
      //LAB_bfc04cbc
      if((mode & 0x20L) == 0) {
        //LAB_bfc04cdc
        _a0009198.setu(0x200L);
      } else {
        _a0009198.setu(0x249L);
      }
    } else {
      _a0009198.setu(0x246L);
    }

    //LAB_bfc04ce4
    if(CDROM_REG0.get(0x10L) != 0x10L) {
      return 0;
    }

    //LAB_bfc04d0c
    CDROM_REG0.setu(0);
    CDROM_REG2.setu(mode & 0xffL);
    _a0009154.setu(0xfeL);
    CDROM_REG1.setu(0xeL);

    //LAB_bfc04d40
    return 1;
  }

  @Method(0xbfc04decL)
  public static int CdromIoIrqFunc1_Impl_A90() {
    if(I_MASK.get(0x4L) == 0) {
      return 0;
    }

    //LAB_bfc04e18
    _a0009150.setu(I_STAT);
    if(I_STAT.get(0x4L) == 0) {
      return 0;
    }

    //LAB_bfc04e34
    CDROM_REG0.setu(0x1L);
    final long interruptFlag = CDROM_REG3.get();
    final long response = interruptFlag & 0b111L;
    final long v1 = interruptFlag & 0b1_1000L;

    if(response != 0) {
      CDROM_REG0.setu(0x1L);
      CDROM_REG3.setu(0x7L);

      //LAB_bfc04e88
//      for(int i = 0; i < 4; i++) {
//        MEMORY.ref(4, 0).setu(i);
//      }
    }

    //LAB_bfc04ebc
    if(v1 != 0) {
      CDROM_REG0.setu(0x1L);
      CDROM_REG3.setu(v1);

      //LAB_bfc04ee8
//      for(int i = 0; i < 4; i++) {
//        MEMORY.ref(4, 0).setu(i);
//      }
    }

    //LAB_bfc04f1c
    switch(SyncCode.fromLong(response)) {
      case DATA_READY -> {
        FUN_bfc0593c();
        return 0x1;
      }
      case COMPLETE -> {
        FUN_bfc05558();
        return 0x1;
      }
      case ACKNOWLEDGE -> {
        FUN_bfc05194();
        return 0x1;
      }
      case DATA_END -> {
        FUN_bfc05a44();
        return 0x1;
      }
      case DISK_ERROR -> {
        FUN_bfc057b0();
        return 0x1;
      }
    }

    //LAB_bfc04fac
    return 0x1;
  }

  @Method(0xbfc04fbcL)
  public static int CdromDmaIrqFunc1_Impl_A91() {
    if(I_MASK.get(0x8L) == 0) {
      return 0;
    }

    //LAB_bfc04fe8
    _a0009150.setu(I_STAT);
    if(I_STAT.get(0x8L) == 0) {
      return 0;
    }

    //LAB_bfc05004
    DMA_DICR.setu(DMA_DICR.get(0xff_ffffL) | 0x800_0000L);

    _a0009180.subu(0x1L);
    if(_a0009180.get() == 0) {
      DeliverEvent(HwCdRom, EvSpACK);
    }

    //LAB_bfc05050
    _a0009160.setu(0);

    //LAB_bfc0505c
    return 1;
  }

  @Method(0xbfc0506cL)
  public static void CdromIoIrqFunc2_Impl_A92(final long a0) {
    if(_a000b938.get() != 0) {
      I_STAT.setu(0xffff_fffbL);
      ReturnFromException();
    }
  }

  @Method(0xbfc050a4)
  public static void CdromDmaIrqFunc2_Impl_A93(final int a0) {
    if(_a000b93c.get() != 0) {
      I_STAT.setu(0xffff_fff7L);
      ReturnFromException();
    }
  }

  @Method(0xbfc050fcL)
  public static void FUN_bfc050fc() {
    CDROM_REG0.setu(0);
    CDROM_REG2.setu(0x1fL);
  }

  @Method(0xbfc05120L)
  public static void acknowledgeCdromInterruptsAndClearParamBuffer() {
    CDROM.acknowledgeInterrupts();
    CDROM.clearParamBuffer();
  }

  @Method(0xbfc05194L)
  public static void FUN_bfc05194() {
    final long v0 = _a0009154.get();

    if(v0 == 0x10L) {
      FUN_bfc05c50();
      return;
    }

    //LAB_bfc051bc
    if(v0 == 0x11L) {
      FUN_bfc05d04();
      return;
    }

    //LAB_bfc051d8
    //LAB_bfc05300
    if(v0 == 0x19L) {
      //LAB_bfc052d4
      FUN_bfc060dc(CDROM_REG1.get());
      return;
    }

    if(v0 < 0x1aL) {
      if(v0 == 0xeL) {
        //LAB_bfc052b4
        FUN_bfc061a0();
        return;
      }

      if(v0 < 0xfL) {
        if(v0 == 0x8L) {
          return;
        }

        if(v0 < 0x9L) {
          if(v0 == 0x5L) {
            //LAB_bfc05220
            //LAB_bfc05224
            _a000915c.setu(0x1L);
            DeliverEvent(HwCdRom, EvSpCOMP);
            return;
          }

          if(v0 < 0x6L) {
            if(v0 == 0x3L) {
              //LAB_bfc05220
              //LAB_bfc05224
              _a000915c.setu(0x1L);
              DeliverEvent(HwCdRom, EvSpCOMP);
              return;
            }

            if(v0 < 0x4L) {
              if(v0 == 0x1L) {
                //LAB_bfc05244
                FUN_bfc05db0(CDROM_REG1.get());
              }

              return;
            }

            //LAB_bfc05360
            if(v0 == 0x4L) {
              //LAB_bfc05224
              _a000915c.setu(0x1L);
              DeliverEvent(HwCdRom, EvSpCOMP);
            }

            return;
          }

          //LAB_bfc05374
          if(v0 == 0x7L) {
            return;
          }

          return;
        }

        //LAB_bfc05388
        if(v0 == 0xbL) {
          //LAB_bfc05284
          FUN_bfc0615c();
          return;
        }

        if(v0 < 0xcL) {
          if(v0 == 0x9L) {
            return;
          }

          return;
        }

        //LAB_bfc053ac
        if(v0 == 0xcL) {
          //LAB_bfc05284
          FUN_bfc0615c();
        }

        return;
      }

      //LAB_bfc053c0
      if(v0 == 0x14L) {
        //LAB_bfc052a4
        FUN_bfc063cc();
        return;
      }

      if(v0 < 0x15L) {
        if(v0 == 0x12L) {
          return;
        }

        if(v0 < 0x13L) {
          if(v0 == 0xfL) {
            //LAB_bfc05254
            FUN_bfc05df4();
          }

          return;
        }

        //LAB_bfc053f4
        if(v0 == 0x13L) {
          //LAB_bfc052c4
          FUN_bfc062cc();
        }

        return;
      }

      //LAB_bfc05408
      if(v0 == 0x16L) {
        return;
      }

      if(v0 < 0x17L) {
        if(v0 == 0x15L) {
          return;
        }

        return;
      }

      //LAB_bfc0542c
      if(v0 == 0x17L) {
        //LAB_bfc052b4
        FUN_bfc061a0();
      }

      return;
    }

    //LAB_bfc05440
    if(v0 == 0xf6L) {
      //LAB_bfc05210
      //LAB_bfc05214
      _a000915c.setu(0x1L);
      return;
    }

    if(v0 < 0xf7L) {
      if(v0 == 0xe6L) {
        //LAB_bfc05210
        //LAB_bfc05214
        _a000915c.setu(0x1L);
        return;
      }

      if(v0 < 0xe7L) {
        if(v0 == 0x50L) {
          //LAB_bfc05274
          FUN_bfc061d4();
          return;
        }

        if(v0 < 0x51L) {
          if(v0 == 0x1aL) {
            return;
          }

          return;
        }

        //LAB_bfc05484
        if(v0 == 0xe2L) {
          //LAB_bfc05200
          FUN_bfc05c18(0x1L);
        }

        return;
      }

      //LAB_bfc05498
      if(v0 == 0xeeL) {
        //LAB_bfc05264
        FUN_bfc05e58();
        return;
      }

      if(v0 < 0xefL) {
        if(v0 == 0xebL) {
          //LAB_bfc05214
          _a000915c.setu(0x1L);
        }

        return;
      }

      //LAB_bfc054bc
      if(v0 == 0xf2L) {
        //LAB_bfc051f0
        FUN_bfc05c18(0);
        return;
      }

      return;
    }

    //LAB_bfc054d0
    if(v0 == 0xdddL) {
      return;
    }

    if(v0 < 0xddeL) {
      if(v0 == 0xfeL) {
        //LAB_bfc05264
        FUN_bfc05e58();
        return;
      }

      if(v0 < 0xffL) {
        if(v0 == 0xfbL) {
          //LAB_bfc05214
          _a000915c.setu(0x1L);
        }

        return;
      }

      //LAB_bfc05504
      if(v0 == 0xcccL) {
        return;
      }

      return;
    }

    //LAB_bfc05518
    if(v0 == 0xfffL) {
      return;
    }

    if(v0 < 0x1000L) {
      if(v0 == 0xf14L) {
        //LAB_bfc05294
        FUN_bfc06288();
      }

      return;
    }

    //LAB_bfc0553c
    if(v0 == 0xffffL) {
      //LAB_bfc052e8
      DeliverEvent(HwCdRom, EvSpUNKNOWN);
    }

    //LAB_bfc05548
    //LAB_bfc0554c
  }

  @Method(0xbfc05558L)
  public static void FUN_bfc05558() {
    CDROM_REG1.get(); // Read to nowhere intentional

    final long v0 = _a0009154.get();

    if(v0 == 0x12L) {
      FUN_bfc06218();
      return;
    }

    if(v0 == 0xcccL) {
      //LAB_bfc05648
      _a00091c4.setu(0x1L);
      _a0009154.set(0xffffL);
      return;
    }

    //LAB_bfc05768
    if(v0 == 0xfffL) {
      //LAB_bfc055f8
      _a0009154.setu(0xffffL);
      DeliverEvent(HwCdRom, EvSpCOMP);
      return;
    }

    if(v0 == 0xdddL) {
      //LAB_bfc0561c
      _a00091c8.setu(0);
      _a0009154.setu(0xffffL);
      DeliverEvent(HwCdRom, EvSpERROR);
      return;
    }

    //LAB_bfc0578c
    if(v0 == 0xffffL) {
      //LAB_bfc056cc
      DeliverEvent(HwCdRom, EvSpUNKNOWN);
      return;
    }

    //LAB_bfc05754
    if(v0 == 0x1aL) {
      //LAB_bfc05664
      long v2 = _a00091ac.get();
      long v1 = 0;

      do {
        MEMORY.ref(1, v2).setu(CDROM_REG1);
        MEMORY.ref(1, v2).offset(0x1L).setu(CDROM_REG1);
        MEMORY.ref(1, v2).offset(0x2L).setu(CDROM_REG1);
        MEMORY.ref(1, v2).offset(0x3L).setu(CDROM_REG1);
        v2 += 0x4L;
        v1 += 0x4L;
      } while(v1 != 0x4L);
    }

    if(v0 == 0x8L || v0 == 0x9L || v0 == 0x15L || v0 == 0x16L) {
      //LAB_bfc05590
      final long v2 = _a0009158.get();
      if(v2 == 0xe6L || v2 == 0xebL || v2 == 0x3L || v2 == 0x4L || v2 == 0x5L) {
        //LAB_bfc055c8
        _a0009158.setu(0xffffL);
      }
    }

    //LAB_bfc05734
    _a0009154.setu(0xffffL);
    DeliverEvent(HwCdRom, EvSpCOMP);
  }

  @Method(0xbfc057b0L)
  public static void FUN_bfc057b0() {
    assert false;
  }

  @Method(0xbfc0593cL)
  public static void FUN_bfc0593c() {
    if(_a0009154.get() == 0xf6L || _a0009154.get() == 0xfbL || _a0009158.get() == 0xf6L || _a0009158.get() == 0xfbL) {
      FUN_bfc05f08();
    } else {
      if(_a0009154.get() == 0xe6L || _a0009154.get() == 0xebL || _a0009158.get() == 0xe6L || _a0009158.get() == 0xebL) {
        FUN_bfc06010();
      } else {
        if(_a0009154.get() == 0x3L || _a0009154.get() == 0x4L || _a0009154.get() == 0x5L) {
          FUN_bfc06038(CDROM_REG1.get());
        } else {
          DeliverEvent(HwCdRom, EvSpUNKNOWN);
        }
      }
    }
  }

  @Method(0xbfc05a44L)
  public static void FUN_bfc05a44() {
    assert false;
  }

  @Method(0xbfc05c18L)
  public static void FUN_bfc05c18(final long a0) {
    final long v0;

    if(a0 == 0) {
      v0 = 0x15L;
    } else {
      v0 = 0x16L;
    }

    //LAB_bfc05c2c
    CDROM_REG0.setu(0);
    CDROM_REG1.setu(v0);
    _a0009154.setu(v0);
  }

  @Method(0xbfc05c50L)
  public static void FUN_bfc05c50() {
    assert false;
  }

  @Method(0xbfc05d04L)
  public static void FUN_bfc05d04() {
    assert false;
  }

  @Method(0xbfc05db0L)
  public static void FUN_bfc05db0(final long a0) {
    _a0009164.deref(1).setu(_a0009164);
    _a0009154.setu(0xffffL);
    DeliverEvent(HwCdRom, EvSpCOMP);
  }

  @Method(0xbfc05df4L)
  public static void FUN_bfc05df4() {
    assert false;
  }

  @Method(0xbfc05e58L)
  public static void FUN_bfc05e58() {
    CDROM_REG0.setu(0);

    if(_a0009190.get(0x100L) == 0) {
      if(_a0009154.get() == 0xfeL) {
        _a0009154.setu(0xf6L);
      } else {
        //LAB_bfc05ea4
        _a0009154.setu(0xe6L);
      }

      //LAB_bfc05eac
      CDROM_REG1.setu(0x6L);
      return;
    }

    //LAB_bfc05ec0
    if(_a0009154.get() == 0xfeL) {
      _a0009154.setu(0xfbL);
    } else {
      //LAB_bfc05ee8
      _a0009154.setu(0xebL);
    }

    //LAB_bfc05ef0
    CDROM_REG1.setu(0x1bL);
  }

  @Method(0xbfc05f08L)
  public static void FUN_bfc05f08() {
    final long a2 = _a000917c.get();

    if((int)a2 > 0) {
      _a000918c.setu(_a0009188);
      CDROM_REG0.setu(0);
      CDROM_REG0.get(); // Intentional read to nowhere
      CDROM_REG3.setu(0);
      CDROM_REG3.get(); // Intentional read to nowhere
      CDROM_REG0.setu(0);
      CDROM_REG3.setu(0x80L);
      CDROM_DELAY.setu(0x2_0943L);
      COMMON_DELAY.setu(0x132cL);
      _a000917c.setu(a2 - 0x1L);
      FUN_bfc065c0(_a0009188.get(), _a0009198.get());

      _a0009188.addu(_a0009198.get() * 4);
      if(_a000917c.get() != 0) {
        return;
      }

      CDROM_REG0.setu(0);
      _a0009154.setu(0xfffL);
      CDROM_REG1.setu(0x9L);
      return;
    }

    //LAB_bfc05fec
    if(a2 == 0) {
      _a000917c.setu(-0x1L);
    }
  }

  @Method(0xbfc06010L)
  public static void FUN_bfc06010() {
    assert false;
  }

  @Method(0xbfc06038L)
  public static void FUN_bfc06038(final long a0) {
    assert false;
  }

  @Method(0xbfc060dcL)
  public static void FUN_bfc060dc(final long a0) {
    assert false;
  }

  @Method(0xbfc0615cL)
  public static void FUN_bfc0615c() {
    assert false;
  }

  @Method(0xbfc061a0L)
  public static void FUN_bfc061a0() {
    assert false;
  }

  @Method(0xbfc061d4L)
  public static void FUN_bfc061d4() {
    assert false;
  }

  @Method(0xbfc06218L)
  public static void FUN_bfc06218() {
    assert false;
  }

  @Method(0xbfc06288L)
  public static void FUN_bfc06288() {
    assert false;
  }

  @Method(0xbfc062ccL)
  public static void FUN_bfc062cc() {
    assert false;
  }

  @Method(0xbfc063ccL)
  public static void FUN_bfc063cc() {
    assert false;
  }

  @Method(0xbfc064d8L)
  public static void FUN_bfc064d8() {
    UnDeliverEvent(HwCdRom, EvSpCOMP);
    UnDeliverEvent(HwCdRom, EvSpDE);
    UnDeliverEvent(HwCdRom, EvSpERROR);
    UnDeliverEvent(HwCdRom, EvSpTIMOUT);
    UnDeliverEvent(HwCdRom, EvSpUNKNOWN);
  }

  @Method(0xbfc06548L)
  public static void FUN_bfc06548() {
    UnDeliverEvent(HwCdRom, EvSpDR);
    UnDeliverEvent(HwCdRom, EvSpACK);
    UnDeliverEvent(HwCdRom, EvSpCOMP);
    UnDeliverEvent(HwCdRom, EvSpDE);
    UnDeliverEvent(HwCdRom, EvSpERROR);
    UnDeliverEvent(HwCdRom, EvSpTIMOUT);
    UnDeliverEvent(HwCdRom, EvSpUNKNOWN);
  }

  @Method(0xbfc065c0L)
  public static void FUN_bfc065c0(final long a0, final long a1) {
    DMA_DICR.setu(DMA_DICR.get(0xff_ffffL) | 0x88_0000L);
    DMA_DPCR.oru(0x8000L);
    DMA.cdrom.MADR.setu(a0);
    DMA.cdrom.BCR.setu(a1 | 0x1_0000L);
    DMA.cdrom.CHCR.setu(0x1100_0000L);
  }

  @Method(0xbfc06680L)
  public static void SetMemSize_Impl_A9f(final int megabytes) {
    if(megabytes == 0x2L) {
      //LAB_bfc066bc
      RAM_SIZE.and(0xffff_f8ffL);
    } else {
      if(megabytes != 0x8L) {
        //LAB_bfc066e4
        LOGGER.error("Effective memory must be 2/8 MBytes");
        return;
      }

      //LAB_bfc066c8
      RAM_SIZE.setu(RAM_SIZE.get(0xffff_f8ff) | 0x300L);
    }

    //LAB_bfc066cc
    MEMORY.ref(4, 0x60L).setu(megabytes);
    LOGGER.info("Change effective memory : %d MBytes", megabytes);
  }

  @Method(0xbfc06784L)
  public static void FUN_bfc06784() {
    bootstrapExecutable("cdrom:SYSTEM.CNF;1", "cdrom:PSX.EXE;1");
  }

  @Method(0xbfc067e8L)
  public static void bootstrapExecutable(final String cnf, final String exe) {
    LOGGER.info("Bootstrapping %s / %s", exe, cnf);

    setBootStatus(0x1L);
    CPU.R12_SR.resetIEc();
    CPU.R12_SR.setIm(CPU.R12_SR.getIm() & 0xffff_fbfeL);

    SPU.REVERB_OUT_L.set(0);
    SPU.REVERB_OUT_R.set(0);
    SPU.MAIN_VOL_L.set(0);
    SPU.MAIN_VOL_R.set(0);

    setBootStatus(0x2L);
    copyKernelSegment2();

    setBootStatus(0x3L);
    copyJumpTableA();
    copyAbcFunctionVectors_Impl_A45();
    AdjustA0Table();
    InstallExceptionHandlers();
    SetDefaultExitFromException();

    setBootStatus(0x4L);
    SPU.REVERB_OUT_L.set(0);
    SPU.REVERB_OUT_R.set(0);
    SPU.MAIN_VOL_L.set(0);
    SPU.MAIN_VOL_R.set(0);

    I_MASK.setu(0);
    I_STAT.setu(0);

    InstallDevices(ttyFlag_a000b9b0.get());

    setBootStatus(0x5L);
    LOGGER.info("");
    LOGGER.info("PS-X Realtime Kernel Ver.2.5");
    LOGGER.info("Copyright 1993,1994 (C) Sony Computer Entertainment Inc.");

    setBootStatus(0x6L);
    SPU.REVERB_OUT_L.set(0);
    SPU.REVERB_OUT_R.set(0);
    SPU.MAIN_VOL_L.set(0);
    SPU.MAIN_VOL_R.set(0);
    memcpy_Impl_A2a(_a000b940.getAddress(), _bfc0e14c.getAddress(), 0xc);

    LOGGER.info("KERNEL SETUP!");
    SysInitMemory(kernelMemoryStart_a000e000.getAddress(), 0x2000);
    allocateExceptionChain(4);
    EnqueueSyscallHandler(0);
    InitDefInt(3);
    allocateEventControlBlock((int)_a000b944.get());
    allocateThreadControlBlock(1, (int)_a000b940.get());
    EnqueueTimerAndVblankIrqs(1);

    SPU.REVERB_OUT_L.set(0);
    SPU.REVERB_OUT_R.set(0);
    SPU.MAIN_VOL_L.set(0);
    SPU.MAIN_VOL_R.set(0);
    setjmp_Impl_A13(jmp_buf_a000b980, MEMORY.ref(4, getMethodAddress(Bios.class, "stop385"), RunnableRef::new));

    setBootStatus(0x7L);
    loadIntroAndBootMenu();

    setBootStatus(0x8L);
    I_MASK.setu(0);
    I_STAT.setu(0);
    CdInit_Impl_A54();
    setjmp_Impl_A13(jmp_buf_a000b980, MEMORY.ref(4, getMethodAddress(Bios.class, "stop399"), RunnableRef::new));

    // PIO shell init here (loadPioShell)

    LOGGER.info("");
    LOGGER.info("BOOTSTRAP LOADER Type C Ver 2.1   03-JUL-1994");
    LOGGER.info("Copyright 1993,1994 (C) Sony Computer Entertainment Inc.");
    setjmp_Impl_A13(jmp_buf_a000b980, MEMORY.ref(4, getMethodAddress(Bios.class, "stop386"), RunnableRef::new));

    setBootStatus(0x9L);
    setjmp_Impl_A13(jmp_buf_a000b980, MEMORY.ref(4, getMethodAddress(Bios.class, "stop387"), RunnableRef::new));

    //LAB_bfc06a3c
    final int fp = open(cnf, 1);
    if(fp < 0) {
      //LAB_bfc06b18
      setjmp_Impl_A13(jmp_buf_a000b980, MEMORY.ref(4, getMethodAddress(Bios.class, "stop391"), RunnableRef::new));

      //LAB_bfc06b34
      argv_00000180.setu(0);
      memcpy_Impl_A2a(_a000b940.getAddress(), _bfc0e14c.getAddress(), 0xc);
      _a000b8b0.set(exe);
    } else {
      LOGGER.info("setup file    : %s", cnf);
      setjmp_Impl_A13(jmp_buf_a000b980, MEMORY.ref(4, getMethodAddress(Bios.class, "stop38f"), RunnableRef::new));

      //LAB_bfc06a7c
      final long size = read(fp, _a000b070.getAddress(), 0x800);
      if(size == 0) {
        memcpy_Impl_A2a(_a000b940.getAddress(), _bfc0e14c.getAddress(), 0xc);
        _a000b8b0.set(exe);
      } else {
        //LAB_bfc06ac4
        _a000b070.offset(size).setu(0);
        close(fp);
        setjmp_Impl_A13(jmp_buf_a000b980, MEMORY.ref(4, getMethodAddress(Bios.class, "stop390"), RunnableRef::new));

        //LAB_bfc06af4
        loadCnf(_a000b070.getAddress(), _a000b940.getAddress(), _a000b8b0.getAddress());
      }
    }

    //LAB_bfc06b60
    setjmp_Impl_A13(jmp_buf_a000b980, MEMORY.ref(4, getMethodAddress(Bios.class, "stop388"), RunnableRef::new));

    //LAB_bfc06b7c
    reinitKernel();
    LOGGER.info("boot file     : %s", _a000b8b0.getString());
    setjmp_Impl_A13(jmp_buf_a000b980, MEMORY.ref(4, getMethodAddress(Bios.class, "stop389"), RunnableRef::new));

    //LAB_bfc06bb4
    //Don't need to clearUserRam();
    if(!LoadExeFile_Impl_A42(_a000b8b0.getString(), _a000b870.getAddress())) {
      stop(0x38a);
    }

    //LAB_bfc06be0
    LOGGER.info("EXEC:PC0(%08x)  T_ADDR(%08x)  T_SIZE(%08x)", _a000b870.pc0.get(), _a000b870.t_addr.get(), _a000b870.t_size.get());
    LOGGER.info("boot address  : %08x %08x", _a000b870.pc0.get(), _a000b948.get());
    LOGGER.info("Execute !");
    _a000b890.setu(_a000b948);
    _a000b894.setu(0);
    LOGGER.info("                S_ADDR(%08x)  S_SIZE(%08x)", _a000b948.get(), 0);

    EnterCriticalSection();
    setjmp_Impl_A13(jmp_buf_a000b980, MEMORY.ref(4, getMethodAddress(Bios.class, "stop38b"), RunnableRef::new));

    //LAB_bfc06c6c
    FUN_bfc0d570(_a000b870, 1, 0);
    LOGGER.info("Exiting");
    System.exit(0);
//    stop(0x38c);
  }

  @Method(0xbfc06980L)
  public static void stop385() {
    stop(0x385);
  }

  @Method(0xbfc069c8L)
  public static void stop399() {
    stop(0x399);
  }

  @Method(0xbfc06a0cL)
  public static void stop386() {
    stop(0x386);
  }

  @Method(0xbfc06a30L)
  public static void stop387() {
    stop(0x387);
  }

  @Method(0xbfc06a70L)
  public static void stop38f() {
    stop(0x38f);
  }

  @Method(0xbfc06aecL)
  public static void stop390() {
    stop(0x390);
  }

  @Method(0xbfc06b2cL)
  public static void stop391() {
    stop(0x391);
  }

  @Method(0xbfc06b78L)
  public static void stop388() {
    stop(0x388);
  }

  @Method(0xbfc06bb0L)
  public static void stop389() {
    stop(0x389);
  }

  @Method(0xbfc06c64L)
  public static void stop38b() {
    stop(0x38b);
  }

  @Method(0xbfc06ec4L)
  public static void FUN_bfc06ec4() {
    setBootStatus(0xfL);
    SPU.REVERB_OUT_L.set(0);
    SPU.REVERB_OUT_R.set(0);
    SPU.MAIN_VOL_L.set(0);
    SPU.MAIN_VOL_R.set(0);

    // There's a check here that looks like it's just to prevent tampering

    setBootStatus(0xeL);
    ttyFlag_a000b9b0.set(0);
    FUN_bfc06784();
  }

  @Method(0xbfc06f28L)
  public static void reinitKernel() {
    LOGGER.info("KERNEL SETUP!");
    SysInitMemory(kernelMemoryStart_a000e000.getAddress(), 0x2000);
    allocateExceptionChain(4);
    EnqueueSyscallHandler(0);
    InitDefInt(3);
    allocateEventControlBlock((int)_a000b944.get());
    allocateThreadControlBlock(1, (int)_a000b940.get());
    EnqueueTimerAndVblankIrqs(1);
    registerCdromEvents();
  }

  @Method(0xbfc06fa4L)
  public static void stop(final int errorCode) {
    setBootStatus(0xf);
    SystemErrorBootOrDiskFailure_Impl_Aa1('B', errorCode);
    System.exit(errorCode);
  }

  @Method(value = 0xbfc06fdcL, ignoreExtraParams = true)
  public static int noop() {
    return 0;
  }

  @Method(0xbfc06ff0L)
  public static void loadIntroAndBootMenu() {
    LOGGER.warn("Skipping intro and boot menu");
  }

  @Method(0xbfc071a0L)
  public static void registerCdromEvents() {
    EnqueueCdIntr();

    EventId_HwCdRom_EvSpACK_a000b9b8.setu(OpenEvent(HwCdRom, EvSpACK, EvMdNOINTR, 0));
    EventId_HwCdRom_EvSpCOMP_a000b9bc.setu(OpenEvent(HwCdRom, EvSpCOMP, EvMdNOINTR, 0));
    EventId_HwCdRom_EvSpDR_a000b9c0.setu(OpenEvent(HwCdRom, EvSpDR, EvMdNOINTR, 0));
    EventId_HwCdRom_EvSpDE_a000b9c4.setu(OpenEvent(HwCdRom, EvSpDE, EvMdNOINTR, 0));
    EventId_HwCdRom_EvSpERROR_a000b9c8.setu(OpenEvent(HwCdRom, EvSpERROR, EvMdNOINTR, 0));

    EnableEvent(EventId_HwCdRom_EvSpACK_a000b9b8.get());
    EnableEvent(EventId_HwCdRom_EvSpCOMP_a000b9bc.get());
    EnableEvent(EventId_HwCdRom_EvSpDR_a000b9c0.get());
    EnableEvent(EventId_HwCdRom_EvSpDE_a000b9c4.get());
    EnableEvent(EventId_HwCdRom_EvSpERROR_a000b9c8.get());

    ExitCriticalSection();

    _a0009d80.setu(0);
  }

  @Method(0xbfc072b8L)
  public static void _96_remove_Impl_A54() {
    EnterCriticalSection();
    CloseEvent((int)EventId_HwCdRom_EvSpACK_a000b9b8.get());
    CloseEvent((int)EventId_HwCdRom_EvSpCOMP_a000b9bc.get());
    CloseEvent((int)EventId_HwCdRom_EvSpDR_a000b9c0.get());
    CloseEvent((int)EventId_HwCdRom_EvSpDE_a000b9c4.get());
    CloseEvent((int)EventId_HwCdRom_EvSpERROR_a000b9c8.get());
    DequeueCdIntr();
  }

  @Method(0xbfc07330L)
  public static void cdromPreInit() {
    registerCdromEvents();
    CdInitSubFunc();
  }

  @Method(0xbfc073a0L)
  public static void CdInit_Impl_A54() {
    cdromPreInit();
    cdromPostInit();
  }

  @Method(0xbfc07410L)
  public static long cdromPostInit() {
    CDROM.readFromDisk(new CdlLOC().unpack(0x10L), 1, _a000b070.getAddress());
//    if(CdReadSector_Impl_Aa5(1, 0x10L, _a000b070.getAddress()) != 0x1L) {
//      return 0;
//    }

    //LAB_bfc0744c
    if(strncmp_Impl_A18(_a000b071.getString(), "CD001", 5) != 0) {
      assert false : "Bad CDROM sector";
      return 0;
    }

    //LAB_bfc07474
    _a0009d70.setu(_a000b0f4);
    _a0009d74.setu(_a000b0fc);
    _a0009d78.setu(_a000b111.get() << 24 | _a000b110.get() << 16 | _a000b10f.get() << 8 | _a000b10e.get());
    _a0009d7c.setu(_a000b0c0);

    CDROM.readFromDisk(new CdlLOC().unpack(_a000b0fc.get()), 1, _a000b070.getAddress());
//    if(CdReadSector_Impl_Aa5(1, _a000b0fc.get(), _a000b070.getAddress()) != 0x1L) {
//      return 0;
//    }

    //LAB_bfc07500
    //LAB_bfc07518
    for(int i = 0; i < 0x800; i += 4) {
      _a0009d7c.xoru(_a000b070.offset(i));
    }

    long s0 = _a000b070.getAddress();
    long s2 = _a00095b0.getAddress();
    long s3 = 0x1L;

    //LAB_bfc07560
    do {
      if(MEMORY.ref(1, s0).get() == 0) {
        break;
      }

      //LAB_bfc07580
      long v0 = MEMORY.ref(1, s0).offset(0x5L).get() << 24 | MEMORY.ref(1, s0).offset(0x4L).get() << 16 | MEMORY.ref(1, s0).offset(0x3L).get() << 8 | MEMORY.ref(1, s0).offset(0x2L).get();
      MEMORY.ref(4, s2).offset(0x8L).setu(v0);
      MEMORY.ref(4, s2).setu(s3);
      MEMORY.ref(4, s2).offset(0x4L).setu(MEMORY.ref(1, s0).offset(0x6L).get() + MEMORY.ref(1, s0).offset(0x7L).get() & 0xffffL);

      final long size = MEMORY.ref(1, s0).get();
      memcpy_Impl_A2a(_a00095bc.getAddress(), _a000b078.getAddress(), (int)size);

      MEMORY.ref(1, s2).offset(size).offset(0xcL).setu(0);

      v0 = MEMORY.ref(1, s0).get();
      s0 += v0;
      if((v0 & 0x1L) == 0x1L) {
        s0 += 0x9L;
      } else {
        //LAB_bfc07604
        s0 += 0x8L;
      }

      //LAB_bfc0760c
      s2 += 0x2cL;
      s3++;
    } while(s0 < _a000b870.getAddress() && s3 != 0x2dL);

    //LAB_bfc07620
    //LAB_bfc07630
    _a0009e00.setu(s3 - 0x1L);
    _a0009e08.setu(0);

    //LAB_bfc07650
    return 0x1L;
  }

  @Method(0xbfc07664L)
  public static long FUN_bfc07664(final long a0, final String a1) {
    long s0 = _a00095b0.getAddress();
    long s1 = 0;

    //LAB_bfc0769c
    while(s1 < _a0009e00.get()) {
      if(MEMORY.ref(4, s0).offset(0x4L).get() == a0) {
        if(strcmp_Impl_A17(a1, MEMORY.ref(1, s0 + 0xcL).getString()) == 0) {
          return s1 + 0x1L;
        }
      }

      //LAB_bfc076c8
      s0 += 0x2cL;
      s1++;
    }

    //LAB_bfc076e0
    //LAB_bfc076e4
    return -0x1L;
  }

  @Method(0xbfc07700L)
  public static long FUN_bfc07700(final long a0) {
    if(a0 == _a0009e08.get()) {
      return 0x1L;
    }

    //LAB_bfc07720
    if(CdReadSector_Impl_Aa5(1, _a000958c.offset(a0 * 44).get(), _a000b070.getAddress()) != 0x1L) {
      return -0x1L;
    }

    //LAB_bfc07774
    long s1 = _a000b070.getAddress();
    long s2 = _a00091f0.getAddress();
    long count = 0;

    //LAB_bfc077a8
    do {
      if(MEMORY.ref(1, s1).get() == 0) {
        break;
      }

      //LAB_bfc077c8
      MEMORY.ref(4, s2).offset(0x4L).setu(MEMORY.ref(1, s1).offset(0x5L).get() << 24 | MEMORY.ref(1, s1).offset(0x4L).get() << 16 | MEMORY.ref(1, s1).offset(0x3L).get() << 8 | MEMORY.ref(1, s1).offset(0x2L).get());
      MEMORY.ref(4, s2).offset(0x8L).setu(MEMORY.ref(1, s1).offset(0xdL).get() << 24 | MEMORY.ref(1, s1).offset(0xcL).get() << 16 | MEMORY.ref(1, s1).offset(0xbL).get() << 8 | MEMORY.ref(1, s1).offset(0xaL).get());

      final int size = (int)MEMORY.ref(1, s1).offset(0x20L).get();
      memcpy_Impl_A2a(s2 + 0xcL, s1 + 0x21L, size);

      MEMORY.ref(1, s2).offset(size).offset(0xcL).setu(0);
      s1 += MEMORY.ref(1, s1).get();
      s2 += 0x18L;
      count++;
    } while(s2 < _a00095b0.getAddress() && s1 < _a000b870.getAddress());

    //LAB_bfc07860
    //LAB_bfc07870
    _a0009e08.setu(a0);
    _a0009e04.setu(count);

    //LAB_bfc07894
    return 0x1L;
  }

  @Method(0xbfc078a4L)
  public static int dev_cd_open_Impl_A5f(final long fcb, final String path, final int mode) {
    if((CdGetStatus_Impl_Aa6() & 0x10L) != 0) {
      if(cdromPostInit() == 0) {
        MEMORY.ref(4, fcb).offset(0x18L).setu(0x10L);
        return -1;
      }
    }

    //LAB_bfc078f8
    final char[] temp = new char[path.length()];

    int s0 = 0;
    int s1 = 0;
    char c = toupper_Impl_A25(path.charAt(0));
    temp[0] = c;

    //LAB_bfc07910
    while(c != 0 && s1 < path.length() - 1) {
      s1++;
      s0++;
      c = toupper_Impl_A25(path.charAt(s1));
      temp[s0] = c;
    }

    //LAB_bfc07928
    s1 = 0;
    c = temp[s1];

    //LAB_bfc07940
    while(c != '\0' && c != ';') {
      s1++;
      c = temp[s1];
    }

    //LAB_bfc07958
    final String str;
    if(path.charAt(s1) == '\0') {
      str = new String(temp) + ";1";
    } else {
      str = new String(temp);
    }

    //LAB_bfc0797c
    final long v0 = FUN_bfc083cc(0, str);
    if(v0 == -0x1L) {
      MEMORY.ref(4, fcb).offset(0x18L).setu(0x2L);
      return -1;
    }

    //LAB_bfc079a0
    final long v1 = _a00091f0.offset(v0 * 24).getAddress();
    MEMORY.ref(4, fcb).offset(0x24L).setu(MEMORY.ref(4, v1).offset(0x4L));
    MEMORY.ref(4, fcb).offset(0x20L).setu(MEMORY.ref(4, v1).offset(0x8L));
    MEMORY.ref(4, fcb).offset(0x10L).setu(0);
    MEMORY.ref(4, fcb).offset(0x18L).setu(0);
    MEMORY.ref(4, fcb).offset(0x4L).setu(_a0009d7c);

    //LAB_bfc079e0
    return 0;
  }

  @Method(0xbfc079f8L)
  public static int dev_cd_close_Impl_A61(final int fcb) {
    return 0;
  }

  @Method(0xbfc07a04L)
  public static int dev_cd_read_Impl_A60(final long fcb, final long dest, final int length) {
    long t7 = length & 0x7ffL;
    if(length < 0 && t7 != 0) {
      t7 -= 0x800L;
    }

    //LAB_bfc07a34
    final long a0 = MEMORY.ref(4, fcb).offset(0x10L).get();
    if(t7 != 0 || (a0 & 0x7ffL) != 0) {
      //LAB_bfc07a54
      MEMORY.ref(4, fcb).offset(0x18L).setu(0x16L);
      return -1;
    }

    //LAB_bfc07a60
    if(a0 >= MEMORY.ref(4, fcb).offset(0x20L).get()) {
      MEMORY.ref(4, fcb).offset(0x18L).setu(0x16L);
      return -1;
    }

    //LAB_bfc07a84
    if((CdGetStatus_Impl_Aa6() & 0x10L) != 0 && cdromPostInit() == 0) {
      MEMORY.ref(4, fcb).offset(0x18L).setu(0x10L);
      return -1;
    }

    //LAB_bfc07ac0
    if(_a0009d7c.get() != MEMORY.ref(4, fcb).offset(0x4L).get()) {
      MEMORY.ref(4, fcb).offset(0x18L).setu(0x10L);
      return -1;
    }

    //LAB_bfc07aec
    int adjustedLength = length;
    if(length < 0) {
      adjustedLength += 0x7ff;
    }

    //LAB_bfc07b08
    final int sectors = adjustedLength / 0x800;

    CDROM.readFromDisk(new CdlLOC().unpack(MEMORY.ref(4, fcb).offset(0x24L).get() + MEMORY.ref(4, fcb).offset(0x10L).get() / 0x800L), sectors, dest);
//    if(CdReadSector_Impl_Aa5(sectors, MEMORY.ref(4, fcb).offset(0x24L).get() + MEMORY.ref(4, fcb).offset(0x10L).get() / 0x800L, dest) != sectors) {
//      MEMORY.ref(4, fcb).offset(0x18L).setu(0x10L);
//      return -1;
//    }

    //LAB_bfc07b40
    final long v = MEMORY.ref(4, fcb).offset(0x10L).get();
    final long v0 = MEMORY.ref(4, fcb).offset(0x20L).get();
    final long v1;
    if(v0 < v + length) {
      v1 = v0 - v;
    } else {
      v1 = length;
    }

    //LAB_bfc07b68
    //LAB_bfc07b6c
    MEMORY.ref(4, fcb).offset(0x10L).setu(v + v1);

    //LAB_bfc07b78
    return (int)v1;
  }

  @Method(0xbfc07c1cL)
  public static int CdReadSector_Impl_Aa5(final int count, final long sector, final long buffer) {
    final CdlLOC pos = new CdlLOC().unpack(sector);

    long attempts = 0;

    LAB_bfc07c5c:
    while(true) {
      attempts++;
      if(attempts >= 10L) {
        SystemErrorBootOrDiskFailure_Impl_Aa1('D', 0xc);
        return -1;
      }

      //LAB_bfc07c78
      long s0 = 99999L;

      //LAB_bfc07d94
      while(CdAsyncSeekL(pos) == 0 && s0 > 0) {
        s0--;
      }

      //LAB_bfc07db0
      if((int)s0 <= 0) {
        SystemErrorBootOrDiskFailure_Impl_Aa1('D', 0xb);
        return -1;
      }

      //LAB_bfc07dcc
      while(TestEvent(EventId_HwCdRom_EvSpCOMP_a000b9bc.get()) != 1) {
        //LAB_bfc07df0
        if(TestEvent(EventId_HwCdRom_EvSpERROR_a000b9c8.get()) == 1) {
          SystemErrorBootOrDiskFailure_Impl_Aa1('D', 0xc);
          continue LAB_bfc07c5c;
        }
      }

      //LAB_bfc07e1c
      int sectorsRead = CdAsyncReadSector(count, buffer, 0x80);
      s0 = 99999L;

      //LAB_bfc07e34
      while(sectorsRead == 0 && s0 > 0) {
        sectorsRead = CdAsyncReadSector(count, buffer, 0x80);
        s0--;
      }

      //LAB_bfc07e58
      if((int)s0 <= 0) {
        SystemErrorBootOrDiskFailure_Impl_Aa1('D', 0xc);
        return -1;
      }

      //LAB_bfc07e74
      //LAB_bfc07e7c
      while((int)s0 > 0) {
        if(TestEvent(EventId_HwCdRom_EvSpCOMP_a000b9bc.get()) == 1) {
          return count;
        }

        //LAB_bfc07e9c
        if(TestEvent(EventId_HwCdRom_EvSpERROR_a000b9c8.get()) == 1) {
          SystemErrorBootOrDiskFailure_Impl_Aa1('D', 0x16);
          continue LAB_bfc07c5c;
        }

        //LAB_bfc07ec8
        if(TestEvent(EventId_HwCdRom_EvSpDE_a000b9c4.get()) == 1) {
          SystemErrorBootOrDiskFailure_Impl_Aa1('D', 0x17);
          return -1;
        }
      }

      return sectorsRead;
    }

    //LAB_bfc07f00
  }

  @Method(0xbfc07f28L)
  public static int CdGetStatus_Impl_Aa6() {
    final Memory.TemporaryReservation temp = MEMORY.temp();
    final Value sp34 = temp.get();
    long s1 = 9;

    //LAB_bfc07f50
    while((CdAsyncGetStatus(sp34.getAddress()) & 0xffL) == 0 && s1-- > 0) {
      DebugHelper.sleep(1);
    }

    //LAB_bfc07f70
    if((int)s1 <= 0) {
      SystemErrorBootOrDiskFailure_Impl_Aa1('D', 0x1f);
      return -1;
    }

    //LAB_bfc07f9c
    do {
      if((TestEvent(EventId_HwCdRom_EvSpCOMP_a000b9bc.get()) & 0xffL) == 0x1L) {
        return (int)sp34.get();
      }

      //LAB_bfc07fc4
      if((TestEvent(EventId_HwCdRom_EvSpERROR_a000b9c8.get()) & 0xffL) == 0x1L) {
        SystemErrorBootOrDiskFailure_Impl_Aa1('D', 0x20);
        return -1;
      }

      //LAB_bfc07ff8
    } while((int)s1 > 0);

    //LAB_bfc0800c
    //LAB_bfc08010
    return (int)sp34.get();
  }

  @Method(0xbfc08020L)
  public static long FUN_bfc08020(long a0, final String a1) {
    //LAB_bfc0803c
    long v0 = MEMORY.ref(1, a0).get();
    int charIndex = 0;
    while(v0 != 0) {
      final char c = a1.charAt(charIndex);
      if(c != '?' && c != v0) {
        return 0;
      }

      //LAB_bfc0805c
      v0 = MEMORY.ref(1, a0).offset(0x1L).get();
      charIndex++;
      a0++;
    }

    //LAB_bfc0806c
    final char c = a1.charAt(charIndex);
    if(c != '\0' && c != '?') {
      //LAB_bfc08090
      return 0;
    }

    //LAB_bfc08084
    return 0x1L;
  }

  @Method(0xbfc083ccL)
  public static long FUN_bfc083cc(final int a0, final String a1) {
    final String str;
    if(a1.charAt(0) == '\\') {
      //LAB_bfc08440
      str = a1;
    } else {
      str = _a0009d80.getString() + '\\' + a1;
    }

    _a0009e18.set(str);

    //LAB_bfc08454
    final char[] out = new char[str.length()];
    int outIndex;

    int index = 1;
    out[0] = '\0';
    long s4 = 0x1L;
    long s3 = 0;

    //LAB_bfc0846c
    do {
      char c = (char)_a0009e18.offset(index).get();
      outIndex = 0;

      //LAB_bfc08484
      while(c != '\0' && c != '\\') {
        out[outIndex] = c;
        index++;
        c = (char)_a0009e18.offset(index).get();
        outIndex++;
      }

      //LAB_bfc084a0
      if(c == '\0') {
        break;
      }

      index++;
      out[outIndex] = 0;
      final long v0 = FUN_bfc07664(s4, new String(out));
      s4 = v0;
      if(v0 == -0x1L) {
        out[0] = 0;
        break;
      }

      //LAB_bfc084cc
      s3++;
    } while(s3 < 0x8L);

    //LAB_bfc084d8
    //LAB_bfc084dc
    if(s3 >= 0x8L) {
      return -0x1L;
    }

    //LAB_bfc084f0
    if(out[0] == '\0') {
      return -0x1L;
    }

    //LAB_bfc08504
    out[outIndex] = '\0';
    if(FUN_bfc07700(s4) == 0) {
      return -0x1L;
    }

    //LAB_bfc08520
    final long s1 = _a0009e04.get();
    s3 = a0;
    if(a0 >= s1) {
      return -0x1L;
    }

    long addr = _a00091fc.offset(s3 * 24).getAddress();

    //LAB_bfc08554
    do {
      if(FUN_bfc08020(addr, new String(out)) != 0) {
        _a0009e10.setu(s3);
        return s3;
      }

      //LAB_bfc08574
      s3++;
      addr += 0x18L;
    } while(s3 < s1);

    //LAB_bfc08588
    return -0x1L;
  }

  @Method(0xbfc085b0L)
  public static void AddCdromDevice_Impl_A96() {
    AddDevice(CdromDeviceInfo_bfc0e2f0.getAddress());
  }

  @Method(0xbfc086b0L)
  public static void AddDummyTtyDevice_Impl_A99() {
    AddDevice(DummyTtyDeviceInfo_bfc0e350.getAddress());
  }

  @Method(0xbfc08720L)
  public static long FUN_bfc08720(long a0) {
    long v0;
    long v1;
    final long a1;
    long t0;
    long t1;
    final long t6;
    final long t7;
    long t8;
    long t9;

    v0 = MEMORY.ref(1, a0).offset(0x0L).get();
    t6 = MEMORY.ref(1, a0).offset(0x1L).get();
    v0 = v0 & 0xffL;
    t7 = MEMORY.ref(1, a0).offset(0x2L).get();
    v0 = v0 ^ t6;
    v0 = v0 & 0xffL;
    a0 = a0 + 0x1L;
    a0 = a0 + 0x1L;
    v0 = v0 ^ t7;
    v0 = v0 & 0xffL;
    a0 = a0 + 0x1L;
    v1 = 0x3L;
    a1 = 0x7fL;

    //LAB_bfc08758
    do {
      t8 = MEMORY.ref(1, a0).offset(0x0L).get();
      t9 = MEMORY.ref(1, a0).offset(0x1L).get();
      v0 = v0 ^ t8;
      v0 = v0 & 0xffL;
      t0 = MEMORY.ref(1, a0).offset(0x2L).get();
      v0 = v0 ^ t9;
      v0 = v0 & 0xffL;
      t1 = MEMORY.ref(1, a0).offset(0x3L).get();
      a0 = a0 + 0x1L;
      v0 = v0 ^ t0;
      v0 = v0 & 0xffL;
      a0 = a0 + 0x1L;
      a0 = a0 + 0x1L;
      v1 = v1 + 0x4L;
      v0 = v0 ^ t1;
      v0 = v0 & 0xffL;
      a0 = a0 + 0x1L;
    } while(v1 != a1);

    if(v0 == MEMORY.ref(1, a0).offset(0x0L).get()) {
      return 0x1L;
    }

    //LAB_bfc087bc
    return 0;
  }

  @Method(0xbfc087c4L)
  public static void FUN_bfc087c4(final long a0) {
    assert false;
  }

  @Method(0xbfc0884cL)
  public static void resetMemcardStatusAndEvents() {
    memcardOkay_a000b9d0.setu(0);

    for(int i = 0; i < 0x10; i += 0x10) {
      memcardError_a000b9d4.offset(i).setu(0);
      memcardBusy_a000b9d8.offset(i).setu(0);
      memcardEjected_a000b9dc.offset(i).setu(0);
      _a000b9e0.offset(i).setu(0);
    }

    resetMemcardEvents();
  }

  @Method(0xbfc088a0L)
  public static void FUN_bfc088a0() {
    _a0009f20.setu(0);
    _a0009f24.setu(0);
    _a0009f90.setu(0);

    resetMemcardStatusAndEvents();

    for(int i = 0; i < 15; i++) {
      bzero_Impl_A28(_a000ba88.offset(i * 0x20L).getAddress(), 0x20);
      bzero_Impl_A28(_a000bc68.offset(i * 0x20L).getAddress(), 0x20);

      _a000ba88.offset(4, i * 0x20L).setu(0xa0L);
      _a000ba8c.offset(4, i * 0x20L).setu(0);
      _a000ba90.offset(2, i * 0x20L).setu(0xffffL);

      _a000bc68.offset(4, i * 0x20L).setu(0xa0L);
      _a000bc6c.offset(4, i * 0x20L).setu(0);
      _a000bc70.offset(2, i * 0x20L).setu(0xffffL);
    }
  }

  @Method(0xbfc0895cL)
  public static long FUN_bfc0895c() {
    //LAB_bfc0896c
    while(true) {
      //LAB_bfc08978
      for(int i = 0; i < 5; i++) {
        if(memcardOkay_a000b9d0.offset(i * 0x4L).get() == 0x1L) {
          resetMemcardStatusAndEvents();
          //LAB_bfc089b0
          return i;
        }

        //LAB_bfc0899c
      }
    }
  }

  @Method(0xbfc089c0L)
  public static long FUN_bfc089c0(final int port) {
    final long t7 = _a000be48.getAddress();

    final long at;
    if(port < 0) {
      at = port + 0xfL;
    } else {
      at = port;
    }

    //LAB_bfc089f4
    final long sp2c = (int)at >> 4;
    long s0 = t7 + sp2c * 0x80L;

    if(read_card_sector(port, 0, s0) == 0x1L) {
      final long v0 = FUN_bfc0895c();
      if(v0 != 0) {
        //LAB_bfc08a78
        if(v0 != 0x3L) {
          return 0;
        }

        //LAB_bfc08a88
        return FUN_bfc08b3c(port);
      }

      if(MEMORY.ref(1, s0).offset(0x0L).get() == 0x4dL && MEMORY.ref(1, s0).offset(0x1L).get() == 0x43L) {
        //LAB_bfc08a6c
        return 0x1L;
      }

      //LAB_bfc08a48
      if(_a0009f90.get() == 0x1L) {
        return FUN_bfc0b170(port);
      }
    }

    //LAB_bfc08a9c
    //LAB_bfc08ac0
    for(s0 = 0; s0 != 0xfL; s0++) {
      bzero_Impl_A28(_a000ba88.offset(sp2c * 0x1e0L).offset(s0 * 0x20L).getAddress(), 0x20);
    }

    //LAB_bfc08b00
    for(s0 = 0; s0 != 0x14L; s0 += 0x4L) {
      final long v0 = _a000b9e8.offset(sp2c * 0x50L).offset(s0 * 0x4L).getAddress();
      MEMORY.ref(4, v0).offset(0x0L).setu(-0x1L);
      MEMORY.ref(4, v0).offset(0x4L).setu(-0x1L);
      MEMORY.ref(4, v0).offset(0x8L).setu(-0x1L);
      MEMORY.ref(4, v0).offset(0xcL).setu(-0x1L);
    }

    //LAB_bfc08b24
    //LAB_bfc08b28
    return 0;
  }

  @Method(0xbfc08b3cL)
  public static long FUN_bfc08b3c(final int port) {
    final long s1;
    final long s2;

    allow_new_card();
    long at = port;
    if(port < 0) {
      at += 0xfL;
    }

    //LAB_bfc08b80
    final long v0 = (int)at >> 0x4L;
    final long s3 = _a000be48.getAddress() + v0 * 0x80L;
    if(read_card_sector(port, 0, s3) == 0x1L) {
      if(waitForMemcard() != 0) {
        //LAB_bfc08bf4
        Outer:
        if(MEMORY.ref(1, s3).get() == 'M' && MEMORY.ref(1, s3).offset(0x1L).get() == 'C') {
          //LAB_bfc08c7c
          allow_new_card();
          write_card_sector(port, 0x3f, s3);
          waitForMemcard();

          s2 = _a000ba88.offset(v0 * 0x1e0L).getAddress();

          //LAB_bfc08cc0
          for(int i = 0; i < 0xfL; i++) {
            final long a2 = s2 + i * 0x20L;
            bzero_Impl_A28(a2, 0x20);
            MEMORY.ref(4, a2).setu(0xa0L);
            MEMORY.ref(4, a2).offset(0x4L).setu(0);
          }

          //LAB_bfc08cf8
          for(int i = 0; i != 0xfL; i++) {
            if(read_card_sector(port, i + 1, s3) != 0x1L || waitForMemcard() == 0) {
              //LAB_bfc08d24
              s1 = _a000b9e8.getAddress() + v0 * 80;
              break Outer;
            }

            //LAB_bfc08d44
            if(FUN_bfc08720(s3) == 0) {
              s1 = _a000b9e8.getAddress() + v0 * 80;
              break Outer;
            }

            //LAB_bfc08d7c
            memcpy_Impl_A2a(s2 + i * 32L, s3, 0x20);
          }

          //LAB_bfc08da4
          final long[] sp58 = new long[15];
          for(int i = 0; i < 0xfL; i++) {
            sp58[i] = 0;
            if(FUN_bfc08ffc(v0, i) == 0x1L) {
              sp58[i] = 0x52L;
            }
          }

          sp58[0] = 0;
          sp58[1] = 0;
          sp58[2] = 0;

          //LAB_bfc08dec
          for(int i = 0; i < 0x3; i++) {
            sp58[i + 3] = 0;
            sp58[i + 4] = 0;
            sp58[i + 5] = 0;
            sp58[i + 6] = 0;
          }

          //LAB_bfc08e18
          for(int i = 0; i < 0xfL; i++) {
            final long a2 = s2 + i * 32L;
            final long v2 = MEMORY.ref(4, a2).get();
            if(v2 == 0x51L || v2 == 0xa1L) {
              //LAB_bfc08e38
              sp58[i] = 0x1L;
              at = MEMORY.ref(4, a2).offset(0x4L).get();
              long v1 = MEMORY.ref(2, a2).offset(0x8L).get();
              if((int)at < 0) {
                at += 0x1fffL;
              }

              //LAB_bfc08e58
              long a5 = (int)at >> 0xdL;
              a5--;
              //LAB_bfc08e74
              while(a5 > 0 && v1 != 0xffffL) {
                sp58[(int)v1]++;
                final long t3 = s2 + v1 * 32;
                v1 = MEMORY.ref(2, t3).offset(0x8L).get();
                a5--;
              }
            }
          }

          //LAB_bfc08ec4
          for(int i = 0; i < 0xfL; i++) {
            if(sp58[i] == 0) {
              final long a2 = s2 + i * 32L;
              MEMORY.ref(4, a2).setu(0xa0L);
              MEMORY.ref(4, a2).offset(0x4L).setu(0);
              MEMORY.ref(4, a2).offset(0x8L).setu(0xffffL);
            }
          }

          s1 = _a000b9e8.getAddress() + v0 * 80;

          //LAB_bfc08f1c
          for(int i = 0; i < 0x14; i++) {
            if(read_card_sector(port, i + 0x10, s3) != 0x1L || waitForMemcard() == 0) {
              //LAB_bfc08f40
              break Outer;
            }

            //LAB_bfc08f48
            if(FUN_bfc08720(s3) == 0) {
              break Outer;
            }

            //LAB_bfc08f64
            memcpy_Impl_A2a(s1 + i * 4, s3, 0x4);
          }

          return 1;
        } else {
          //LAB_bfc08c14
          if(_a0009f90.get() == 0x1L) {
            return FUN_bfc0b170(port);
          }

          //LAB_bfc08c40
          s2 = _a000ba88.getAddress() + v0 * 480;
          s1 = _a000b9e8.getAddress() + v0 * 80;
        }
      } else {
        //LAB_bfc08bb8
        s2 = _a000ba88.getAddress() + v0 * 480;
        s1 = _a000b9e8.getAddress() + v0 * 80;
      }
    } else {
      //LAB_bfc08bb8
      s2 = _a000ba88.getAddress() + v0 * 480;
      s1 = _a000b9e8.getAddress() + v0 * 80;
    }

    //LAB_bfc08f8c
    //LAB_bfc08f90
    for(int i = 0; i < 0xfL; i++) {
      bzero_Impl_A28(s2 + i * 32L, 0x20);
    }

    //LAB_bfc08fb4
    for(int i = 0; i < 20; i += 4) {
      final long v2 = s1 + i * 4L;
      MEMORY.ref(4, v2).setu(0xffff_ffffL);
      MEMORY.ref(4, v2).offset(0x4L).setu(0xffff_ffffL);
      MEMORY.ref(4, v2).offset(0x8L).setu(0xffff_ffffL);
      MEMORY.ref(4, v2).offset(0xcL).setu(0xffff_ffffL);
    }

    //LAB_bfc08fd8
    //LAB_bfc08fdc
    return 0;
  }

  @Method(0xbfc08ffcL)
  public static long FUN_bfc08ffc(long a0, long a1) {
    long at;
    long v0;
    final long v1;
    final long a2;
    long a3;
    long t0;
    final long t1;
    long t2;
    final long t3;
    long t5;
    long t6;
    long t7;
    final long t8;
    long t9;
    t6 = a0 << 4;
    t6 = t6 - a0;
    t7 = _a000ba88.getAddress();
    t6 = t6 << 5;
    v1 = t6 + t7;
    t8 = a1 << 5;
    a2 = v1 + t8;
    v0 = MEMORY.ref(1, a2).offset(0x0L).get();
    if(v0 == 0x51L) {
      v0 = 0xa0L;
      //LAB_bfc09038
    } else if(v0 == 0xa1L) {
      v0 = 0x50L;
    } else {
      //LAB_bfc09048
      return 0;
    }

    //LAB_bfc09050
    a3 = MEMORY.ref(4, a2).offset(0x4L).get();
    t1 = MEMORY.ref(2, a2).offset(0x8L).get();
    a0 = 0x1L;

    if((int)a3 >= 0) {
      at = a3;
    } else {
      at = a3;
      at = at + 0x1fffL;
    }

    //LAB_bfc0906c
    a3 = (int)at >> 13;
    a3 = a3 - 0x1L;
    a1 = a3;
    t0 = t1;
    if((int)a1 <= 0) {
      t3 = 0xffffL;
    } else {
      //LAB_bfc09088
      t3 = 0xffffL;
      if(t0 != t3) {
        t9 = t0 << 5;

        //LAB_bfc09098
        do {
          t2 = v1 + t9;
          t5 = MEMORY.ref(4, t2).offset(0x0L).get();

          t6 = t5 & 0xf0L;
          a0 = 0;
          if(v0 == t6) {
            break;
          }

          //LAB_bfc090b8
          t0 = MEMORY.ref(2, t2).offset(0x8L).get();
          a1 = a1 - 0x1L;
          if((int)a1 <= 0) {
            break;
          }

          t9 = t0 << 5;
        } while(t0 != t3);
      }
    }

    //LAB_bfc090d0
    if(a0 != 0) {
      return 0;
    }

    MEMORY.ref(1, a2).offset(0xaL).setu(0);
    a1 = a3;
    MEMORY.ref(4, a2).offset(0x0L).setu(0xa0L);
    MEMORY.ref(4, a2).offset(0x4L).setu(0);
    MEMORY.ref(2, a2).offset(0x8L).setu(0xffffL);
    t0 = t1;
    if((int)a1 > 0) {
      if(t0 != t3) {
        t7 = t0 << 5;

        //LAB_bfc09108
        do {
          t2 = v1 + t7;
          a0 = MEMORY.ref(2, t2).offset(0x8L).get();
          a1 = a1 - 0x1L;
          MEMORY.ref(4, t2).offset(0x0L).setu(0xa0L);
          MEMORY.ref(4, t2).offset(0x4L).setu(0);
          MEMORY.ref(2, t2).offset(0x8L).setu(0xffffL);
          if((int)a1 <= 0) {
            break;
          }
          t0 = a0;
          t7 = t0 << 5;
        } while(a0 != t3);
      }
    }

    //LAB_bfc09130
    return 0x1L;
  }

  @Method(0xbfc09144L)
  public static int waitForMemcard() {
    //LAB_bfc09158
    while(true) {
      if(memcardOkay_a000b9d0.get() == 0x1L) {
        resetMemcardStatusAndEvents();
        return 1;
      }

      //LAB_bfc0917c
      //LAB_bfc09188
      for(int i = 0; i < 0x10; i += 4) {
        if(memcardError_a000b9d4.offset(i).get() == 0x1L) {
          resetMemcardStatusAndEvents();
          return 0;
        }
      }

      DebugHelper.sleep(1);
    }
  }

  @Method(0xbfc091ccL)
  public static long FUN_bfc091cc(final int port, final long a1, final long a2, final long a3) {
    final long at;
    if(port < 0) {
      at = port + 0xfL;
    } else {
      at = port;
    }

    //LAB_bfc09228
    final long s6 = (int)at >> 4;

    //LAB_bfc0922c
    long s0 = a1;
    long s2 = a3;
    long s3 = 0;
    for(long s1 = 0; s1 < a2; s1++) {
      long v0 = FUN_bfc09540(s6, s0);
      final int sector = (int)(v0 == -0x1L ? s0 : v0);

      //LAB_bfc09244
      if(read_card_sector(port, sector, s2) != 0x1L) {
        return 0;
      }

      //LAB_bfc0926c
      v0 = FUN_bfc0895c();
      if(v0 != 0) {
        _a0009d80.offset(s6 * 0x4L).setu(v0);
        return s3;
      }

      //LAB_bfc092a0
      s0++;
      s2 += 0x80L;
      s3 += 0x80L;
    }

    //LAB_bfc092c4
    //LAB_bfc092c8
    return s3;
  }

  @Method(0xbfc09540L)
  public static int FUN_bfc09540(final long joypadIndex, final long a1) {
    //LAB_bfc09568
    for(int v1 = 0; v1 != 0x14; v1++) {
      if(_a000b9e8.offset(joypadIndex * 0x50L).offset(v1 * 0x4L).get() == a1) {
        return v1 + 0x24;
      }

      //LAB_bfc09580
    }

    return -1;
  }

  @Method(0xbfc09720L)
  public static void FUN_bfc09720(final int port, final jmp_buf jmpBuf, final long callback, final long[] a3) {
    //LAB_bfc09774
    for(int sector = 0; sector != 0xf; sector++) {
      if(callback == 0 || (long)MEMORY.ref(4, callback).call(a3[sector]) == 0) {
        //LAB_bfc097a0
        final long at;
        if(port < 0) {
          at = port + 0xfL;
        } else {
          at = port;
        }

        //LAB_bfc097ac
        final long joypadIndex = (int)at >> 4;
        final long s0 = _a000be48.offset(joypadIndex * 0x80L).getAddress();
        memcpy_Impl_A2a(s0, _a000ba88.offset(joypadIndex * 0x1e0L).offset(sector * 0x20L).getAddress(), 0x20);
        FUN_bfc087c4(s0);
        if(!write_card_sector(port, sector + 1, s0) || waitForMemcard() == 0) {
          //LAB_bfc09814
          longjmp_Impl_A14(jmpBuf, 1);
        }
      }

      //LAB_bfc09820
      //LAB_bfc09824
    }

    if(!_card_info_Impl_Aab(port) || waitForMemcard() == 0) {
      //LAB_bfc09850
      longjmp_Impl_A14(jmpBuf, 1);
    }

    //LAB_bfc09860
  }

  @Method(0xbfc098a8L)
  public static long FUN_bfc098a8(final int port, final long[] a1) {
    final jmp_buf sp0x28 = null;

    //TODO this shouldn't be necessary
//    if(setjmp_Impl_A13(sp0x28) != 0) {
//      return 0x1L;
//    }

    FUN_bfc09720(port, sp0x28, getMethodAddress(Bios.class, "FUN_bfc09890", long.class), a1);
    FUN_bfc09720(port, sp0x28, getMethodAddress(Bios.class, "FUN_bfc0989c", long.class), a1);

    //LAB_bfc09904
    return 0;
  }

  @Method(0xbfc09914L)
  public static void _bu_init_Impl_A55() {
    _a0009f20.setu(0);
    _a0009f24.setu(0);
    FUN_bfc088a0();
    FUN_bfc08b3c(0);
    FUN_bfc08b3c(0x10);
  }

  @Method(0xbfc092ecL)
  public static long FUN_bfc092ec(final int port, final long a1, final long a2, final long a3) {
    long at;
    long v0;
    final long t0;
    final long t1;
    final long t2;
    final long t6;
    final long t7;
    long s0;
    long s2;
    long s4;
    final long s5;
    long s7;
    final long t8;
    final long t9;
    long s1 = a1;
    long s3 = a3;
    s2 = 0;
    s7 = 0;
    s5 = -0x1L;
    if((int)a2 > 0) {
      //LAB_bfc09334
      if(port >= 0) {
        at = port;
      } else {
        at = port;
        at = at + 0xfL;
      }

      //LAB_bfc0934c
      s4 = (int)at >> 4;

      //LAB_bfc09350
      do {
        v0 = FUN_bfc09540(s4, s1);

        //LAB_bfc09368
        if(!write_card_sector(port, (int)(v0 == s5 ? s1 : v0), s3)) {
          return 0;
        }

        //LAB_bfc09390
        v0 = FUN_bfc0895c();
        at = 0x4L;
        if(v0 != at) {
          s0 = v0;
          //LAB_bfc093f8
          if((int)v0 > 0) {
            t8 = s4 << 2;
            at = 0xa001_0000L;
            at = at + t8;
            MEMORY.ref(4, at).offset(-0x6080L).setu(s0);
            return -1;
          }
        } else {
          s0 = s1 - 0x1L;
          v0 = FUN_bfc09540(s4, s0);

          //LAB_bfc093c0
          v0 = FUN_bfc09598(port, (int)(v0 == s5 ? s0 : v0), s3 - 0x80L);
          if(v0 == 0) {
            t7 = s4 << 2;
            at = 0xa001_0000L;
            at = at + t7;
            t6 = 0x1L;
            MEMORY.ref(4, at).offset(-0x6080L).setu(t6);
            return -1;
          }
        }
        s2 = s2 + 0x1L;

        //LAB_bfc09424
        s3 = s3 + 0x80L;
        s7 = s7 + 0x80L;
        s1 = s1 + 0x1L;
      } while(s2 != a2);
    }

    //LAB_bfc09440
    if(!_card_info_Impl_Aab(port)) {
      return 0;
    }

    //LAB_bfc0945c
    v0 = FUN_bfc0895c();
    at = 0x4L;
    s0 = v0;
    if(v0 == at) {
      if(port >= 0) {
        at = port;
      } else {
        at = port + 0xfL;
      }

      //LAB_bfc09480
      s4 = (int)at >> 4;
      v0 = FUN_bfc09540(s4, s1 - 0x1L);

      //LAB_bfc0949c
      v0 = FUN_bfc09598(port, (int)(v0 == s5 ? s1 : v0), s3 - 0x80L);
      if(v0 == 0) {
        t0 = s4 << 2;
        at = 0xa001_0000L;
        at = at + t0;
        t9 = 0x1L;
        MEMORY.ref(4, at).offset(-0x6080L).setu(t9);
        return -1;
      }

      //LAB_bfc094d4
    }

    //LAB_bfc094dc
    if((int)s0 > 0) {
      if(port >= 0) {
        at = port;
      } else {
        at = port + 0xfL;
      }

      //LAB_bfc094f0
      t1 = (int)at >> 4;
      at = 0xa001_0000L;
      t2 = t1 << 2;
      at = at + t2;
      MEMORY.ref(4, at).offset(-0x6080L).setu(s0);
      return -1;
    }

    //LAB_bfc09510
    v0 = s7;

    //LAB_bfc09518
    return v0;
  }

  @Method(0xbfc09598L)
  public static long FUN_bfc09598(final int port, final int a1, final long a2) {
    //LAB_bfc095f8
    long s1 = _a000b9e8.offset(port / 0x10L * 0x50L).getAddress();

    //LAB_bfc09618
    int sector;
    long s4;
    do {
      for(sector = 0; sector < 20; sector++) {
        if((int)MEMORY.ref(4, s1).offset(sector * 0x4L).get() == -1) {
          break;
        }
      }

      //LAB_bfc0963c
      if(sector >= 20) {
        return 0;
      }

      //LAB_bfc09650
      s4 = s1 + sector * 0x4L;
      MEMORY.ref(4, s4).offset(0x0L).setu(-2);
      if(write_card_sector(port, sector + 36, a2)) {
        if(waitForMemcard() != 0) {
          break;
        }
      }

      //LAB_bfc09680
    } while(true);

    //LAB_bfc0968c
    MEMORY.ref(4, s4).offset(0x0L).setu(a1);
    s1 = _a000be48.offset(port / 0x10L * 0x80L).getAddress();
    bzero_Impl_A28(s1, 0x80);
    memcpy_Impl_A2a(s1, s4, 0x4);
    FUN_bfc087c4(s1);
    if(!write_card_sector(port, sector + 16, s1)) {
      return 0;
    }

    //LAB_bfc096e8
    //LAB_bfc096f0
    return waitForMemcard();
  }

  @Method(0xbfc0996cL)
  public static int dev_card_open_Impl_A65(final long fcb, final String filename, final int mode) {
    long s2 = MEMORY.ref(4, fcb).offset(0x4L).get();
    MEMORY.ref(4, fcb).offset(0x18L).setu(0x10L);

    final long at;
    if((int)s2 < 0) {
      at = s2 + 0xfL;
    } else {
      at = s2;
    }

    //LAB_bfc099ac
    final long joypadIndex = (int)at >> 4;

    if(_a0009f20.offset(joypadIndex * 0x4L).get() != 0) {
      return 1;
    }

    //LAB_bfc099d0
    resetMemcardStatusAndEvents();

    if((mode & 0x8000L) != 0x8000L) {
      if(FUN_bfc089c0((int)MEMORY.ref(4, fcb).offset(0x4L).get()) == 0) {
        return 1;
      }
    }

    //LAB_bfc09a08
    if((mode & 0x200L) == 0x200L) {
      set_card_find_mode(0);

      long a2 = FUN_bfc0a754(MEMORY.ref(4, fcb).offset(0x4L).get(), 0, filename);
      if(a2 != -0x1L) {
        MEMORY.ref(4, fcb).offset(0x18L).setu(0x11L);
        return 1;
      }

      //LAB_bfc09a48
      final long t2 = _a000ba88.offset(joypadIndex * 0x1e0L).getAddress();
      long s1 = t2;

      //LAB_bfc09a80
      final long[] sp0x4c = new long[0xf];
      long t0 = 0;
      for(int sector = 0; sector < 0xf; sector++) {
        sp0x4c[sector] = 0;

        if((MEMORY.ref(4, s1).offset(0x0L).get() & 0xf0L) == 0xa0L) {
          t0++;
        }

        //LAB_bfc09a98
        s1 += 0x20L;
      }

      long v0 = mode >> 16;
      v0 = v0 & 0xffffL;
      v0 = v0 << 13;
      final long t9 = t0 << 13;

      MEMORY.ref(4, fcb).offset(0x20L).setu(v0);
      if(t9 < v0) {
        //LAB_bfc09c40
        MEMORY.ref(4, fcb).offset(0x18L).setu(0x1cL);
        return 1;
      }

      v0 = MEMORY.ref(4, fcb).offset(0x20L).get();
      t0 = v0 >>> 13;
      if((v0 & 0x1fffL) != 0) {
        t0++;
      }

      //LAB_bfc09ad8
      s2 = 0;
      long s3 = 0;
      long v1 = 0;
      s1 = t2;

      //LAB_bfc09af0
      for(int sector = 0; sector < 0xf; sector++) {
        if((MEMORY.ref(4, s1).offset(0x0L).get() & 0xf0L) == 0xa0L) {
          if(v1 == 0) {
            MEMORY.ref(4, s1).offset(0x0L).setu(0x51L);
            MEMORY.ref(4, s1).offset(0x4L).setu(MEMORY.ref(4, fcb).offset(0x20L));

            final String newFilename = MEMORY.ref(1, s1).offset(0xaL).getString() + filename;
            MEMORY.ref(1, s1).offset(0xaL).set(newFilename.substring(0, Math.min(20, newFilename.length())));
            //Done manually on previous line: strncpy_Impl_A1a(MEMORY.ref(20, s1 + 0xaL, CString::new), filename, 20);

            sp0x4c[sector] = 0x51L;
            s3 = sector;
            a2 = sector;
          } else {
            //LAB_bfc09b70
            MEMORY.ref(2, t2).offset(s2 * 0x20L).offset(0x8L).setu(sector);
            MEMORY.ref(4, s1).offset(0x0L).setu(0x52L);

            if(sp0x4c[sector] != 0x51L) {
              sp0x4c[sector] = 0x52L;
            }
          }

          s2 = sector;

          //LAB_bfc09b98
          v1++;

          if((int)v1 >= (int)t0) {
            MEMORY.ref(2, s1).offset(0x8L).setu(0xffffL);

            if((int)v1 >= 0x2L) {
              MEMORY.ref(4, s1).offset(0x0L).setu(0x53L);
            }

            //LAB_bfc09bc0
            if(FUN_bfc098a8((int)MEMORY.ref(4, fcb).offset(0x4L).get(), sp0x4c) == 0) {
              //LAB_bfc09c30
              //LAB_bfc09ca8
              MEMORY.ref(4, fcb).offset(0x24L).setu(a2);
              MEMORY.ref(4, fcb).offset(0x10L).setu(0);
              MEMORY.ref(4, fcb).offset(0x18L).setu(0);
              MEMORY.ref(4, fcb).offset(0x20L).setu(MEMORY.ref(4, t2).offset(a2 * 0x20L).offset(0x4L));
              return 0;
            }

            //LAB_bfc09be4
            do {
              v0 = t2 + s3 * 0x20L;
              MEMORY.ref(2, v0).offset(0x8L).setu(0xffffL);
              s3 = MEMORY.ref(2, v0).offset(0x8L).get(); //TODO This is an infinite loop...? It's getting set to 0xffff on the previous line
              MEMORY.ref(4, v0).offset(0x0L).setu(0xa0L);
              MEMORY.ref(4, v0).offset(0x4L).setu(0);
            } while(s3 != 0);

            MEMORY.ref(4, fcb).offset(0x18L).setu(0x10L);
            return 1;
          }
        }

        //LAB_bfc09c18
        //LAB_bfc09c1c
        s1 += 0x20L;
      }

      //LAB_bfc09c30
      //LAB_bfc09ca8
      MEMORY.ref(4, fcb).offset(0x24L).setu(a2);
      MEMORY.ref(4, fcb).offset(0x10L).setu(0);
      MEMORY.ref(4, fcb).offset(0x18L).setu(0);
      MEMORY.ref(4, fcb).offset(0x20L).setu(MEMORY.ref(4, t2).offset(a2 * 0x20L).offset(0x4L));
      return 0;
    }

    //LAB_bfc09c58
    set_card_find_mode(0);
    final long sector = FUN_bfc0a754(MEMORY.ref(4, fcb).offset(0x4L).get(), 0, filename);
    if(sector == -0x1L) {
      MEMORY.ref(4, fcb).offset(0x18L).setu(0x2L);
      return 1;
    }

    //LAB_bfc09c8c
    //LAB_bfc09ca8
    MEMORY.ref(4, fcb).offset(0x24L).setu(sector);
    MEMORY.ref(4, fcb).offset(0x10L).setu(0);
    MEMORY.ref(4, fcb).offset(0x18L).setu(0);
    MEMORY.ref(4, fcb).offset(0x20L).setu(_a000ba88.offset(s2 * 0x1e0L).offset(sector * 0x20L).offset(0x4L));

    //LAB_bfc09cc4
    return 0;
  }

  @Method(0xbfc09cdcL)
  public static int dev_card_close_Impl_A68(final int fcb) {
    final long t6 = MEMORY.ref(4, fcb).offset(0x4L).get();
    final long at;
    if((int)t6 < 0) {
      at = t6 + 0xfL;
    } else {
      at = t6;
    }

    //LAB_bfc09cfc
    final long joypadIndex = (int)at >> 4;

    if(_a0009f20.offset(joypadIndex * 0x4L).get() == 0) {
      //LAB_bfc09d20
      resetMemcardStatusAndEvents();
      return 0;
    }

    //LAB_bfc09d2c
    return 1;
  }

  @Method(0xbfc09d3cL)
  public static int dev_card_read_Impl_A66(final long fcb, final long dest, final int length) {
    final long s7 = MEMORY.ref(4, fcb).offset(0x4L).get();
    MEMORY.ref(4, fcb).offset(0x18L).setu(0x10L);

    long at;
    if((int)s7 < 0) {
      at = s7 + 0xfL;
    } else {
      at = s7;
    }

    //LAB_bfc09d88
    final long joypadIndex = (int)at >> 4;

    if(_a0009f20.offset(joypadIndex * 0x4L).get() != 0) {
      return -1;
    }

    //LAB_bfc09dac
    resetMemcardStatusAndEvents();

    long v1 = MEMORY.ref(4, fcb).offset(0x10L).get();
    if((v1 & 0x7fL) != 0) {
      final long t0 = 0x16L;
      MEMORY.ref(4, fcb).offset(0x18L).setu(t0);
      return -1;
    }

    //LAB_bfc09dd8
    if(v1 >= MEMORY.ref(4, fcb).offset(0x20L).get()) {
      MEMORY.ref(4, fcb).offset(0x18L).setu(0x16L);
      return -1;
    }

    //LAB_bfc09dfc
    long s1 = v1 >>> 7;
    if(length < 0) {
      at = length + 0x7fL;
    } else {
      at = length;
    }

    //LAB_bfc09e24
    long s2 = (int)at >> 7;
    long v0;
    long s0;
    if((MEMORY.ref(4, fcb).get() & 0x8000L) == 0x8000L) {
      if(_a0009f20.offset(joypadIndex * 0x4L).get() != 0) {
        return -1;
      }

      //LAB_bfc09e5c
      _a0009f20.offset(joypadIndex * 0x4L).setu(0x2);
      _a0009f50.offset(joypadIndex * 0x4L).setu(s1);
      _a0009f58.offset(joypadIndex * 0x4L).setu(s2);
      _a0009f78.offset(joypadIndex * 0x4L).setu(dest);
      _a0009f70.offset(joypadIndex * 0x4L).setu(fcb);
      resetMemcardStatusAndEvents();
      if(s2 == 0) {
        MEMORY.ref(4, fcb).offset(0x18L).setu(0);
        memcardOkay_a000b9d0.setu(0x1L);
        FUN_bfc0bff0(joypadIndex, EvSpIOE);
        return 0;
      }

      //LAB_bfc09eec
      s0 = FUN_bfc0a3d4(joypadIndex, _a0009f70.offset(joypadIndex * 0x4L).deref(4).offset(0x24L).get(), _a0009f50.offset(joypadIndex * 0x4L).get());
      v0 = FUN_bfc09540(joypadIndex, s0);
      final long sector;
      if(v0 == -0x1L) {
        sector = s0;
      } else {
        sector = v0;
      }

      //LAB_bfc09f20
      MEMORY.ref(4, fcb).offset(0x18L).setu(0x10L);
//      if(read_card_sector((int)MEMORY.ref(4, fcb).offset(0x4L).get(), (int)sector, dest) != 0x1L) {
//        return -1;
//      }

      //LAB_bfc09f50
//      MEMORY.ref(4, fcb).offset(0x18L).setu(0);
//      return 0;

      MEMCARD.directRead((int)sector, dest, length / 0x80);
      _a0009f20.offset(joypadIndex * 0x4L).setu(0);
      return 0;
    }

    //LAB_bfc09f68
    long s3 = 0;
    if((int)s2 > 0) {
      //LAB_bfc09f78
      long s4 = dest;
      do {
        long t5 = s1 & 0x3fL;
        if((int)s1 < 0 && t5 != 0) {
          t5 -= 0x40L;
        }

        //LAB_bfc09f8c
        v0 = 0x40L - t5;
        if((int)v0 < (int)s2) {
          s0 = v0;
        } else {
          s0 = s2;
        }

        //LAB_bfc09fa8
        v0 = FUN_bfc0a3d4(joypadIndex, MEMORY.ref(4, fcb).offset(0x24L).get(), s1);
        v0 = FUN_bfc091cc((int)MEMORY.ref(4, fcb).offset(0x4L).get(), v0, s0, s4);
        v1 = s0 << 7;
        s2 = s2 - s0;
        if(v0 != v1) {
          break;
        }

        //LAB_bfc09fec
        s1 += s0;
        s4 += v1;
        s3 += s0;
      } while((int)s2 > 0);

      v1 = MEMORY.ref(4, fcb).offset(0x10L).get();
    }

    v0 = s3 << 7;

    //LAB_bfc0a00c
    MEMORY.ref(4, fcb).offset(0x10L).setu(v1 + v0);
    MEMORY.ref(4, fcb).offset(0x18L).setu(0);
    _a0009f20.offset(joypadIndex * 0x4L).setu(0);

    if(v0 == length) {
      return length;
    }

    //LAB_bfc0a048
    //LAB_bfc0a060
    return (int)-_a0009f80.offset(joypadIndex * 0x4L).get();
  }

  @Method(0xbfc0a080L)
  public static int dev_card_write_Impl_A67(final long fcb, final long src, final int length) {
    long v0;
    long t6;
    long s0;
    long s7 = MEMORY.ref(4, fcb).offset(0x4L).get();
    final long t7 = _a0009f20.getAddress();
    MEMORY.ref(4, fcb).offset(0x18L).setu(0x10L);

    //LAB_bfc0a0cc
    s7 = (int)s7 / 0x10;
    final long sp50 = t7 + s7 * 0x4L;

    if(MEMORY.ref(4, sp50).offset(0x0L).get() != 0) {
      return -1;
    }

    //LAB_bfc0a0f0
    resetMemcardStatusAndEvents();

    final long v1 = MEMORY.ref(4, fcb).offset(0x10L).get();
    if((v1 & 0x7fL) != 0) {
      MEMORY.ref(4, fcb).offset(0x18L).setu(0x16L);
      return -1;
    }

    //LAB_bfc0a11c
    if(v1 >= MEMORY.ref(4, fcb).offset(0x20L).get()) {
      MEMORY.ref(4, fcb).offset(0x18L).setu(0x16L);
      return -1;
    }

    //LAB_bfc0a140
    long s3 = 0;
    long s1 = v1 >>> 7;

    //LAB_bfc0a168
    long s2 = length / 0x80;
    if((MEMORY.ref(4, fcb).offset(0x0L).get() & 0x8000L) == 0x8000L) {
      if(MEMORY.ref(4, sp50).offset(0x0L).get() != 0) {
        return -1;
      }

      //LAB_bfc0a1a0
      MEMORY.ref(4, sp50).offset(0x0L).setu(0x3L);
      _a0009f50.offset(s7 * 0x4L).setu(s1);
      _a0009f58.offset(s7 * 0x4L).setu(s2);
      _a0009f78.offset(s7 * 0x4L).setu(src);
      _a0009f70.offset(s7 * 0x4L).setu(fcb);
      resetMemcardStatusAndEvents();

      if(s2 == 0) {
        MEMORY.ref(4, fcb).offset(0x18L).setu(0);
        memcardOkay_a000b9d0.setu(0x1L);
        FUN_bfc0bff0(s7, EvSpIOE);
        return 0;
      }

      //LAB_bfc0a230
      final int sector1 = FUN_bfc0a3d4(s7, _a0009f70.offset(s7 * 0x4L).deref(4).offset(0x24L).get(), _a0009f50.offset(s7 * 0x4L).get());
      final int sector2 = FUN_bfc09540(s7, sector1);
      final int sector = sector2 == -1 ? sector1 : sector2;

      //LAB_bfc0a264
      _a0009f88.offset(s7 * 0x2L).setu(sector);
      MEMORY.ref(4, fcb).offset(0x18L).setu(0x10L);

      if(!write_card_sector((int)MEMORY.ref(4, fcb).offset(0x4L).get(), sector, src)) {
        return -1;
      }

      //LAB_bfc0a2a4
      MEMORY.ref(4, fcb).offset(0x18L).setu(0);
      return 0;
    }

    //LAB_bfc0a2bc
    //LAB_bfc0a2cc
    long s4 = src;
    while((int)s2 > 0) {
      t6 = s1 & 0x3fL;
      if((int)s1 < 0 && t6 != 0) {
        t6 = t6 - 0x40L;
      }

      //LAB_bfc0a2e0
      v0 = 0x40L - t6;
      if((int)v0 >= (int)s2) {
        s0 = s2;
      } else {
        s0 = v0;
      }

      //LAB_bfc0a2fc
      if(FUN_bfc092ec((int)MEMORY.ref(4, fcb).offset(0x4L).get(), FUN_bfc0a3d4(s7, MEMORY.ref(4, fcb).offset(0x24L).get(), s1), s0, s4) != s0 * 0x80L) {
        break;
      }

      //LAB_bfc0a340
      s2 = s2 - s0;
      s1 = s1 + s0;
      s4 = s4 + s0 * 0x80L;
      s3 = s3 + s0;
    }

    //LAB_bfc0a360
    MEMORY.ref(4, fcb).offset(0x10L).addu(s3 * 0x80L);
    MEMORY.ref(4, fcb).offset(0x18L).setu(0);
    MEMORY.ref(4, sp50).offset(0x0L).setu(0);

    if(length == s3 * 0x80L) {
      return length;
    }

    //LAB_bfc0a39c
    //LAB_bfc0a3b4
    return (int)-_a0009f80.offset(s7 * 0x4L).get();
  }

  @Method(0xbfc0a3d4L)
  public static int FUN_bfc0a3d4(final long a0, long a1, long a2) {
    //LAB_bfc0a400
    for(; (int)a2 >= 0x40L; a2 -= 0x40L) {
      a1 = _a000ba88.offset(2, a0 * 0x1e0L).offset(a1 * 0x20L).offset(0x8L).get();
      if(a1 == 0xffffL) {
        return -1;
      }

      //LAB_bfc0a420
    }

    //LAB_bfc0a42c
    return (int)(a2 + (a1 + 1) * 0x40L);
  }

  @Method(0xbfc0a43cL)
  public static long memcardNamesMatch(final String str1, final String str2) {
    char v0 = str1.charAt(0);

    //LAB_bfc0a458
    int i = 0;
    while(v0 != '\0') {
      final char v1 = str2.charAt(i);

      if(v1 != '?' && v1 != v0) {
        return 0;
      }

      //LAB_bfc0a478
      i++;
      v0 = i < str1.length() ? str1.charAt(i) : '\0';
    }

    //LAB_bfc0a488
    final char v1 = i < str2.length() ? str2.charAt(i) : '\0';

    if(v1 == '\0' || v1 == '?') {
      //LAB_bfc0a4a0
      return 0x1L;
    }

    //LAB_bfc0a4ac
    return 0;
  }

  @Method(0xbfc0a4b4L)
  @Nullable
  public static DIRENTRY dev_card_nextfile_Impl_A6a(final long fcb, final DIRENTRY dir) {
    final long v1 = MEMORY.ref(4, fcb).offset(0x4L).get();

    final long at;
    if((int)v1 < 0) {
      at = v1 + 0xfL;
    } else {
      at = v1;
    }

    //LAB_bfc0a4dc
    final long t0 = (int)at >> 4;

    if(_a0009f20.offset(t0 * 0x4L).get() != 0) {
      MEMORY.ref(4, fcb).offset(0x18L).setu(0x10L);
      return null;
    }

    //LAB_bfc0a508
    resetMemcardStatusAndEvents();

    final long v1_0 = FUN_bfc0a754(v1, memcardFileIndex_a0009f8c.getSigned() + 0x1L, _a0009f98.getString());
    if(v1_0 == -0x1L) {
      MEMORY.ref(4, fcb).offset(0x18L).setu(0x2L);
      return null;
    }

    //LAB_bfc0a570
    final long v0 = _a000ba88.offset(t0 * 0x1e0L).offset(v1_0 * 0x20L).getAddress();

    dir.attr.set((v1_0 + 0x1L) * 0x40L);
    MEMORY.ref(4, dir.name.getAddress()).offset(0x14L).setu(MEMORY.ref(4, v0).get() & 0xf0L);
    MEMORY.ref(4, dir.name.getAddress()).offset(0x18L).setu(MEMORY.ref(4, v0).offset(0x4L));

    // This actually overflows the string buffer (20 chars + null term). My code blocks overflows, so this is just a hack to... let it happen
    int i = 0;
    for(; MEMORY.ref(1, v0 + 0xaL + i).get() != 0; i++) {
      MEMORY.ref(1, dir.name.getAddress() + i).setu(MEMORY.ref(1, v0 + 0xaL + i));
    }

    MEMORY.ref(1, dir.name.getAddress() + i).setu(0);

    //strcpy_Impl_A19(dir.name, MEMORY.ref(1, v0 + 0xaL).getString());

    MEMORY.ref(4, fcb).offset(0x18L).setu(0);

    //LAB_bfc0a5d4
    return dir;
  }

  @Method(0xbfc0a5e4L)
  @Nullable
  public static DIRENTRY dev_card_firstfile_Impl_A69(final long a0, final String a1, final DIRENTRY a2) {
    final long v1 = MEMORY.ref(4, a0).offset(0x4L).get();
    MEMORY.ref(4, a0).offset(0x18L).setu(0x10L);

    final long at;
    if((int)v1 < 0) {
      at = v1 + 0xfL;
    } else {
      at = v1;
    }

    //LAB_bfc0a610
    if(_a0009f20.offset((at >> 4) * 0x4L).get() != 0) {
      return null;
    }

    //LAB_bfc0a634
    resetMemcardStatusAndEvents();

    if(FUN_bfc089c0((int)v1) == 0) {
      return null;
    }

    //LAB_bfc0a660
    long v1_0 = _a0009f98.getAddress();
    int a1_0 = 0;

    if(a1.isEmpty()) {
      //LAB_bfc0a694
      do {
        v1_0++;
        MEMORY.ref(1, v1_0).offset(-0x1L).setu(0x3fL);
      } while(v1_0 < _a0009fac.getAddress());

      MEMORY.ref(1, v1_0).setu(0);
    } else {
      //LAB_bfc0a6b0
      if(a1.charAt(0) != 0) {
        //LAB_bfc0a6cc
        char v0 = a1.charAt(a1_0);

        //LAB_bfc0a6d4
        do {
          if(v0 == '*') {
            break;
          }

          MEMORY.ref(1, v1_0).setu(v0);
          v0 = a1.charAt(a1_0 + 1);
          a1_0++;
          v1_0++;
        } while(v0 != 0);
      }

      //LAB_bfc0a6f0
      if(a1.charAt(a1_0) == '*') {
        //LAB_bfc0a718
        while(v1_0 < _a0009fac.getAddress()) {
          v1_0++;
          MEMORY.ref(1, v1_0).offset(-0x1L).setu(0x3fL);
        }
      }

      //LAB_bfc0a728
      MEMORY.ref(1, v1_0).setu(0);
    }

    //LAB_bfc0a72c
    memcardFileIndex_a0009f8c.setu(-0x1L);

    //LAB_bfc0a744
    return dev_card_nextfile_Impl_A6a(a0, a2);
  }

  @Method(0xbfc0a754L)
  public static long FUN_bfc0a754(final long a0, final long a1, final String a2) {
    if((int)a1 < 0xfL) {
      final long at;
      if((int)a0 < 0) {
        at = a0 + 0xfL;
      } else {
        at = a0;
      }

      //LAB_bfc0a7a4
      final long t6 = (int)at >> 4;

      //LAB_bfc0a7bc
      for(long sector = a1; sector < 0xfL; sector++) {
        final long s1 = _a000ba88.offset(t6 * 0x1e0L).offset(sector * 0x20L).getAddress();

        if(get_card_find_mode() == 0) {
          if(MEMORY.ref(4, s1).get() == 0x51L) {
            final String s0 = MEMORY.ref(1, s1).offset(0xaL).getString();
            if(!s0.isEmpty()) {
              if(memcardNamesMatch(s0, a2) != 0) {
                memcardFileIndex_a0009f8c.setu(sector);
                return sector;
              }
            }
          }

          //LAB_bfc0a824
        } else if(MEMORY.ref(4, s1).offset(0x0L).get() == 0xa1L) {
          final String s0 = MEMORY.ref(1, s1).offset(0xaL).getString();
          if(!s0.isEmpty()) {
            if(memcardNamesMatch(s0, a2) != 0) {
              memcardFileIndex_a0009f8c.setu(sector);
              return sector;
            }
          }
        }

        //LAB_bfc0a87c
      }
    }

    //LAB_bfc0a8a4
    //LAB_bfc0a8a8
    return -0x1L;
  }

  @Method(0xbfc0b170L)
  public static int FUN_bfc0b170(final int port) {
    assert false;
    return 0;
  }

  @Method(0xbfc0b54cL)
  public static void handleMemcardBusy() {
    int port = get_bu_callback_port();
    if(port < 0) {
      port += 0xfL;
    }

    //LAB_bfc0b568
    final long v1 = port >> 0x4L;
    if(_a0009f20.offset(v1 * 4).get() != 0) {
      FUN_bfc0bff0(v1, EvSpTIMOUT);
    }

    //LAB_bfc0b594
  }

  @Method(0xbfc0b5a0L)
  public static void handleMemcardIoError() {
    long port = get_bu_callback_port();
    if((int)port < 0) {
      port += 0xfL;
    }

    final long v1 = port >> 0x4L;
    if(_a0009f20.offset(v1 * 4L).get() != 0) {
      FUN_bfc0bff0(v1, EvSpERROR);
    }
  }

  @Method(0xbfc0b5f4L)
  public static void handleMemcardEjected() {
    long at = get_bu_callback_port();
    if(at < 0) {
      at += 0xfL;
    }

    //lAB_bfc0b610
    final long v1 = at / 16;
    if(_a0009f20.offset(v1 * 4).get() != 0) {
      FUN_bfc0bff0(v1, EvSpNEW);
    }

    //LAB_bfc0b63c
  }

  @Method(0xbfc0b648L)
  public static void handleMemcardOkay() {
    final long[] sp54 = new long[0xf];
    long a0;
    final int sector;
    final int s0;
    final int v0;
    long v1;

    final int port = get_bu_callback_port();
    long at = port;
    if((int)at < 0) {
      at += 0xfL;
    }

    //LAB_bfc0b670
    final long s1 = at >> 4;

    switch((int)_a0009f20.offset(s1 * 4).get()) {
      case 0:
        return;

      case 2:
        _a0009f58.offset(s1 * 0x4L).subu(0x1L);
        if(_a0009f58.offset(s1 * 0x4L).get() == 0) {
          FUN_bfc0bff0(s1, EvSpIOE);
          DeliverEvent(_a0009f70.offset(s1 * 0x4L).deref(4).offset(0x28L).get(), EvSpIOE);
          return;
        }

        //LAB_bfc0b714
        _a0009f78.offset(s1 * 0x4L).addu(0x80L);
        _a0009f70.offset(s1 * 0x4L).deref(4).offset(0x10L).addu(0x80L);
        _a0009f50.offset(s1 * 0x4L).addu(0x1L);

        s0 = FUN_bfc0a3d4(s1, _a0009f70.offset(s1 * 0x4L).deref(4).offset(0x24L).get(), _a0009f50.offset(s1 * 0x4L).get());
        v0 = FUN_bfc09540(s1, s0);

        if(v0 == -0x1L) {
          sector = s0;
        } else {
          sector = v0;
        }

        //LAB_bfc0b7b0
        if(read_card_sector(port, sector, MEMORY.ref(4, _a0009f78.offset(s1 * 0x4L).getAddress()).get()) != 0x1L) {
          memcardOkay_a000b9d0.setu(0);
          memcardError_a000b9d4.setu(0x1L);
          FUN_bfc0bff0(s1, EvSpERROR);
        }

        //LAB_bfc0b7e8
        return;

      case 6:
        _a0009f20.offset(s1 * 4).setu(0x3L);
        // Fall through

      case 3:
        _a0009f58.offset(s1 * 0x4L).subu(0x1L);
        if(_a0009f58.offset(s1 * 0x4L).get() == 0) {
          memcardOkay_a000b9d0.setu(0);

          if(!_card_info_Impl_Aab(port)) {
            memcardError_a000b9d4.setu(0x1L);
            FUN_bfc0bff0(s1, EvSpERROR);
          }

          //LAB_bfc0b850
          _a0009f20.offset(s1 * 0x4L).setu(0x7L);
          return;
        }

        //LAB_bfc0b864
        _a0009f78.offset(s1 * 0x4L).addu(0x80L);
        _a0009f70.offset(s1 * 0x4L).deref(4).offset(0x10L).addu(0x80L);
        _a0009f50.offset(s1 * 0x4L).addu(0x1L);

        s0 = FUN_bfc0a3d4(s1, _a0009f70.offset(s1 * 0x4L).deref(4).offset(0x24L).get(), _a0009f50.offset(s1 * 0x4L).get());
        v0 = FUN_bfc09540(s1, s0);

        if(v0 == -0x1L) {
          sector = s0;
        } else {
          sector = v0;
        }

        //LAB_bfc0b900
        _a0009f88.offset(s1 * 0x2L).setu(sector);
        if(!write_card_sector(port, sector, _a0009f78.offset(s1 * 0x4L).get())) {
          memcardOkay_a000b9d0.setu(0);
          memcardError_a000b9d4.setu(0x1L);
          FUN_bfc0bff0(s1, EvSpERROR);
        }

        //LAB_bfc0b948
        return;

      case 5:
        _a0009f20.offset(s1 * 0x4L).setu(0x6L);
        _a000b9e8.offset(s1 * 0x50L).offset(_a0009f60.offset(s1 * 0x4L).get() * 0x4L).setu(_a0009f68.offset(s1 * 0x4L));

        bzero_Impl_A28(_a000be48.offset(s1 * 0x80L).getAddress(), 0x80);
        memcpy_Impl_A2a(_a000be48.offset(s1 * 0x80L).getAddress(), _a0009f68.offset(s1 * 0x4L).getAddress(), 0x4);
        FUN_bfc087c4(_a000be48.offset(s1 * 0x80L).getAddress());
        if(!write_card_sector(port, (int)(_a0009f60.offset(s1 * 0x4L).get() + 0x10), _a000be48.offset(s1 * 0x80L).getAddress())) {
          memcardOkay_a000b9d0.setu(0);
          memcardError_a000b9d4.setu(0x1L);
          FUN_bfc0bff0(s1, EvSpERROR);
        }

        //LAB_bfc0ba2c
        return;

      case 7:
        DeliverEvent(_a0009f70.offset(s1 * 4L).deref(4).offset(0x28L).get(), EvSpIOE);
        _a0009f70.offset(s1 * 4L).deref(4).offset(0x10L).addu(0x80L);
        // Fall through

      case 8:
      case 1:
        FUN_bfc0bff0(s1, EvSpIOE);
        return;

      case 4:
        v1 = _a0009f28.offset(s1 * 4).get();
        if(v1 == 0x1L) {
          //LAB_bfc0bad4
          if(_a000be48.offset(s1 * 0x80).get() != 0x4dL || _a000be48.offset(s1 * 0x80).offset(0x1L).get() != 0x43L) {
            //LAB_bfc0bb00
            memcardOkay_a000b9d0.setu(0);
            memcardEjected_a000b9dc.setu(0x1L);
            FUN_bfc0bff0(s1, EvSpNEW);
            return;
          }

          //LAB_bfc0bb28
          //LAB_bfc0bb48
          for(int i = 0; i < 0xf; i++) {
            bzero_Impl_A28(_a000ba88.offset(s1 * 0x1e0L).offset(i * 0x20L).getAddress(), 0x20);
            _a000ba88.offset(s1 * 0x1e0L).offset(i * 0x20L).offset(0x0L).setu(0xa0L);
            _a000ba88.offset(s1 * 0x1e0L).offset(i * 0x20L).offset(0x4L).setu(0);
            _a000ba88.offset(s1 * 0x1e0L).offset(i * 0x20L).offset(0x8L).setu(0xffffL);
          }

          //LAB_bfc0bbcc
          _a0009f30.offset(s1 * 0x4L).setu(0);
          _a0009f28.offset(s1 * 0x4L).setu(0x2L);

          if(read_card_sector(port, 1, _a000be48.offset(s1 * 0x80L).getAddress()) == 0x1L) {
          } else {
            memcardOkay_a000b9d0.setu(0);
            memcardError_a000b9d4.setu(0x1L);
            FUN_bfc0bff0(s1, EvSpERROR);
          }

          //LAB_bfc0bbf4
          return;
        }

        if(v1 == 0x2L) {
          //LAB_bfc0bbfc
          if(FUN_bfc08720(_a000be48.offset(s1 * 0x80L).getAddress()) != 0) {
            memcpy_Impl_A2a(_a000ba88.offset(s1 * 0x1e0L).offset(_a0009f30.offset(s1 * 0x4L).get() * 0x20L).getAddress(), _a000be48.offset(s1 * 0x80L).getAddress(), 0x20);
          }

          //LAB_bfc0bc64
          _a0009f30.offset(s1 * 0x4L).addu(0x1L);
          if(_a0009f30.offset(s1 * 0x4L).get() < 0xfL) {
            if(read_card_sector(port, (int)(_a0009f30.offset(s1 * 0x4L).get() + 1), _a000be48.offset(s1 * 0x80L).getAddress()) != 0x1L) {
              memcardOkay_a000b9d0.setu(0);
              memcardError_a000b9d4.setu(0x1L);
              FUN_bfc0bff0(s1, EvSpERROR);
            }

            return;
          }

          //LAB_bfc0bcd0
          //LAB_bfc0bcfc
          for(int i = 0; i < 0xfL; i++) {
            if(FUN_bfc08ffc(s1, i) == 0x1L) {
              sp54[i] = 0x52L;
            } else {
              sp54[i] = 0;
            }

            //LAB_bfc0bd38
          }

          sp54[0] = 0;
          sp54[1] = 0;
          sp54[2] = 0;

          //LAB_bfc0bd5c
          //TODO ??? these stack vals aren't used?
//          for(int i = 0; i < 3; i++) {
//            MEMORY.ref(4, sp60).offset(i * 0x10L).setu(0);
//            MEMORY.ref(4, sp64).offset(i * 0x10L).setu(0);
//            MEMORY.ref(4, sp68).offset(i * 0x10L).setu(0);
//            MEMORY.ref(4, sp6c).offset(i * 0x10L).setu(0);
//          }

          //LAB_bfc0bd84
          for(int i = 0; i < 0xf; i++) {
            if(_a000ba88.offset(s1 * 0x1e0L).offset(i * 0x20L).get() == 0x51L) {
              sp54[i] = 0x1L;
              a0 = _a000ba88.offset(s1 * 0x1e0L).offset(i * 0x20L).offset(0x4L).get();
              v1 = _a000ba88.offset(s1 * 0x1e0L).offset(i * 0x20L).offset(0x8L).get();
              if((int)a0 < 0) {
                a0 += 0x1fffL;
              }

              //LAB_bfc0bdbc
              a0 = a0 / 0x2000 - 1;

              //LAB_bfc0bdd8
              while(v1 != 0xffffL && a0 > 0) {
                sp54[(int)v1] += 0x1L;
                v1 = _a000ba88.offset(s1 * 0x1e0L).offset(2, v1 * 0x20L).offset(0x8L).get();
                a0--;
              }
            }

            //LAB_bfc0be0c
          }

          //LAB_bfc0be28
          for(int i = 0; i < 0xf; i++) {
            if(sp54[i] == 0) {
              _a000ba88.offset(s1 * 0x1e0L).offset(i * 0x20L).setu(0xa0L);
            }

            //LAB_bfc0be44
          }

          //LAB_bfc0be74
          for(int i = 0; i < 5; i++) {
            _a000b9e8.offset(s1 * 0x50L).offset(i * 0x10L).offset(0x0L).setu(-0x1L);
            _a000b9e8.offset(s1 * 0x50L).offset(i * 0x10L).offset(0x4L).setu(-0x1L);
            _a000b9e8.offset(s1 * 0x50L).offset(i * 0x10L).offset(0x8L).setu(-0x1L);
            _a000b9e8.offset(s1 * 0x50L).offset(i * 0x10L).offset(0xcL).setu(-0x1L);
          }

          if(read_card_sector(port, 0x10, _a000be48.offset(s1 * 0x80L).getAddress()) != 0x1L) {
            FUN_bfc0bff0(s1, EvSpERROR);
          }

          //LAB_bfc0bebc
          _a0009f28.offset(s1 * 0x4L).setu(0x3L);
          _a0009f30.offset(s1 * 0x4L).setu(0);
          return;
        }

        if(v1 != 0x3L) {
          break;
        }

        //LAB_bfc0bedc
        if(FUN_bfc08720(_a000be48.offset(s1 * 0x80L).getAddress()) != 0) {
          memcpy_Impl_A2a(_a000b9e8.offset(s1 * 0x50L).offset(_a0009f30.offset(s1 * 0x4L).get() * 0x4L).getAddress(), _a000be48.offset(s1 * 0x80L).getAddress(), 0x4);
        }

        //LAB_bfc0bf38
        _a0009f30.offset(s1 * 0x4L).addu(0x1L);
        if(_a0009f30.offset(s1 * 0x4L).get() < 0x14L) {
          if(read_card_sector(port, (int)(_a0009f30.offset(s1 * 0x4L).get() + 0x10), _a000be48.offset(s1 * 0x80L).getAddress()) == 0x1L) {
            return;
          }

          memcardOkay_a000b9d0.setu(0);
          memcardError_a000b9d4.setu(0x1L);
          FUN_bfc0bff0(s1, EvSpERROR);
          return;
        }

        //LAB_bfc0bfa4
        FUN_bfc0bff0(s1, EvSpIOE);
        return;
    }

    memcardOkay_a000b9d0.setu(0);
    memcardError_a000b9d4.setu(0x1L);
    FUN_bfc0bff0(s1, EvSpERROR);

    //LAB_bfc0bfe0
    //LAB_bfc0bfe4
  }

  @Method(0xbfc0bff0L)
  public static void FUN_bfc0bff0(final long a0, final int spec) {
    _a0009f20.offset(a0 * 4L).setu(0);
    _a0009f28.offset(a0 * 4L).setu(0);
    _a0009f30.offset(a0 * 4L).setu(0);
    DeliverEvent(SwCARD, spec);
  }

  @Method(0xbfc0c0dcL)
  public static boolean _card_info_Impl_Aab(final int port) {
    long at = port;
    if(port < 0) {
      at += 0xfL;
    }

    _a0009f20.offset(at / 16 * 4).setu(0x1L);

    if(_card_info_subfunc(port)) {
      return true;
    }

    _a0009f20.offset(at / 16 * 4).setu(0);
    return false;
  }

  @Method(0xbfc0c140L)
  public static boolean _card_async_load_directories_Impl_Aac(final int port) {
    int at = port;
    if(port < 0) {
      at += 0xf;
    }

    //LAB_bfc0c16c
    final long joypadIndex = at >> 0x4;

    _a0009f20.offset(joypadIndex * 4).setu(0x4L);

    if(read_card_sector(port, 0, _a000be48.offset(joypadIndex * 0x80).getAddress()) == 0) {
      _a0009f20.offset(joypadIndex * 4).setu(0);
      return false;
    }

    //LAB_bfc0c1b0
    // Don't thing this should be here anymore since read_card_sector executes immediately _a0009f20.offset(joypadIndex * 4).setu(0x4L);
    _a0009f28.offset(joypadIndex * 4).setu(0x1L);

    //LAB_bfc0c1cc
    return true;
  }

  @Method(0xbfc0c1fcL)
  public static void AddMemCardDevice_Impl_A97() {
    AddDevice(MemCardDeviceInfo_bfc0e3e4.getAddress());
  }

  @Method(0xbfc0c220L)
  public static void bu_callback_okay_Impl_Aa7() {
    memcardOkay_a000b9d0.setu(0x1L);
    handleMemcardOkay();
  }

  @Method(0xbfc0c248L)
  public static void bu_callback_err_write_Impl_Aa8() {
    memcardError_a000b9d4.setu(0x1L);
    handleMemcardIoError();
  }

  @Method(0xbfc0c270L)
  public static void bu_callback_err_busy_Impl_Aa9() {
    memcardBusy_a000b9d8.setu(0x1L);
    handleMemcardBusy();
  }

  @Method(0xbfc0c298L)
  public static void bu_callback_err_eject_Impl_Aaa() {
    memcardEjected_a000b9dc.setu(0x1L);
    handleMemcardEjected();
  }

  @Method(0xbfc0c354L)
  public static void resetMemcardEvents() {
    UnDeliverEvent(SwCARD, EvSpIOE);
    UnDeliverEvent(SwCARD, EvSpERROR);
    UnDeliverEvent(SwCARD, EvSpNEW);
    UnDeliverEvent(SwCARD, EvSpTIMOUT);
  }

  @Method(0xbfc0d570L)
  public static void FUN_bfc0d570(final EXEC header, final int argc, final long argv) {
    ExitCriticalSection();

    if(responseFromBootMenu_a000dffc.get() != 0) {
      if(FUN_bfc0d72c() < 0) {
        SystemErrorBootOrDiskFailure_Impl_Aa1('D', 0x38b);
      }

      //LAB_bfc0d5b8
      if(FUN_bfc0d7bc() < 0) {
        SystemErrorBootOrDiskFailure_Impl_Aa1('D', 0x38b);
      }
    }

    //LAB_bfc0d5d4
    EnterCriticalSection();

    Exec_Impl_A43(header, argc, argv);
  }

  @Method(0xbfc0d72cL)
  public static long FUN_bfc0d72c() {
    assert false;
    return -1;
  }

  @Method(0xbfc0d7bcL)
  public static long FUN_bfc0d7bc() {
    assert false;
    return -1;
  }

  @Method(0xbfc0d850L)
  public static void clearUserRam() {
    for(int i = 0; i < 0x1e_0000; i += 0x4L) {
      userRamStart_a0010000.offset(i).setu(0);
    }
  }

  @Method(0xbfc0d890L)
  public static int open(final String name, final int mode) {
    return (int)functionVectorB_000000b0.run(0x32L, new Object[] {name, mode});
  }

  @Method(0xbfc0d8a0L)
  public static void close(final int fd) {
    functionVectorB_000000b0.run(0x36L, new Object[] {fd});
  }

  @Method(0xbfc0d8b0L)
  public static int read(final int fd, final long buf, final int size) {
    return (int)functionVectorB_000000b0.run(0x34L, new Object[] {fd, buf, size});
  }

  @Method(0xbfc0d8c0L)
  public static void ExitCriticalSection() {
    CPU.SYSCALL(2);
  }

  @Method(0xbfc0d8e0L)
  public static void SystemErrorUnresolvedException_Impl_A40() {
    // Normally does infinite loop: functionVectorA_000000a0.run(0x40L, EMPTY_OBJ_ARRAY);
    LOGGER.error("Unresolved exception", new Throwable());
    DebugHelper.pause();
  }

  @Method(0xbfc0d950L)
  public static void SystemErrorBootOrDiskFailure_Impl_Aa1(final char type, final int errorCode) {
    // Normally does infinite loop: functionVectorA_000000a0.run(0xa1L, new Object[] {type, errorCode});
    LOGGER.error("Boot failure %s: %08x", type, errorCode);
    DebugHelper.pause();
  }

  @Method(0xbfc0d960L)
  public static boolean EnterCriticalSection() {
    CPU.SYSCALL(1);

    // The exception handler stores v0 (return value) here
    GATE.acquire();
    final boolean ret = ProcessControlBlockPtr_a0000108.deref().threadControlBlockPtr.deref().registers.get(1).get() != 0;
    GATE.release();
    return ret;
  }

  @Method(0xbfc0d970L)
  public static void DeliverEvent(final long cls, final int spec) {
    functionVectorB_000000b0.run(0x7L, new Object[] {cls, spec});
  }

  @Method(0xbfc0d980L)
  public static void ReturnFromException() {
    functionVectorB_000000b0.run(0x17L, EMPTY_OBJ_ARRAY);
  }

  @Method(0xbfc0d990L)
  public static void UnDeliverEvent(final long cls, final int spec) {
    functionVectorB_000000b0.run(0x20L, new Object[] {cls, spec});
  }

  @Method(0xbfc0d9a0L)
  public static void SetDefaultExitFromException() {
    functionVectorB_000000b0.run(0x18L, EMPTY_OBJ_ARRAY);
  }

  @Method(0xbfc0d9b0L)
  public static long OpenEvent(final long cls, final int spec, final int mode, final long func) {
    return (long)functionVectorB_000000b0.run(0x8L, new Object[] {cls, spec, mode, func});
  }

  @Method(0xbfc0d9c0L)
  public static void EnableEvent(final long event) {
    functionVectorB_000000b0.run(0xcL, new Object[] {event});
  }

  @Method(0xbfc0d9d0L)
  public static void CloseEvent(final int event) {
    functionVectorB_000000b0.run(0x9L, new Object[] {event});
  }

  @Method(0xbfc0d9e0L)
  public static int TestEvent(final long event) {
    return (int)functionVectorB_000000b0.run(0xbL, new Object[] {event & 0xffff});
  }

  @Method(0xbfc0d9f0L)
  public static boolean AddDevice(final long deviceInfo) {
    return (boolean)functionVectorB_000000b0.run(0x47L, new Object[] {deviceInfo});
  }

  @Method(0xbfc0da00L)
  public static int read_card_sector(final int port, final int sector, final long dest) {
    return (int)functionVectorB_000000b0.run(0x4fL, new Object[] {port, sector, dest});
  }

  @Method(0xbfc0da10L)
  public static void allow_new_card() {
    functionVectorB_000000b0.run(0x50L, EMPTY_OBJ_ARRAY);
  }

  @Method(0xbfc0da20L)
  public static boolean write_card_sector(final int port, final int sector, final long source) {
    return (boolean)functionVectorB_000000b0.run(0x4eL, new Object[] {port, sector, source});
  }

  @Method(0xbfc0da30L)
  public static int get_bu_callback_port() {
    return (int)functionVectorB_000000b0.run(0x58L, EMPTY_OBJ_ARRAY);
  }

  @Method(0xbfc0da40L)
  public static boolean _card_info_subfunc(final int port) {
    return (boolean)functionVectorB_000000b0.run(0x4dL, new Object[] {port});
  }

  @Method(0xbfc0dae0L)
  public static long alloc_kernel_memory(final int size) {
    return (long)functionVectorB_000000b0.run(0x0L, new Object[] {size});
  }

  @Method(0xbfc0daf0L)
  public static long SysEnqIntRP(final int priority, final PriorityChainEntry struct) {
    return (long)functionVectorC_000000c0.run(0x2L, new Object[] {priority, struct});
  }

  @Method(0xbfc0db00L)
  public static PriorityChainEntry SysDeqIntRP(final int priority, final PriorityChainEntry struct) {
    return (PriorityChainEntry)functionVectorC_000000c0.run(0x3L, new Object[] {priority, struct});
  }

  @Method(0xbfc0db10L)
  public static void AdjustA0Table() {
    functionVectorC_000000c0.run(0x1cL, EMPTY_OBJ_ARRAY);
  }

  @Method(0xbfc0db20L)
  public static void InstallExceptionHandlers() {
    functionVectorC_000000c0.run(0x7L, EMPTY_OBJ_ARRAY);
  }

  @Method(0xbfc0db30L)
  public static void InstallDevices(final int ttyFlag) {
    functionVectorC_000000c0.run(0x12L, new Object[] {ttyFlag});
  }

  @Method(0xbfc0db40L)
  public static void SysInitMemory(final long address, final int size) {
    functionVectorC_000000c0.run(0x8L, new Object[] {address, size});
  }

  @Method(0xbfc0db50L)
  public static long EnqueueSyscallHandler(final int priority) {
    return (long)functionVectorC_000000c0.run(0x1L, new Object[] {priority});
  }

  @Method(0xbfc0db60L)
  public static long InitDefInt(final int priority) {
    return (long)functionVectorC_000000c0.run(0xcL, new Object[] {priority});
  }

  @Method(0xbfc0db70L)
  public static void EnqueueTimerAndVblankIrqs(final int priority) {
    functionVectorC_000000c0.run(0x0L, new Object[] {priority});
  }

  @Method(0xbfc0db80L)
  public static void set_card_find_mode(final int mode) {
    functionVectorC_000000c0.run(0x1aL, new Object[] {mode});
  }

  @Method(0xbfc0db90L)
  public static int get_card_find_mode() {
    return (int)functionVectorC_000000c0.run(0x1dL, EMPTY_OBJ_ARRAY);
  }

  @Method(0xbfc0dbf0L)
  public static void EnqueueCdIntr() {
    functionVectorA_000000a0.run(0xa2L, EMPTY_OBJ_ARRAY);
  }

  @Method(0xbfc0dc00L)
  public static void DequeueCdIntr() {
    functionVectorA_000000a0.run(0xa3L, EMPTY_OBJ_ARRAY);
  }

  @Method(0xbfc0dc10L)
  public static boolean CdInitSubFunc() {
    return (boolean)functionVectorA_000000a0.run(0x95L, EMPTY_OBJ_ARRAY);
  }

  @Method(0xbfc0dc20L)
  public static int CdAsyncSeekL(final CdlLOC src) {
    return (int)functionVectorA_000000a0.run(0x78L, new Object[] {src});
  }

  @Method(0xbfc0dc30L)
  public static int CdAsyncReadSector(final int count, final long dest, final int mode) {
    return (int)functionVectorA_000000a0.run(0x7eL, new Object[] {count, dest, mode});
  }

  @Method(0xbfc0dc40L)
  public static int CdAsyncGetStatus(final long dest) {
    return (int)functionVectorA_000000a0.run(0x7cL, new Object[] {dest});
  }
}
