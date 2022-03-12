package legend.core.kernel;

import legend.core.DebugHelper;
import legend.core.memory.Method;
import legend.core.memory.Ref;
import legend.core.memory.Value;
import legend.core.memory.types.ArrayRef;
import legend.core.memory.types.BiFunctionRef;
import legend.core.memory.types.BoolRef;
import legend.core.memory.types.ConsumerRef;
import legend.core.memory.types.DIRENTRY;
import legend.core.memory.types.IntRef;
import legend.core.memory.types.Pointer;
import legend.core.memory.types.ProcessControlBlock;
import legend.core.memory.types.RunnableRef;
import legend.core.memory.types.SupplierRef;
import legend.core.memory.types.ThreadControlBlock;
import legend.core.memory.types.TriFunctionRef;
import legend.core.memory.types.UnsignedByteRef;
import legend.core.memory.types.UnsignedIntRef;
import legend.core.memory.types.VariadicFunctionRef;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.function.Function;

import static legend.core.Hardware.CPU;
import static legend.core.Hardware.GATE;
import static legend.core.Hardware.MEMCARD;
import static legend.core.Hardware.MEMORY;
import static legend.core.InterruptController.I_MASK;
import static legend.core.InterruptController.I_STAT;
import static legend.core.MemoryHelper.getMethodAddress;
import static legend.core.Timers.TMR_DOTCLOCK_MAX;
import static legend.core.Timers.TMR_DOTCLOCK_MODE;
import static legend.core.Timers.TMR_DOTCLOCK_VAL;
import static legend.core.Timers.TMR_HRETRACE_MAX;
import static legend.core.Timers.TMR_HRETRACE_MODE;
import static legend.core.Timers.TMR_HRETRACE_VAL;
import static legend.core.Timers.TMR_SYSCLOCK_MAX;
import static legend.core.Timers.TMR_SYSCLOCK_MODE;
import static legend.core.Timers.TMR_SYSCLOCK_VAL;
import static legend.core.input.MemoryCard.JOY_MCD_BAUD;
import static legend.core.input.MemoryCard.JOY_MCD_CTRL;
import static legend.core.input.MemoryCard.JOY_MCD_DATA;
import static legend.core.input.MemoryCard.JOY_MCD_MODE;
import static legend.core.input.MemoryCard.JOY_MCD_STAT;
import static legend.core.kernel.Bios.EventControlBlockAddr_a0000120;
import static legend.core.kernel.Bios.EventControlBlockSize_a0000124;
import static legend.core.kernel.Bios.ExceptionChainPtr_a0000100;
import static legend.core.kernel.Bios.FUN_bfc0bff0;
import static legend.core.kernel.Bios.ProcessControlBlockPtr_a0000108;
import static legend.core.kernel.Bios.memcardOkay_a000b9d0;
import static legend.core.kernel.Bios.setBootStatus;

public final class Kernel {
  private Kernel() { }

  private static final Logger LOGGER = LogManager.getFormatterLogger(Kernel.class);

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static final ArrayRef<Pointer<VariadicFunctionRef<Object>>> jumpTableA = (ArrayRef<Pointer<VariadicFunctionRef<Object>>>)MEMORY.ref(0x300, 0x200L, ArrayRef.of(Pointer.class, 192, 4, (Function)Pointer.of(4, VariadicFunctionRef::new)));
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static final ArrayRef<Pointer<VariadicFunctionRef<Object>>> jumpTableB = (ArrayRef<Pointer<VariadicFunctionRef<Object>>>)MEMORY.ref(0x400, 0x874L, ArrayRef.of(Pointer.class, 256, 4, (Function)Pointer.of(4, VariadicFunctionRef::new)));
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static final ArrayRef<Pointer<VariadicFunctionRef<Object>>> jumpTableC = (ArrayRef<Pointer<VariadicFunctionRef<Object>>>)MEMORY.ref(0x140, 0x674L, ArrayRef.of(Pointer.class,  80, 4, (Function)Pointer.of(4, VariadicFunctionRef::new)));

  private static final SupplierRef<Integer> exceptionVector_00000080 = MEMORY.ref(4, 0x00000080L, SupplierRef::new);

  private static final Pointer<ArrayRef<Pointer<PriorityChainEntry>>> exceptionChainAddr_00000100 = (Pointer<ArrayRef<Pointer<PriorityChainEntry>>>)MEMORY.ref(4, 0x00000100L, Pointer.of(0x20, ArrayRef.of(Pointer.class, 4, 4, 8, (Function)Pointer.of(0x10, PriorityChainEntry::new))));

  private static final Value _00000cf0 = MEMORY.ref(1, 0x00000cf0L);

  private static final Value _00006408 = MEMORY.ref(1, 0x00006408L);

  private static final jmp_buf DefaultExceptionExitStruct_00006cf4 = MEMORY.ref(0x30, 0x00006cf4L, jmp_buf::new);

  private static final Value systemMemoryInitialized_00006d30 = MEMORY.ref(4, 0x00006d30L);

  private static final ArrayRef<PriorityChainEntry> _00006d58 = MEMORY.ref(1, 0x00006d58L, ArrayRef.of(PriorityChainEntry.class, 4, 16, PriorityChainEntry::new));

  private static final PriorityChainEntry DefaultInterruptPriorityChainStruct_00006d98 = MEMORY.ref(1, 0x00006d98L, PriorityChainEntry::new);

  private static final PriorityChainEntry SyscallHandlerStruct_00006da8 = MEMORY.ref(1, 0x00006da8L, PriorityChainEntry::new);

  private static final Value _00006e54 = MEMORY.ref(4, 0x00006e54L);

  private static final Value DeviceControlBlockBaseAddr_00006ee0 = MEMORY.ref(1, 0x00006ee0L);

  private static final Value _00007200 = MEMORY.ref(4, 0x00007200L);

  private static final UnsignedByteRef joypadIndex_00007264 = MEMORY.ref(1, 0x00007264L, UnsignedByteRef::new);

  private static final Value _000072f0 = MEMORY.ref(4, 0x000072f0L);

  private static final Value systemMemoryAddr_00007460 = MEMORY.ref(4, 0x00007460L);
  private static final Value systemMemorySize_00007464 = MEMORY.ref(4, 0x00007464L);
  private static final Value systemMemoryEnd_00007468 = MEMORY.ref(4, 0x00007468L);

  private static final Value _00007480 = MEMORY.ref(4, 0x00007480L);

  private static final Value joyNBit_000074a0 = MEMORY.ref(4, 0x000074a0L);
  private static final Value _000074a4 = MEMORY.ref(4, 0x000074a4L);
  private static final PriorityChainEntry memcardPriorityChain_000074a8 = MEMORY.ref(16, 0x000074a8L, PriorityChainEntry::new);
  private static final Value memcardShareWithController_000074b8 = MEMORY.ref(4, 0x000074b8L);
  private static final Value cardStarted_000074bc = MEMORY.ref(4, 0x000074bcL);
  /** Read/write functions normally fail if the card was changed; this bypasses that */
  private static final Value allowCardChange_000074c0 = MEMORY.ref(4, 0x000074c0L);
  private static final Value _000074c4 = MEMORY.ref(4, 0x000074c4L);

  private static final ArrayRef<UnsignedIntRef> memcardData_000074f8 = MEMORY.ref(4, 0x000074f8L, ArrayRef.of(UnsignedIntRef.class, 2, 4, UnsignedIntRef::new));
  private static final ArrayRef<IntRef> memcardIoPort_00007500 = MEMORY.ref(4, 0x00007500L, ArrayRef.of(IntRef.class, 2, 4, IntRef::new));
  private static final ArrayRef<UnsignedIntRef> memcardIoSector_00007508 = MEMORY.ref(4, 0x00007508L, ArrayRef.of(UnsignedIntRef.class, 2, 4, UnsignedIntRef::new));

  private static final Value memcardIoStep_00007514 = MEMORY.ref(4, 0x00007514L);

  private static final UnsignedIntRef joypadIndex_0000751c = MEMORY.ref(4, 0x0000751cL, UnsignedIntRef::new);
  /** This seems to get set when there's an issue writing to the memcard */
  private static final Value _00007520 = MEMORY.ref(4, 0x00007520L);

  private static final ArrayRef<Pointer<SupplierRef<Long>>> memcardIoCallback_00007528 = (ArrayRef<Pointer<SupplierRef<Long>>>)MEMORY.ref(4, 0x00007528L, ArrayRef.of(Pointer.class, 2, 4, (Function)Pointer.of(4, SupplierRef::new)));

  private static final PriorityChainEntry memcardIoPriorityChainEntry_00007540 = MEMORY.ref(4, 0x00007540L, PriorityChainEntry::new);
  private static final ArrayRef<UnsignedIntRef> memcardIoAddress_00007550 = MEMORY.ref(4, 0x00007550L, ArrayRef.of(UnsignedIntRef.class, 2, 4, UnsignedIntRef::new));
  private static final ArrayRef<UnsignedByteRef> _00007558 = MEMORY.ref(1, 0x00007558L, ArrayRef.of(UnsignedByteRef.class, 2, 1, UnsignedByteRef::new));
  private static final Value memcardTransferActive_0000755a = MEMORY.ref(1, 0x0000755aL);

  private static final ArrayRef<UnsignedIntRef> _00007560 = MEMORY.ref(4, 0x00007560L, ArrayRef.of(UnsignedIntRef.class, 2, 4, UnsignedIntRef::new));
  /** 1h -> reset, 2h -> read, 4h -> write, 8h -> info, 11h -> failed/timeout, 21h -> failed/other */
  private static final ArrayRef<UnsignedByteRef> memcardStateBitset_00007568 = MEMORY.ref(1, 0x00007568L, ArrayRef.of(UnsignedByteRef.class, 2, 1, UnsignedByteRef::new));

  private static final Value _0000756c = MEMORY.ref(4, 0x0000756cL);

  private static final Value memcardIoFinished_000075c0 = MEMORY.ref(4, 0x000075c0L);
  private static final Value memcardIoAddress_000075c4 = MEMORY.ref(4, 0x000075c4L);
  /** @see Kernel#memcardStateBitset_00007568 */
  private static final Pointer<UnsignedByteRef> memcardStateBitset_000075c8 = MEMORY.ref(4, 0x000075c8L, Pointer.of(1, UnsignedByteRef::new));
  private static final Pointer<UnsignedIntRef> _000075cc = MEMORY.ref(4, 0x000075ccL, Pointer.of(4, UnsignedIntRef::new));

  private static final Pointer<jmp_buf> ExceptionExitStruct_000075d0 = MEMORY.ref(4, 0x000075d0L, Pointer.of(0x30, jmp_buf::new));

  private static final Value _000085f8 = MEMORY.ref(4, 0x000085f8L);
  private static final Value _000085fc = MEMORY.ref(4, 0x000085fcL);
  private static final ArrayRef<BoolRef> _00008600 = MEMORY.ref(16, 0x00008600L, ArrayRef.of(BoolRef.class, 4, 4, BoolRef::new));

  private static final Value _0000860c = MEMORY.ref(4, 0x0000860cL);
  private static final Value _00008610 = MEMORY.ref(4, 0x00008610L);
  private static final Value _00008614 = MEMORY.ref(4, 0x00008614L);
  private static final Value _00008618 = MEMORY.ref(4, 0x00008618L);
  private static final Value _0000861c = MEMORY.ref(4, 0x0000861cL);

  private static final Value _0000863c = MEMORY.ref(4, 0x0000863cL);
  private static final Value _00008640 = MEMORY.ref(4, 0x00008640L);
  private static final Value memcardFindMode_00008644 = MEMORY.ref(4, 0x00008644L);
  private static final Value FileControlBlockBaseAddr_00008648 = MEMORY.ref(1, 0x00008648L);

