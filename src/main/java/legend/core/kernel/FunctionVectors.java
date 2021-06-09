package legend.core.kernel;

import legend.core.memory.Method;
import legend.core.memory.types.BiFunctionRef;

import static legend.core.Hardware.MEMORY;

public final class FunctionVectors {
  private FunctionVectors() { }

  private static final BiFunctionRef<Long, Object[], Object> kernelFunctionVectorA = MEMORY.ref(4, 0x5c4L, BiFunctionRef::new);
  private static final BiFunctionRef<Long, Object[], Object> kernelFunctionVectorB = MEMORY.ref(4, 0x5e0L, BiFunctionRef::new);
  private static final BiFunctionRef<Long, Object[], Object> kernelFunctionVectorC = MEMORY.ref(4, 0x600L, BiFunctionRef::new);

  @Method(0xa0L)
  public static Object functionVectorA(final long fn, final Object... params) {
    return kernelFunctionVectorA.run(fn, params);
  }

  @Method(0xb0L)
  public static Object functionVectorB(final long fn, final Object... params) {
    return kernelFunctionVectorB.run(fn, params);
  }

  @Method(0xc0L)
  public static Object functionVectorC(final long fn, final Object... params) {
    return kernelFunctionVectorC.run(fn, params);
  }
}
