package legend.core.gpu;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import legend.core.Config;
import legend.core.InterruptType;
import legend.core.MathHelper;
import legend.core.Timers;
import legend.core.dma.DmaChannel;
import legend.core.memory.IllegalAddressException;
import legend.core.memory.Memory;
import legend.core.memory.MisalignedAccessException;
import legend.core.memory.Segment;
import legend.core.memory.Value;
import legend.core.opengl.Camera;
import legend.core.opengl.Context;
import legend.core.opengl.Mesh;
import legend.core.opengl.Shader;
import legend.core.opengl.Texture;
import legend.core.opengl.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;

import static legend.core.Hardware.DMA;
import static legend.core.Hardware.INTERRUPTS;
import static legend.core.Hardware.MEMORY;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLE_STRIP;

public class Gpu implements Runnable {
  private static final Logger LOGGER = LogManager.getFormatterLogger(Gpu.class);

  public static final Value GPU_REG0 = MEMORY.ref(4, 0x1f801810L);
  public static final Value GPU_REG1 = MEMORY.ref(4, 0x1f801814L);

  private static final int VRAM_WIDTH = 1024;
  private static final int VRAM_HEIGHT = 512;

  private static final int[] dotClockDiv = { 10, 8, 5, 4, 7 };

  private Camera camera;
  private Window window;
  private Context ctx;
  private Shader.UniformBuffer transforms2;
  private final Matrix4f transforms = new Matrix4f();

  private final long[] vram24 = new long[VRAM_WIDTH * VRAM_HEIGHT];
  private final long[] vram15 = new long[VRAM_WIDTH * VRAM_HEIGHT];

  private Shader vramShader;
  private Texture vramTexture;

  public final Status status = new Status();
  private int gpuInfo;

  public final DmaChannel dma = DMA.gpu;
  private final DmaChannel dmaOtc = DMA.otc;
  private long dmaOtcAddress;
  private int dmaOtcCount;

  private final Queue<Runnable> commandQueue = new ConcurrentLinkedQueue<>();
  private int displayStartX;
  private int displayStartY;
  private int displayRangeX1;
  private int displayRangeX2;
  private int displayRangeY1;
  private int displayRangeY2;
  private final RECT drawingArea = new RECT();
  private short offsetX;
  private short offsetY;
  private boolean texturedRectXFlip;
  private boolean texturedRectYFlip;
  private int textureWindowMaskX;
  private int textureWindowMaskY;
  private int textureWindowOffsetX;
  private int textureWindowOffsetY;
  private int preMaskX;
  private int preMaskY;
  private int postMaskX;
  private int postMaskY;

  @Nullable
  private Gp0CommandBuffer currentCommand;

  private int videoCycles;
  private final int horizontalTiming = 3413;
  private final int verticalTiming = 263;
  private int scanLine;
  private boolean isOddLine;

  public Gpu(final Memory memory) {
    memory.addSegment(new GpuSegment(0x1f80_1810L));
  }

  public void command00Nop() {
    LOGGER.trace("GPU NOP");
  }

  public void command01ClearCache() {
    // NOOP - we don't do caching
  }

  public void commandA0CopyRectFromCpuToVram(final RECT rect, final long address) {
    assert address != 0;

    assert rect.x.get() + rect.w.get() <= this.vramTexture.width  : "Rect right (" + (rect.x.get() + rect.w.get()) + ") overflows VRAM width (" + this.vramTexture.width + ')';
    assert rect.y.get() + rect.h.get() <= this.vramTexture.height : "Rect bottom (" + (rect.y.get() + rect.h.get()) + ") overflows VRAM height (" + this.vramTexture.height + ')';

    this.commandQueue.add(() -> {
      LOGGER.info("Copying %s from CPU to VRAM (address: %08x)", rect, address);

      final int offset = rect.y.get() * VRAM_WIDTH + rect.x.get();

      MEMORY.waitForLock(() -> {
        int i = 0;
        for(int y = 0; y < rect.h.get(); y++) {
          for(int x = 0; x < rect.w.get(); x++) {
            final long packed = MEMORY.ref(2, address).offset(i * 2L).get();
            final int index = offset + y * VRAM_WIDTH + x;
            this.vram24[index] = MathHelper.colour15To24(packed);
            this.vram15[index] = packed;
            i++;
          }
        }
      });
    });
  }

  public void commandC0CopyRectFromVramToCpu(final RECT rect, final long address) {
    assert address != 0;

    assert rect.x.get() + rect.w.get() <= this.vramTexture.width  : "Rect right (" + (rect.x.get() + rect.w.get()) + ") overflows VRAM width (" + this.vramTexture.width + ')';
    assert rect.y.get() + rect.h.get() <= this.vramTexture.height : "Rect bottom (" + (rect.y.get() + rect.h.get()) + ") overflows VRAM height (" + this.vramTexture.height + ')';

    this.commandQueue.add(() -> {
      LOGGER.info("Copying %s from VRAM to CPU (address: %08x)", rect, address);

      final int offset = rect.y.get() * VRAM_WIDTH + rect.x.get();

      MEMORY.waitForLock(() -> {
        int i = 0;
        for(int y = 0; y < rect.h.get(); y++) {
          for(int x = 0; x < rect.w.get(); x++) {
            final int index = offset + y * VRAM_WIDTH + x;
            MEMORY.ref(2, address).offset(i * 2L).setu(this.vram15[index]);
            i++;
          }
        }
      });
    });
  }

  public void uploadLinkedList(final long address) {
    MEMORY.waitForLock(() -> {
      Value value;
      long a = address;

      do {
        value = MEMORY.ref(4, a);

        final long words = value.get(0xff00_0000L) >>> 24;

        for(int i = 1; i <= words; i++) {
          this.queueGp0Command(value.offset(i * 4L).get());
        }

        a = a & 0xff00_0000 | value.get(0xff_ffffL);
      } while(value.get(0xff_ffffL) != 0xff_ffffL);
    });

    LOGGER.trace("GPU linked list uploaded");
  }

  private void queueGp0Command(final long command) {
    if(this.currentCommand == null) {
      this.currentCommand = new Gp0CommandBuffer(command);
    } else {
      this.currentCommand.queueValue(command);
    }

    if(this.currentCommand.isComplete()) {
      this.commandQueue.add(this.currentCommand.command.factory.apply(this.currentCommand.buffer, this));
      this.currentCommand = null;
    }
  }

  private void processGp0Command() {
    this.commandQueue.remove().run();
  }

  /**
   * GP1(01h) - Reset Command Buffer
   */
  private void resetCommandBuffer() {
    this.commandQueue.clear();
  }

  /**
   * GP1(02h) - Acknowledge GPU Interrupt (IRQ1)
   */
  private void acknowledgeGpuInterrupt() {
    this.status.interruptRequest = false;
  }

  /**
   * GP1(03h) - Display Enable
   */
  private void enableDisplay() {
    this.status.displayEnable = true;
  }

  /**
   * GP1(03h) - Display Enable
   */
  private void disableDisplay() {
    this.status.displayEnable = false;
  }

  /**
   * GP1(04h) - DMA Direction / Data Request
   */
  private void dmaDirection(final DMA_DIRECTION direction) {
    this.status.dmaDirection = direction;
  }

  /**
   * GP1(05h) - Start of Display Area in VRAM
   *
   * Upper/left Display source address in VRAM. The size and target position on screen is set via Display Range registers; target=X1,Y2; size=(X2-X1/cycles_per_pix), (Y2-Y1).
   */
  private void displayStart(final int x, final int y) {
    this.displayStartX = x;
    this.displayStartY = y;
  }