  private static final Value ttyFlag_00008908 = MEMORY.ref(4, 0x00008908L);
  private static final Value _0000890c = MEMORY.ref(4, 0x0000890cL);
  private static final Value _00008910 = MEMORY.ref(4, 0x00008910L);
  private static final Value memcardUseVblank_00008914 = MEMORY.ref(4, 0x00008914L);

  private static final Value FileControlBlockAddr_a0000140 = MEMORY.ref(4, 0xa0000140L);
  private static final Value FileControlBlockSize_a0000144 = MEMORY.ref(4, 0xa0000144L);

  private static final Value DeviceControlBlockAddr_a0000150 = MEMORY.ref(4, 0xa0000150L);
  private static final Value DeviceControlBlockSize_a0000154 = MEMORY.ref(4, 0xa0000154L);

  public static final long DescMask = 0xff000000L;
  public static final long DescTH   = DescMask;
  public static final long DescHW   = 0xf0000000L;
  public static final long DescEV   = 0xf1000000L;
  public static final long DescRC   = 0xf2000000L;
  public static final long DescUEV  = 0xf3000000L; /* User event */
  public static final long DescSW   = 0xf4000000L; /* BIOS */

  public static final long HwVBLANK = DescHW | 0x01; /* VBLANK */
  public static final long HwGPU    = DescHW | 0x02; /* GPU */
  public static final long HwCdRom  = DescHW | 0x03; /* CDROM Decoder */
  public static final long HwDMAC   = DescHW | 0x04; /* DMA controller */
  public static final long HwRTC0   = DescHW | 0x05; /* RTC0 */
  public static final long HwRTC1   = DescHW | 0x06; /* RTC1 */
  public static final long HwRTC2   = DescHW | 0x07; /* RTC2 */
  public static final long HwCNTL   = DescHW | 0x08; /* Controller */
  public static final long HwSPU    = DescHW | 0x09; /* SPU */
  public static final long HwPIO    = DescHW | 0x0a; /* PIO */
  public static final long HwSIO    = DescHW | 0x0b; /* SIO */

  public static final long HwCPU    = DescHW | 0x10; /* Exception */
  public static final long HwCARD   = DescHW | 0x11; /* memory card */
  public static final long HwCARD_0 = DescHW | 0x12; /* memory card */
  public static final long HwCARD_1 = DescHW | 0x13; /* memory card */
  public static final long SwCARD   = DescSW | 0x01; /* memory card */
  public static final long SwMATH   = DescSW | 0x02; /* libmath */

  public static final long RCntCNT0 = DescRC | 0x00; /* display pixel */
  public static final long RCntCNT1 = DescRC | 0x01; /* horizontal sync */
  public static final long RCntCNT2 = DescRC | 0x02; /* one-eighth of system clock */
  public static final long RCntCNT3 = DescRC | 0x03; /* vertical sync target value fixed to 1 */

  public static final int RCntMdINTR   = 0x1000;
  public static final int RCntMdNOINTR = 0x2000;
  public static final int RCntMdSC     = 0x0001;
  public static final int RCntMdSP     = 0x0000;
  public static final int RCntMdFR     = 0x0000;
  public static final int RCntMdGATE   = 0x0010;

  public static final int EvSpCZ      = 0x0001; /* counter becomes zero */
  public static final int EvSpINT     = 0x0002; /* interrupted */
  public static final int EvSpIOE     = 0x0004; /* end of i/o */
  public static final int EvSpCLOSE   = 0x0008; /* file was closed */
  public static final int EvSpACK     = 0x0010; /* command acknowledged */
  public static final int EvSpCOMP    = 0x0020; /* command completed */
  public static final int EvSpDR      = 0x0040; /* data ready */
  public static final int EvSpDE      = 0x0080; /* data end */
  public static final int EvSpTIMOUT  = 0x0100; /* time out */
  public static final int EvSpUNKNOWN = 0x0200; /* unknown command */
  public static final int EvSpIOER    = 0x0400; /* end of read buffer */
  public static final int EvSpIOEW    = 0x0800; /* end of write buffer */
  public static final int EvSpTRAP    = 0x1000; /* general interrupt */
  public static final int EvSpNEW     = 0x2000; /* new device */
  public static final int EvSpSYSCALL = 0x4000; /* system call instruction */
  public static final int EvSpERROR   = 0x8000; /* error happened */
  public static final int EvSpPERROR  = 0x8001; /* previous write error happened */
  public static final int EvSpEDOM    = 0x0301; /* domain error in libmath */
  public static final int EvSpERANGE  = 0x0302; /* range error in libmath */

  public static final int EvMdINTR   = 0x1000;
  public static final int EvMdNOINTR = 0x2000;

  public static final int EvStUNUSED  = 0x0000;
  public static final int EvStWAIT    = 0x1000;
  public static final int EvStACTIVE  = 0x2000;
  public static final int EvStALREADY = 0x4000;

  public static final int TcbMdRT  = 0x1000; /* reserved by system */
  public static final int TcbMdPRI = 0x2000; /* reserved by system */

  public static final int TcbStUNUSED = 0x1000;
  public static final int TcbStACTIVE = 0x4000;

  /**
   * No {@link Method} annotation - this method is not registered automatically. See {@link #InstallExceptionHandlers_Impl_C07()}
   */
  public static int exceptionVector() {
    // ExceptionHandler_Impl_C06
    return (int)MEMORY.ref(4, 0xc80L).call();
  }

  @Method(0x500L)
  public static void kernelInit() {
    LOGGER.info("Initializing kernel");

    FUN_00000598();
  }

  @Method(0x540L)
  public static void AdjustA0Table_Impl_C1c() {
    long a1 = 0x93cL;
    long a2 = 0x200L;

    //LAB_00000554
    do {
      MEMORY.ref(4, a2).setu(MEMORY.ref(4, a1));
      a1 += 0x4L;
      a2 += 0x4L;
    } while(a1 != 0x964L);

    a1 = 0x964L;
    a2 = 0x2ecL;

    //LAB_0000057c
    do {
      MEMORY.ref(4, a2).setu(MEMORY.ref(4, a1));
      a1 += 0x4L;
      a2 += 0x4L;
    } while(a1 != 0x974L);
  }

  @Method(0x598L)
  public static void FUN_00000598() {
    for(int i = 0; i < 0x530; i++) {
      systemMemoryAddr_00007460.offset(i * 4).setu(0);
    }
  }

  @Method(0x5c4L)
  public static Object functionVectorA(final long fn, final Object... params) {
    return jumpTableA.get((int)fn).deref().run(params);
  }

  @Method(0x5e0L)
  public static Object functionVectorB(final long fn, final Object... params) {
    return jumpTableB.get((int)fn).deref().run(params);
  }

  @Method(0x600L)
  public static Object functionVectorC(final long fn, final Object... params) {
    return jumpTableC.get((int)fn).deref().run(params);
  }

  @Method(0xc80L)
  public static int ExceptionHandler_Impl_C06() {
    final ThreadControlBlock tcb = ProcessControlBlockPtr_a0000108.deref().threadControlBlockPtr.deref();

    tcb.registers.get(4).set(CPU.getLastSyscall());
    tcb.cop0r12Sr.set(CPU.R12_SR.get());
    tcb.cop0r13Cause.set(CPU.R13_CAUSE.get());

    final ArrayRef<Pointer<PriorityChainEntry>> chains = ExceptionChainPtr_a0000100.deref();

    //LAB_00000de8
    for(int i = 0; i < 4; i++) {
      Pointer<PriorityChainEntry> ptr = chains.get(i);

      //LAB_00000df8
      while(!ptr.isNull()) {
        final PriorityChainEntry entry = ptr.deref();

        final int ret = entry.firstFunction.deref().run();

        if(ret != 0 && !entry.secondFunction.isNull()) {
          entry.secondFunction.deref().run(ret);
        }

        //LAB_00000e28
        ptr = entry.next;
      }
    }

    ExceptionExitStruct_000075d0.deref().run();

    return 1;
  }

  @Method(0xeb0L)
  public static void InstallExceptionHandlers_Impl_C07() {
    long v0 = exceptionVector_00000080.getAddress();
    long k0 = 0xf0cL;

    //LAB_00000ec8
    do {
      MEMORY.ref(4, v0).setu(MEMORY.ref(4, k0));
      k0 += 0x4L;
      v0 += 0x4L;
    } while(k0 != 0xf1cL);

    // Don't know why the exception handler is also copied to 0x0
//    v0 = 0x8000_0000L;
//    k0 = 0xf0cL;

    //LAB_00000ef0
//    do {
//      MEMORY.ref(4, v0).setu(MEMORY.ref(4, k0));
//      k0 += 0x4L;
//      v0 += 0x4L;
//    } while(k0 != 0xf1cL);

    exceptionVector_00000080.set(Kernel::exceptionVector);

    FlushCache();
  }

  @Method(0xf20L)
  public static void SetCustomExitFromException_Impl_B19(final jmp_buf buffer) {
    ExceptionExitStruct_000075d0.set(buffer);
  }

  @Method(0xf2cL)
  public static void SetDefaultExitFromException_Impl_B18() {
    ExceptionExitStruct_000075d0.set(DefaultExceptionExitStruct_00006cf4);
  }

  @Method(0xf40)
  public static void ReturnFromException_Impl_B17() {
    final ProcessControlBlock pcb = ProcessControlBlockPtr_a0000108.deref();
    final ThreadControlBlock tcb = pcb.threadControlBlockPtr.deref();

    CPU.R12_SR.set(tcb.cop0r12Sr.get());
    CPU.RFE();
  }

  @Method(0x1030L)
  public static long FUN_00001030(long a0) {
    final long a2;

    if(a0 == 0) {
      //LAB_000010b8
      a0 = systemMemoryAddr_00007460.get();
      final long t7 = systemMemoryEnd_00007468.get();
      a2 = 0;
      if(a0 >= t7) {
        return -0x1L;
      }
    } else {
      long v0 = systemMemorySize_00007464.get();

      //LAB_00001040
      final long a1;
      final long v1;
      if(v0 >= a0) {
        v1 = v0;
        a1 = a0;
      } else {
        //LAB_00001060
        v1 = a0;
        a1 = v0;
      }

      //LAB_00001064
      a0 = systemMemoryAddr_00007460.get();

      //LAB_00001070
      v0 = systemMemoryEnd_00007468.get() - a0;
      v0 &= ~0x3L;
      v0 -= 0x4L;
      if(v0 < v1) {
        //LAB_000010a0
        if(v0 >= a1) {
          a2 = a1;
        } else {
          //LAB_000010b0
          return -0x1L;
        }
      } else {
        a2 = v1;
      }
    }

    //LAB_000010e4
    final long t3 = a2 + 0x4L;
    final long t2 = a2 | 0x1L;
    long at = a2;
    if((int)a2 < 0) {
      at += 0x3L;
    }

    //LAB_00001100
    final long t0 = at & ~0x3L;
    final long t1 = a0 + t0;
    MEMORY.ref(4, t1).setu(-0x2L);
    MEMORY.ref(4, a0).offset(-0x4L).setu(t2);
    at = t3;
    if((int)t3 < 0) {
      at += 0x3L;
    }

    //LAB_00001120
    systemMemoryAddr_00007460.setu(a0 + at & ~0x3L);
    return 0;
  }

  @Method(0x113cL)
  public static void SysInitMemory_Impl_C08(final long addr, final int size) {
    systemMemoryAddr_00007460.setu(addr);
    systemMemorySize_00007464.setu(size);
    systemMemoryEnd_00007468.setu(addr + (size & 0xfffffffcL) + 0x4L);

    MEMORY.ref(4, addr).setu(0);

    systemMemoryInitialized_00006d30.setu(0);
  }

