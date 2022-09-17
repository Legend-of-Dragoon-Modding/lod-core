package legend.core.kernel;

import legend.core.memory.Method;
import legend.core.memory.Ref;
import legend.core.memory.Value;
import legend.core.memory.types.ArrayRef;
import legend.core.memory.types.BiFunctionRef;
import legend.core.memory.types.BoolRef;
import legend.core.memory.types.DIRENTRY;
import legend.core.memory.types.Pointer;
import legend.core.memory.types.ProcessControlBlock;
import legend.core.memory.types.RunnableRef;
import legend.core.memory.types.SupplierRef;
import legend.core.memory.types.ThreadControlBlock;
import legend.core.memory.types.TriFunctionRef;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;

import static legend.core.Hardware.CPU;
import static legend.core.Hardware.GATE;
import static legend.core.Hardware.MEMORY;
import static legend.core.InterruptController.I_MASK;
import static legend.core.InterruptController.I_STAT;
import static legend.core.Timers.TMR_DOTCLOCK_MAX;
import static legend.core.Timers.TMR_DOTCLOCK_MODE;
import static legend.core.Timers.TMR_DOTCLOCK_VAL;
import static legend.core.Timers.TMR_HRETRACE_MAX;
import static legend.core.Timers.TMR_HRETRACE_MODE;
import static legend.core.Timers.TMR_HRETRACE_VAL;
import static legend.core.Timers.TMR_SYSCLOCK_MAX;
import static legend.core.Timers.TMR_SYSCLOCK_MODE;
import static legend.core.Timers.TMR_SYSCLOCK_VAL;
import static legend.core.kernel.Bios.AddCdromDevice_Impl_A96;
import static legend.core.kernel.Bios.EventControlBlockAddr_a0000120;
import static legend.core.kernel.Bios.EventControlBlockSize_a0000124;
import static legend.core.kernel.Bios.ExceptionChainPtr_a0000100;
import static legend.core.kernel.Bios.ProcessControlBlockPtr_a0000108;
import static legend.core.kernel.Bios.SystemErrorUnresolvedException_Impl_A40;
import static legend.core.kernel.Bios.memcpy_Impl_A2a;
import static legend.core.kernel.Bios.strcmp_Impl_A17;
import static legend.core.kernel.Bios.toupper_Impl_A25;

public final class Kernel {
  private Kernel() { }

  private static final Logger LOGGER = LogManager.getFormatterLogger(Kernel.class);

  private static final SupplierRef<Integer> exceptionVector_00000080 = MEMORY.ref(4, 0x00000080L, SupplierRef::new);

  private static final Pointer<ArrayRef<Pointer<PriorityChainEntry>>> exceptionChainAddr_00000100 = MEMORY.ref(4, 0x00000100L, Pointer.of(4, ArrayRef.of(Pointer.classFor(PriorityChainEntry.class), 4, 4, 8, Pointer.of(4, PriorityChainEntry::new))));

  private static final jmp_buf DefaultExceptionExitStruct_00006cf4 = MEMORY.ref(4, 0x00006cf4L, jmp_buf::new);

  private static final Value systemMemoryInitialized_00006d30 = MEMORY.ref(4, 0x00006d30L);

  private static final ArrayRef<PriorityChainEntry> _00006d58 = MEMORY.ref(1, 0x00006d58L, ArrayRef.of(PriorityChainEntry.class, 4, 16, PriorityChainEntry::new));

  private static final PriorityChainEntry DefaultInterruptPriorityChainStruct_00006d98 = MEMORY.ref(1, 0x00006d98L, PriorityChainEntry::new);

  private static final PriorityChainEntry SyscallHandlerStruct_00006da8 = MEMORY.ref(1, 0x00006da8L, PriorityChainEntry::new);

  private static final Value DeviceControlBlockBaseAddr_00006ee0 = MEMORY.ref(1, 0x00006ee0L);

  private static final Value _00007200 = MEMORY.ref(4, 0x00007200L);

  private static final Value systemMemoryAddr_00007460 = MEMORY.ref(4, 0x00007460L);
  private static final Value systemMemorySize_00007464 = MEMORY.ref(4, 0x00007464L);
  private static final Value systemMemoryEnd_00007468 = MEMORY.ref(4, 0x00007468L);

