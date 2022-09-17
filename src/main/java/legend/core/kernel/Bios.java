package legend.core.kernel;

import legend.core.DebugHelper;
import legend.core.cdrom.CdlLOC;
import legend.core.cdrom.SyncCode;
import legend.core.memory.Memory;
import legend.core.memory.Method;
import legend.core.memory.Value;
import legend.core.memory.types.ArrayRef;
import legend.core.memory.types.BiConsumerRef;
import legend.core.memory.types.CString;
import legend.core.memory.types.ConsumerRef;
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
import static legend.core.Hardware.MEMORY;
import static legend.core.InterruptController.I_MASK;
import static legend.core.InterruptController.I_STAT;
import static legend.core.MathHelper.toBcd;
import static legend.core.cdrom.CdDrive.CDROM_REG0;
import static legend.core.cdrom.CdDrive.CDROM_REG1;
import static legend.core.cdrom.CdDrive.CDROM_REG2;
import static legend.core.cdrom.CdDrive.CDROM_REG3;
import static legend.core.dma.DmaManager.DMA_DICR;
import static legend.core.dma.DmaManager.DMA_DPCR;
import static legend.core.kernel.Kernel.AddDevice_Impl_B47;
import static legend.core.kernel.Kernel.CloseEvent_Impl_B09;
import static legend.core.kernel.Kernel.DeliverEvent_Impl_B07;
import static legend.core.kernel.Kernel.EnableEvent_Impl_B0c;
import static legend.core.kernel.Kernel.EnqueueSyscallHandler_Impl_C01;
import static legend.core.kernel.Kernel.EnqueueTimerAndVblankIrqs_Impl_C00;
import static legend.core.kernel.Kernel.EvMdNOINTR;
import static legend.core.kernel.Kernel.EvSpACK;
import static legend.core.kernel.Kernel.EvSpCOMP;
import static legend.core.kernel.Kernel.EvSpDE;
import static legend.core.kernel.Kernel.EvSpDR;
import static legend.core.kernel.Kernel.EvSpERROR;
import static legend.core.kernel.Kernel.EvSpTIMOUT;
import static legend.core.kernel.Kernel.EvSpUNKNOWN;
import static legend.core.kernel.Kernel.FileClose_Impl_B36;
import static legend.core.kernel.Kernel.FileOpen_Impl_B32;
import static legend.core.kernel.Kernel.FileRead_Impl_B34;
import static legend.core.kernel.Kernel.HwCdRom;
import static legend.core.kernel.Kernel.InitDefInt_Impl_C0c;
import static legend.core.kernel.Kernel.InstallDevices_Impl_C12;
import static legend.core.kernel.Kernel.InstallExceptionHandlers_Impl_C07;
import static legend.core.kernel.Kernel.OpenEvent_Impl_B08;
import static legend.core.kernel.Kernel.ReturnFromException_Impl_B17;
import static legend.core.kernel.Kernel.SetDefaultExitFromException_Impl_B18;
import static legend.core.kernel.Kernel.SysDeqIntRP_Impl_C03;
import static legend.core.kernel.Kernel.SysEnqIntRP_Impl_C02;
import static legend.core.kernel.Kernel.SysInitMemory_Impl_C08;
import static legend.core.kernel.Kernel.TestEvent_Impl_B0b;
import static legend.core.kernel.Kernel.UnDeliverEvent_Impl_B20;
import static legend.core.kernel.Kernel.alloc_kernel_memory_Impl_B00;

public final class Bios {
  private Bios() { }

  private static final Logger LOGGER = LogManager.getFormatterLogger(Bios.class);