  @Method(0x1174L)
  public static long alloc_kernel_memory_Impl_B00(final int size) {
    long v0;

    final long s1 = size + 3 & 0xfffffffcL;
    long t6 = systemMemoryInitialized_00006d30.get();
    long s0 = 0x2L;
    if(t6 == 0) {
      //LAB_000011b8
      v0 = systemMemoryAddr_00007460.get();
      if(v0 >= systemMemoryEnd_00007468.get()) {
        return 0;
      }

      //LAB_000011e4
      MEMORY.ref(4, v0).setu(0xfffffffeL);
      _000085fc.setu(v0);
      v0 += 0x4L;
      systemMemoryAddr_00007460.setu(v0);
      if(FUN_00001030(s1) != 0) {
        return 0;
      }

      //LAB_00001214
      _000085f8.setu(_000085fc);
      systemMemoryInitialized_00006d30.setu(0x1L);
    }

    //LAB_00001230
    long a0 = _000085fc.deref(4).get();
    long v1 = a0 & 0x1L;

    //LAB_00001248
    do {
      if(v1 != 0) {
        v0 = s1 | 0x1L;
        if(v0 == a0) {
          _000085fc.deref(4).setu(a0 & 0xfffffffeL);
          break;
        }

        //LAB_00001274
        if(v0 < a0) {
          long at = s1;
          if(s1 < 0) {
            at += 0x3L;
          }

          //LAB_0000129c
          _000085fc.deref(4).offset(at & ~0x3L).offset(0x4L).setu(a0 - s1 - 0x4L);
          _000085fc.deref(4).setu(s1);
          break;
        }

        //LAB_000012bc
        if(a0 >= s1) {
          continue;
        }

        v1 = _000085fc.get() + (a0 & ~0x3L);
        v0 = MEMORY.ref(4, v1).offset(0x4L).get();
        v1 += 0x4L;
        t6 = v0 & 0x1L;
        if(v0 == 0xfffffffeL) {
          s0--;
          if(s0 <= 0) {
            v0 = FUN_00001030(s1);
            if(v0 != 0) {
              return 0;
            }
          } else {
            //LAB_0000130c
            _000085fc.setu(_000085f8);
          }
        } else {
          //LAB_00001324
          if(t6 == 0) {
            //LAB_00001348
            _000085fc.setu(v1);
          } else {
            //LAB_0000132c
            _000085fc.deref(4).setu(a0 + (v0 & 0xfffffffeL) + 0x4L);
          }
        }

        //LAB_00001350
        a0 = _000085fc.deref(4).get();
        v1 = a0 & 0x1L;
        continue;
      }

      //LAB_00001368
      if(a0 == 0xfffffffeL) {
        //LAB_00001394
        s0--;
        if(s0 <= 0) {
          v0 = FUN_00001030(s1);
          if(v0 != 0) {
            return v0;
          }
        } else {
          //LAB_000013b8
          _000085fc.setu(_000085f8);
        }
      } else {
        _000085fc.addu((a0 & ~0x3L) + 0x4L);
      }

      //LAB_000013c8
      a0 = _000085fc.deref(4).get();
      v1 = a0 & 0x1L;
    } while(true);

    //LAB_000013e0
    //LAB_000013f0
    //LAB_00001400
    return _000085fc.get() + 0x4L;
  }

  @Method(0x1420L)
  public static long SysEnqIntRP_Impl_C02(final int priority, final PriorityChainEntry struct) {
    final Pointer<PriorityChainEntry> current = exceptionChainAddr_00000100.deref().get(priority);

    if(current.isNull()) {
      struct.next.clear();
    } else {
      if(struct.getAddress() == current.deref().getAddress()) {
        throw new IllegalStateException("Priority chain entry cannot reference itself");
      }

      struct.next.set(current.deref());
    }

    current.set(struct);
    return 0;
  }

  /**
   * Don't think my decomp was right. I rewrote it in the way I'm pretty sure it's supposed to work.
   * It just removes the element from the specified priority chain if it exists.
   */
  @Method(0x1444L)
  @Nullable
  public static PriorityChainEntry SysDeqIntRP_Impl_C03(final int priority, final PriorityChainEntry struct) {
    Pointer<PriorityChainEntry> current = exceptionChainAddr_00000100.deref().get(priority);

    while(!current.isNull()) {
      final PriorityChainEntry entry = current.deref();

      if(entry.getAddress() == struct.getAddress()) {
        if(entry.next.isNull()) {
          current.clear();
        } else {
          current.set(entry.next.deref());
        }

        return entry;
      }

      current = entry.next;
    }

    return null;

//    if(current.isNull()) {
//      return null;
//    }
//
//    Pointer<PriorityChainEntry> next = current.deref().next;
//
//    if(next.isNull()) {
//      return null;
//    }
//
//    //LAB_0000146c
//    if(next.deref().getAddress() == struct.getAddress()) {
//      if(next.deref().next.isNull()) {
//        next.clear();
//        return null;
//      }
//
//      if(next.deref().next.deref().getAddress() == current.deref().getAddress()) {
//        throw new IllegalStateException("Priority chain entry cannot reference itself");
//      }
//
//      next.set(next.deref().next.deref());
//      return next.deref();
//    }
//
//    //LAB_00001484
//    Pointer<PriorityChainEntry> nextNext = next.deref().next;
//    if(nextNext.isNull()) {
//      return null;
//    }
//
//    //LAB_0000149c
//    //LAB_000014b0
//    //LAB_000014c4
//    do {
//      current = next;
//      next = nextNext;
//
//      //LAB_000014c8
//      if(nextNext.deref().getAddress() == struct.getAddress()) {
//        break;
//      }
//
//      nextNext = nextNext.deref().next;
//    } while(!nextNext.isNull());
//
//    //LAB_000014e4
//    if(next.deref().getAddress() != struct.getAddress()) {
//      return null;
//    }
//
//    if(next.deref().getAddress() == current.deref().getAddress()) {
//      throw new IllegalStateException("Priority chain entry cannot reference itself");
//    }
//
//    current.deref().next.set(next.deref());
//
//    //LAB_00001500
//    return next.deref();
  }

  @Method(0x1508L)
  public static void EnqueueTimerAndVblankIrqs_Impl_C00(final int priority) {
    I_MASK.and(0xffffff8eL);

    FUN_000027a0();

    //LAB_00001570
    for(int i = 0; i < 4; i++) {
      _00008600.get(i).set(true);
      SysEnqIntRP_Impl_C02(priority, _00006d58.get(i));
    }

    //LAB_00001594
    TMR_DOTCLOCK_VAL.setu(0);
    TMR_DOTCLOCK_MODE.setu(0);
    TMR_DOTCLOCK_MAX.setu(0);
    TMR_HRETRACE_VAL.setu(0);
    TMR_HRETRACE_MODE.setu(0);
    TMR_HRETRACE_MAX.setu(0);
    TMR_SYSCLOCK_VAL.setu(0);
    TMR_SYSCLOCK_MODE.setu(0);
    TMR_SYSCLOCK_MAX.setu(0);
    FUN_000027a0();
  }

  @Method(0x15d8L)
  public static boolean ChangeClearRCnt_Impl_C0a(final int t, final boolean flag) {
    final boolean oldValue = _00008600.get(t).get();
    _00008600.get(t).set(flag);
    return oldValue;
  }

  @Method(0x1794L)
  public static int FUN_00001794() {
    if(I_MASK.get(0x10L) == 0 || I_STAT.get(0x10L) == 0) {
      return 0;
    }

    DeliverEvent_Impl_B07(RCntCNT0, EvSpINT);
    return 1;
  }

  @Method(0x17f4L)
  public static int FUN_000017f4() {
    if(I_MASK.get(0x20L) == 0 || I_STAT.get(0x20L) == 0) {
      return 0;
    }

    DeliverEvent_Impl_B07(RCntCNT1, EvSpINT);
    return 1;
  }

  @Method(0x1858L)
  public static int FUN_00001858() {
    if(I_MASK.get(0x40L) == 0 || I_STAT.get(0x40L) == 0) {
      return 0;
    }

    DeliverEvent_Impl_B07(RCntCNT2, EvSpINT);
    return 1;
  }

  @Method(0x18bcL)
  public static int FUN_000018bc() {
    if(I_MASK.get(0x1L) == 0 || I_STAT.get(0x1L) == 0) {
      return 0;
    }

    DeliverEvent_Impl_B07(RCntCNT3, EvSpINT);
    return 1;
  }

  @Method(0x19c8L)
  public static void FUN_000019c8(final long a0) {
    if(_0000860c.get() != 0) {
      I_STAT.setu(0xfffffffeL);
      ReturnFromException_Impl_B17();
    }
  }

  /**
   * Called from exception handler
   */
  @Method(0x1a00L)
  public static int FUN_00001a00() {
    final ProcessControlBlock pcb = ProcessControlBlockPtr_a0000108.deref();
    final ThreadControlBlock tcb = pcb.threadControlBlockPtr.deref();
    final long exceptionCause = tcb.cop0r13Cause.get() & 0x3cL;

    //LAB_00001ae8
    if(exceptionCause == 0) { // Nothing
      //LAB_00001a24
      return 0;
    }

    if(exceptionCause == 0x20L) { // Syscall
      //LAB_00001a2c
      tcb.cop0r14Epc.add(0x4L);

      switch((int)tcb.registers.get(4).get()) { // syscall index
        case 0: // Nothing
          break;

        case 1: // EnterCriticalSection
          if((tcb.cop0r12Sr.get() & 0x404L) == 0x404L) {
            tcb.registers.get(1).set(0x1L);
          } else {
            //LAB_00001a80
            tcb.registers.get(1).set(0);
          }

          //LAB_00001a84
          tcb.cop0r12Sr.and(0xffff_fbfbL);
          break;

        case 2: // ExitCriticalSection
          tcb.cop0r12Sr.or(0x404L);
          break;

        case 3:
          pcb.threadControlBlockPtr.set(MEMORY.ref(4, tcb.registers.get(5).get()).cast(ThreadControlBlock::new));
          tcb.registers.get(1).set(0x1L);
          break;

        default:
          DeliverEvent_Impl_B07(HwCPU, EvSpSYSCALL);
          break;
      }

      //LAB_a0001ad8
      ReturnFromException_Impl_B17();

      if(CPU.wasExceptionHandled()) {
        return 1;
      }
    }

    //LAB_a0001afc
    DeliverEvent_Impl_B07(HwCPU, EvSpTRAP);

    //LAB_a0001b10
    return SystemErrorUnresolvedException();
  }

  @Method(0x1b20L)
  public static long EnqueueSyscallHandler_Impl_C01(final int priority) {
    return SysEnqIntRP_Impl_C02(priority, SyscallHandlerStruct_00006da8);
  }

  @Method(0x1b44L)
  public static void DeliverEvent_Impl_B07(final long cls, final int spec) {
    final long v1 = EventControlBlockAddr_a0000120.get();
    final long s4 = v1 + EventControlBlockSize_a0000124.get() / 0x1cL * 0x1c;
    long s0 = v1;

    //LAB_00001bb8
    while(s0 < s4) {
      if(MEMORY.ref(4, s0).offset(0x4L).get() == EvStACTIVE) {
        if(cls == MEMORY.ref(4, s0).get()) {
          if(MEMORY.ref(4, s0).offset(0x8L).get() == spec) {
            long v0 = MEMORY.ref(4, s0).offset(0xcL).get();
            if(v0 == EvMdNOINTR) {
              MEMORY.ref(4, s0).offset(0x4L).set(EvStALREADY);
              //LAB_00001c00
            } else if(v0 == EvMdINTR) {
              v0 = MEMORY.ref(4, s0).offset(0x10L).get();
              if(v0 != 0) {
                MEMORY.ref(4, v0, RunnableRef::new).run();
              }
            }
          }
        }
      }

      //LAB_00001c20
      s0 += 0x1cL;
    }

    //LAB_00001c40
  }

