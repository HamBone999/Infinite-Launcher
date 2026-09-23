package infinite.launcher;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * The launcher updates itself without the installer being run again.
 *
 * The installed InfiniteLauncher.jar is also a bootstrap. On start it looks in launcher/ for a
 * newer InfiniteLauncher-X.Y.Z.jar and, if there is one, runs that instead, in-process. A new one
 * is fetched from a GitHub release tagged launcher-vX.Y.Z. If a downloaded launcher throws before
 * its window is up, it is marked bad and the installed one carries on, so a broken update can't
 * lock anybody out.
 */
public final class SelfUpdate {
   static final String DELEGATED = "infinite.launcher.delegated";
   static final String BOOTSTRAP = "infinite.launcher.bootstrap";

   /** Returns true if a newer downloaded launcher took over. Tries them newest first. */
   static boolean delegate(String[] args) {
      if (System.getProperty(DELEGATED) != null || Boolean.getBoolean("infinite.launcher.noDelegate")) {
         return false;
      }
      File self = selfJar();
      if (self != null) {
         System.setProperty(BOOTSTRAP, self.getAbsolutePath());
      }
      List<String> bad = bad();
      final java.util.Map<File, String> found = new java.util.HashMap<File, String>();
      File[] kids = Dirs.launcherUpdates().listFiles();
      if (kids != null) {
         for (File k : kids) {
            String n = k.getName();
            if (n.startsWith("InfiniteLauncher-") && n.endsWith(".jar")) {
               String v = n.substring("InfiniteLauncher-".length(), n.length() - 4);
               if (!bad.contains(v) && BuildInfo.compare(v, BuildInfo.VERSION) > 0) {
                  found.put(k, v);
               }
            }
         }
      }
      List<File> order = new ArrayList<File>(found.keySet());
      java.util.Collections.sort(order, new java.util.Comparator<File>() {
         public int compare(File a, File b) {
            return BuildInfo.compare(found.get(b), found.get(a));
         }
      });
      for (File jar : order) {
         String v = found.get(jar);
         try {
            Log.info("handing over to launcher " + v);
            System.setProperty(DELEGATED, v);
            URLClassLoader cl = new URLClassLoader(new URL[] { jar.toURI().toURL() }, ClassLoader.getSystemClassLoader().getParent());
            Thread.currentThread().setContextClassLoader(cl);
            Class<?> main = Class.forName("infinite.launcher.Main", true, cl);
            Method m = main.getMethod("main", String[].class);
            m.invoke(null, (Object)args);
            return true;
         } catch (Throwable t) {
            Log.warn("launcher " + v + " failed to start; marking it bad", t instanceof java.lang.reflect.InvocationTargetException ? t.getCause() : null);
            System.clearProperty(DELEGATED);
            Thread.currentThread().setContextClassLoader(SelfUpdate.class.getClassLoader());
            bad.add(v);
            try {
               Io.writeText(new File(Dirs.launcherUpdates(), "bad.txt"), join(bad));
            } catch (IOException ignored) {
            }
         }
      }
      return false;
   }

   /** Downloads a newer launcher if GitHub has one. Returns its version, or null. */
   public static String fetchIfNewer(Releases rs, Progress p) {
      Releases.LauncherRelease l = rs.newestLauncher;
      if (l == null || l.url == null || BuildInfo.compare(l.version, BuildInfo.VERSION) <= 0 || bad().contains(l.version)) {
         return null;
      }
      File target = new File(Dirs.launcherUpdates(), "InfiniteLauncher-" + l.version + ".jar");
      try {
         if (!target.isFile()) {
            Http.download(l.url, target, null, l.size, p, new long[] { 0 }, l.size);
            if (l.sha256 != null && !Io.sha256(target).equalsIgnoreCase(l.sha256)) {
               target.delete();
               throw new IOException("checksum mismatch");
            }
         }
         prune(l.version);
         return l.version;
      } catch (IOException e) {
         Log.warn("launcher update " + l.version + " not downloaded: " + e.getMessage(), null);
         return null;
      }
   }

   /** Starts the launcher again through the installed bootstrap, which picks up the new jar. */
   public static void restart() throws IOException {
      String boot = System.getProperty(BOOTSTRAP);
      File jar = boot != null ? new File(boot) : selfJar();
      if (jar == null) {
         throw new IOException("Don't know where the launcher is installed; close and reopen it instead.");
      }
      List<String> cmd = new ArrayList<String>();
      File java = Runtimes.bin(new File(System.getProperty("java.home")));
      cmd.add(java == null ? "java" : java.getAbsolutePath());
      for (String a : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
         if (a.startsWith("-Xdock") || a.startsWith("-Dapple") || a.startsWith("-Dinfinite.launcher.dir")) {
            cmd.add(a);
         }
      }
      cmd.add("-jar");
      cmd.add(jar.getAbsolutePath());
      new ProcessBuilder(cmd).start();
      System.exit(0);
   }

   static File selfJar() {
      try {
         File f = new File(SelfUpdate.class.getProtectionDomain().getCodeSource().getLocation().toURI());
         return f.isFile() ? f : null;
      } catch (Exception e) {
         return null;
      }
   }

   private static void prune(String keep) {
      File[] kids = Dirs.launcherUpdates().listFiles();
      if (kids == null) {
         return;
      }
      String running = System.getProperty(DELEGATED, BuildInfo.VERSION);
      for (File k : kids) {
         String n = k.getName();
         if (n.startsWith("InfiniteLauncher-") && n.endsWith(".jar") && !n.contains(keep) && !n.contains("-" + running + ".jar")) {
            k.delete();
         }
      }
   }

   private static List<String> bad() {
      List<String> out = new ArrayList<String>();
      File f = new File(Dirs.launcherUpdates(), "bad.txt");
      if (f.isFile()) {
         try {
            for (String l : Io.readText(f).split("\n")) {
               if (!l.trim().isEmpty()) {
                  out.add(l.trim());
               }
            }
         } catch (IOException ignored) {
         }
      }
      return out;
   }

   private static String join(List<String> l) {
      StringBuilder b = new StringBuilder();
      for (String s : l) {
         b.append(s).append('\n');
      }
      return b.toString();
   }

   private SelfUpdate() {
   }
}
