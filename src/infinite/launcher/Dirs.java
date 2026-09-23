package infinite.launcher;

import java.io.File;

/**
 * Where the launcher keeps things. One folder per user:
 *
 *   Windows  %APPDATA%\InfiniteLauncher
 *   macOS    ~/Library/Application Support/InfiniteLauncher
 *   Linux    $XDG_DATA_HOME/InfiniteLauncher  (~/.local/share/InfiniteLauncher)
 *
 * -Dinfinite.launcher.dir or INFINITE_LAUNCHER_DIR moves it, for portable installs and tests.
 */
public final class Dirs {
   public static final File ROOT = root();

   public static File versions() { return sub("versions"); }
   public static File libraries() { return sub("libraries"); }
   public static File base() { return sub("base"); }
   public static File runtimes() { return sub("runtime"); }
   public static File cache() { return sub("cache"); }
   public static File logs() { return sub("logs"); }
   public static File launcherUpdates() { return sub("launcher"); }

   public static File game(Settings s) {
      if (s != null && s.gameDir != null && !s.gameDir.trim().isEmpty()) {
         File f = new File(s.gameDir.trim());
         f.mkdirs();
         return f;
      }
      return sub("game");
   }

   private static File sub(String name) {
      File f = new File(ROOT, name);
      f.mkdirs();
      return f;
   }

   private static File root() {
      String o = System.getProperty("infinite.launcher.dir");
      if (o == null || o.isEmpty()) {
         o = System.getenv("INFINITE_LAUNCHER_DIR");
      }
      File f;
      if (o != null && !o.isEmpty()) {
         f = new File(o);
      } else if (Os.WINDOWS) {
         String appdata = System.getenv("APPDATA");
         f = new File(appdata != null ? appdata : System.getProperty("user.home"), "InfiniteLauncher");
      } else if (Os.MAC) {
         f = new File(System.getProperty("user.home"), "Library/Application Support/InfiniteLauncher");
      } else {
         String xdg = System.getenv("XDG_DATA_HOME");
         f = new File(xdg != null && !xdg.isEmpty() ? xdg : System.getProperty("user.home") + "/.local/share", "InfiniteLauncher");
      }
      f.mkdirs();
      return f.getAbsoluteFile();
   }

   private Dirs() {
   }
}