  @Method(0x1c5cL)
  public static void UnDeliverEvent_Impl_B20(final long cls, final int spec) {
    long addr = EventControlBlockAddr_a0000120.get();
    final long end = addr + EventControlBlockSize_a0000124.get() / 28 * 28;

    //LAB_00001ca4
    while(addr < end) {
      if(MEMORY.ref(4, addr).offset(0x4L).get() == 0x4000L) {
        if(MEMORY.ref(4, addr).get() == cls) {
          if(MEMORY.ref(4, addr).offset(0x8L).get() == spec) {
            if(MEMORY.ref(4, addr).offset(0xcL).get() == 0x2000L) {
              MEMORY.ref(4, addr).offset(0x4L).setu(0x2000L);
            }
          }
        }
      }

      //LAB_00001ce8
      addr += 0x1cL;
    }

    //LAB_00001cf8
  }

  @Method(0x1d00L)
  public static int get_free_EvCB_slot_Impl_C04() {
    final long v0 = EventControlBlockAddr_a0000120.deref(4).offset(EventControlBlockSize_a0000124.get() / 28L * 28L).getAddress();
    long v1 = EventControlBlockAddr_a0000120.get();

    //LAB_00001d40
    while(v1 < v0) {
      if(MEMORY.ref(4, v1).offset(0x4L).get() == 0) {
        return (int)((v1 - EventControlBlockAddr_a0000120.get()) / 28L);
      }

      //LAB_00001d70
      v1 += 0x1cL;
    }

    //LAB_00001d80
    return -1;
  }

  @Method(0x1d8cL)
  public static long OpenEvent_Impl_B08(final long cls, final int spec, final int mode, final long func) {
    final int id = get_free_EvCB_slot_Impl_C04();
    if(id == -0x1L) {
      return -1;
    }

    //LAB_00001dcc
    final Value addr = EventControlBlockAddr_a0000120.deref(4).offset(id * 0x1cL);
    addr.offset(0x00L).setu(cls);
    addr.offset(0x04L).setu(0x1000L);
    addr.offset(0x08L).setu(spec);
    addr.offset(0x0cL).setu(mode);
    addr.offset(0x10L).setu(func);

    //LAB_00001e0c
    return 0xf100_0000L | id;
  }

  @Method(0x1e1cL)
  public static int CloseEvent_Impl_B09(final int event) {
    EventControlBlockAddr_a0000120.deref(4).offset((event & 0xffff) * 28L).setu(0);
    return 1;
  }

  @Method(0x1ec8L)
  public static int TestEvent_Impl_B0b(final long event) {
    final Value addr = EventControlBlockAddr_a0000120.deref(4).offset((event & 0xffff) * 28L).offset(0x4L);

    if(addr.get() == 0x4000L) {
      addr.setu(0x2000L);
      return 1;
    }

    //LAB_00001f04
    return 0;
  }

  @Method(0x1f10L)
  public static boolean EnableEvent_Impl_B0c(final long event) {
    final Value addr = EventControlBlockAddr_a0000120.deref(4).offset((event & 0xffff) * 28L).offset(0x4L);

    if(addr.get() != 0) {
      addr.setu(0x2000L);
    }

    //LAB_00001f44
    return true;
  }

  @Method(0x2150L)
  public static long clearMemory(final long addr, final int size) {
    if(addr == 0 || size <= 0) {
      //LAB_00002160
      return 0;
    }

    //LAB_00002170
    for(int i = 0; i < size; i++) {
      MEMORY.ref(1, addr).offset(i).setu(0);
    }

    //LAB_00002180
    return addr;
  }

  @Method(0x2458L)
  public static int FUN_00002458() {
    if((I_MASK.get() & I_STAT.get() & 0x4L) != 0) {
      DeliverEvent_Impl_B07(HwCdRom, EvSpTRAP);
    }
    if((I_MASK.get() & I_STAT.get() & 0x200) != 0) {
      DeliverEvent_Impl_B07(HwSPU, EvSpTRAP);
    }
    if((I_MASK.get() & I_STAT.get() & 2) != 0) {
      DeliverEvent_Impl_B07(HwGPU, EvSpTRAP);
    }
    if((I_MASK.get() & I_STAT.get() & 0x400) != 0) {
      DeliverEvent_Impl_B07(HwPIO, EvSpTRAP);
    }
    if((I_MASK.get() & I_STAT.get() & 0x100) != 0) {
      DeliverEvent_Impl_B07(HwSIO, EvSpTRAP);
    }
    if((I_MASK.get() & I_STAT.get() & 1) != 0) {
      DeliverEvent_Impl_B07(HwVBLANK, EvSpTRAP);
    }
    if((I_MASK.get() & I_STAT.get() & 0x10) != 0) {
      DeliverEvent_Impl_B07(HwRTC0, EvSpTRAP);
    }
    if((I_MASK.get() & I_STAT.get() & 0x20) != 0) {
      DeliverEvent_Impl_B07(HwRTC1, EvSpTRAP);
    }
    if((I_MASK.get() & I_STAT.get() & 0x40) != 0) {
      DeliverEvent_Impl_B07(HwRTC2, EvSpTRAP);
    }
    if((I_MASK.get() & I_STAT.get() & 0x80) != 0) {
      DeliverEvent_Impl_B07(HwCNTL, EvSpTRAP);
    }
    if((I_MASK.get() & I_STAT.get() & 8) != 0) {
      DeliverEvent_Impl_B07(HwDMAC, EvSpTRAP);
    }

    long v1 = 0x8610L;
    long a0 = 0;

    for(int i = 0; i < 0xbL; i++) {
      if(MEMORY.ref(4, v1).get() != 0) {
        a0 |= 0x1L << i;
      }

      v1 += 0x4L;
    }

    I_STAT.setu(~a0);
    return 0;
  }

  @Method(0x2724L)
  public static long InitDefInt_Impl_C0c(final int priority) {
    _00008610.setu(0);
    _00008614.setu(0);
    _00008618.setu(0);

    for(int i = 0; i < 0x20; i += 4) {
      _0000861c.offset(i).setu(0);
    }

    return SysEnqIntRP_Impl_C02(priority, DefaultInterruptPriorityChainStruct_00006d98);
  }

  @Method(0x27c0L)
  public static void InstallDevices_Impl_C12(final int ttyFlag) {
    ttyFlag_00008908.setu(ttyFlag);

    FileControlBlockAddr_a0000140.setu(FileControlBlockBaseAddr_00008648.getAddress());
    FileControlBlockSize_a0000144.setu(704L);
    DeviceControlBlockAddr_a0000150.setu(DeviceControlBlockBaseAddr_00006ee0.getAddress());
    DeviceControlBlockSize_a0000154.setu(_00007200.get() * 0x50L);

    clearMemory(FileControlBlockBaseAddr_00008648.getAddress(), 704);
    setBootStatus(1);
    KernelRedirect_Impl_C1b(ttyFlag);
    setBootStatus(2);

    _00007480.setu(0);
    memcardFindMode_00008644.setu(0);

    AddCdromDevice();
    AddMemCardDevice();
  }

  @Method(0x27a0L)
  public static void FUN_000027a0() {
    _0000863c.setu(0);
  }

  @Method(0x2870L)
  public static void KernelRedirect_Impl_C1b(final int ttyFlag) {
    setBootStatus(3);
    _0000890c.setu(0);
    _00008910.setu(0);
    RemoveDevice_Impl_B48("tty");

    setBootStatus(4);
    if(ttyFlag == 0) {
      AddDummyTtyDevice();
    } else {
      if(ttyFlag != 1) {
        return;
      }

      AddDuartTtyDevice();
    }

    setBootStatus(5);
    FlushStdInOutPut_Impl_C13();
    setBootStatus(6);
  }

  @Method(0x2908L)
  public static void FlushStdInOutPut_Impl_C13() {
    FileClose_Impl_B36(0);
    FileClose_Impl_B36(1);

    if(FileOpen_Impl_B32("tty00:", 1) == 0) {
      FileOpen_Impl_B32("tty00:", 2);
    }
  }

  @Method(0x2958L)
  public static int FileOpen_Impl_B32(final String filename, final int mode) {
    final long fcb = FUN_00003060();

    if(fcb == 0) {
      _00008640.setu(0x18L);
      return -1;
    }

    final Ref<Long> sp24 = new Ref<>();
    final Ref<Long> deviceIndex = new Ref<>();

    //LAB_00002988
    final int pathStartIndex = FUN_000031e8(filename, deviceIndex, sp24);
    if(pathStartIndex == -1) {
      _00008640.setu(0x13L);
      MEMORY.ref(4, fcb).setu(0);
      return -1;
    }

    //LAB_000029c4
    MEMORY.ref(4, fcb).setu(mode);
    MEMORY.ref(4, fcb).offset(0x4L).setu(sp24.get());
    MEMORY.ref(4, fcb).offset(0x1cL).setu(deviceIndex.get());
    MEMORY.ref(4, fcb).offset(0x14L).setu(MEMORY.ref(4, deviceIndex.get()).offset(0x4L));

    if((int)MEMORY.ref(4, deviceIndex.get()).offset(0x14L).deref(4).cast(TriFunctionRef::new).run(fcb, filename.substring(pathStartIndex), mode) != 0) {
      throw new RuntimeException("Failed to open file " + filename.substring(pathStartIndex));

//      _00008640.setu(MEMORY.ref(4, fcb).offset(0x18L));
//      MEMORY.ref(4, fcb).setu(0);
//      return -1;
    }

    //LAB_00002a30
    final long v1 = (fcb - FileControlBlockBaseAddr_00008648.getAddress()) / 0x2cL;
    MEMORY.ref(4, fcb).offset(0x10L).setu(0);
    MEMORY.ref(4, fcb).offset(0x28L).setu(v1);

    //LAB_00002a54
    return (int)v1;
  }

  @Method(0x2a64L)
  public static int FileSeek_Impl_B33(final int fd, final long offset, final int seektype) {
    final long v0 = FUN_000030c8(fd);
    if(v0 == 0 || MEMORY.ref(4, v0).get() == 0) {
      //LAB_2a98
      _00008640.setu(0x9L);
      return -1;
    }

    //LAB_2af8
    if(seektype == 0) {
      //LAB_2aac
      MEMORY.ref(4, v0).offset(0x10L).setu(offset);
      return (int)MEMORY.ref(4, v0).offset(0x10L).get();
    }

    if(seektype == 0x1L) {
      //LAB_2ab4
      MEMORY.ref(4, v0).offset(0x10L).addu(offset);
      return (int)MEMORY.ref(4, v0).offset(0x10L).get();
    }

    if(seektype == 0x2L) {
      //LAB_2b10
      //LAB_2b18
      return (int)MEMORY.ref(4, v0).offset(0x10L).get();
    }

    //LAB_2ac8
    _00008640.setu(0x16L);
    MEMORY.ref(4, v0).offset(0x18L).setu(0x16L);
    LOGGER.error("Invalid seektype %d", seektype);
    throw new RuntimeException("Invalid seektype " + seektype);
  }

