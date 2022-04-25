package legend.core;

import legend.core.cdrom.CdDrive;
import legend.core.dma.DmaManager;
import legend.core.gpu.Gpu;
import legend.core.input.Controller;
import legend.core.input.DigitalController;
import legend.core.input.Joypad;
import legend.core.input.MemoryCard;
import legend.core.kernel.Bios;
import legend.core.mdec.Mdec;
import legend.core.memory.EntryPoint;
import legend.core.memory.Memory;
import legend.core.memory.segments.ExpansionRegion1Segment;
import legend.core.memory.segments.ExpansionRegion2Segment;
import legend.core.memory.segments.IntSegment;
import legend.core.memory.segments.InvalidSegment;
import legend.core.memory.segments.MemoryControl1Segment;
import legend.core.memory.segments.MemoryControl2Segment;
import legend.core.memory.segments.PrivilegeGate;
import legend.core.memory.segments.RamSegment;
import legend.core.memory.segments.RomSegment;
import legend.core.memory.types.RunnableRef;
import legend.core.spu.Spu;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections8.Reflections;
import org.reflections8.util.ClasspathHelper;

import javax.annotation.Nullable;
import java.nio.file.Paths;
import java.util.Set;

public final class Hardware {
  private Hardware() { }

  private static final Logger LOGGER = LogManager.getFormatterLogger(Hardware.class);

  public static final Memory MEMORY = new Memory();
  public static final PrivilegeGate GATE = new PrivilegeGate();

  public static final Cpu CPU = new Cpu();
  public static final InterruptController INTERRUPTS;
  public static final DmaManager DMA;
  public static final Gpu GPU;
  public static final Mdec MDEC;
  public static final Timers TIMERS;
  public static final CdDrive CDROM;
  public static final Spu SPU;
  public static final MemoryCard MEMCARD;
  public static final Controller CONTROLLER;
  public static final Joypad JOYPAD;

  public static final Thread codeThread;
  public static final Thread hardwareThread;
  public static final Thread gpuThread;
  public static final Thread timerThread;
  public static final Thread spuThread;
  public static final Thread joyThread;

  @Nullable
  public static final Class<?> ENTRY_POINT;