  private static final Value _00007480 = MEMORY.ref(4, 0x00007480L);

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
  private static final Value FileControlBlockBaseAddr_00008648 = MEMORY.ref(1, 0x00008648L);

  private static final Value _0000890c = MEMORY.ref(4, 0x0000890cL);

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

    exceptionVector_00000080.set(Kernel::exceptionVector);
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
    SystemErrorUnresolvedException();
    return 0;
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
    FileControlBlockAddr_a0000140.setu(FileControlBlockBaseAddr_00008648.getAddress());
    FileControlBlockSize_a0000144.setu(704L);
    DeviceControlBlockAddr_a0000150.setu(DeviceControlBlockBaseAddr_00006ee0.getAddress());
    DeviceControlBlockSize_a0000154.setu(_00007200.get() * 0x50L);

    _00007480.setu(0);

    AddCdromDevice();
  }

  @Method(0x27a0L)
  public static void FUN_000027a0() {
    _0000863c.setu(0);
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
    final long v0 = getFcb(fd);
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
    final long fcb = getFcb(fd);
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

  @Method(0x2c94L)
  public static int FileWrite_Impl_B35(final int fd, final long src, final int length) {
    final long fcb = getFcb(fd);
    if(fcb == 0 || MEMORY.ref(4, fcb).offset(0x0L).get() == 0) {
      //LAB_2cc0
      _00008640.setu(0x9L);
      return -1;
    }

    //LAB_2cd4
    tty_cdevscan_Impl_C16();

    final long t0 = MEMORY.ref(4, fcb).offset(0x1cL).get();

    final int v0;
    if((MEMORY.ref(4, fcb).offset(0x14L).get() & 0x10L) == 0) {
      //LAB_2d24
      MEMORY.ref(4, fcb).offset(0x8L).setu(src);
      MEMORY.ref(4, fcb).offset(0xcL).setu(length);

      if((MEMORY.ref(4, t0).offset(0x4L).get() & 0x4L) != 0) {
        //LAB_2d6c
        if(MEMORY.ref(4, fcb).offset(0x10L).get() % MEMORY.ref(4, t0).offset(0x8L).get() != 0) {
          throw new RuntimeException("Offset not on block boundary");
        }

        //LAB_2d88
        MEMORY.ref(4, fcb).offset(0xcL).setu(MEMORY.ref(4, fcb).offset(0xcL).get() / MEMORY.ref(4, t0).offset(0x8L).get());
      }

      //LAB_2da8
      v0 = (int)MEMORY.ref(4, t0).offset(0x18L).deref(4).call(fcb, 0x2L);

      if(v0 > 0) {
        MEMORY.ref(4, fcb).offset(0x10L).addu(v0);
      }
    } else {
      v0 = (int)MEMORY.ref(4, t0).offset(0x28L).deref(4).call(fcb, src, length);
    }

    //LAB_2dd8
    if(v0 < 0) {
      _00008640.setu(MEMORY.ref(4, fcb).offset(0x18L).get());
    }

    //LAB_2df0
    return v0;
  }

  @Method(0x2e00L)
  public static int FileClose_Impl_B36(final int fd) {
    final long v0 = getFcb(fd);
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
    final long v0 = getFcb(fd);
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
    } while(fcb < 0x8908L);

    throw new RuntimeException("Out of file descriptors");
  }

  @Method(0x30c8L)
  public static long getFcb(final int fd) {
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

  @Method(0x6a80L)
  public static void SystemErrorUnresolvedException() {
    SystemErrorUnresolvedException_Impl_A40();
  }

  @Method(0x6a90)
  public static char toupper(final char c) {
    return toupper_Impl_A25(c);
  }

  @Method(0x6aa0L)
  public static void AddCdromDevice() {
    AddCdromDevice_Impl_A96();
  }

  @Method(0x6b10L)
  public static int strcmp(final String str1, final String str2) {
    return strcmp_Impl_A17(str1, str2);
  }

  @Method(0x6b20L)
  public static long memcpy(final long dst, final long src, final int len) {
    return memcpy_Impl_A2a(dst, src, len);
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