  @Method(0x2b28L)
  public static int FileRead_Impl_B34(final int fd, final long dest, final int length) {
    final long fcb = FUN_000030c8(fd);
    if(fcb == 0 || MEMORY.ref(4, fcb).get() == 0) {
      //LAB_00002b54
      _00008640.setu(0x9L);
      return -1;
    }

    //LAB_00002b68
    tty_cdevscan_Impl_C16();

    final int ret;
    if(MEMORY.ref(4, fcb).offset(0x14L).get(0x10L) == 0) {
      //LAB_00002bb8
      final long v1 = MEMORY.ref(4, fcb).offset(0x1cL).get();
      MEMORY.ref(4, fcb).offset(0x8L).setu(dest);
      MEMORY.ref(4, fcb).offset(0xcL).setu(length);

      if(MEMORY.ref(4, v1).offset(0x4L).get(0x4L) == 0) {
        final long divisor = MEMORY.ref(4, v1).offset(0x8L).get();

        //LAB_00002c00
        if(MEMORY.ref(4, fcb).offset(0x10L).get() % divisor != 0) {
          LOGGER.error("offset not on block boundary");
          return -1;
        }

        //LAB_00002c1c
        MEMORY.ref(4, fcb).offset(0xcL).divu(divisor);
      }

      //LAB_00002c3c
      ret = (int)MEMORY.ref(4, v1).offset(0x18L).deref(4).cast(BiFunctionRef::new).run(fcb, 0x1);

      if(ret > 0) {
        MEMORY.ref(4, fcb).offset(0x10L).addu(ret);
      }
    } else {
      ret = (int)MEMORY.ref(4, fcb).offset(0x1cL).deref(4).offset(0x24L).deref(4).cast(TriFunctionRef::new).run(fcb, dest, length);
    }

    //LAB_00002c6c
    if(ret < 0) {
      _00008640.setu(MEMORY.ref(4, fcb).offset(0x18L));
    }

    //LAB_00002c84
    return ret;
  }

  @Method(0x2e00L)
  public static int FileClose_Impl_B36(final int fd) {
    final long v0 = FUN_000030c8(fd);
    if(v0 == 0 || MEMORY.ref(4, v0).get() == 0) {
      //LAB_00002e30
      _00008640.setu(0x9L);
      return -1;
    }

    //LAB_00002e44;
    final long ret = (int)MEMORY.ref(4, v0).offset(0x1cL).deref(4).offset(0x1cL).deref(4).call((int)v0);
    MEMORY.ref(4, v0).setu(0);

    if(ret == 0) {
      //LAB_00002e80
      return fd;
    }

    _00008640.setu(MEMORY.ref(4, v0).offset(0x18L));

    //LAB_00002e88
    return -1;
  }

  @Method(0x2efcL)
  public static int FileIoctl_Impl_B37(final int fd, final int cmd, final int arg) {
    final long v0 = FUN_000030c8(fd);
    if(v0 != 0) {
      if(MEMORY.ref(4, v0).get() != 0) {
        //LAB_00002f70
        if(cmd == 0x6601L) {
          //LAB_00002f44
          if(arg == 0) {
            //LAB_00002f5c
            MEMORY.ref(4, v0).and(0xffff_fffbL);
          } else {
            MEMORY.ref(4, v0).oru(0x4L);
          }

          //LAB_00002f64
          return 1;
        }

        if((int)MEMORY.ref(4, v0).offset(0x1cL).deref(4).offset(0x20L).deref(4).cast(SupplierRef::new).run() >= 0) {
          return 1;
        }

        _00008640.setu(MEMORY.ref(4, v0).offset(0x18L));

        //LAB_00002fb8
        return 0;
      }
    }

    //LAB_00002f30
    _00008640.setu(0x9L);
    return -1;
  }

  @Method(0x2fc8L)
  public static void ioabort_Impl_C19(final String txt1, final String txt2) {
    LOGGER.error("ioabort exit:%s %s", txt1, txt2);
    ioabort_raw(1);
  }

  @Method(0x3060L)
  public static long FUN_00003060() {
    long fcb = FileControlBlockBaseAddr_00008648.getAddress();

    //LAB_00003078
    do {
      if(MEMORY.ref(4, fcb).get() == 0) {
        return fcb;
      }

      //LAB_00003090
      fcb += 0x2cL;
    } while(fcb < ttyFlag_00008908.getAddress());

    ioabort_Impl_C19("out of file descriptors", _00006e54.getString());

    //LAB_000030b8
    return 0;
  }

  @Method(0x30c8L)
  public static long FUN_000030c8(final int fd) {
    if(fd < 0 || fd >= 0x10L) {
      return 0;
    }

    return FileControlBlockBaseAddr_00008648.offset(fd * 0x2cL).getAddress();
  }

  @Method(0x3108L)
  public static long getDeviceIndex(final String a0) {
    if(_00007200.get() > 0) {
      final long t2 = DeviceControlBlockBaseAddr_00006ee0.offset(_00007200.get() * 80).getAddress();
      long s0 = DeviceControlBlockBaseAddr_00006ee0.getAddress();

      //LAB_00003154
      do {
        final String s1 = MEMORY.ref(4, s0).deref(1).getString();
        if(!s1.isEmpty()) {
          if(strcmp(s1, a0) == 0) {
            return s0;
          }
        }

        //LAB_0000317c
        s0 += 0x50L;
      } while(s0 < t2);
    }

    //LAB_000031a0
    _0000890c.setu(0);
    LOGGER.error("%s is not known device", a0);
    LOGGER.error("Known devices are:");
    PrintInstalledDevices_Impl_B49();

    //LAB_000031d0
    return 0;
  }

  @Method(0x31e8L)
  public static int FUN_000031e8(final String filename, final Ref<Long> deviceIndexRef, final Ref<Long> a2) {
    int s0;
    int charIndex = 0;

    //LAB_00003210
    while(filename.charAt(charIndex) == ' ') {
      charIndex++;
    }

    //LAB_00003220
    a2.set(0L);

    final char[] sp44 = new char[0x2c];
    final char currentChar = filename.charAt(charIndex);
    int charIndex2 = charIndex;
    int v1 = 0;
    if(currentChar != '\0' && currentChar != ':') {
      char currentChar2 = filename.charAt(charIndex2);
      s0 = 0;

      //LAB_00003250
      do {
        sp44[s0] = currentChar2;
        charIndex2++;
        currentChar2 = filename.charAt(charIndex2);
        v1++;
        s0++;
      } while(currentChar2 != '\0' && currentChar2 != ':');

      //LAB_00003270
    }

    //LAB_00003274
    s0 = v1;
    int lastIndex = s0;
    sp44[s0] = 0;
    final char t0 = filename.charAt(charIndex2);
    if(t0 != '\0') {
      a2.set(0L);
      charIndex2++;
      v1--;
      s0--;

      //LAB_000032bc
      while(MEMORY.ref(1, 0x73d1).offset(sp44[s0]).get(0x44L) != 0) {
        s0--;
        v1--;
      }

      //LAB_000032dc
      v1++;
      final int sp68 = v1;
      s0++;
      char a0 = sp44[s0];

      //LAB_000032fc
      while(a0 != 0) {
        final char uppercase = toupper(a0);

        if(MEMORY.ref(1, 0x73d1).offset(sp44[s0]).get(0x4L) == 0) {
          v1 = 0x37;
        } else {
          v1 = 0x30;
        }

        //LAB_00003330
        s0++;
        a2.set(uppercase + a2.get() * 16 - v1);
        a0 = sp44[s0];
      }

      //LAB_0000335c
      sp44[sp68] = 0;
      lastIndex = sp68;
    }

    //LAB_0000336c
    final long deviceIndex = getDeviceIndex(new String(sp44, 0, lastIndex));
    deviceIndexRef.set(deviceIndex);
    if(deviceIndex == 0) {
      return -1;
    }

    //LAB_00003388
    //LAB_0000338c
    return charIndex2;
  }

  @Method(0x3c2cL)
  public static boolean AddDevice_Impl_B47(final long deviceInfo) {
    if(_00007200.get() == 0) {
      return false;
    }

    final long v0 = DeviceControlBlockBaseAddr_00006ee0.offset(_00007200.get() * 80).getAddress();

    //LAB_00003c6c
    long v1 = 0;
    do {
      if(DeviceControlBlockBaseAddr_00006ee0.offset(v1).get() == 0) {
        memcpy(DeviceControlBlockBaseAddr_00006ee0.offset(v1).getAddress(), deviceInfo, 0x50);
        FlushCache();

        DeviceControlBlockBaseAddr_00006ee0.offset(v1).offset(4, 0x10L).deref(4).cast(RunnableRef::new).run();
        return true;
      }

      //LAB_00003cbc
      v1 += 0x50L;
    } while(v1 < v0);

    //LAB_00003ccc
    //LAB_00003cd0
    return false;
  }

  @Method(0x3ce0L)
  public static boolean RemoveDevice_Impl_B48(final String device) {
    if(_00007200.get() == 0) {
      return false;
    }

    //LAB_00003d2c
    long s0 = 0;
    do {
      if(DeviceControlBlockBaseAddr_00006ee0.get() != 0) {
        if(strcmp(device, DeviceControlBlockBaseAddr_00006ee0.getString()) == 0) {
          DeviceControlBlockBaseAddr_00006ee0.offset(s0).offset(0x48L).deref(4).cast(RunnableRef::new).run();
          DeviceControlBlockBaseAddr_00006ee0.offset(s0).setu(0);
          return true;
        }
      }

      //LAB_00003d68
      s0 += 0x50L;
    } while(s0 < DeviceControlBlockBaseAddr_00006ee0.offset(_00007200.get() * 80).getAddress());

    //LAB_00003d90
    //LAB_00003d94
    return false;
  }

  @Method(0x3dacL)
  public static void PrintInstalledDevices_Impl_B49() {
    long devicePtr = DeviceControlBlockBaseAddr_00006ee0.getAddress();

    if(_00007200.get() > 0) {
      final long lastDevicePtr = DeviceControlBlockBaseAddr_00006ee0.offset(_00007200.get() * 80).getAddress();

      //LAB_00003df8
      do {
        final long namePtr = MEMORY.ref(4, devicePtr).get();

        if(namePtr != 0) {
          LOGGER.info("\t%s:\t%s", MEMORY.ref(1, namePtr).getString(), MEMORY.ref(4, devicePtr).offset(0xcL).deref(1).getString());
        }

        //LAB_00003e34
        devicePtr += 0x50L;
      } while(devicePtr < lastDevicePtr);
    }

    //LAB_00003e4c
  }

  @Method(0x3e5cL)
  public static void set_card_find_mode_Impl_C1a(final int mode) {
    memcardFindMode_00008644.setu(mode);
  }

  @Method(0x3e80L)
  public static void tty_cdevscan_Impl_C16() {
    for(int fd = 0; fd < 0x10; fd++) {
      if(FileControlBlockBaseAddr_00008648.offset(fd * 0x2cL).get(0x1000L) != 0) {
        FileIoctl_Impl_B37(fd, 0x6602, 0);
      }
    }
  }

  @Method(0x39a4L)
  @Nullable
  public static DIRENTRY firstfile(final String name, final DIRENTRY dir) {
    if(_00007480.get() == 0) {
      _00007480.setu(FUN_00003060());

      if(_00007480.get() == 0) {
        _00008640.setu(0x18L);
        return null;
      }
    }

    //LAB_39ec
    final Ref<Long> sp0x28 = new Ref<>();
    final Ref<Long> sp0x24 = new Ref<>();
    final int a1 = FUN_000031e8(name, sp0x28, sp0x24);

    if(a1 == -0x1L) {
      _00008640.setu(0x13L);
      _00007480.deref(4).setu(0);
      return null;
    }

    //LAB_3a28
    _00007480.deref(4).offset(0x4L).setu(sp0x24.get());
    _00007480.deref(4).offset(0x1cL).setu(sp0x28.get());

    //LAB_3a60
    return (DIRENTRY)MEMORY.ref(4, sp0x28.get()).offset(0x34L).deref(4).call(_00007480.get(), name.substring(a1), dir);
  }

  @Method(0x3a70L)
  public static DIRENTRY nextfile_Impl_B43(final DIRENTRY dir) {
    return (DIRENTRY)_00007480.deref(4).offset(0x1cL).deref(4).offset(0x38L).deref(4).call(_00007480.get(), dir);
  }

  @Method(0x3e68L)
  public static int get_card_find_mode_Impl_C1d() {
    return (int)memcardFindMode_00008644.get();
  }