  static {
    // --- BIOS memory ------------------------

    // 0x00 (0x10) - garbage area
    // 0x10 (0x30) - unused/reserved
    // 0x40 (0x20) - debug-break vector
    MEMORY.addSegment(GATE.wrap(new InvalidSegment(0x0000L, 0x60)));

    // 0x60 (0x4) - RAM size
    MEMORY.addSegment(GATE.wrap(new IntSegment(0x0060L)));

    // 0x64 (0x4) - Unknown (0x00)
    MEMORY.addSegment(GATE.wrap(new IntSegment(0x0064L)));
    // 0x68 (0x4) - Unknown (0xff)
    MEMORY.addSegment(GATE.wrap(new IntSegment(0x0068L)));

    // 0x6c (0x14) - Unused/reserved
    MEMORY.addSegment(GATE.wrap(new InvalidSegment(0x006cL, 0x14)));

    // 0x80 (0x10) - Exception vector
    MEMORY.addSegment(GATE.wrap(new RamSegment(0x80L, 0x10)));

    // 0x90 (0x10) - Unused/reserved
    MEMORY.addSegment(GATE.wrap(new InvalidSegment(0x0090L, 0x10)));

    // 0xa0 (0x10) - A(nn) function vector
    MEMORY.addSegment(GATE.readonly(new RamSegment(0xa0L, 0x10)));
    // 0xb0 (0x10) - B(nn) function vector
    MEMORY.addSegment(GATE.readonly(new RamSegment(0xb0L, 0x10)));
    // 0xc0 (0x10) - C(nn) function vector
    MEMORY.addSegment(GATE.readonly(new RamSegment(0xc0L, 0x10)));

    // 0xd0 (0x30) - Unused/reserved
    MEMORY.addSegment(GATE.wrap(new InvalidSegment(0x00d0L, 0x30)));

    // 0x100 (0x58) - Table of tables
    MEMORY.addSegment(GATE.wrap(new RamSegment(0x100L, 0x58)));

    // 0x158 (0x28) - Unused/reserved
    MEMORY.addSegment(GATE.wrap(new InvalidSegment(0x158L, 0x28)));

    // 0x180 (0x80) - Command line argument
    MEMORY.addSegment(GATE.wrap(new RamSegment(0x180L, 0x80)));

    // 0x200 (0x300) - A(nn) jump table
    MEMORY.addSegment(GATE.wrap(new RamSegment(0x200L, 0x300)));

    // 0x500 (0xbb00) - Kernel code/data - relocated from ROM
    MEMORY.addSegment(GATE.wrap(new RamSegment(0x500L, 0xbb00)));

    // 0xc000 (0x1f80) - Unused/reserved
    MEMORY.addSegment(GATE.wrap(new RamSegment(0xc000L, 0x1f80)));

    // 0xdf80 (0x80) - BIOS patches
    //TODO

    // 0xdffc (0x4) - Response value from intro/boot menu
    MEMORY.addSegment(GATE.wrap(new IntSegment(0xdffcL)));

    // 0xe000 (0x2000) - Kernel memory (ExCBs, EvCBs, TCBs)
    MEMORY.addSegment(GATE.wrap(new RamSegment(0xe000L, 0x2000)));

    // --- User memory ------------------------

    MEMORY.addSegment(new RamSegment(0x0001_0000L, 0x1f_0000));
    MEMORY.addSegment(new RamSegment(0x1f80_0000L, 0x400));

    // --- Bios ROM ---------------------------

    MEMORY.addSegment(GATE.wrap(RomSegment.fromFile(0x1fc0_0000L, 0x8_0000, Paths.get("bios.rom"))));

    GATE.acquire();
    MEMORY.addFunctions(Bios.class);
    GATE.release();

    // --- IO ports ---------------------------

    MEMORY.addSegment(new ExpansionRegion1Segment(0x1f00_0000L));
    MEMORY.addSegment(new MemoryControl1Segment(0x1f80_1000L));
    MEMORY.addSegment(new MemoryControl2Segment(0x1f80_1060L));
    MEMORY.addSegment(new ExpansionRegion2Segment(0x1f80_2000L));

    INTERRUPTS = new InterruptController(MEMORY);
    DMA = new DmaManager(MEMORY);
    GPU = new Gpu(MEMORY);
    MDEC = new Mdec(MEMORY);
    TIMERS = new Timers(MEMORY);
    CDROM = new CdDrive(MEMORY);
    SPU = new Spu(MEMORY);
    MEMCARD = new MemoryCard();
    CONTROLLER = new DigitalController();
    JOYPAD = new Joypad(MEMORY, CONTROLLER, MEMCARD);

    codeThread = new Thread(Hardware::run);
    codeThread.setName("Code");
    hardwareThread = Thread.currentThread();
    hardwareThread.setName("Hardware");
    gpuThread = new Thread(GPU);
    gpuThread.setName("GPU");
    timerThread = new Thread(TIMERS);
    timerThread.setName("Timers");
    spuThread = new Thread(SPU);
    spuThread.setName("SPU");
    joyThread = new Thread(JOYPAD);
    joyThread.setName("Joypad");

    LOGGER.info("Scanning for entry point class...");
    final Reflections reflections = new Reflections(ClasspathHelper.forClassLoader());
    final Set<Class<?>> entryPoints = reflections.getTypesAnnotatedWith(EntryPoint.class);
    if(entryPoints.size() > 1) {
      throw new IllegalStateException("Multiple classes marked as entry points were found!");
    }

    if(entryPoints.isEmpty()) {
      LOGGER.warn("No entry point found - launch will fail once bootstrapping is complete!");
      ENTRY_POINT = null;
    } else {
      ENTRY_POINT = entryPoints.iterator().next();
      LOGGER.info("Found entry point %s", ENTRY_POINT);
    }
  }

  public static void start() {
    codeThread.start();
    gpuThread.start();
    timerThread.start();
    spuThread.start();
    joyThread.start();

    boolean running = true;
    while(running) {
      if(CDROM.tick(100)) {
        INTERRUPTS.set(InterruptType.CDROM);
      }

      if(DMA.tick()) {
        INTERRUPTS.set(InterruptType.DMA);
      }

      TIMERS.syncGPU(GPU.getBlanksAndDot());

      CPU.tick();

      DebugHelper.sleep(0);
      if(!codeThread.isAlive() || !gpuThread.isAlive() || !timerThread.isAlive() || !spuThread.isAlive() || !joyThread.isAlive()) {
        running = false;
        TIMERS.stop();
        SPU.stop();
        JOYPAD.stop();
      }
    }
  }

  private static void run() {
    LOGGER.info("--- Legend start ---");

    GATE.acquire();
    MEMORY.ref(4, 0xbfc0_0000L).cast(RunnableRef::new).run();
  }

  public static boolean isGpuThread() {
    return Thread.currentThread() == gpuThread;
  }
}
