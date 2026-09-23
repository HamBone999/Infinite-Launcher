package infinite.launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/** The launcher's own log, logs/launcher.log, one previous run kept as launcher.old.log. */
public final class Log {
   private static PrintWriter out;

   static synchronized void open() {
      if (out != null) {
         return;
      }
      try {
         File dir = Dirs.logs();
         File cur = new File(dir, "launcher.log");
         File old = new File(dir, "launcher.old.log");
         if (cur.exists()) {
            old.delete();
            cur.renameTo(old);
         }
         out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(cur), "UTF-8"), true);
      } catch (Exception e) {
         out = null;
      }
   }

   public static synchronized void info(String msg) {
      line("INFO ", msg);
   }

   public static synchronized void warn(String msg, Throwable t) {
      if (t != null) {
         StringWriter sw = new StringWriter();
         t.printStackTrace(new PrintWriter(sw));
         msg = msg + "\n" + sw;
      }
      line("WARN ", msg);
   }

   private static void line(String level, String msg) {
      open();
      String l = new SimpleDateFormat("HH:mm:ss").format(new Date()) + " " + level + msg;
      System.out.println(l);
      if (out != null) {
         out.println(l);
      }
   }

   private Log() {
   }
}