  @Method(0x43d0L)
  public static boolean ChangeClearPad_Impl_B5b(final boolean val) {
    final boolean oldValue = memcardUseVblank_00008914.get() != 0;
    memcardUseVblank_00008914.setu(val ? 1 : 0);
    return oldValue;
  }

  @Method(0x43e8L)
  public static void initCard() {
    JOY_MCD_CTRL.setu(0b100_0000L); // Reset registers
    JOY_MCD_BAUD.setu(0x88L);
    JOY_MCD_MODE.setu(0b1101L); // Baudrate reload factor: mul1; char len: 8 bits

    JOY_MCD_CTRL.setu(0);
    cardWait(10L);

    JOY_MCD_CTRL.setu(0b10L); // JOY1 output selected
    cardWait(10L);

    JOY_MCD_CTRL.setu(0b10_0000_0000_0010L); // JOY2 output selected
    cardWait(10L);

    JOY_MCD_CTRL.setu(0);
    allowCardChange_000074c0.setu(0);
  }

  @Method(0x445cL)
  public static void cardWait(final long a0) {
//    long sp00 = a0 - 1;
//
//    while(sp00 >= 0) {
//      sp00--;
//    }
    DebugHelper.sleep(a0);
  }

  @Method(0x4498L)
  public static void FUN_00004498(final long a0) {
    assert false;
  }

  @Method(0x49bcL)
  public static int memcardPriorityChainSecondCallback(final long a0) {
    if(memcardShareWithController_000074b8.get() != 0) {
      FUN_00004498(0);
      FUN_00004498(1);

      if(_000074c4.get() != 0) {
        OutdatedPadGetButtons_Impl_B16();
      }
    }

    //LAB_000049fc
    if(memcardUseVblank_00008914.get() != 0) {
      I_STAT.setu(0xfffffffe);
    }

    //LAB_00004a20
    if(cardStarted_000074bc.get() != 0) {
      FUN_00005000();
    }

    //LAB_00004a40
    return 0;
  }

  @Method(0x4a4cL)
  public static int memcardPriorityChainFirstCallback() {
    if(I_MASK.get(0x1L) == 0 || I_STAT.get(0x1L) == 0) {
      return 0;
    }

    if(memcardTransferActive_0000755a.get() != 0) {
      return 0;
    }

    return 1;
  }

  @Method(0x4a94L)
  public static void initMemcardPriorityChain() {
    memcardPriorityChain_000074a8.next.clear();
    memcardPriorityChain_000074a8.secondFunction.set(MEMORY.ref(4, getMethodAddress(Kernel.class, "memcardPriorityChainSecondCallback", long.class), ConsumerRef::new));
    memcardPriorityChain_000074a8.firstFunction.set(MEMORY.ref(4, getMethodAddress(Kernel.class, "memcardPriorityChainFirstCallback"), SupplierRef::new));
    memcardPriorityChain_000074a8.unknown.set(0);
  }

  @Method(0x4c70L)
  public static int StartCard_Impl_B4b() {
    initCard();
    EnterCriticalSection();

    SysDeqIntRP_Impl_C03(2, memcardPriorityChain_000074a8);
    SysEnqIntRP_Impl_C02(2, memcardPriorityChain_000074a8);

    I_MASK.oru(0x1L);

    ChangeClearPad_Impl_B5b(true);
    ChangeClearRCnt_Impl_C0a(3, false); // Don't auto-ack vblank

    cardStarted_000074bc.setu(0x1L);

    ExitCriticalSection();

    return 1;
  }

  @Method(0x4d3cL)
  public static void allow_new_card_Impl_B50() {
    allowCardChange_000074c0.setu(0x1L);
  }

  @Method(0x4d4cL)
  public static long FUN_00004d4c(final long a0) {
    final long v1 = _0000756c.get();
    _0000756c.setu(a0);
    return v1;
  }

  @Method(0x4d6cL)
  @Nullable
  public static PriorityChainEntry memcardIoPriorityChainSecondFunction(final long a0) {
    final long joyN;
    if(joypadIndex_00007264.get() == 0) {
      joyN = 0; // JOY1
    } else {
      //LAB_00004d88
      joyN = 0b10_0000_0000_0000L; // JOY2
    }

    //LAB_00004d8c
    JOY_MCD_CTRL.oru(joyN | 0b1_0010L); // Enable JOYn; Acknowledge (reset JOY_STAT bits 3/9)
    memcardIoStep_00007514.addu(0x1L);
    joyNBit_000074a0.setu(joyN);

    final long ret = memcardIoCallback_00007528.get(joypadIndex_00007264.get()).deref().run();

    //LAB_00004df0
    if(ret == 0) {
      //LAB_00004e10
      I_STAT.setu(0xffff_ff7fL);
      I_MASK.oru(0x80L);
      return null;
    }

    if(ret != 0x1L) {
      //LAB_00004e34
      memcardTransferActive_0000755a.setu(0);
      JOY_MCD_CTRL.setu(0);
      if(_00007520.get() == 0) {
        allowCardChange_000074c0.setu(0);
        memcardStateBitset_00007568.get(joypadIndex_00007264.get()).set(0x21);
        joypadIndex_0000751c.set(joypadIndex_00007264.get());
        bu_callback_err_write();
        DeliverEvent_Impl_B07(HwCARD, EvSpERROR);
      }

      //LAB_00004e9c
      memcardIoStep_00007514.setu(0);
      _00007520.setu(0);
      SysDeqIntRP_Impl_C03(0x1, memcardIoPriorityChainEntry_00007540);
      I_STAT.setu(0xffffff7fL);
      I_MASK.and(0xffffff7fL);
      return null;
    }

    //LAB_00004de4
    _000074a4.setu(0);

    //LAB_00004ee0
    memcardTransferActive_0000755a.setu(0);
    JOY_MCD_CTRL.setu(0);
    memcardIoStep_00007514.setu(0);
    SysDeqIntRP_Impl_C03(0x1, memcardIoPriorityChainEntry_00007540);
    I_STAT.setu(0xffffff7fL);
    I_MASK.and(0xffffff7fL);

    if(_00007520.get() == 0) {
      allowCardChange_000074c0.setu(0);
      memcardStateBitset_00007568.get(joypadIndex_00007264.get()).set(0x1);
      joypadIndex_0000751c.set(joypadIndex_00007264.get());
      bu_callback_okay();
      DeliverEvent_Impl_B07(HwCARD, EvSpIOE);
    }

    //LAB_00004f7c
    //LAB_00004f80
    return null;
  }

  @Method(0x4f90L)
  public static int memcardIoPriorityChainFirstFunction() {
    if(I_MASK.get(0x80L) == 0 || I_STAT.get(0x80L) == 0) { // Controller and memcard interrupt
      return 0;
    }

    if((isMemcardIoFinished() & 0x1L) != 0) {
      return 0;
    }

    return 1;
  }

  @Method(0x5000L)
  @Nullable
  public static PriorityChainEntry FUN_00005000() {
    UnDeliverEvent_Impl_B20(HwCARD, EvSpIOE);
    UnDeliverEvent_Impl_B20(HwCARD, EvSpERROR);
    UnDeliverEvent_Impl_B20(HwCARD, EvSpTIMOUT);
    UnDeliverEvent_Impl_B20(HwCARD, EvSpUNKNOWN);
    UnDeliverEvent_Impl_B20(HwCARD, EvSpNEW);

    if(memcardTransferActive_0000755a.get() != 0) {
      LOGGER.warn("Warning: memcard transfer already active");

      memcardTransferActive_0000755a.setu(0);
      _000074a4.setu(0);
      memcardIoStep_00007514.setu(0);
      I_STAT.setu(0xffffff7fL);
      I_MASK.and(0xffffff7fL);
      JOY_MCD_CTRL.setu(0);
      allowCardChange_000074c0.setu(0);
      memcardStateBitset_00007568.get(joypadIndex_00007264.get()).set(0x11);
      joypadIndex_0000751c.set(joypadIndex_00007264.get());
      bu_callback_err_busy();
      DeliverEvent_Impl_B07(HwCARD, EvSpTIMOUT);
      final PriorityChainEntry priorityChainEntry = SysDeqIntRP_Impl_C03(0x1, memcardIoPriorityChainEntry_00007540);
      JOY_MCD_CTRL.setu(0b100_0000L); // Reset
      JOY_MCD_BAUD.setu(0x88L);
      JOY_MCD_MODE.setu(0b1101L); // Baudrate reload factor MUL1; Char len 8 bits
      JOY_MCD_CTRL.setu(0);
      return priorityChainEntry;
    }

    //LAB_00005128
    joypadIndex_00007264.set(1 - joypadIndex_00007264.get());
    if((memcardStateBitset_00007568.get(joypadIndex_00007264.get()).get() & 0x1) != 0) {
      return null;
    }

    //LAB_00005170
    FUN_00006390(
      memcardIoAddress_00007550.get(joypadIndex_00007264.get()).get(),
      memcardStateBitset_00007568.get(joypadIndex_00007264.get()),
      _00007560.get(joypadIndex_00007264.get())
    );

    SysDeqIntRP_Impl_C03(0x1, memcardIoPriorityChainEntry_00007540);
    SysEnqIntRP_Impl_C02(0x1, memcardIoPriorityChainEntry_00007540);
    memcardIoStep_00007514.setu(0);
    _00007520.setu(0);

    //LAB_000051e4
    return memcardIoPriorityChainSecondFunction(0);
  }

