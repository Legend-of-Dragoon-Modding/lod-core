package legend.core.kernel;

import legend.core.DebugHelper;
import legend.core.cdrom.CdlLOC;
import legend.core.cdrom.SyncCode;
import legend.core.memory.Memory;
import legend.core.memory.Method;
import legend.core.memory.Value;
import legend.core.memory.types.BiConsumerRef;
import legend.core.memory.types.BiFunctionRef;
import legend.core.memory.types.CString;
import legend.core.memory.types.IntRef;
import legend.core.memory.types.RunnableRef;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.function.Consumer;

import static legend.core.Hardware.CPU;
import static legend.core.Hardware.DMA;
import static legend.core.Hardware.GATE;
import static legend.core.Hardware.MEMORY;
import static legend.core.Hardware.ENTRY_POINT;
import static legend.core.InterruptController.I_MASK;
import static legend.core.InterruptController.I_STAT;
import static legend.core.MathHelper.toBcd;
import static legend.core.cdrom.CdDrive.CDROM_REG0;
import static legend.core.cdrom.CdDrive.CDROM_REG1;
import static legend.core.cdrom.CdDrive.CDROM_REG2;
import static legend.core.cdrom.CdDrive.CDROM_REG3;
import static legend.core.dma.DmaManager.DMA_DICR;
import static legend.core.dma.DmaManager.DMA_DPCR;
import static legend.core.kernel.Kernel.EvMdNOINTR;
import static legend.core.kernel.Kernel.EvSpACK;
import static legend.core.kernel.Kernel.EvSpCOMP;
import static legend.core.kernel.Kernel.EvSpDE;
import static legend.core.kernel.Kernel.EvSpDR;
import static legend.core.kernel.Kernel.EvSpERROR;
import static legend.core.kernel.Kernel.EvSpTIMOUT;
import static legend.core.kernel.Kernel.EvSpUNKNOWN;
import static legend.core.kernel.Kernel.HwCdRom;
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
import static legend.core.spu.Spu.SPU_MAIN_VOL_L;
import static legend.core.spu.Spu.SPU_MAIN_VOL_R;
import static legend.core.spu.Spu.SPU_REVERB_OUT_L;
import static legend.core.spu.Spu.SPU_REVERB_OUT_R;

public final class Bios {
  private Bios() { }

  private static final Logger LOGGER = LogManager.getFormatterLogger(Bios.class);

  private static final Object[] EMPTY_OBJ_ARRAY = new Object[0];

  public static final BiFunctionRef<Long, Object[], Object> functionVectorA_000000a0 = MEMORY.ref(4, 0x000000a0L, BiFunctionRef::new);
  public static final BiFunctionRef<Long, Object[], Object> functionVectorB_000000b0 = MEMORY.ref(4, 0x000000b0L, BiFunctionRef::new);
  public static final BiFunctionRef<Long, Object[], Object> functionVectorC_000000c0 = MEMORY.ref(4, 0x000000c0L, BiFunctionRef::new);

  public static final Value argv_00000180 = MEMORY.ref(1, 0x00000180L);

  public static final Value jumpTableA_00000200 = MEMORY.ref(4, 0x00000200L);

  public static final Value ExceptionChainAddr_a0000100 = MEMORY.ref(4, 0xa0000100L);
  public static final Value ExceptionChainSize_a0000104 = MEMORY.ref(4, 0xa0000104L);
  public static final Value ProcessControlBlockAddr_a0000108 = MEMORY.ref(4, 0xa0000108L);
  public static final Value ProcessControlBlockSize_a000010c = MEMORY.ref(4, 0xa000010cL);
  public static final Value ThreadControlBlockAddr_a0000110 = MEMORY.ref(4, 0xa0000110L);
  public static final Value ThreadControlBlockSize_a0000114 = MEMORY.ref(4, 0xa0000114L);

  public static final Value EventControlBlockAddr_a0000120 = MEMORY.ref(4, 0xa0000120L);
  public static final Value EventControlBlockSize_a0000124 = MEMORY.ref(4, 0xa0000124L);