  public static final Pointer<ArrayRef<Pointer<PriorityChainEntry>>> ExceptionChainPtr_a0000100 = MEMORY.ref(4, 0xa0000100L, Pointer.of(0x20, ArrayRef.of(Pointer.classFor(PriorityChainEntry.class), 4, 4, 8, Pointer.of(0x10, PriorityChainEntry::new))));
  public static final Value ExceptionChainSize_a0000104 = MEMORY.ref(4, 0xa0000104L);
  public static final Pointer<ProcessControlBlock> ProcessControlBlockPtr_a0000108 = MEMORY.ref(4, 0xa0000108L, Pointer.of(4, ProcessControlBlock::new));
  public static final Value ProcessControlBlockSize_a000010c = MEMORY.ref(4, 0xa000010cL);
  public static final Value ThreadControlBlockAddr_a0000110 = MEMORY.ref(4, 0xa0000110L);
  public static final Value ThreadControlBlockSize_a0000114 = MEMORY.ref(4, 0xa0000114L);

  public static final Value EventControlBlockAddr_a0000120 = MEMORY.ref(4, 0xa0000120L);
  public static final Value EventControlBlockSize_a0000124 = MEMORY.ref(4, 0xa0000124L);

  public static final Value kernelStart_a0000500 = MEMORY.ref(4, 0xa0000500L);

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

  public static final EXEC exe_a000b870 = MEMORY.ref(4, 0xa000b870L, EXEC::new);

  public static final Value exeName_a000b8b0 = MEMORY.ref(1, 0xa000b8b0L);

  public static final Value _a000b938 = MEMORY.ref(4, 0xa000b938L);
  public static final Value _a000b93c = MEMORY.ref(4, 0xa000b93cL);

  public static final Value EventId_HwCdRom_EvSpACK_a000b9b8 = MEMORY.ref(1, 0xa000b9b8L);
  public static final Value EventId_HwCdRom_EvSpCOMP_a000b9bc = MEMORY.ref(1, 0xa000b9bcL);
  public static final Value EventId_HwCdRom_EvSpDR_a000b9c0 = MEMORY.ref(1, 0xa000b9c0L);
  public static final Value EventId_HwCdRom_EvSpDE_a000b9c4 = MEMORY.ref(1, 0xa000b9c4L);
  public static final Value EventId_HwCdRom_EvSpERROR_a000b9c8 = MEMORY.ref(1, 0xa000b9c8L);

  public static final Value kernelMemoryStart_a000e000 = MEMORY.ref(1, 0xa000e000L);

  public static final Value _bfc0e14c = MEMORY.ref(1, 0xbfc0e14cL);

  public static final Value CdromDeviceInfo_bfc0e2f0 = MEMORY.ref(12, 0xbfc0e2f0L);

  public static final Value kernelStartRom_bfc10000 = MEMORY.ref(4, 0xbfc10000L);

  @Method(0xbfc00000L)
  public static void main() {
    LOGGER.info("Executing BIOS");

    bootstrapExecutable("cdrom:SYSTEM.CNF;1", "cdrom:PSX.EXE;1");
  }