  /**
   * This appears to be the sequence for writing data to the memcard (81h, 57h, ...)
   */
  @Method(0x51f4L)
  public static long writeMemcardIoCallback() {
    final int joypadIndex = joypadIndex_00007264.get();

    switch((int)memcardIoStep_00007514.get()) {
      case 0x1 -> {
        final long joyN;
        if(joypadIndex == 0) {
          joyN = 0; // JOY1
        } else {
          joyN = 0b10_0000_0000_0000L; // JOY2
        }

        //LAB_00005258
        JOY_MCD_CTRL.setu(joyN | 0b1_0000_0000_0011L); // TX enable; JOYn output enable; ACK interrupt enable
        JOY_MCD_DATA.get(); // Intentional read to nowhere
        memcardTransferActive_0000755a.setu(0x1L);
        JOY_MCD_DATA.setu(0x81L + (memcardIoPort_00007500.get(joypadIndex).get() & 0xf));
        JOY_MCD_CTRL.oru(0b1_0000L); // ACK - reset JOY_STAT bits 3/9
        I_STAT.setu(0xffff_ff7fL);
        joyNBit_000074a0.setu(joyN);
        return 0;
      }

      case 0x2 -> {
        JOY_MCD_DATA.get(); // Intentional read to nowhere
        JOY_MCD_DATA.setu(0x57L);
        JOY_MCD_CTRL.oru(0b1_0000L); // ACK
        I_STAT.setu(0xffff_ff7fL);
        return 0;
      }

      case 0x3 -> {
        final long data = JOY_MCD_DATA.get();
        JOY_MCD_DATA.setu(0);
        JOY_MCD_CTRL.oru(0b1_0000L); // ACK
        I_STAT.setu(0xffff_ff7fL);
        memcardData_000074f8.get(joypadIndex).set(data);

        // If memcard changes are allowed, no need to do below checks
        if(allowCardChange_000074c0.get() != 0) {
          return 0;
        }

        // This flag must mean "memcard changed"
        if((data & 0x8L) == 0) {
          return 0;
        }

        // Memcard changed - set flags and send events
        allowCardChange_000074c0.setu(0);
        memcardStateBitset_00007568.get(joypadIndex).set(0x1);
        joypadIndex_0000751c.set(joypadIndex_00007264.get());
        bu_callback_err_eject();
        DeliverEvent_Impl_B07(HwCARD, EvSpNEW);
        _00007520.setu(0x1L);
        LOGGER.error("Memcard IO error during write stage 3 data %x", data);
        return -0x1L;
      }

      case 0x4 -> {
        final long data = JOY_MCD_DATA.get();
        JOY_MCD_DATA.setu(0);
        JOY_MCD_CTRL.oru(0b1_0000L); // ACK
        I_STAT.setu(0xffff_ff7fL);

        if(data == 0x5aL) {
          return 0;
        }

        LOGGER.error("Memcard IO error during write stage 4 data %x", data);
        return -0x1L;
      }

      case 0x5 -> {
        final long v0 = memcardIoSector_00007508.get(joypadIndex).get() >>> 8 & 0xffL;
        final long data = JOY_MCD_DATA.get();
        JOY_MCD_DATA.setu(v0);
        JOY_MCD_CTRL.oru(0b1_0000L); // ACK
        I_STAT.setu(0xffff_ff7fL);

        if(data != 0x5dL) {
          LOGGER.error("Memcard IO error during write stage 5 data %x", data);
          return -0x1L;
        }

        //LAB_00005418
        _00007560.get(joypadIndex).set(v0);
        return 0;
      }

      case 0x6 -> {
        final long v1 = memcardIoSector_00007508.get(joypadIndex).get() & 0xffL;
        JOY_MCD_DATA.get(); // Intentional read to nowhere
        JOY_MCD_DATA.setu(v1);
        JOY_MCD_CTRL.oru(0b1_0000L); // ACK
        I_STAT.setu(0xffff_ff7fL);
        _00007560.get(joypadIndex).xor(v1);
        memcardIoFinished();
        return 0;
      }

      case 0x7 -> {
        JOY_MCD_DATA.get(); // Intentional read to nowhere
        JOY_MCD_DATA.get(); // Intentional read to nowhere
        JOY_MCD_DATA.setu(_00007560.get(joypadIndex).get());
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(0xffff_ff7fL);
        return 0;
      }

      case 0x8 -> {
        JOY_MCD_DATA.get(); // Intentional read to nowhere
        JOY_MCD_DATA.setu(0);
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(0xffff_ff7fL);
        return 0;
      }

      case 0x9 -> {
        final long data = JOY_MCD_DATA.get();
        JOY_MCD_DATA.setu(0);
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(0xffff_ff7fL);

        if(data == 0x5cL) {
          return 0;
        }

        LOGGER.error("Memcard IO error during write stage 9 data %x", data);
        return -0x1L;
      }

      case 0xa -> {
        final long v1 = JOY_MCD_DATA.get();
        JOY_MCD_DATA.setu(0);
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(0xffff_ff7fL);

        if(v1 != 0x5dL) {
          LOGGER.error("Memcard IO error during write stage 10.1 data %x", v1);
          return -0x1L;
        }

        //LAB_00005574
        //LAB_00005588
        while(JOY_MCD_STAT.get(0x2L) == 0) {
          DebugHelper.sleep(1);
        }

        //LAB_0000559c
        if(allowCardChange_000074c0.get() == 0) {
          if((memcardData_000074f8.get(joypadIndex).get() & 0x4L) != 0) {
            allowCardChange_000074c0.setu(0);
            joypadIndex_0000751c.set(joypadIndex_00007264.get());
            memcardStateBitset_00007568.get(joypadIndex_00007264.get()).set(0x1);
            bu_callback_err_prev_write();
            DeliverEvent_Impl_B07(HwCARD, EvSpPERROR);
            _00007520.setu(0x1L);
          }

          //LAB_00005628
        }

        final long data = JOY_MCD_DATA.get();

        //LAB_0000562c
        if(data == 0x47L) {
          //LAB_0000564c
          return 0x1L;
        }

        if(data == 0x4eL) {
          //LAB_00005654
          LOGGER.error("Memcard IO error during write stage 10.2 data %x", data);
          return -0x1L;
        }

        if(data == 0xffL) {
          //LAB_0000565c
          LOGGER.error("Memcard IO error during write stage 10.3 data %x", data);
          return -0x1L;
        }

        //LAB_00005664
        LOGGER.error("Memcard IO error during write stage 10.4 data %x", data);
        return -0x1L;
      }

      default -> {
        LOGGER.error("Memcard IO error during write stage %d", memcardIoStep_00007514.get());
        return -0x1L;
      }
    }

    //LAB_00005674
    //LAB_00005678
  }

  @Method(0x5688L)
  public static long readMemcardIoCallback() {
    final int joypadIndex = joypadIndex_00007264.get();
    final long v0;
    final long a2 = memcardIoSector_00007508.get(joypadIndex).get();

    switch((int)memcardIoStep_00007514.get()) {
      case 0x1 -> {
        if(joypadIndex == 0) {
          v0 = 0;
        } else {
          v0 = 0x2000L;
        }

        //LAB_000056fc
        JOY_MCD_CTRL.setu(v0 | 0x1003L);
        JOY_MCD_DATA.get(); // Intentional read to nowhere
        JOY_MCD_DATA.setu(0x81L + (memcardIoPort_00007500.get(joypadIndex).get() & 0xf));
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(0xffff_ff7fL);
        memcardTransferActive_0000755a.setu(0x1L);
        joyNBit_000074a0.setu(v0);
        return 0;
      }

      case 0x2 -> {
        JOY_MCD_DATA.get(); // Intentional read to nowhere
        JOY_MCD_DATA.setu(0x52L);
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(0xffff_ff7fL);
        return 0;
      }

      case 0x3 -> {
        final long data = JOY_MCD_DATA.get();
        JOY_MCD_DATA.setu(0);
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(0xffff_ff7fL);

        if(allowCardChange_000074c0.get() != 0) {
          return 0;
        }

        if((data & 0x8L) == 0) {
          return 0;
        }

        allowCardChange_000074c0.setu(0);
        memcardStateBitset_00007568.get(joypadIndex).set(0x1);
        joypadIndex_0000751c.set(joypadIndex_00007264.get());

        bu_callback_err_eject();
        DeliverEvent_Impl_B07(HwCARD, EvSpNEW);

        _00007520.setu(0x1L);

        LOGGER.error("Memcard IO error during read stage 3");
        return -0x1L;
      }

      case 0x4 -> {
        final long data = JOY_MCD_DATA.get();
        JOY_MCD_DATA.setu(0);
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(0xffff_ff7fL);

        if(data != 0x5aL) {
          LOGGER.error("Memcard IO error during read stage 4");
          return -0x1L;
        }

        return 0;
      }

      case 0x5 -> {
        final long data = JOY_MCD_DATA.get();
        JOY_MCD_DATA.setu(a2 >> 0x8L & 0xffL); // Send MSB
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(0xffff_ff7fL);

        if(data != 0x5dL) {
          LOGGER.error("Memcard IO error during read stage 5");
          return -0x1L;
        }

        return 0;
      }

      case 0x6 -> {
        JOY_MCD_DATA.get(); // Intentional read to nowhere
        JOY_MCD_DATA.setu(a2 & 0xffL); // Send LSB
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(0xffff_ff7fL);
        return 0;
      }

      case 0x7 -> {
        JOY_MCD_DATA.get(); // Intentional read to nowhere
        JOY_MCD_DATA.setu(0);
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(0xffff_ff7fL);
        return 0;
      }

      case 0x8 -> {
        final long data = JOY_MCD_DATA.get();
        JOY_MCD_DATA.setu(0);
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(0xffff_ff7fL);

        if(data != 0x5cL) {
          LOGGER.error("Memcard IO error during read stage 8 (got data %02x)", data);
          return -0x1L;
        }

        return 0;
      }

      case 0x9 -> {
        final long data = JOY_MCD_DATA.get();
        JOY_MCD_DATA.setu(0);
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(0xffff_ff7fL);

        if(data != 0x5dL) {
          LOGGER.error("Memcard IO error during read stage 9");
          return -0x1L;
        }

        return 0;
      }

      case 0xa -> {
        final long data = JOY_MCD_DATA.get();
        JOY_MCD_DATA.setu(0);
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(0xffff_ff7fL);

        v0 = a2 >>> 0x8L & 0xffL;
        if(data != v0) {
          LOGGER.error("Memcard IO error during read stage 10");
          return -0x1L;
        }

        //LAB_000059d0
        _00007560.get(joypadIndex).set(a2 & 0xffL ^ v0);
        _00007558.get(joypadIndex).set(0);
        return 0;
      }

      case 0xb -> {
        final long data = JOY_MCD_DATA.get();
        JOY_MCD_DATA.setu(_00007558.get(joypadIndex).get());
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(0xffff_ff7fL);

        if(data != (a2 & 0xffL)) {
          LOGGER.error("Memcard IO error during read stage 11");
          return -0x1L;
        }

        //LAB_00005a48
        memcardIoFinished();
        return 0;
      }

      case 0xc -> {
        final long data = JOY_MCD_DATA.get();
        JOY_MCD_DATA.setu(0);
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(0xffff_ff7fL);

        final long memcardIoAddress = memcardIoAddress_00007550.get(joypadIndex).get();
        MEMORY.ref(1, memcardIoAddress).offset(0x7fL).setu(data);
        _00007560.get(joypadIndex).xor(MEMORY.ref(1, memcardIoAddress).offset(0x7fL).get());
        return 0;
      }

      case 0xd -> {
        final long data = JOY_MCD_DATA.get();
        JOY_MCD_DATA.setu(0);
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(0xffff_ff7fL);

        if(data != _00007560.get(joypadIndex).get()) {
          LOGGER.error("Memcard IO error during read stage 13");
          return -0x1L;
        }

        //LAB_00005b00
        //LAB_00005b14
        while(JOY_MCD_STAT.get(0x2L) == 0) {
          DebugHelper.sleep(1);
        }

        //LAB_00005b28
        if(JOY_MCD_DATA.get() != 0x47L) {
          LOGGER.error("Memcard IO error during read stage 13");
          return -0x1L;
        }

        //LAB_00005b40
        return 0x1;
      }
    }

    LOGGER.error("Memcard IO error during read stage %d", memcardIoStep_00007514.get());
    return -0x1L;
  }

  @Method(0x5b64L)
  public static long memcardInfoIoCallback() {
    switch((int)memcardIoStep_00007514.get()) {
      case 0x1 -> {
        final long v1;
        if(joypadIndex_00007264.get() == 0) {
          v1 = 0;
        } else {
          v1 = 0x2000L;
        }

        //lAB_00005bb8
        JOY_MCD_CTRL.setu(v1 | 0x1003L);
        JOY_MCD_DATA.get(); // intentional read to nowhere
        JOY_MCD_DATA.setu(0x81L + (memcardIoPort_00007500.get(joypadIndex_00007264.get()).get() & 0xfL));
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(-0x81L);
        memcardTransferActive_0000755a.setu(0x1L);
        joyNBit_000074a0.setu(v1);
        return 0;
      }

      case 0x2 -> {
        JOY_MCD_DATA.get(); // intentional read to nowhere
        JOY_MCD_DATA.setu(0x52L);
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(-0x81L);
        return 0;
      }

      case 0x3 -> {
        final long data = JOY_MCD_DATA.get();
        JOY_MCD_DATA.setu(0);
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(-0x81L);
        if(allowCardChange_000074c0.get() != 0) {
          return 0;
        }

        if((data & 0x4L) != 0) {
          allowCardChange_000074c0.setu(0);
          memcardStateBitset_00007568.get(joypadIndex_00007264.get()).set(0x1);
          joypadIndex_0000751c.set(joypadIndex_00007264.get());
          bu_callback_err_write();
          DeliverEvent_Impl_B07(HwCARD, EvSpERROR);
          _00007520.setu(0x1L);
          return -0x1L;
        }

        //LAB_00005cf0
        if((data & 0x8L) == 0) {
          return 0;
        }

        allowCardChange_000074c0.setu(0);
        memcardStateBitset_00007568.get(joypadIndex_00007264.get()).set(0x1);
        joypadIndex_0000751c.set(joypadIndex_00007264.get());
        bu_callback_err_eject();
        DeliverEvent_Impl_B07(HwCARD, EvSpNEW);
        _00007520.setu(0x1L);
        return -0x1L;
      }

      case 0x4 -> {
        final long data = JOY_MCD_DATA.get();
        JOY_MCD_DATA.setu(0);
        JOY_MCD_CTRL.oru(0x10L);
        I_STAT.setu(-0x81L);
        if(data != 0x5aL) {
          return -0x1L;
        }

        //LAB_00005d84
        return 0x1L;
      }
    }

    return -0x1L;
  }

