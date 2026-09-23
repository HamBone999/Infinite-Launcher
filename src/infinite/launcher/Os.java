package infinite.launcher;

import java.util.Locale;

/** Which platform this is, in the words the release manifests use. */
public final class Os {
   public static final String NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
   public static final boolean WINDOWS = NAME.contains("win");
   public static final boolean MAC = NAME.contains("mac") || NAME.contains("darwin");
   public static final boolean LINUX = !WINDOWS && !MAC;

   /** "windows", "osx" or "linux" -- the keys in version.json's natives maps. */
   public static String tag() {
      return WINDOWS ? "windows" : MAC ? "osx" : "linux";
   }

   public static boolean arm64() {
      String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
      return a.equals("aarch64") || a.equals("arm64");
   }

   /**
    * The hardware, not the JVM. An x64 Java under Rosetta reports x86_64, but a Mac with an
    * Apple chip always has sysctl hw.optional.arm64 = 1.
    */
   public static boolean arm64Hardware() {
      if (arm64()) {
         return true;
      }
      if (!MAC) {
         return false;
      }
      try {
         Process p = new ProcessBuilder("sysctl", "-n", "hw.optional.arm64").redirectErrorStream(true).start();
         byte[] out = Io.readAll(p.getInputStream());
         p.waitFor();
         return new String(out, "UTF-8").trim().equals("1");
      } catch (Exception e) {
         return false;
      }
   }

   private Os() {
   }
}