  /**
   * See no$ "BIOS Memory Map"
   */
  @Method(0xbfc00420L)
  public static void copyKernelSegment2() {
    //LAB_bfc00434
    MEMORY.memcpy(kernelStart_a0000500.getAddress(), kernelStartRom_bfc10000.getAddress(), 0x8bf0);
    MEMORY.addFunctions(Kernel.class);
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

  @Method(0xbfc03a18L)
  public static boolean LoadExeFile_Impl_A42(final String filename, final long header) {
    final int fd = open(filename, 1);
    if(fd < 0) {
      return false;
    }

    //LAB_bfc03a3c
    if(!readExeHeader(fd, header)) {
      close(fd);
      return false;
    }

    //LAB_bfc03a68
    read(fd, MEMORY.ref(4, header).offset(0x8L).get(), (int)MEMORY.ref(4, header).offset(0xcL).get());
    close(fd);

    //LAB_bfc03a94
    return true;
  }

  @Method(0xbfc03c90L)
  public static boolean readExeHeader(final int fd, final long header) {
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

  @Method(0xbfc067e8L)
  public static void bootstrapExecutable(final String cnf, final String exe) {
    LOGGER.info("Bootstrapping %s / %s", exe, cnf);

    CPU.R12_SR.resetIEc();
    CPU.R12_SR.setIm(CPU.R12_SR.getIm() & 0xffff_fbfeL);

    copyKernelSegment2();

    InstallExceptionHandlers();
    SetDefaultExitFromException();

    I_MASK.setu(0);
    I_STAT.setu(0);

    InstallDevices(0);

    LOGGER.info("");
    LOGGER.info("PS-X Realtime Kernel Ver.2.5");
    LOGGER.info("Copyright 1993,1994 (C) Sony Computer Entertainment Inc.");

    LOGGER.info("KERNEL SETUP!");
    SysInitMemory(kernelMemoryStart_a000e000.getAddress(), 0x2000);
    allocateExceptionChain(4);
    EnqueueSyscallHandler(0);
    InitDefInt(3);
    allocateEventControlBlock(10);
    allocateThreadControlBlock(1, 4);
    EnqueueTimerAndVblankIrqs(1);

    I_MASK.setu(0);
    I_STAT.setu(0);
    CdInit_Impl_A54();

    LOGGER.info("");
    LOGGER.info("BOOTSTRAP LOADER Type C Ver 2.1   03-JUL-1994");
    LOGGER.info("Copyright 1993,1994 (C) Sony Computer Entertainment Inc.");

    //LAB_bfc06a3c
    //LAB_bfc06af4
    exeName_a000b8b0.set("cdrom:\\SCUS_944.91;1");

    //LAB_bfc06b7c
    SysInitMemory(kernelMemoryStart_a000e000.getAddress(), 0x2000);
    allocateExceptionChain(4);
    EnqueueSyscallHandler(0);
    InitDefInt(3);
    allocateEventControlBlock(10);
    allocateThreadControlBlock(1, 4);
    EnqueueTimerAndVblankIrqs(1);
    registerCdromEvents();
    LOGGER.info("boot file     : %s", exeName_a000b8b0.getString());

    //LAB_bfc06bb4
    if(!LoadExeFile_Impl_A42(exeName_a000b8b0.getString(), exe_a000b870.getAddress())) {
      throw new RuntimeException("Failed to load exe");
    }

    //LAB_bfc06be0
    LOGGER.info("EXEC:PC0(%08x)  T_ADDR(%08x)  T_SIZE(%08x)", exe_a000b870.pc0.get(), exe_a000b870.t_addr.get(), exe_a000b870.t_size.get());
    LOGGER.info("boot address  : %08x", exe_a000b870.pc0.get());
    LOGGER.info("Execute !");

    EnterCriticalSection();

    //LAB_bfc06c6c
    Exec_Impl_A43(exe_a000b870, 1, 0);
    LOGGER.info("Exiting");
    System.exit(0);
  }

  @Method(value = 0xbfc06fdcL, ignoreExtraParams = true)
  public static int noop() {
    return 0;
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
    } while(s0 < exe_a000b870.getAddress() && s3 != 0x2dL);

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
    } while(s2 < _a00095b0.getAddress() && s1 < exe_a000b870.getAddress());

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
    char c = Character.toUpperCase(path.charAt(0));
    temp[0] = c;

    //LAB_bfc07910
    while(c != 0 && s1 < path.length() - 1) {
      s1++;
      s0++;
      c = Character.toUpperCase(path.charAt(s1));
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

  @Method(0xbfc0d890L)
  public static int open(final String name, final int mode) {
    return FileOpen_Impl_B32(name, mode);
  }

  @Method(0xbfc0d8a0L)
  public static void close(final int fd) {
    FileClose_Impl_B36(fd);
  }

  @Method(0xbfc0d8b0L)
  public static int read(final int fd, final long buf, final int size) {
    return FileRead_Impl_B34(fd, buf, size);
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
    DeliverEvent_Impl_B07(cls, spec);
  }

  @Method(0xbfc0d980L)
  public static void ReturnFromException() {
    ReturnFromException_Impl_B17();
  }

  @Method(0xbfc0d990L)
  public static void UnDeliverEvent(final long cls, final int spec) {
    UnDeliverEvent_Impl_B20(cls, spec);
  }

  @Method(0xbfc0d9a0L)
  public static void SetDefaultExitFromException() {
    SetDefaultExitFromException_Impl_B18();
  }

  @Method(0xbfc0d9b0L)
  public static long OpenEvent(final long cls, final int spec, final int mode, final long func) {
    return OpenEvent_Impl_B08(cls, spec, mode, func);
  }

  @Method(0xbfc0d9c0L)
  public static void EnableEvent(final long event) {
    EnableEvent_Impl_B0c(event);
  }

  @Method(0xbfc0d9d0L)
  public static void CloseEvent(final int event) {
    CloseEvent_Impl_B09(event);
  }

  @Method(0xbfc0d9e0L)
  public static int TestEvent(final long event) {
    return TestEvent_Impl_B0b(event);
  }

  @Method(0xbfc0d9f0L)
  public static boolean AddDevice(final long deviceInfo) {
    return AddDevice_Impl_B47(deviceInfo);
  }

  @Method(0xbfc0dae0L)
  public static long alloc_kernel_memory(final int size) {
    return alloc_kernel_memory_Impl_B00(size);
  }

  @Method(0xbfc0daf0L)
  public static long SysEnqIntRP(final int priority, final PriorityChainEntry struct) {
    return SysEnqIntRP_Impl_C02(priority, struct);
  }

  @Method(0xbfc0db00L)
  public static PriorityChainEntry SysDeqIntRP(final int priority, final PriorityChainEntry struct) {
    return SysDeqIntRP_Impl_C03(priority, struct);
  }

  @Method(0xbfc0db20L)
  public static void InstallExceptionHandlers() {
    InstallExceptionHandlers_Impl_C07();
  }

  @Method(0xbfc0db30L)
  public static void InstallDevices(final int ttyFlag) {
    InstallDevices_Impl_C12(ttyFlag);
  }

  @Method(0xbfc0db40L)
  public static void SysInitMemory(final long address, final int size) {
    SysInitMemory_Impl_C08(address, size);
  }

  @Method(0xbfc0db50L)
  public static long EnqueueSyscallHandler(final int priority) {
    return EnqueueSyscallHandler_Impl_C01(priority);
  }

  @Method(0xbfc0db60L)
  public static long InitDefInt(final int priority) {
    return InitDefInt_Impl_C0c(priority);
  }

  @Method(0xbfc0db70L)
  public static void EnqueueTimerAndVblankIrqs(final int priority) {
    EnqueueTimerAndVblankIrqs_Impl_C00(priority);
  }

  @Method(0xbfc0dbf0L)
  public static void EnqueueCdIntr() {
    EnqueueCdIntr_Impl_Aa2();
  }

  @Method(0xbfc0dc00L)
  public static void DequeueCdIntr() {
    DequeueCdIntr_Impl_Aa3();
  }

  @Method(0xbfc0dc10L)
  public static boolean CdInitSubFunc() {
    return CdInitSubFunc_Impl_A95();
  }

  @Method(0xbfc0dc20L)
  public static int CdAsyncSeekL(final CdlLOC src) {
    return CdAsyncSeekL_Impl_A78(src);
  }

  @Method(0xbfc0dc30L)
  public static int CdAsyncReadSector(final int count, final long dest, final int mode) {
    return CdAsyncReadSector_Impl_A7e(count, dest, mode);
  }

  @Method(0xbfc0dc40L)
  public static int CdAsyncGetStatus(final long dest) {
    return CdAsyncGetStatus_Impl_A7c(dest);
  }
}