  @Method(0x5da8L)
  public static void InitCard_Impl_B4a(final boolean pad_enable) {
    initMemcardPriorityChain();

    memcardTransferActive_0000755a.setu(0);
    joypadIndex_00007264.set(0);
    memcardStateBitset_00007568.get(0).set(1);
    memcardStateBitset_00007568.get(1).set(1);
    memcardIoPriorityChainEntry_00007540.next.clear();
    memcardIoPriorityChainEntry_00007540.secondFunction.set(MEMORY.ref(4, getMethodAddress(Kernel.class, "memcardIoPriorityChainSecondFunction", long.class), ConsumerRef::new));
    memcardIoPriorityChainEntry_00007540.firstFunction.set(MEMORY.ref(4, getMethodAddress(Kernel.class, "memcardIoPriorityChainFirstFunction"), SupplierRef::new));
    memcardIoPriorityChainEntry_00007540.unknown.set(0);

    installEarlyCardIrqHandler();
    FUN_00004d4c(0x1L);

    memcardShareWithController_000074b8.setu(pad_enable ? 1 : 0);
  }

  @Method(0x5e30L)
  public static int get_bu_callback_port_Impl_B58() {
    return memcardIoPort_00007500.get((int)joypadIndex_0000751c.get()).get();
  }

  @Method(0x5e50L)
  public static int read_card_sector_Impl_B4f(final int port, final int sector, final long dest) {
    int at = port;
    if(port < 0) {
      at += 0xf;
    }

    //LAB_00005e64
    final int joypadIndex = at >>> 4;
    if((memcardStateBitset_00007568.get(joypadIndex).get() & 0x1) == 0 || sector < 0 || sector > 0x400L) {
      //LAB_00005e90
      return 0;
    }

    //LAB_00005e98
    _000074a4.setu(0);
    memcardIoStep_00007514.setu(0);

    // Direct read, no interrupts
    MEMCARD.directRead(sector, dest);
    memcardOkay_a000b9d0.setu(0x1L);
//    bu_callback_okay();
    DeliverEvent_Impl_B07(HwCARD, EvSpIOE);
    FUN_bfc0bff0(joypadIndex, EvSpIOE);
    memcardIoFinished();

    // Disable old code
    if(false) {
      memcardIoPort_00007500.get(joypadIndex).set(port);
      memcardIoSector_00007508.get(joypadIndex).set(sector);
      memcardIoCallback_00007528.get(joypadIndex).set(MEMORY.ref(4, getMethodAddress(Kernel.class, "readMemcardIoCallback"), SupplierRef::new));
      memcardIoAddress_00007550.get(joypadIndex).set(dest);
      memcardStateBitset_00007568.get(joypadIndex).set(0x2);
    }

    return 1;
  }

  @Method(0x5f04L)
  public static boolean write_card_sector_Impl_B4e(final int port, final int sector, final long src) {
    int at = port;
    if(port < 0) {
      at += 0xf;
    }

    //LAB_00005f18
    final int joypadIndex = at >>> 4;
    if((memcardStateBitset_00007568.get(joypadIndex).get() & 0x1) == 0 || sector < 0 || sector > 0x400L) {
      //LAB_00005f44
      return false;
    }

    //LAB_00005f4c
    _000074a4.setu(0);
    memcardIoStep_00007514.setu(0);

    // Direct write, no interrupts
    MEMCARD.directWrite(sector, src);
    memcardOkay_a000b9d0.setu(0x1L);
//    bu_callback_okay();
    DeliverEvent_Impl_B07(HwCARD, EvSpIOE);
    FUN_bfc0bff0(joypadIndex, EvSpIOE);
    memcardIoFinished();

    // Disable old code
    if(false) {
      memcardIoPort_00007500.get(joypadIndex).set(port);
      memcardIoSector_00007508.get(joypadIndex).set(sector);
      memcardIoCallback_00007528.get(joypadIndex).set(MEMORY.ref(4, getMethodAddress(Kernel.class, "writeMemcardIoCallback"), SupplierRef::new));
      memcardIoAddress_00007550.get(joypadIndex).set(src);
      memcardStateBitset_00007568.get(joypadIndex).set(0x4);
    }

    return true;
  }

  @Method(0x5fb8L)
  public static boolean _card_info_subfunc_Impl_B4d(final int port) {
    int at = port;
    if(port < 0) {
      at += 0xfL;
    }

    //LAB_00005fcc
    final int joypadIndex = at >>> 4;
    if((memcardStateBitset_00007568.get(joypadIndex).get() & 0x1) == 0) {
      return false;
    }

    //LAB_00005ff0
    _000074a4.setu(0);
    memcardIoStep_00007514.setu(0);

    // No need to actually do anything
//    bu_callback_okay();
    memcardOkay_a000b9d0.setu(0x1L);
    DeliverEvent_Impl_B07(HwCARD, EvSpIOE);
    FUN_bfc0bff0(joypadIndex, EvSpIOE);
    memcardIoFinished();

    // Disable old code
    if(false) {
      memcardIoPort_00007500.get(joypadIndex).set(port);
      memcardIoAddress_00007550.get(joypadIndex).set(0);
      memcardIoCallback_00007528.get(joypadIndex).set(MEMORY.ref(4, getMethodAddress(Kernel.class, "memcardInfoIoCallback"), SupplierRef::new));
      memcardIoSector_00007508.get(joypadIndex).set(0);
      memcardStateBitset_00007568.get(joypadIndex).set(0x8);
    }

    return true;
  }

  @Method(0x61c4L)
  public static long OutdatedPadGetButtons_Impl_B16() {
    assert false;
    return 0;
  }

  @Method(0x6380L)
  public static void memcardIoFinished() {
    memcardIoFinished_000075c0.setu(0x1L);
  }

  @Method(0x6390L)
  public static void FUN_00006390(final long memcardIoAddress, final UnsignedByteRef memcardStateBitset, final UnsignedIntRef a2) {
    _000072f0.setu(0);
    memcardIoFinished_000075c0.setu(0);
    memcardIoAddress_000075c4.setu(memcardIoAddress);
    memcardStateBitset_000075c8.set(memcardStateBitset);
    _000075cc.set(a2);
  }

  /**
   * Injects card handling code into the exception handler
   */
  @Method(0x63bcL)
  public static void installEarlyCardIrqHandler() {
    memcardIoFinished_000075c0.setu(0);

    //LAB_000063dc
    for(int i = 0; i < 0x10; i += 4) {
      _00000cf0.offset(i).setu(_00006408.offset(i));
    }

    MEMORY.ref(4, 0xc80L, SupplierRef::new).set(() -> {
      if(early_card_irq_vector.run()) {
        return 1;
      }

      return ExceptionHandler_Impl_C06();
    });

    FlushCache();
  }

  @Method(0x63f8L)
  public static long isMemcardIoFinished() {
    return memcardIoFinished_000075c0.get();
  }

  private static final SupplierRef<Boolean> early_card_irq_vector = MEMORY.ref(4, 0x0000641cL, SupplierRef::new);

  @Method(0x641cL)
  public static boolean early_card_irq_vector() {
    if(memcardIoFinished_000075c0.get() == 0 || I_STAT.get(0x80L) == 0 || I_MASK.get(0x80L) == 0) {
      return false;
    }

    final long memcardState = memcardStateBitset_000075c8.deref().get();
    if(memcardState == 0x4L) { // Write
      JOY_MCD_DATA.get(); // Intentional read to nowhere

      final long data = memcardIoAddress_000075c4.deref(1).get();
      memcardIoAddress_000075c4.addu(0x1L);
      JOY_MCD_DATA.setu(data);
      JOY_MCD_CTRL.oru(0x10L);

      I_STAT.setu(0xffff_ff7fL);

      _000075cc.deref().xor(data);
      _000072f0.addu(0x1L);

      if(_000072f0.get() >= 0x80L) {
        memcardIoFinished_000075c0.setu(0);
      }

      //LAB_000064ec;
    } else if(memcardState == 0x2L) { // Read
      final long data = JOY_MCD_DATA.get();
      memcardIoAddress_000075c4.deref(1).setu(data);
      memcardIoAddress_000075c4.addu(0x1L);
      JOY_MCD_DATA.setu(0);
      JOY_MCD_CTRL.oru(0x10L);

      I_STAT.setu(0xffff_ff7fL);

      _000075cc.deref().xor(data);
      _000072f0.addu(0x1L);

      if(_000072f0.get() >= 0x7fL) {
        memcardIoFinished_000075c0.setu(0);
      }

      //LAB_0000658c
    } else {
      //LAB_00006594
      return false;
    }

    //LAB_0000659c
//    final long epc = ProcessControlBlockPtr_a0000108.deref().threadControlBlockPtr.deref().cop0r14Epc.get();
//    MEMORY.ref(4, epc).call();

    CPU.RFE();
    return true;
  }

  @Method(0x6a70L)
  public static void FlushCache() {
    functionVectorA(0x44L);
  }

  @Method(0x6a80L)
  public static int SystemErrorUnresolvedException() {
    return (int)functionVectorA(0x40L);
  }

  @Method(0x6a90)
  public static char toupper(final char c) {
    return (char)functionVectorA(0x25L, c);
  }

  @Method(0x6aa0L)
  public static void AddCdromDevice() {
    functionVectorA(0x96L);
  }

  @Method(0x6ab0L)
  public static void AddMemCardDevice() {
    functionVectorA(0x97L);
  }

  @Method(0x6ac0L)
  public static void AddDummyTtyDevice() {
    functionVectorA(0x99L);
  }

  @Method(0x6ad0L)
  public static void AddDuartTtyDevice() {
    functionVectorA(0x98L);
  }

  @Method(0x6af0L)
  public static void ioabort_raw(final int param) {
    functionVectorA(0xb2L, param);
  }

  @Method(0x6b10L)
  public static int strcmp(final String str1, final String str2) {
    return (int)functionVectorA(0x17L, str1, str2);
  }

  @Method(0x6b20L)
  public static long memcpy(final long dst, final long src, final int len) {
    return (long)functionVectorA(0x2aL, dst, src, len);
  }

  @Method(0x6b30L)
  public static void bu_callback_okay() {
    functionVectorA(0xa7L);
  }

  @Method(0x6b40L)
  public static void bu_callback_err_write() {
    functionVectorA(0xa8L);
  }

  @Method(0x6b50L)
  public static void bu_callback_err_busy() {
    functionVectorA(0xa9L);
  }

  @Method(0x6b60L)
  public static void bu_callback_err_eject() {
    functionVectorA(0xaaL);
  }

  @Method(0x6b70L)
  public static void bu_callback_err_prev_write() {
    functionVectorA(0xaeL);
  }

  @Method(0x6b80L)
  public static boolean EnterCriticalSection() {
    CPU.SYSCALL(1);

    // The exception handler stores v0 (return value) here
    GATE.acquire();
    final boolean ret = ProcessControlBlockPtr_a0000108.deref().threadControlBlockPtr.deref().registers.get(1).get() != 0;
    GATE.release();
    return ret;
  }

  @Method(0x6b90L)
  public static void ExitCriticalSection() {
    CPU.SYSCALL(2);
  }
}
