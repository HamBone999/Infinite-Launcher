package infinite.launcher;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class Main {
   private static FileLock lock;

   public static void main(String[] args) throws Exception {
      if (args.length > 0 && args[0].equals("--cli")) {
         System.exit(Cli.run(args));
         return;
      }
      if (SelfUpdate.delegate(args)) {
         return;
      }
      System.setProperty("awt.useSystemAAFontSettings", "on");
      System.setProperty("swing.aatext", "true");
      System.setProperty("apple.awt.application.name", "Infinite Launcher");
      Log.info("Infinite Launcher " + BuildInfo.VERSION + " -- Java " + System.getProperty("java.version") + " ("
         + System.getProperty("java.vendor") + "), " + System.getProperty("os.name") + " " + System.getProperty("os.arch")
         + ", data in " + Dirs.ROOT);
      if (!singleInstance()) {
         SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "Infinite Launcher is already open.",
            "Infinite Launcher", JOptionPane.INFORMATION_MESSAGE));
         Thread.sleep(4000);
         System.exit(0);
      }
      SwingUtilities.invokeLater(() -> {
         infinite.launcher.ui.Theme.install();
         new infinite.launcher.ui.LauncherFrame().open();
      });
   }

   /** Two launchers would fight over accounts.json and the install folders. */
   private static boolean singleInstance() {
      try {
         RandomAccessFile raf = new RandomAccessFile(new File(Dirs.ROOT, ".lock"), "rw");
         lock = raf.getChannel().tryLock();
         return lock != null;
      } catch (Exception e) {
         return true;   // can't tell; don't block the player over it
      }
   }

   private Main() {
   }
}
