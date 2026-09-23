package infinite.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/** Builds the java command line, starts the game, and keeps its output. */
public final class GameProcess {
   public interface Listener {
      void line(String line);

      void exited(int code, long runtimeMs, List<String> tail);
   }

   public static List<String> command(File java, Installer.Installed inst, String name, String session, Settings s) {
      List<String> cmd = new ArrayList<String>();
      cmd.add(java.getAbsolutePath());
      int mem = Math.max(512, s.memoryMb);
      cmd.add("-Xmx" + mem + "M");
      cmd.add("-Xms" + Math.min(512, mem) + "M");
      cmd.add("-XX:+UseG1GC");
      cmd.add("-XX:MaxGCPauseMillis=50");
      String nat = inst.natives.getAbsolutePath();
      cmd.add("-Djava.library.path=" + nat);
      cmd.add("-Dorg.lwjgl.librarypath=" + nat);
      cmd.add("-Dnet.java.games.input.librarypath=" + nat);
      cmd.add("-Dminecraft.launcher.brand=InfiniteLauncher");
      cmd.add("-Dminecraft.launcher.version=" + BuildInfo.VERSION);
      if (Os.MAC) {
         cmd.add("-Xdock:name=Minecraft Infinite");
         File icon = dockIcon();
         if (icon != null) {
            cmd.add("-Xdock:icon=" + icon.getAbsolutePath());
         }
      }
      cmd.addAll(splitArgs(s.jvmArgs));
      StringBuilder cp = new StringBuilder();
      cp.append(inst.gameJar.getAbsolutePath());
      cp.append(File.pathSeparator).append(inst.assets.getAbsolutePath());
      for (File l : inst.libraries) {
         cp.append(File.pathSeparator).append(l.getAbsolutePath());
      }
      cmd.add("-cp");
      cmd.add(cp.toString());
      cmd.add(inst.mainClass);
      cmd.add(name);
      cmd.add(session);
      if (s.fullscreen) {
         cmd.add("--fullscreen");
      }
      if (s.width > 0 && s.height > 0) {
         cmd.add("--width");
         cmd.add(String.valueOf(s.width));
         cmd.add("--height");
         cmd.add(String.valueOf(s.height));
      }
      return cmd;
   }

   /** Anything that looks like a token is masked before a command line is logged. */
   public static String redact(List<String> cmd) {
      StringBuilder b = new StringBuilder();
      for (String a : cmd) {
         if (b.length() > 0) {
            b.append(' ');
         }
         b.append(a.startsWith("token:") ? "token:<hidden>" : a.contains(" ") ? "\"" + a + "\"" : a);
      }
      return b.toString();
   }

   public static Process start(List<String> cmd, File gameDir, final Listener l) throws IOException {
      gameDir.mkdirs();
      File logs = Dirs.logs();
      File cur = new File(logs, "game-latest.log");
      File prev = new File(logs, "game-previous.log");
      if (cur.exists()) {
         prev.delete();
         cur.renameTo(prev);
      }
      Log.info("launching: " + redact(cmd));
      ProcessBuilder pb = new ProcessBuilder(cmd).directory(gameDir).redirectErrorStream(true);
      final long started = System.currentTimeMillis();
      final Process proc = pb.start();
      final PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(cur), "UTF-8"), true);
      Thread pump = new Thread(new Runnable() {
         public void run() {
            LinkedList<String> tail = new LinkedList<String>();
            try {
               InputStream in = proc.getInputStream();
               BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
               String line;
               while ((line = r.readLine()) != null) {
                  out.println(line);
                  tail.add(line);
                  if (tail.size() > 400) {
                     tail.removeFirst();
                  }
                  l.line(line);
               }
            } catch (IOException ignored) {
            }
            int code;
            try {
               code = proc.waitFor();
            } catch (InterruptedException e) {
               code = -1;
            }
            out.println("[launcher] game exited with code " + code);
            out.close();
            Log.info("game exited with code " + code);
            l.exited(code, System.currentTimeMillis() - started, new ArrayList<String>(tail));
         }
      }, "game-output");
      pump.setDaemon(true);
      pump.start();
      return proc;
   }

   static List<String> splitArgs(String s) {
      List<String> out = new ArrayList<String>();
      if (s == null) {
         return out;
      }
      StringBuilder cur = new StringBuilder();
      boolean q = false;
      for (char c : s.toCharArray()) {
         if (c == '"') {
            q = !q;
         } else if (Character.isWhitespace(c) && !q) {
            if (cur.length() > 0) {
               out.add(cur.toString());
               cur.setLength(0);
            }
         } else {
            cur.append(c);
         }
      }
      if (cur.length() > 0) {
         out.add(cur.toString());
      }
      return out;
   }

   private static File dockIcon() {
      try {
         File f = new File(Dirs.cache(), "icon-256.png");
         if (!f.isFile()) {
            InputStream in = GameProcess.class.getResourceAsStream("/infinite/launcher/icon-256.png");
            if (in == null) {
               return null;
            }
            Io.writeBytes(f, Io.readAll(in));
         }
         return f;
      } catch (IOException e) {
         return null;
      }
   }

   private GameProcess() {
   }
}