  public static final Value kernelStart_a0000500 = MEMORY.ref(4, 0xa0000500L);
  public static final Value abcFunctionVectorsStart_a0000510 = MEMORY.ref(4, 0xa0000510L);

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
  public static final Value _a00091d0 = MEMORY.ref(4, 0xa00091d0L);
  public static final Value _a00091d4 = MEMORY.ref(4, 0xa00091d4L);
  public static final Value _a00091d8 = MEMORY.ref(4, 0xa00091d8L);

  public static final Value _a00091e0 = MEMORY.ref(4, 0xa00091e0L);
  public static final Value _a00091e4 = MEMORY.ref(4, 0xa00091e4L);
  public static final Value _a00091e8 = MEMORY.ref(4, 0xa00091e8L);

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

  public static final Value _a000b870 = MEMORY.ref(4, 0xa000b870L);

  public static final Value _a000b878 = MEMORY.ref(4, 0xa000b878L);
  public static final Value _a000b87c = MEMORY.ref(4, 0xa000b87cL);

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

    SPU_MAIN_VOL_L.setu(0);
    SPU_MAIN_VOL_R.setu(0);
    SPU_REVERB_OUT_L.setu(0);
    SPU_REVERB_OUT_R.setu(0);

    FUN_bfc06ec4();
  }

  /**
   * See no$ "BIOS Memory Map"
   *
   * TODO verify
   */
  @Method(0xbfc00420L)
  public static void copyKernelSegment2() {
    long offset = 0;
    long bytesRemaining = 0x8bf0L;

    //LAB_bfc00434
    do {
      kernelStart_a0000500.offset(offset).setu(kernelStartRom_bfc10000.offset(offset));
      offset += 0x4L;
      bytesRemaining -= 0x4L;
    } while(bytesRemaining != 0);

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

    for(int i = 0; i < size; i++) {
      MEMORY.ref(1, dst).offset(i).setu(0);
    }

    return dst;
  }

  @Method(0xbfc02240L)
  public static void setjmp_Impl_A13(final jmp_buf buffer, final Consumer<Long> callback) {
    buffer.set(callback);
  }

  @Method(0xbfc02b50L)
  public static long memcpy_Impl_A2a(final long dst, final long src, final int size) {
    if(dst == 0) {
      return 0;
    }

    if(size <= 0) {
      return dst;
    }

    MEMORY.waitForLock(() -> {
      for(int i = 0; i < size; i++) {
        MEMORY.ref(1, dst).offset(i).setu(MEMORY.ref(1, src).offset(i));
      }
    });

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

  @Method(0xbfc03cf0L)
  public static long DoExecute_Impl_A43(final long header, final long param1, final long param2) {
    long t0 = MEMORY.ref(4, header).offset(0x1cL).get();
    long t1 = MEMORY.ref(4, header).offset(0x18L).get();

    while((int)t0 > 0) {
      MEMORY.ref(4, t1).setu(0);
      t1 += 0x4L;
      t0 -= 0x4L;
    }

    final Value entry = MEMORY.ref(4, header).deref(4);

    GATE.release();

    if(ENTRY_POINT != null) {
      MEMORY.addFunctions(ENTRY_POINT);
    }

    entry.cast(BiConsumerRef::new).run(param1, param2);

    GATE.acquire();

    return 0x1L;
  }

  @Method(0xbfc042a0L)
  public static void copyAbcFunctionVectors_Impl_A45() {
    for(int i = 0; i < 0x30; i += 4) {
      MEMORY.ref(4, functionVectorA_000000a0.getAddress()).offset(i).setu(abcFunctionVectorsStart_a0000510.offset(i));
    }

    MEMORY.addFunctions(FunctionVectors.class);
  }

  @Method(0xbfc042d0L)
  public static void copyJumpTableA() {
    for(int i = 0; i < 0x300; i += 4) {
      jumpTableA_00000200.offset(i).setu(jumpTableARom_bfc04300.offset(i));
    }
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
    ExceptionChainAddr_a0000100.setu(mem);
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

    final int processSize = processCount * 4;
    final int threadSize = threadCount * 192;

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

    //LAB_bfc047b8
    //LAB_bfc047d4
    for(int i = 0; i < processCount; i++) {
      MEMORY.ref(4, processAddr).offset(i * 4L).setu(0);
    }

    //LAB_bfc047e8
    //LAB_bfc0480c
    for(int i = 0; i < threadCount; i++) {
      MEMORY.ref(4, threadAddr).offset(i * 192L).setu(0x1000L);
    }

    //LAB_bfc0481c
    MEMORY.ref(4, threadAddr).setu(0x4000L);
    MEMORY.ref(4, processAddr).setu(threadAddr);
    ProcessControlBlockAddr_a0000108.setu(processAddr);
    ThreadControlBlockAddr_a0000110.setu(threadAddr);

    //LAB_bfc0483c
    return processSize + threadSize;
  }

  @Method(0xbfc04850L)
  public static void EnqueueCdIntr_Impl_Aa2() {
    _a00091d0.setu(0);
    _a00091d4.setu(0xbfc0506cL); // CdromIoIrqFunc2_Impl_A92
    _a00091d8.setu(0xbfc04decL); // CdromIoIrqFunc1_Impl_A90
    SysEnqIntRP(0, _a00091d0.getAddress());

    _a00091e0.setu(0);
    _a00091e4.setu(0xbfc050a4L); // CdromDmaIrqFunc2_Impl_A93
    _a00091e8.setu(0xbfc04fbcL); // CdromDmaIrqFunc1_Impl_A91
    SysEnqIntRP(0, _a00091e0.getAddress());
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

    _a0009154.setu(0xcccL);
    _a0009158.setu(0xffffL);
    _a000915c.setu(0);
    _a0009160.setu(0);

    FUN_bfc05120();
    FUN_bfc050fc();

    I_STAT.setu(0xfffffffbL);
    I_MASK.oru(0x4L);
    I_MASK.oru(0x8L);

    ExitCriticalSection();

    CDROM_REG0.setu(0);
    CDROM_REG1.setu(0xaL); // Init

    //LAB_bfc04a48
    long attempts = 30000L;
    do {
      if(_a00091c4.get() == 0x1L) {
        return true;
      }

      //LAB_bfc04a64
      if(attempts == 0 || _a00091c4.get() == 0x2L) {
        //LAB_bfc04a80
        return false;
      }

      //LAB_bfc04a88
      attempts--;
    } while(true);
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
      DebugHelper.sleep(15);
    }

    //LAB_bfc04ebc
    if(v1 != 0) {
      CDROM_REG0.setu(0x1L);
      CDROM_REG3.setu(v1);

      //LAB_bfc04ee8
//      for(int i = 0; i < 4; i++) {
//        MEMORY.ref(4, 0).setu(i);
//      }
      DebugHelper.sleep(15);
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
  public static void FUN_bfc05120() {
    CDROM_REG0.setu(0x1L);
    CDROM_REG3.setu(0x1fL);
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
    //TODO
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
    //TODO
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
    //TODO
  }

  @Method(0xbfc05d04L)
  public static void FUN_bfc05d04() {
    assert false;
    //TODO
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
    //TODO
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
    //TODO
  }

  @Method(0xbfc06038L)
  public static void FUN_bfc06038(final long a0) {
    assert false;
    //TODO
  }

  @Method(0xbfc060dcL)
  public static void FUN_bfc060dc(final long a0) {
    assert false;
    //TODO
  }

  @Method(0xbfc0615cL)
  public static void FUN_bfc0615c() {
    assert false;
    //TODO
  }

  @Method(0xbfc061a0L)
  public static void FUN_bfc061a0() {
    assert false;
    //TODO
  }

  @Method(0xbfc061d4L)
  public static void FUN_bfc061d4() {
    assert false;
    //TODO
  }

  @Method(0xbfc06218L)
  public static void FUN_bfc06218() {
    assert false;
    //TODO
  }

  @Method(0xbfc06288L)
  public static void FUN_bfc06288() {
    assert false;
    //TODO
  }

  @Method(0xbfc062ccL)
  public static void FUN_bfc062cc() {
    assert false;
    //TODO
  }

  @Method(0xbfc063ccL)
  public static void FUN_bfc063cc() {
    assert false;
    //TODO
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

    SPU_REVERB_OUT_L.setu(0);
    SPU_REVERB_OUT_R.setu(0);
    SPU_MAIN_VOL_L.setu(0);
    SPU_MAIN_VOL_R.setu(0);

    setBootStatus(0x2L);
    copyKernelSegment2();

    setBootStatus(0x3L);
    copyJumpTableA();
    copyAbcFunctionVectors_Impl_A45();
    AdjustA0Table();
    InstallExceptionHandlers();
    SetDefaultExitFromException();

    setBootStatus(0x4L);
    SPU_REVERB_OUT_L.setu(0);
    SPU_REVERB_OUT_R.setu(0);
    SPU_MAIN_VOL_L.setu(0);
    SPU_MAIN_VOL_R.setu(0);

    I_MASK.setu(0);
    I_STAT.setu(0);

    InstallDevices(ttyFlag_a000b9b0.get());

    setBootStatus(0x5L);
    LOGGER.info("");
    LOGGER.info("PS-X Realtime Kernel Ver.2.5");
    LOGGER.info("Copyright 1993,1994 (C) Sony Computer Entertainment Inc.");

    setBootStatus(0x6L);
    SPU_REVERB_OUT_L.setu(0);
    SPU_REVERB_OUT_R.setu(0);
    SPU_MAIN_VOL_L.setu(0);
    SPU_MAIN_VOL_R.setu(0);
    memcpy_Impl_A2a(_a000b940.getAddress(), _bfc0e14c.getAddress(), 0xc);

    LOGGER.info("KERNEL SETUP!");
    SysInitMemory(kernelMemoryStart_a000e000.getAddress(), 0x2000);
    allocateExceptionChain(4);
    EnqueueSyscallHandler(0);
    InitDefInt(3);
    allocateEventControlBlock((int)_a000b944.get());
    allocateThreadControlBlock(1, (int)_a000b940.get());
    EnqueueTimerAndVblankIrqs(1);

    SPU_REVERB_OUT_L.setu(0);
    SPU_REVERB_OUT_R.setu(0);
    SPU_MAIN_VOL_L.setu(0);
    SPU_MAIN_VOL_R.setu(0);
    setjmp_Impl_A13(jmp_buf_a000b980, value -> stop(0x385));

    setBootStatus(0x7L);
    loadIntroAndBootMenu();

    setBootStatus(0x8L);
    I_MASK.setu(0);
    I_STAT.setu(0);
    CdInit_Impl_A54();
    setjmp_Impl_A13(jmp_buf_a000b980, value -> stop(0x399));

    // PIO shell init here (loadPioShell)

    LOGGER.info("");
    LOGGER.info("BOOTSTRAP LOADER Type C Ver 2.1   03-JUL-1994");
    LOGGER.info("Copyright 1993,1994 (C) Sony Computer Entertainment Inc.");
    setjmp_Impl_A13(jmp_buf_a000b980, value -> stop(0x386));

    setBootStatus(0x9L);
    setjmp_Impl_A13(jmp_buf_a000b980, value -> stop(0x387));

    //LAB_bfc06a3c
    final int fp = open(cnf, 1);
    if(fp < 0) {
      //LAB_bfc06b18
      setjmp_Impl_A13(jmp_buf_a000b980, value -> stop(0x391));

      //LAB_bfc06b34
      argv_00000180.setu(0);
      memcpy_Impl_A2a(_a000b940.getAddress(), _bfc0e14c.getAddress(), 0xc);
      _a000b8b0.set(exe);
    } else {
      LOGGER.info("setup file    : %s", cnf);
      setjmp_Impl_A13(jmp_buf_a000b980, value -> stop(0x38f));

      //LAB_bfc06a7c
      final long size = read(fp, _a000b070.getAddress(), 0x800);
      if(size == 0) {
        memcpy_Impl_A2a(_a000b940.getAddress(), _bfc0e14c.getAddress(), 0xc);
        _a000b8b0.set(exe);
      } else {
        //LAB_bfc06ac4
        _a000b070.offset(size).setu(0);
        close(fp);
        setjmp_Impl_A13(jmp_buf_a000b980, value -> stop(0x390));

        //LAB_bfc06af4
        loadCnf(_a000b070.getAddress(), _a000b940.getAddress(), _a000b8b0.getAddress());
      }
    }

    //LAB_bfc06b60
    setjmp_Impl_A13(jmp_buf_a000b980, value -> stop(0x388));

    //LAB_bfc06b7c
    reinitKernel();
    LOGGER.info("boot file     : %s", _a000b8b0.getString());
    setjmp_Impl_A13(jmp_buf_a000b980, value -> stop(0x389));

    //LAB_bfc06bb4
    clearUserRam();
    if(!LoadExeFile_Impl_A42(_a000b8b0.getString(), _a000b870.getAddress())) {
      stop(0x38a);
    }

    //LAB_bfc06be0
    LOGGER.info("EXEC:PC0(%08x)  T_ADDR(%08x)  T_SIZE(%08x)", _a000b870.get(), _a000b878.get(), _a000b87c.get());
    LOGGER.info("boot address  : %08x %08x", _a000b870.get(), _a000b948.get());
    LOGGER.info("Execute !");
    _a000b890.setu(_a000b948);
    _a000b894.setu(0);
    LOGGER.info("                S_ADDR(%08x)  S_SIZE(%08x)", _a000b948.get(), 0);

    EnterCriticalSection();
    setjmp_Impl_A13(jmp_buf_a000b980, value -> stop(0x38b));

    //LAB_bfc06c6c
    FUN_bfc0d570(_a000b870.getAddress(), 0x1L, 0);
    LOGGER.info("End of Main");
    stop(0x38c);
  }

  @Method(0xbfc06ec4L)
  public static void FUN_bfc06ec4() {
    setBootStatus(0xfL);
    SPU_REVERB_OUT_L.setu(0);
    SPU_REVERB_OUT_R.setu(0);
    SPU_MAIN_VOL_L.setu(0);
    SPU_MAIN_VOL_R.setu(0);

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

    EnableEvent((int)EventId_HwCdRom_EvSpACK_a000b9b8.get());
    EnableEvent((int)EventId_HwCdRom_EvSpCOMP_a000b9bc.get());
    EnableEvent((int)EventId_HwCdRom_EvSpDR_a000b9c0.get());
    EnableEvent((int)EventId_HwCdRom_EvSpDE_a000b9c4.get());
    EnableEvent((int)EventId_HwCdRom_EvSpERROR_a000b9c8.get());

    ExitCriticalSection();

    _a0009d80.setu(0);
  }

  @Method(0xbfc07330L)
  public static void cdromPreInit() {
    registerCdromEvents();

    while(!CdInitSubFunc()) {
      DebugHelper.sleep(1);
    }
  }

  @Method(0xbfc073a0L)
  public static void CdInit_Impl_A54() {
    cdromPreInit();

    // There's an empty loop here (50k iterations)
    // Dunno how long of a wait that'd be
    DebugHelper.sleep(50);

    cdromPostInit();
  }

  @Method(0xbfc07410L)
  public static long cdromPostInit() {
    if(CdReadSector_Impl_Aa5(1, 0x10L, _a000b070.getAddress()) != 0x1L) {
      return 0;
    }

    //LAB_bfc0744c
    if(strncmp_Impl_A18(_a000b071.getString(), "CD001", 5) != 0) {
      return 0;
    }

    //LAB_bfc07474
    _a0009d70.setu(_a000b0f4);
    _a0009d74.setu(_a000b0fc);
    _a0009d78.setu(_a000b111.get() << 24 | _a000b110.get() << 16 | _a000b10f.get() << 8 | _a000b10e.get());
    _a0009d7c.setu(_a000b0c0);

    if(CdReadSector_Impl_Aa5(1, _a000b0fc.get(), _a000b070.getAddress()) != 0x1L) {
      return 0;
    }

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
  public static int dev_cd_close_Impl_A61() {
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
    if(CdReadSector_Impl_Aa5(sectors, MEMORY.ref(4, fcb).offset(0x24L).get() + MEMORY.ref(4, fcb).offset(0x10L).get() / 0x800L, dest) != sectors) {
      MEMORY.ref(4, fcb).offset(0x18L).setu(0x10L);
      return -1;
    }

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
      while(TestEvent((int)EventId_HwCdRom_EvSpCOMP_a000b9bc.get()) != 1) {
        //LAB_bfc07df0
        if(TestEvent((int)EventId_HwCdRom_EvSpERROR_a000b9c8.get()) == 1) {
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
        if(TestEvent((int)EventId_HwCdRom_EvSpCOMP_a000b9bc.get()) == 1) {
          return count;
        }

        //LAB_bfc07e9c
        if(TestEvent((int)EventId_HwCdRom_EvSpERROR_a000b9c8.get()) == 1) {
          SystemErrorBootOrDiskFailure_Impl_Aa1('D', 0x16);
          continue LAB_bfc07c5c;
        }

        //LAB_bfc07ec8
        if(TestEvent((int)EventId_HwCdRom_EvSpDE_a000b9c4.get()) == 1) {
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
      if((TestEvent((int)EventId_HwCdRom_EvSpCOMP_a000b9bc.get()) & 0xffL) == 0x1L) {
        return (int)sp34.get();
      }

      //LAB_bfc07fc4
      if((TestEvent((int)EventId_HwCdRom_EvSpERROR_a000b9c8.get()) & 0xffL) == 0x1L) {
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

  @Method(0xbfc0c1fcL)
  public static void AddMemCardDevice_Impl_A97() {
    AddDevice(MemCardDeviceInfo_bfc0e3e4.getAddress());
  }

  @Method(0xbfc0d570L)
  public static void FUN_bfc0d570(final long a0, final long a1, final long a2) {
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

    DoExecute_Impl_A43(a0, a1, a2);
  }

  @Method(0xbfc0d72cL)
  public static long FUN_bfc0d72c() {
    assert false;
    return -1;
    //TODO
  }

  @Method(0xbfc0d7bcL)
  public static long FUN_bfc0d7bc() {
    assert false;
    return -1;
    //TODO
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
    return ProcessControlBlockAddr_a0000108.deref(4).deref(4).offset(0x10).get() != 0;
  }

  @Method(0xbfc0d97cL)
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
  public static int OpenEvent(final long cls, final int spec, final int mode, final long func) {
    return (int)functionVectorB_000000b0.run(0x8L, new Object[] {cls, spec, mode, func});
  }

  @Method(0xbfc0d9c0L)
  public static void EnableEvent(final int event) {
    functionVectorB_000000b0.run(0xcL, new Object[] {event});
  }

  @Method(0xbfc0d9e0L)
  public static int TestEvent(final int event) {
    return (int)functionVectorB_000000b0.run(0xbL, new Object[] {event & 0xffff});
  }

  @Method(0xbfc0d9f0L)
  public static boolean AddDevice(final long deviceInfo) {
    return (boolean)functionVectorB_000000b0.run(0x47L, new Object[] {deviceInfo});
  }

  @Method(0xbfc0dae0L)
  public static long alloc_kernel_memory(final int size) {
    return (long)functionVectorB_000000b0.run(0x0L, new Object[] {size});
  }

  @Method(0xbfc0daf0L)
  public static int SysEnqIntRP(final int priority, final long struct) {
    return (int)functionVectorC_000000c0.run(0x2L, new Object[] {priority, struct});
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
  public static void EnqueueSyscallHandler(final int priority) {
    functionVectorC_000000c0.run(0x1L, new Object[] {priority});
  }

  @Method(0xbfc0db60L)
  public static void InitDefInt(final int priority) {
    functionVectorC_000000c0.run(0xcL, new Object[] {priority});
  }

  @Method(0xbfc0db70L)
  public static void EnqueueTimerAndVblankIrqs(final int priority) {
    functionVectorC_000000c0.run(0x0L, new Object[] {priority});
  }

  @Method(0xbfc0dbf0L)
  public static void EnqueueCdIntr() {
    functionVectorA_000000a0.run(0xa2L, EMPTY_OBJ_ARRAY);
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
