package legend.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

public final class Config {
  private Config() { }

  private static final Path path = Paths.get(".", "config.conf");
  private static final Properties properties = new Properties();

  static {
    properties.setProperty("window_width", "320");
    properties.setProperty("window_height", "240");
    properties.setProperty("render_scale", "1");
  }

  public static int windowWidth() {
    return readInt("window_width", 320, 1, Integer.MAX_VALUE);
  }

  public static int windowHeight() {
    return readInt("window_height", 240, 1, Integer.MAX_VALUE);
  }

  public static int renderScale() {
    return readInt("render_scale", 1, 1, 5);
  }

  private static int readInt(final String key, final int defaultVal, final int min, final int max) {
    int val;
    try {
      val = Integer.parseInt(properties.getProperty(key, String.valueOf(defaultVal)));
    } catch(final NumberFormatException e) {
      val = defaultVal;
    }

    return MathHelper.clamp(val, min, max);
  }

  public static boolean exists() {
    return Files.exists(path);
  }

  public static void load() throws IOException {
    properties.load(Files.newInputStream(path, StandardOpenOption.READ));
  }

  public static void save() throws IOException {
    properties.store(Files.newOutputStream(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING), "");
  }
}