  /**
   * GP1(06h) - Horizontal Display Range (on Screen)
   *
   * Specifies the horizontal range within which the display area is displayed. For resolutions other than 320 pixels it may be necessary to fine adjust the value to obtain an exact match (eg. X2=X1+pixels*cycles_per_pix).
   * The number of displayed pixels per line is "(((X2-X1)/cycles_per_pix)+2) AND NOT 3" (ie. the hardware is rounding the width up/down to a multiple of 4 pixels).
   * Most games are using a width equal to the horizontal resolution (ie. 256, 320, 368, 512, 640 pixels). A few games are using slightly smaller widths (probably due to programming bugs). Pandemonium 2 is using a bigger "overscan" width (ensuring an intact picture without borders even on mis-calibrated TV sets).
   * The 260h value is the first visible pixel on normal TV Sets, this value is used by MOST NTSC games, and SOME PAL games (see below notes on Mis-Centered PAL games).
   */
  private void horizontalDisplayRange(final int x1, final int x2) {
    this.displayRangeX1 = x1;
    this.displayRangeX2 = x2;
  }

  /**
   * GP1(07h) - Vertical Display Range (on Screen)
   *
   * Specifies the vertical range within which the display area is displayed. The number of lines is Y2-Y1 (unlike as for the width, there's no rounding applied to the height). If Y2 is set to a much too large value, then the hardware stops to generate vblank interrupts (IRQ0).
   * The 88h/A3h values are the middle-scanlines on normal TV Sets, these values are used by MOST NTSC games, and SOME PAL games (see below notes on Mis-Centered PAL games).
   * The 224/264 values are for fullscreen pictures. Many NTSC games display 240 lines (overscan with hidden lines). Many PAL games display only 256 lines (underscan with black borders).
   */
  private void verticalDisplayRange(final int y1, final int y2) {
    this.displayRangeY1 = y1;
    this.displayRangeY2 = y2;
  }

  /**
   * GP1(08h) - Display Mode
   *
   * Note: Interlace must be enabled to see all lines in 480-lines mode (interlace is causing ugly flickering, so a non-interlaced low resolution image is typically having better quality than a high resolution interlaced image, a pretty bad example are the intro screens shown by the BIOS). The Display Area Color Depth does NOT affect the Drawing Area (the Drawing Area is always 15bit).
   * When the "Reverseflag" is set, the display scrolls down 2 lines or so, and colored regions are getting somehow hatched/distorted, but black and white regions are still looking okay. Don't know what that's good for? Probably relates to PAL/NTSC-Color Clock vs PSX-Dot Clock mismatches: Bit7=0 causes Flimmering errors (errors at different locations in each frame), and Bit7=1 causes Static errors (errors at same locations in all frames)?
   */
  private void displayMode(final HORIZONTAL_RESOLUTION_1 hRes1, final VERTICAL_RESOLUTION vRes, final VIDEO_MODE vMode, final DISPLAY_AREA_COLOUR_DEPTH dispColourDepth, final boolean interlace, final HORIZONTAL_RESOLUTION_2 hRes2) {
    this.status.horizontalResolution1 = hRes1;
    this.status.verticalResolution = vRes;
    this.status.videoMode = vMode;
    this.status.displayAreaColourDepth = dispColourDepth;
    this.status.verticalInterlace = interlace;
    this.status.horizontalResolution2 = hRes2;
    this.status.reverse = false;
  }

  private Shader loadShader(final Path vsh, final Path fsh) {
    final Shader shader;

    try {
      shader = new Shader(vsh, fsh);
    } catch(final IOException e) {
      throw new RuntimeException("Failed to load vram shader", e);
    }

    shader.bindUniformBlock("transforms", Shader.UniformBuffer.TRANSFORM);
    shader.bindUniformBlock("transforms2", Shader.UniformBuffer.TRANSFORM2);
    return shader;
  }

  public Timers.Sync getBlanksAndDot() { //test
    final int dot = dotClockDiv[this.status.horizontalResolution2 == HORIZONTAL_RESOLUTION_2._256_320_512_640 ? this.status.horizontalResolution1.ordinal() : 4];
    final boolean hBlank = this.videoCycles < this.displayRangeX1 || this.videoCycles > this.displayRangeX2;
    final boolean vBlank = this.scanLine < this.displayRangeY1 || this.scanLine > this.displayRangeY2;

    return new Timers.Sync(dot, hBlank, vBlank);
  }

