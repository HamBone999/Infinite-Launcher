package infinite.launcher;

import java.io.InputStream;
import java.util.Properties;

/** Values baked in at build time, from launcher.properties. */
public final class BuildInfo {
   public static final String VERSION;
   /** Azure application (client) ID for Microsoft sign-in. Public, not a secret. */
   public static final String MSA_CLIENT_ID;
   /** owner/name of the GitHub repository game releases are read from. */
   public static final String REPO;
   /** owner/name of the repository the launcher updates itself from. */
   public static final String LAUNCHER_REPO;

   static {
      Properties p = new Properties();
      try {
         InputStream in = BuildInfo.class.getResourceAsStream("/infinite/launcher/launcher.properties");
         if (in != null) {
            try {
               p.load(in);
            } finally {
               in.close();
            }
         }
      } catch (Exception ignored) {
      }
      VERSION = p.getProperty("version", "0.0.0-dev");
      String id = System.getProperty("infinite.msa.clientId", p.getProperty("msa.clientId", "")).trim();
      MSA_CLIENT_ID = id;
      REPO = System.getProperty("infinite.repo", p.getProperty("repo", "HamBone999/Minecraft-Infinite-Reborn")).trim();
      LAUNCHER_REPO = System.getProperty("infinite.launcherRepo", p.getProperty("launcherRepo", "HamBone999/Infinite-Launcher")).trim();
   }

   public static String userAgent() {
      return "InfiniteLauncher/" + VERSION + " (+https://github.com/" + LAUNCHER_REPO + ")";
   }

   /** Compares dotted versions numerically: 1.10.0 > 1.9.2. Suffixes after '-' sort lower. */
   public static int compare(String a, String b) {
      String[] x = a.split("-", 2)[0].split("\\.");
      String[] y = b.split("-", 2)[0].split("\\.");
      for (int i = 0; i < Math.max(x.length, y.length); i++) {
         int p = i < x.length ? parse(x[i]) : 0;
         int q = i < y.length ? parse(y[i]) : 0;
         if (p != q) {
            return p < q ? -1 : 1;
         }
      }
      boolean xs = a.contains("-"), ys = b.contains("-");
      return xs == ys ? 0 : xs ? -1 : 1;
   }

   private static int parse(String s) {
      try {
         return Integer.parseInt(s.replaceAll("[^0-9]", ""));
      } catch (NumberFormatException e) {
         return 0;
      }
   }

   private BuildInfo() {
   }
}
