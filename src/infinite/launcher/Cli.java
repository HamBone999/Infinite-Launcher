package infinite.launcher;

import java.io.File;
import java.util.List;

/**
 * Command-line entry, for testing and for players who want it:
 *
 *   java -jar InfiniteLauncher.jar --cli list
 *   java -jar InfiniteLauncher.jar --cli install [version|latest]
 *   java -jar InfiniteLauncher.jar --cli check   [version|latest]   install, find Java, print the command
 */
final class Cli {
   static int run(String[] args) {
      String cmd = args.length > 1 ? args[1] : "help";
      String ver = args.length > 2 ? args[2] : Settings.LATEST;
      Progress p = new Progress() {
         private int lastPct = -1;

         public void step(String what) {
            System.out.println("==> " + what);
            lastPct = -1;
         }

         public void fraction(double f) {
            int pct = (int)(f * 100);
            if (f >= 0 && pct / 10 != lastPct / 10) {
               System.out.println("    " + pct + "%");
               lastPct = pct;
            }
         }

         public boolean cancelled() {
            return false;
         }
      };
      try {
         if (cmd.equals("list")) {
            Releases rs = Releases.fetch();
            System.out.println(rs.offline ? "(offline, from cache)" : "(from GitHub)");
            List<String> installed = Installer.installedVersions();
            for (GameRelease r : rs.games) {
               System.out.printf("%-12s %-12s %-10s %s%s%n", r.clientVersion, r.tag, Io.humanBytes(r.assetSize), r.date(),
                  installed.contains(r.clientVersion) ? "  [installed]" : "");
            }
            if (rs.newestLauncher != null) {
               System.out.println("newest launcher on GitHub: " + rs.newestLauncher.version + " (this is " + BuildInfo.VERSION + ")");
            }
            return 0;
         }
         if (cmd.equals("install") || cmd.equals("check") || cmd.equals("launch-dev")) {
            Releases rs = Releases.fetch();
            GameRelease r = ver.equals(Settings.LATEST) ? rs.latest() : rs.find(ver);
            String cv = r != null ? r.clientVersion : ver;
            if (cv.equals(Settings.LATEST)) {
               System.err.println("No releases found and nothing installed.");
               return 2;
            }
            Installer.Installed inst = Installer.ensure(r, cv, p);
            System.out.println("installed : " + inst.dir);
            System.out.println("libraries : " + inst.libraries.size() + " on the classpath");
            System.out.println("natives   : " + inst.natives + " (" + count(inst.natives) + " files)");
            System.out.println("textures  : " + count(inst.assets) + " files");
            if (cmd.equals("install")) {
               return 0;
            }
            Settings s = Settings.load();
            File java = Runtimes.java(s, p);
            System.out.println("java      : " + java);
            String name = args.length > 3 ? args[3] : "Player";
            List<String> c = GameProcess.command(java, inst, name, cmd.equals("launch-dev") ? "-" : "token:<access-token>:<uuid>", s);
            System.out.println("command   : " + GameProcess.redact(c));
            if (cmd.equals("check")) {
               return 0;
            }
            // Offline launch, for testing the install on a machine with no account. The game
            // itself accepts this; online-mode servers reject it, so it only reaches singleplayer.
            if (!Boolean.getBoolean("infinite.dev")) {
               System.err.println("launch-dev needs -Dinfinite.dev=true");
               return 2;
            }
            final Object done = new Object();
            final int[] exit = { -1 };
            GameProcess.start(c, Dirs.game(s), new GameProcess.Listener() {
               public void line(String line) {
                  System.out.println("[game] " + line);
               }

               public void exited(int code, long ms, List<String> tail) {
                  synchronized (done) {
                     exit[0] = code;
                     done.notifyAll();
                  }
               }
            });
            synchronized (done) {
               while (exit[0] == -1) {
                  done.wait();
               }
            }
            return exit[0];
         }
         System.out.println("usage: --cli list | install [version] | check [version]");
         return 1;
      } catch (Exception e) {
         System.err.println("error: " + e.getMessage());
         Log.warn("cli " + cmd + " failed", e);
         return 1;
      }
   }

   private static int count(File dir) {
      int n = 0;
      File[] kids = dir.listFiles();
      if (kids != null) {
         for (File k : kids) {
            n += k.isDirectory() ? count(k) : k.getName().startsWith(".") ? 0 : 1;
         }
      }
      return n;
   }

   private Cli() {
   }
}