  @Override
  public void run() {
    this.camera = new Camera(0.0f, 0.0f);
    this.window = new Window(Config.GAME_NAME, Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
    this.window.setFpsLimit(30);
    this.ctx = new Context(this.window, this.camera);

    final FloatBuffer transform2Buffer = BufferUtils.createFloatBuffer(4 * 4);
    this.transforms2 = new Shader.UniformBuffer((long)transform2Buffer.capacity() * Float.BYTES, Shader.UniformBuffer.TRANSFORM2);

    this.vramShader = this.loadShader(Paths.get("gfx", "shaders", "vram.vsh"), Paths.get("gfx", "shaders", "vram.fsh"));
    this.vramTexture = Texture.empty(1024, 512);

    final Mesh vramMesh = new Mesh(GL_TRIANGLE_STRIP, new float[] {
         0,   0, 0, 0,
         0, 512, 0, 1,
      1024,   0, 1, 0,
      1024, 512, 1, 1,
    }, 4);
    vramMesh.attribute(0, 0L, 2, 4);
    vramMesh.attribute(1, 2L, 2, 4);

    this.ctx.onDraw(() -> {
      INTERRUPTS.set(InterruptType.VBLANK);

      //Video clock is the cpu clock multiplied by 11/7.
      this.videoCycles += 100 * 11 / 7;

      if(this.videoCycles >= this.horizontalTiming) {
        this.videoCycles -= this.horizontalTiming;
        this.scanLine++;

        if(this.status.verticalResolution == VERTICAL_RESOLUTION._240) {
          this.isOddLine = (this.scanLine & 0x1) != 0;
        }

        if(this.scanLine >= this.verticalTiming) {
          this.scanLine = 0;

          if(this.status.verticalInterlace && this.status.verticalResolution == VERTICAL_RESOLUTION._480) {
            this.isOddLine = !this.isOddLine;
          }
        }
      }

      // Restore model buffer to identity
      this.transforms.identity();
      this.transforms2.set(this.transforms);

      //TODO most stuff here is deprecated, communicate with GPU directly rather than using DMA

      if(this.dma.channelControl.isBusy()) {
        switch(this.status.dmaDirection) {
          case OFF -> throw new RuntimeException("GPU DMA channel busy but no DMA direction");

          case CPU_TO_GP0 -> {
            switch(this.dma.channelControl.getMode()) {
              case SYNC_TO_DMA_REQUESTS -> {
                while(this.dma.getBlockCount() > 0) {
                  LOGGER.info("Transferring size %04x", this.dma.getBlockSize());

                  for(int n = 0; n < this.dma.getBlockSize() / 4; n++) {
                    this.queueGp0Command(this.dma.MADR.deref(4).offset(n * 4L).get());
                  }

                  while(!this.commandQueue.isEmpty()) {
                    this.processGp0Command();
                  }

                  this.dma.MADR.addu(this.dma.getBlockSize());
                  this.dma.decrementBlockCount();
                }

                LOGGER.info("GPU DMA transfer complete");
                this.dma.channelControl.resetBusy();
              }

              case LINKED_LIST -> {
                LOGGER.info("Linked list transfer");

                final Value value = this.dma.MADR.deref(4);
                this.dma.MADR.setu(0x8000_0000 | value.get(0xff_ffffL));

                final long words = value.get(0xff00_0000L) >> 24;

                for(int i = 1; i < words; i++) {
                  this.queueGp0Command(value.offset(i * 4L).get());
                }

                if(value.get(0xff_ffffL) == 0xff_ffffL) {
                  LOGGER.info("GPU DMA linked list transfer complete");
                  this.dma.channelControl.resetBusy();
                }
              }

              default -> throw new RuntimeException("Unsupported GPU DMA sync mode " + this.dma.channelControl.getMode());
            }
          }

          default -> throw new RuntimeException("Unsupported GPU DMA transfer direction " + this.status.dmaDirection);
        }
      }

      this.status.readyToReceiveCommand = true;

      if(this.dmaOtc.channelControl.getStartTrigger() == DmaChannel.ChannelControl.START_TRIGGER.MANUAL) {
        this.dmaOtcAddress = this.dmaOtc.MADR.get();
        this.dmaOtcCount = (int)this.dmaOtc.BCR.get();

        LOGGER.info("Starting OTC DMA transfer at %08x (count: %04x)", this.dmaOtcAddress, this.dmaOtcCount);
        this.dmaOtc.channelControl.resetStartTrigger();
      }

      if(this.dmaOtc.channelControl.isBusy()) {
        MEMORY.waitForLock(() -> {
          for(int i = 0; i < this.dmaOtcCount - 1; i++) {
            MEMORY.ref(4, this.dmaOtcAddress).offset(-i * 4L).setu(this.dmaOtcAddress - (i + 1) * 4L & 0xff_ffffL);
          }

          //TODO no$ docs seem to say this should be added here but this needs to be verified
          MEMORY.ref(4, this.dmaOtcAddress).offset(-this.dmaOtcCount * 4L).setu(0xff_ffffL);
        });

        LOGGER.info("OTC DMA transfer complete");
        this.dmaOtc.transferComplete();
        this.dmaOtc.channelControl.resetBusy();
      }

      final int size = VRAM_WIDTH * VRAM_HEIGHT;
      final ByteBuffer pixels = BufferUtils.createByteBuffer(size * 4);

      for(int i = 0; i < size; i++) {
        final long packed = this.vram24[i];

        pixels.put((byte)(packed        & 0xff));
        pixels.put((byte)(packed >>>  8 & 0xff));
        pixels.put((byte)(packed >>> 16 & 0xff));
        pixels.put((byte)(packed >>> 24 & 0xff));
      }

      pixels.flip();

      this.vramTexture.data(new RECT((short)0, (short)0, (short)VRAM_WIDTH, (short)VRAM_HEIGHT), pixels);

      this.vramShader.use();
      this.vramTexture.use();
      vramMesh.draw();

      while(!this.commandQueue.isEmpty()) {
        this.processGp0Command();
      }

      //TODO in 240-line vertical resolution mode, this changes per scanline. We don't do scanlines. Not sure of the implications.
      this.status.drawingLine = this.status.drawingLine.flip();
    });

    this.window.show();

    try {
      this.window.run();
    } catch(final Throwable t) {
      LOGGER.error("Shutting down due to GPU exception:", t);
      this.window.close();
    }
  }

  private static short signed11bit(final int n) {
    return (short)(n << 21 >> 21);
  }

  private int clipX(final int x) {
    if(x < this.drawingArea.x.get()) {
      return this.drawingArea.x.get() - x;
    }

    if(x > this.drawingArea.w.get()) {
      return this.drawingArea.w.get() - x;
    }

    return 0;
  }

  private int clipY(final int y) {
    if(y < this.drawingArea.y.get()) {
      return this.drawingArea.y.get() - y;
    }

    if(y > this.drawingArea.h.get()) {
      return this.drawingArea.h.get() - y;
    }

    return 0;
  }

  /**
   * Note: only handles textured
   */
  private static Runnable texturedPolygonRenderer(final LongList buffer, final Gpu gpu) {
    final long cmd = buffer.getLong(0);
    final int colour = (int)(cmd & 0xff_ffff);
    final long command = cmd >>> 24;
    final boolean isQuad = (command & 0b1000) != 0;
    final boolean isTranslucent = (command & 0b0010) != 0;
    final boolean isRaw = (command & 0b0001) != 0;

    final int[] x = new int[4];
    final int[] y = new int[4];
    final int[] tx = new int[4];
    final int[] ty = new int[4];

    final long vertex0 = buffer.getLong(1);
    y[0] = (short)(vertex0 >>> 16 & 0xffff);
    x[0] = (short)(vertex0        & 0xffff);

    final long tex0 = buffer.getLong(2);
    final int clut = (int)(tex0 >>> 16 & 0xffff);
    ty[0] = (int)(tex0 >>> 8 & 0xff);
    tx[0] = (int)(tex0       & 0xff);

    final long vertex1 = buffer.getLong(3);
    y[1] = (short)(vertex1 >>> 16 & 0xffff);
    x[1] = (short)(vertex1        & 0xffff);

    final long tex1 = buffer.getLong(4);
    final int page = (int)(tex1 >>> 16 & 0xffff);
    ty[1] = (int)(tex1 >>> 8 & 0xff);
    tx[1] = (int)(tex1       & 0xff);

    final long vertex2 = buffer.getLong(5);
    y[2] = (short)(vertex2 >>> 16 & 0xffff);
    x[2] = (short)(vertex2        & 0xffff);

    final long tex2 = buffer.getLong(6);
    ty[2] = (int)(tex2 >>> 8 & 0xff);
    tx[2] = (int)(tex2       & 0xff);

    if(isQuad) {
      final long vertex3 = buffer.getLong(7);
      y[3] = (short)(vertex3 >>> 16 & 0xffff);
      x[3] = (short)(vertex3        & 0xffff);

      final long tex3 = buffer.getLong(8);
      ty[3] = (int)(tex3 >>> 8 & 0xff);
      tx[3] = (int)(tex3       & 0xff);
    }

    final int clutX = (short)((clut & 0x3f) * 16);
    final int clutY = (short)(clut >>> 6 & 0x1ff);

    return () -> {
      LOGGER.trace("[GP0.%02x] Drawing textured four-point poly (opaque, blended) offset %d %d, XYUV0 %d %d %d %d, XYUV1 %d %d %d %d, XYUV2 %d %d %d %d, XYUV3 %d %d %d %d, Clut(XY) %04x (%d %d), Page %04x, RGB %06x", command, gpu.offsetX, gpu.offsetY, x[0], y[0], tx[0], ty[0], x[1], y[1], tx[1], ty[1], x[2], y[2], tx[2], ty[2], x[3], y[3], tx[3], ty[3], clut, clutX, clutY, page, colour);

      final int texturePageXBase = (page       & 0b1111) *  64;
      final int texturePageYBase = (page >>> 4 & 0b0001) * 256;
      final SEMI_TRANSPARENCY translucency = SEMI_TRANSPARENCY.values()[page >>> 5 & 0b11];
      final Bpp texturePageColours = Bpp.values()[page >>> 7 & 0b11];

      for(int i = 0; i < 4; i++) {
        final int vx = gpu.offsetX + x[i];
        final int vy = gpu.offsetY + y[i];

        final int clipXOffset = gpu.clipX(vx);
        final int clipYOffset = gpu.clipY(vy);

        x[i] = vx + clipXOffset;
        y[i] = vy + clipYOffset;
        tx[i] += clipXOffset;
        ty[i] += clipYOffset;
      }

      gpu.rasterizeTriangle(x[0], y[0], x[1], y[1], x[2], y[2], tx[0], ty[0], tx[1], ty[1], tx[2], ty[2], colour, colour, colour, clutX, clutY, texturePageXBase, texturePageYBase, texturePageColours, isTranslucent, isRaw, translucency);
      gpu.rasterizeTriangle(x[1], y[1], x[2], y[2], x[3], y[3], tx[1], ty[1], tx[2], ty[2], tx[3], ty[3], colour, colour, colour, clutX, clutY, texturePageXBase, texturePageYBase, texturePageColours, isTranslucent, isRaw, translucency);
    };
  }

  private Runnable untexturedRectangleBuilder(final long command, final long vertex, final long size) {
    final boolean isTranslucent = (command & 1 << 25) != 0;

    final long colour = command & 0xff_ffffL;

    final int vy = (short)((vertex & 0xffff0000) >>> 16);
    final int vx = (short)(vertex & 0xffff);

    final int vh = (short)((size & 0xffff0000) >>> 16);
    final int vw = (short)(size & 0xffff);

    return () -> {
      LOGGER.trace("[GP0.%02x] Drawing variable-sized untextured quad offset %d %d, XYWH %d %d %d %d RGB %06x", command >>> 24, this.offsetX, this.offsetY, vx, vy, vw, vh, colour);

      final int x1 = Math.max(vx + this.offsetX, this.drawingArea.x.get());
      final int y1 = Math.max(vy + this.offsetY, this.drawingArea.y.get());
      final int x2 = Math.min(vx + this.offsetX + vw, this.drawingArea.w.get());
      final int y2 = Math.min(vy + this.offsetY + vh, this.drawingArea.h.get());

      for(int y = y1; y < y2; y++) {
        for(int x = x1; x < x2; x++) {
          // Check background mask
          if(this.status.drawPixels == DRAW_PIXELS.NOT_TO_MASKED_AREAS) {
            if((this.getPixel(x & 0x3ff, y & 0x1ff) & 0xff00_0000L) != 0) {
              continue;
            }
          }

          final long texel;
          if(isTranslucent) {
            texel = this.handleTranslucence(x, y, colour, this.status.semiTransparency);
          } else {
            texel = colour;
          }

          this.setPixel(x, y, (this.status.setMaskBit ? 1 : 0) << 24 | texel);
        }
      }
    };
  }

  private Runnable texturedRectangleBuilder(final long command, final long vertex, final long tex, final long size) {
    final boolean isTranslucent = (command & 1 << 25) != 0;
    final boolean isRaw = (command & 1 << 24) != 0;

    final long colour = command & 0xff_ffffL;

    final int vy = (short)((vertex & 0xffff0000) >>> 16);
    final int vx = (short)(vertex & 0xffff);

    final int clut = (int)((tex & 0xffff0000) >>> 16);
    final int ty = (int)((tex & 0xff00) >>> 8);
    final int tx = (int)(tex & 0xff);

    final int vh = (short)((size & 0xffff0000) >>> 16);
    final int vw = (short)(size & 0xffff);

    final int clutX = (short)((clut & 0x3f) * 16);
    final int clutY = (short)(clut >>> 6 & 0x1ff);

    return () -> {
      LOGGER.trace("[GP0.%02x] Drawing variable-sized textured quad offset %d %d, texpage XY %d %d, XYWH %d %d %d %d, UV %d %d, Clut(XY) %04x (%d %d), RGB %06x", command >>> 24, this.offsetX, this.offsetY, this.status.texturePageXBase, this.status.texturePageYBase.value, vx, vy, vw, vh, tx, ty, clut, clutX, clutY, colour);

      final int x1 = Math.max(vx + this.offsetX, this.drawingArea.x.get());
      final int y1 = Math.max(vy + this.offsetY, this.drawingArea.y.get());
      final int x2 = Math.min(vx + this.offsetX + vw, this.drawingArea.w.get());
      final int y2 = Math.min(vy + this.offsetY + vh, this.drawingArea.h.get());

      final int offsetX = x1 - (vx + this.offsetX);
      final int offsetY = y1 - (vy + this.offsetY);

      final int u1 = tx + offsetX;
      final int v1 = ty + offsetY;

      for(int y = y1, v = v1; y < y2; y++, v++) {
        for(int x = x1, u = u1; x < x2; x++, u++) {
          // Check background mask
          if(this.status.drawPixels == DRAW_PIXELS.NOT_TO_MASKED_AREAS) {
            if((this.getPixel(x & 0x3ff, y & 0x1ff) & 0xff00_0000L) != 0) {
              continue;
            }
          }

          long texel = this.getTexel(this.maskTexelAxis(u, this.preMaskX, this.postMaskX), this.maskTexelAxis(v, this.preMaskY, this.postMaskY), clutX, clutY, this.status.texturePageXBase, this.status.texturePageYBase.value, this.status.texturePageColours);

          if(texel != 0) {
            if(!isRaw) {
              texel = this.applyBlending(colour, texel);
            }
          }

          if(isTranslucent && (texel & 0xff00_0000L) != 0) {
            texel = this.handleTranslucence(x, y, texel, this.status.semiTransparency);
          }

          this.setPixel(x, y, (this.status.setMaskBit ? 1 : 0) << 24 | texel);
        }
      }
    };
  }

  private void rasterizeTriangle(final int vx0, final int vy0, int vx1, int vy1, int vx2, int vy2, final int tu0, final int tv0, int tu1, int tv1, int tu2, int tv2, final int c0, int c1, int c2, final int clutX, final int clutY, final int textureBaseX, final int textureBaseY, final Bpp bpp, final boolean isTranslucent, final boolean isRaw, final SEMI_TRANSPARENCY translucencyMode) {
    int area = orient2d(vx0, vy0, vx1, vy1, vx2, vy2);
    if(area == 0) {
      return;
    }

    if(area < 0) {
      final int tempVX = vx1;
      final int tempVY = vy1;
      vx1 = vx2;
      vy1 = vy2;
      vx2 = tempVX;
      vy2 = tempVY;

      final int tempTU = tu1;
      final int tempTV = tv1;
      tu1 = tu2;
      tv1 = tv2;
      tu2 = tempTU;
      tv2 = tempTV;

      final int tempC = c1;
      c1 = c2;
      c2 = tempC;

      area = -area;
    }

    /*boundingBox*/
    int minX = Math.min(vx0, Math.min(vx1, vx2));
    int minY = Math.min(vy0, Math.min(vy1, vy2));
    int maxX = Math.max(vx0, Math.max(vx1, vx2));
    int maxY = Math.max(vy0, Math.max(vy1, vy2));

    if(maxX - minX > 1024 || maxY - minY > 512) return;

    /*clip*/
    minX = (short)Math.max(minX, this.drawingArea.x.get());
    minY = (short)Math.max(minY, this.drawingArea.y.get());
    maxX = (short)Math.min(maxX, this.drawingArea.w.get());
    maxY = (short)Math.min(maxY, this.drawingArea.h.get());

    final int A01 = vy0 - vy1;
    final int B01 = vx1 - vx0;
    final int A12 = vy1 - vy2;
    final int B12 = vx2 - vx1;
    final int A20 = vy2 - vy0;
    final int B20 = vx0 - vx2;

    final int bias0 = isTopLeft(vx1, vy1, vx2, vy2) ? 0 : -1;
    final int bias1 = isTopLeft(vx2, vy2, vx0, vy0) ? 0 : -1;
    final int bias2 = isTopLeft(vx0, vy0, vx1, vy1) ? 0 : -1;

    int w0_row = orient2d(vx1, vy1, vx2, vy2, minX, minY);
    int w1_row = orient2d(vx2, vy2, vx0, vy0, minX, minY);
    int w2_row = orient2d(vx0, vy0, vx1, vy1, minX, minY);

    // Rasterize
    for(int y = minY; y < maxY; y++) {
      // Barycentric coordinates at start of row
      int w0 = w0_row;
      int w1 = w1_row;
      int w2 = w2_row;

      for(int x = minX; x < maxX; x++) {
        // If p is on or inside all edges, render pixel.
        if((w0 + bias0 | w1 + bias1 | w2 + bias2) >= 0) {
          // Adjustments per triangle instead of per pixel can be done at area level
          // but it still does some little by 1 error appreciable on some textured quads
          // I assume it could be handled recalculating AXX and BXX offsets but those maths are beyond my scope

          //Check background mask
          if(this.status.drawPixels == DRAW_PIXELS.NOT_TO_MASKED_AREAS) {
            if((this.getPixel(x, y) & 0xff00_0000L) != 0) {
              w0 += A12;
              w1 += A20;
              w2 += A01;
              continue;
            }
          }

          // reset default color of the triangle calculated outside the for as it gets overwritten as follows...
          long colour = c0;

          final int texelX = interpolateCoords(w0, w1, w2, tu0, tu1, tu2, area);
          final int texelY = interpolateCoords(w0, w1, w2, tv0, tv1, tv2, area);
          long texel = this.getTexel(this.maskTexelAxis(texelX, this.preMaskX, this.postMaskX), this.maskTexelAxis(texelY, this.preMaskY, this.postMaskY), clutX, clutY, textureBaseX, textureBaseY, bpp);
          if(texel == 0) {
            w0 += A12;
            w1 += A20;
            w2 += A01;
            continue;
          }

          if(!isRaw) {
            texel = this.applyBlending(colour, texel);
          }

          colour = texel;

          if(isTranslucent && (colour & 0xff00_0000L) != 0) {
            colour = this.handleTranslucence(x, y, colour, translucencyMode);
          }

          colour |= (this.status.setMaskBit ? 1 : 0) << 24;

          this.setPixel(x, y, colour);
        }

        // One step to the right
        w0 += A12;
        w1 += A20;
        w2 += A01;
      }

      // One row step
      w0_row += B12;
      w1_row += B20;
      w2_row += B01;
    }
  }

  private long applyBlending(final long colour, final long texel) {
    return
      texel & 0xff00_0000 |
      (colour >>> 16 & 0xff) * (texel >>> 16 & 0xff) / 128 << 16 |
      (colour >>>  8 & 0xff) * (texel >>>  8 & 0xff) / 128 <<  8 |
      (colour        & 0xff) * (texel        & 0xff) / 128;
  }

  private long getPixel(final int x, final int y) {
    return this.vram24[y * VRAM_WIDTH + x];
  }

  private long getPixel15(final int x, final int y) {
    return this.vram15[y * VRAM_WIDTH + x];
  }

  private void setPixel(final int x, final int y, final long pixel) {
    this.vram24[y * VRAM_WIDTH + x] = pixel;
  }

  private static int interpolateCoords(final int w0, final int w1, final int w2, final int t0, final int t1, final int t2, final int area) {
    //https://codeplea.com/triangular-interpolation
    return (t0 * w0 + t1 * w1 + t2 * w2) / area;
  }

  private int maskTexelAxis(final int axis, final int preMaskAxis, final int postMaskAxis) {
    return axis & 0xff & preMaskAxis | postMaskAxis;
  }

  private static boolean isTopLeft(final int ax, final int ay, final int bx, final int by) {
    return ay == by && bx > ax || by < ay;
  }

  private long getTexel(final int x, final int y, final int clutX, final int clutY, final int textureBaseX, final int textureBaseY, final Bpp depth) {
    if(depth == Bpp.BITS_4) {
      return this.get4bppTexel(x, y, clutX, clutY, textureBaseX, textureBaseY);
    }

    if(depth == Bpp.BITS_8) {
      return this.get8bppTexel(x, y, clutX, clutY, textureBaseX, textureBaseY);
    }

    return this.get16bppTexel(x, y, textureBaseX, textureBaseY);
  }

  private long get4bppTexel(final int x, final int y, final int clutX, final int clutY, final int textureBaseX, final int textureBaseY) {
    final long index = this.getPixel15(x / 4 + textureBaseX, y + textureBaseY);
    final int p = (int)(index >> (x & 3) * 4 & 0xF);
    return this.getPixel(clutX + p, clutY);
  }

  private long get8bppTexel(final int x, final int y, final int clutX, final int clutY, final int textureBaseX, final int textureBaseY) {
    final long index = this.getPixel15(x / 2 + textureBaseX, y + textureBaseY);
    final int p = (int)(index >> (x & 1) * 8 & 0xFF);
    return this.getPixel(clutX + p, clutY);
  }

  private long get16bppTexel(final int x, final int y, final int textureBaseX, final int textureBaseY) {
    return this.getPixel(x + textureBaseX, y + textureBaseY);
  }

  private static int orient2d(final int ax, final int ay, final int bx, final int by, final int cx, final int cy) {
    return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
  }

  private int handleTranslucence(final int x, final int y, final long texel, final SEMI_TRANSPARENCY mode) {
    final long pixel = this.getPixel(x, y);

    final int br = (int)(pixel        & 0xff);
    final int bg = (int)(pixel >>>  8 & 0xff);
    final int bb = (int)(pixel >>> 16 & 0xff);
    final int fr = (int)(texel        & 0xff);
    final int fg = (int)(texel >>>  8 & 0xff);
    final int fb = (int)(texel >>> 16 & 0xff);
    final int r;
    final int g;
    final int b;

    switch(mode) {
      case HALF_B_PLUS_HALF_F -> {
        r = (br + fr) / 2;
        g = (bg + fg) / 2;
        b = (bb + fb) / 2;
      }

      case B_PLUS_F -> {
        r = Math.min(0xff, br + fr);
        g = Math.min(0xff, bg + fg);
        b = Math.min(0xff, bb + fb);
      }

      case B_MINUS_F -> {
        r = Math.max(0, br - fr);
        g = Math.max(0, bg - fg);
        b = Math.max(0, bb - fb);
      }

      case B_PLUS_QUARTER_F -> {
        r = Math.min(0xff, br + fr / 4);
        g = Math.min(0xff, bg + fg / 4);
        b = Math.min(0xff, bb + fb / 4);
      }

      default -> throw new RuntimeException();
    }

    return b << 16 | g << 8 | r;
  }

  private int getShadedColor(final int w0, final int w1, final int w2, final int c0, final int c1, final int c2, final int area) {
    final int r = ((c0        & 0xff) * w0 + (c1        & 0xff) * w1 + (c2        & 0xff) * w2) / area;
    final int g = ((c0 >>>  8 & 0xff) * w0 + (c1 >>>  8 & 0xff) * w1 + (c2 >>>  8 & 0xff) * w2) / area;
    final int b = ((c0 >>> 16 & 0xff) * w0 + (c1 >>> 16 & 0xff) * w1 + (c2 >>> 16 & 0xff) * w2) / area;

    return r << 16 | g << 8 | b;
  }

  public enum GP0_COMMAND {
    NOOP(0x00, 1, (buffer, gpu) -> () -> LOGGER.trace("GPU NOOP")),

    FILL_RECTANGLE_IN_VRAM(0x02, 3, (buffer, gpu) -> {
      final long colour = buffer.getLong(0) & 0xff_ffffL;

      final long vertex = buffer.getLong(1);
      final int y = (short)((vertex & 0xffff0000) >>> 16);
      final int x = (short)(vertex & 0xffff);

      final long size = buffer.getLong(2);
      final int h = (short)((size & 0xffff0000) >>> 16);
      final int w = (short)(size & 0xffff);

      return () -> {
        LOGGER.trace("Fill rectangle in VRAM XYWH %d %d %d %d, RGB %06x", x, y, w, h, colour);

        for(int posY = y; posY < y + h; posY++) {
          for(int posX = x; posX < x + w; posX++) {
            gpu.setPixel(x, y, colour);
          }
        }
      };
    }),

    TEXTURED_FOUR_POINT_POLYGON_OPAQUE_BLENDED(0x2c, 9, Gpu::texturedPolygonRenderer),
    TEXTURED_FOUR_POINT_POLYGON_TRANSLUCENT_BLENDED(0x2e, 9, Gpu::texturedPolygonRenderer),

    MONO_RECT_VAR_SIZE_OPAQUE(0x60, 3, (buffer, gpu) -> {
      final long command = buffer.getLong(0);
      final long vertex = buffer.getLong(1);
      final long size = buffer.getLong(2);
      return gpu.untexturedRectangleBuilder(command, vertex, size);
    }),

    MONOCHROME_RECT_VAR_SIZE_TRANS(0x62, 3, (buffer, gpu) -> {
      final long command = buffer.getLong(0);
      final long vertex = buffer.getLong(1);
      final long size = buffer.getLong(2);
      return gpu.untexturedRectangleBuilder(command, vertex, size);
    }),

    TEX_RECT_VAR_SIZE_OPAQUE_BLENDED(0x64, 4, (buffer, gpu) -> {
      final long command = buffer.getLong(0);
      final long vertex = buffer.getLong(1);
      final long tex = buffer.getLong(2);
      final long size = buffer.getLong(3);
      return gpu.texturedRectangleBuilder(command, vertex, tex, size);
    }),

    TEX_RECT_VAR_SIZE_TRANSPARENT_BLENDED(0x66, 4, (buffer, gpu) -> {
      final long command = buffer.getLong(0);
      final long vertex = buffer.getLong(1);
      final long tex = buffer.getLong(2);
      final long size = buffer.getLong(3);
      return gpu.texturedRectangleBuilder(command, vertex, tex, size);
    }),

    TEX_RECT_VAR_SIZE_TRANSPARENT_RAW(0x67, 4, (buffer, gpu) -> {
      final long command = buffer.getLong(0);
      final long vertex = buffer.getLong(1);
      final long tex = buffer.getLong(2);
      final long size = buffer.getLong(3);
      return gpu.texturedRectangleBuilder(command, vertex, tex, size);
    }),

    COPY_RECT_VRAM_VRAM(0x80, 4, (buffer, gpu) -> {
      final long source = buffer.getLong(1);
      final int sourceY = (short)((source & 0xffff0000) >>> 16);
      final int sourceX = (short)(source & 0xffff);

      final long dest = buffer.getLong(2);
      final int destY = (short)((dest & 0xffff0000) >>> 16);
      final int destX = (short)(dest & 0xffff);

      final long size = buffer.getLong(3);
      final int height = (short)((size & 0xffff0000) >>> 16);
      final int width = (short)(size & 0xffff);

      return () -> LOGGER.warn("COPY VRAM VRAM from (not implemented) %d %d to %d %d size %d %d", sourceX, sourceY, destX, destY, width, height);
    }),

    DRAW_MODE_SETTINGS(0xe1, 1, (buffer, gpu) -> {
      final long settings = buffer.getLong(0);

      return () -> {
        LOGGER.trace("[GP0.e1] Draw mode set to %08x", settings);

        gpu.status.texturePageXBase = (int)(settings & 0b1111) * 64;
        gpu.status.texturePageYBase = (settings & 0b1_0000) != 0 ? TEXTURE_PAGE_Y_BASE.BASE_256 : TEXTURE_PAGE_Y_BASE.BASE_0;
        gpu.status.semiTransparency = SEMI_TRANSPARENCY.values()[(int)((settings & 0b110_0000) >>> 5)];
        gpu.status.texturePageColours = Bpp.values()[(int)((settings & 0b1_1000_0000) >>> 7)];
        gpu.status.drawable = (settings & 0b100_0000_0000) != 0;
        gpu.status.disableTextures = (settings & 0b1000_0000_0000) != 0;
        gpu.texturedRectXFlip = (settings & 0b1_0000_0000_0000) != 0;
        gpu.texturedRectYFlip = (settings & 0b10_0000_0000_0000) != 0;
      };
    }),

    TEXTURE_WINDOW_SETTINGS(0xe2, 1, (buffer, gpu) -> {
      final long settings = buffer.getLong(0);

      return () -> {
        gpu.textureWindowMaskX   = (int)( (settings & 0b0000_0000_0000_0001_1111)         * 8);
        gpu.textureWindowMaskY   = (int)(((settings & 0b0000_0000_0011_1110_0000) >>>  5) * 8);
        gpu.textureWindowOffsetX = (int)(((settings & 0b0000_0111_1100_0000_0000) >>> 10) * 8);
        gpu.textureWindowOffsetY = (int)(((settings & 0b1111_1000_0000_0000_0000) >>> 15) * 8);

        gpu.preMaskX = ~(gpu.textureWindowMaskX * 8);
        gpu.preMaskY = ~(gpu.textureWindowMaskY * 8);
        gpu.postMaskX = (gpu.textureWindowOffsetX & gpu.textureWindowMaskX) * 8;
        gpu.postMaskY = (gpu.textureWindowOffsetY & gpu.textureWindowMaskY) * 8;
      };
    }),

    DRAWING_AREA_TOP_LEFT(0xe3, 1, (buffer, gpu) -> {
      final long area = buffer.getLong(0);

      final short x = (short)(area & 0b11_1111_1111);
      final short y = (short)(area >>> 10 & 0b1_1111_1111L);

      assert x != 16;

      return () -> {
        LOGGER.trace("GP0.e3 setting drawing area top left to %d, %d", x, y);

        gpu.drawingArea.x.set(x);
        gpu.drawingArea.y.set(y);
      };
    }),

    DRAWING_AREA_BOTTOM_RIGHT(0xe4, 1, (buffer, gpu) -> {
      final long area = buffer.getLong(0);

      final short x = (short)(area & 0b11_1111_1111);
      final short y = (short)(area >>> 10 & 0b1_1111_1111L);

      return () -> {
        LOGGER.trace("GP0.e3 setting drawing area bottom right to %d, %d", x, y);

        gpu.drawingArea.w.set(x);
        gpu.drawingArea.h.set(y);
      };
    }),

    DRAWING_OFFSET(0xe5, 1, (buffer, gpu) -> {
      final long offset = buffer.getLong(0);

      return () -> {
        gpu.offsetX = signed11bit((int)offset & 0x7ff);
        gpu.offsetY = signed11bit((int)offset >>> 11 & 0x7ff);
      };
    }),

    MASK_BIT(0xe6, 1, (buffer, gpu) -> {
      final long val = buffer.getLong(0);

      return () -> {
        gpu.status.setMaskBit = (val & 0x1) != 0;
        gpu.status.drawPixels = (val & 0x2) != 0 ? DRAW_PIXELS.NOT_TO_MASKED_AREAS : DRAW_PIXELS.ALWAYS;

        LOGGER.trace("[GP0.e6] set mask bit %b, draw pixels %s", gpu.status.setMaskBit, gpu.status.drawPixels);
      };
    }),
    ;

    public static GP0_COMMAND getCommand(final long command) {
      for(final GP0_COMMAND cmd : GP0_COMMAND.values()) {
        if(cmd.command == command) {
          return cmd;
        }
      }

      throw new IndexOutOfBoundsException("Invalid GP0 command " + Long.toString(command, 16));
    }

    public final int command;
    public final int params;
    private final BiFunction<LongList, Gpu, Runnable> factory;

    GP0_COMMAND(final int command, final int params, final BiFunction<LongList, Gpu, Runnable> factory) {
      this.command = command;
      this.params = params;
      this.factory = factory;
    }
  }

  private static final class Gp0CommandBuffer {
    private final GP0_COMMAND command;
    private final LongList buffer = new LongArrayList();

    private Gp0CommandBuffer(final long command) {
      this.command = GP0_COMMAND.getCommand((command & 0xff000000L) >>> 24);
      this.queueValue(command);
    }

    private void queueValue(final long value) {
      this.buffer.add(value);
    }

    private boolean isComplete() {
      return this.buffer.size() >= this.command.params;
    }
  }

  public static class Status {
    /**
     * Bits 0-3 - Texture page X base (value * 64)
     */
    public int texturePageXBase;
    /**
     * Bit 4 - Texture page Y base (0 = 0, 1 = 256)
     */
    public TEXTURE_PAGE_Y_BASE texturePageYBase = TEXTURE_PAGE_Y_BASE.BASE_0;
    /**
     * Bits 5-6 - Semi-transparency
     */
    public SEMI_TRANSPARENCY semiTransparency = SEMI_TRANSPARENCY.HALF_B_PLUS_HALF_F;
    /**
     * Bits 7-8 - Texture page colours
     */
    public Bpp texturePageColours = Bpp.BITS_4;
    /**
     * Bit 9 - Dither 24-bit to 15-bit (false = strip MSB, true = dither)
     */
    public boolean dither;
    /**
     * Bit 10 - Drawing to display area
     */
    public boolean drawable;
    /**
     * Bit 11 - Set mask bit when drawing pixels
     */
    public boolean setMaskBit;
    /**
     * Bit 12 - Draw pixels
     */
    public DRAW_PIXELS drawPixels = DRAW_PIXELS.ALWAYS;
    /**
     * Bit 13 - Interlace field (always set when bit 22 is set)
     */
    public boolean interlaceField;
    /**
     * Bit 14 - Reverse flag (0 = normal, 1 = distorted)
     */
    public boolean reverse;
    /**
     * Bit 15 - Texture disable
     */
    public boolean disableTextures;
    /**
     * Bit 16 - Horizontal resolution 2
     */
    public HORIZONTAL_RESOLUTION_2 horizontalResolution2 = HORIZONTAL_RESOLUTION_2._256_320_512_640;
    /**
     * Bits 17-18 - Horizontal resolution 1
     */
    public HORIZONTAL_RESOLUTION_1 horizontalResolution1 = HORIZONTAL_RESOLUTION_1._256;
    /**
     * Bit 19 - Vertical resolution
     */
    public VERTICAL_RESOLUTION verticalResolution = VERTICAL_RESOLUTION._240;
    /**
     * Bit 20 - Video mode
     */
    public VIDEO_MODE videoMode = VIDEO_MODE.NTSC;
    /**
     * Bit 21 - Display area colour depth
     */
    public DISPLAY_AREA_COLOUR_DEPTH displayAreaColourDepth = DISPLAY_AREA_COLOUR_DEPTH.BITS_15;
    /**
     * Bit 22 - Vertical interlace
     */
    public boolean verticalInterlace;
    /**
     * Bit 23 - Display enable
     */
    public boolean displayEnable = true;
    /**
     * Bit 24 - Interrupt request (IRQ1)
     */
    public boolean interruptRequest;
    /**
     * Bit 25 - DMA/data request
     *
     * When DMA direction = off -> 0
     * When DMA direction = (unknown) -> FIFO state (0 = full, 1 = not full)
     * When DMA direction = CPU to GP0 -> same as bit 28
     * When DMA direction = GPUREAD to CPU -> same as bit 27
     */
    public boolean dmaRequest;
    /**
     * Bit 26 - Ready to receive command word
     */
    public boolean readyToReceiveCommand = true;
    /**
     * Bit 27 - Ready to send VRAM to CPU
     */
    public boolean readyToSendVramToCpu;
    /**
     * Bit 28 - Ready to receive DMA block
     */
    public boolean readyToReceiveDmaBlock = true;
    /**
     * Bits 29-30 - DMA direction
     */
    public DMA_DIRECTION dmaDirection = DMA_DIRECTION.OFF;
    /**
     * Bits 31 - Drawing even/odd lines in interlace mode
     */
    public DRAWING_LINE drawingLine = DRAWING_LINE.EVEN;

    private long pack() {
      return
        this.texturePageXBase / 64 & 0b111 |
        (long)this.texturePageYBase.ordinal() << 4 |
        (long)this.semiTransparency.ordinal() << 5 |
        (long)this.texturePageColours.ordinal() << 7 |
        (this.dither ? 1 : 0) << 9 |
        (this.drawable ? 1 : 0) << 10 |
        (this.setMaskBit ? 1 : 0) << 11 |
        (long)this.drawPixels.ordinal() << 12 |
        (this.interlaceField ? 1 : 0) << 13 |
        (this.reverse ? 1 : 0) << 14 |
        (this.disableTextures ? 1 : 0) << 15 |
        (long)this.horizontalResolution2.ordinal() << 16 |
        (long)this.horizontalResolution1.ordinal() << 17 |
        (long)this.verticalResolution.ordinal() << 19 |
        (long)this.videoMode.ordinal() << 20 |
        (long)this.displayAreaColourDepth.ordinal() << 21 |
        (this.verticalInterlace ? 1 : 0) << 22 |
        (this.displayEnable ? 1 : 0) << 23 |
        (this.interruptRequest ? 1 : 0) << 24 |
        (this.dmaRequest ? 1 : 0) << 25 |
        (this.readyToReceiveCommand ? 1 : 0) << 26 |
        (this.readyToSendVramToCpu ? 1 : 0) << 27 |
        (this.readyToReceiveDmaBlock ? 1 : 0) << 28 |
        (long)this.dmaDirection.ordinal() << 29 |
        (long)this.drawingLine.ordinal() << 31;
    }
  }

  public enum TEXTURE_PAGE_Y_BASE {
    BASE_0(0),
    BASE_256(256),
    ;

    public final int value;

    TEXTURE_PAGE_Y_BASE(final int value) {
      this.value = value;
    }
  }

  public enum SEMI_TRANSPARENCY {
    HALF_B_PLUS_HALF_F,
    B_PLUS_F,
    B_MINUS_F,
    B_PLUS_QUARTER_F,
  }

  public enum DRAW_PIXELS {
    ALWAYS,
    NOT_TO_MASKED_AREAS,
  }

  public enum HORIZONTAL_RESOLUTION_2 {
    _256_320_512_640,
    _368,
  }

  public enum HORIZONTAL_RESOLUTION_1 {
    _256,
    _320,
    _512,
    _640,
  }

  public enum VERTICAL_RESOLUTION {
    _240,
    _480,
  }

  public enum VIDEO_MODE {
    NTSC,
    PAL,
  }

  public enum DISPLAY_AREA_COLOUR_DEPTH {
    BITS_15,
    BITS_24,
  }

  public enum DMA_DIRECTION {
    OFF,
    FIFO,
    CPU_TO_GP0,
    GPU_READ_TO_CPU,
  }

  public enum DRAWING_LINE {
    EVEN,
    ODD,
    ;

    public DRAWING_LINE flip() {
      return this == EVEN ? ODD : EVEN;
    }
  }

  public class GpuSegment extends Segment {
    public GpuSegment(final long address) {
      super(address, 8);
    }

    @Override
    public byte get(final int offset) {
      throw new MisalignedAccessException("GPU ports may only be accessed with 32-bit reads and writes");
    }

    @Override
    public long get(final int offset, final int size) {
      if(size != 4) {
        throw new MisalignedAccessException("GPU ports may only be accessed with 32-bit reads and writes");
      }

      return switch(offset & 0x4) {
        case 0x0 -> this.onReg0Read();
        case 0x4 -> this.onReg1Read();
        default -> throw new IllegalAddressException("There is no GPU port at " + Long.toHexString(this.getAddress() + offset));
      };
    }

    @Override
    public void set(final int offset, final byte value) {
      throw new MisalignedAccessException("GPU ports may only be accessed with 32-bit reads and writes");
    }

    @Override
    public void set(final int offset, final int size, final long value) {
      if(size != 4) {
        throw new MisalignedAccessException("GPU ports may only be accessed with 32-bit reads and writes");
      }

      switch(offset & 0x4) {
        case 0x0 -> this.onReg0Write(value);
        case 0x4 -> this.onReg1Write(value);
      }
    }

    private void onReg0Write(final long value) {
      Gpu.this.status.readyToReceiveCommand = false;
      Gpu.this.queueGp0Command(value);
      Gpu.this.processGp0Command();
    }

    /**
     * Display Control Commands
     *
     * These commands are executed immediately
     */
    private void onReg1Write(final long value) {
      final int command = (int)((value & 0xff000000) >>> 24);

      switch(command) {
        case 0x00: // Reset GPU
          LOGGER.info("Resetting GPU");

          Gpu.this.resetCommandBuffer();
          Gpu.this.acknowledgeGpuInterrupt();
          Gpu.this.disableDisplay();
          Gpu.this.dmaDirection(DMA_DIRECTION.OFF);
          Gpu.this.displayStart(0, 0);
          Gpu.this.horizontalDisplayRange(0x200, 0x200 + 0xa00);
          Gpu.this.verticalDisplayRange(0x10, 0x10 + 0xf0);
          Gpu.this.displayMode(HORIZONTAL_RESOLUTION_1._320, VERTICAL_RESOLUTION._240, VIDEO_MODE.NTSC, DISPLAY_AREA_COLOUR_DEPTH.BITS_15, false, HORIZONTAL_RESOLUTION_2._256_320_512_640);

          //TODO GP0 commands
          //TODO verify that this sets GPUSTAT to 0x14802000
          return;

        case 0x01: // Reset Command Buffer
          LOGGER.info("Resetting GPU command buffer");
          Gpu.this.resetCommandBuffer();
          return;

        case 0x02: // Acknowledge GPU Interrupt (IRQ1)
          LOGGER.trace("Acknowledging GPU interrupt");
          Gpu.this.acknowledgeGpuInterrupt();
          return;

        case 0x03: // Display enable
          final boolean enable = (value & 0b1) == 0;

          if(enable) {
            LOGGER.trace("Enabling display");
            Gpu.this.enableDisplay();
          } else {
            LOGGER.trace("Disabling display");
            Gpu.this.disableDisplay();
          }

          return;

        case 0x04: // DMA type
          switch((int)(value & 0b11)) {
            case 0:
              Gpu.this.dmaDirection(DMA_DIRECTION.OFF);
              break;

            case 1:
              Gpu.this.dmaDirection(DMA_DIRECTION.FIFO);
              break;

            case 2:
              Gpu.this.dmaDirection(DMA_DIRECTION.CPU_TO_GP0);
              break;

            case 3:
              Gpu.this.dmaDirection(DMA_DIRECTION.GPU_READ_TO_CPU);
              break;

            default:
              throw new RuntimeException("GPU DMA type " + (value & 0b11) + " not supported");
          }

          LOGGER.trace("GPU DMA type set to %s", Gpu.this.status.dmaDirection);

          return;

        case 0x05: // Start of display area (in VRAM)
          Gpu.this.displayStartX = (int)(value & 0x3ff);
          Gpu.this.displayStartY = (int)(value >> 10 & 0x3ff);

          LOGGER.trace("Setting start of display area (in VRAM) to %d, %d", Gpu.this.displayStartX, Gpu.this.displayStartY);
          return;

        case 0x06: // Horizontal display range (on screen)
          // Both values are counted in 53.222400MHz units, relative to HSYNC
          // Note: 260h is the first visible pixel on normal CRT TV sets
          final int x1 = (int)(value & 0xfff); // (260h + 0)
          final int x2 = (int)(value >> 12 & 0xfffL); // (260h + 320 * 8)

          LOGGER.trace("Setting horizontal display range (on screen) to %d, %d", x1, x2);
          Gpu.this.horizontalDisplayRange(x1, x2);
          return;

        case 0x07: // Vertical display range (on screen)
          final int y1 = (int)(value & 0x3ff);
          final int y2 = (int)(value >> 10 & 0x3ff);

          LOGGER.trace("Setting vertical display range (on screen) to %d, %d", y1, y2);
          Gpu.this.verticalDisplayRange(y1, y2);
          return;

        case 0x08: // Display mode
          LOGGER.trace("Setting display mode %02x", value);

          final HORIZONTAL_RESOLUTION_1 hRes1 = HORIZONTAL_RESOLUTION_1.values()[(int)(value & 0b11)];
          final VERTICAL_RESOLUTION vRes = (value & 0b100) != 0 ? VERTICAL_RESOLUTION._240 : VERTICAL_RESOLUTION._480;
          final VIDEO_MODE videoMode = (value & 0b1000) != 0 ? VIDEO_MODE.NTSC : VIDEO_MODE.PAL;
          final DISPLAY_AREA_COLOUR_DEPTH colourDepth = (value & 0b1_0000) != 0 ? DISPLAY_AREA_COLOUR_DEPTH.BITS_15 : DISPLAY_AREA_COLOUR_DEPTH.BITS_24;
          final boolean interlace = (value & 0b10_0000) != 0;
          final HORIZONTAL_RESOLUTION_2 hRes2 = (value & 0b100_0000) != 0 ? HORIZONTAL_RESOLUTION_2._256_320_512_640 : HORIZONTAL_RESOLUTION_2._368;

          Gpu.this.displayMode(hRes1, vRes, videoMode, colourDepth, interlace, hRes2);

          return;

        case 0x10: // Get GPU Info
          final int info = (int)(value & 0xffffff);

          switch(info) {
            case 0x03: // Draw area top left
              Gpu.this.gpuInfo = Gpu.this.drawingArea.y.get() << 10 | Gpu.this.drawingArea.x.get();
              return;

            case 0x04: // Draw area bottom right
              Gpu.this.gpuInfo = Gpu.this.drawingArea.h.get() << 10 | Gpu.this.drawingArea.w.get();
              return;

            case 0x05: // Draw area offset
              Gpu.this.gpuInfo = Gpu.this.offsetY << 11 | Gpu.this.offsetX;
              return;

            case 0x07: // GPU type
              Gpu.this.gpuInfo = 0x2;
              return;
          }

          throw new RuntimeException("Get GPU info " + Integer.toString(info, 16) + " not yet supported");
      }

      throw new RuntimeException("GPU command 1." + Integer.toString(command, 16) + " not yet supported (command word: " + Long.toString(value, 16) + ')');
    }

    private long onReg0Read() {
      return Gpu.this.gpuInfo;
    }

    private long onReg1Read() {
      return Gpu.this.status.pack();
    }
  }
}