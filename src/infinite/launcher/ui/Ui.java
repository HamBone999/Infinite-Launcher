package infinite.launcher.ui;

import infinite.launcher.Log;
import infinite.launcher.Os;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.net.URI;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** Small shared pieces: labels, the thin scrollbar, opening links and folders. */
public final class Ui {
   public static JLabel label(String text, int style, int size, Color c) {
      JLabel l = new JLabel(text);
      l.setFont(Theme.font(style, size));
      l.setForeground(c);
      return l;
   }

   /** An uppercase caption above a control. */
   public static JLabel caption(String text) {
      JLabel l = label(text.toUpperCase(), Font.BOLD, 10, Theme.FAINT);
      l.setBorder(BorderFactory.createEmptyBorder(0, Theme.s(2), Theme.s(5), 0));
      return l;
   }

   public static void thin(JScrollPane sp, final Color track) {
      sp.setBorder(null);
      sp.getViewport().setBackground(track);
      for (JScrollBar bar : new JScrollBar[] { sp.getVerticalScrollBar(), sp.getHorizontalScrollBar() }) {
         bar.setUnitIncrement(Theme.s(18));
         bar.setPreferredSize(new Dimension(Theme.s(10), Theme.s(10)));
         bar.setUI(new BasicScrollBarUI() {
            protected void configureScrollBarColors() {
               thumbColor = Theme.SURFACE_3;
               trackColor = track;
            }

            protected JComponentButton createDecreaseButton(int o) {
               return new JComponentButton();
            }

            protected JComponentButton createIncreaseButton(int o) {
               return new JComponentButton();
            }

            protected void paintThumb(Graphics g0, JComponent c, java.awt.Rectangle r) {
               if (r.isEmpty()) {
                  return;
               }
               Graphics2D g = (Graphics2D)g0.create();
               Theme.hints(g);
               g.setColor(isDragging ? Theme.FAINT : Theme.SURFACE_3);
               int pad = Theme.s(2);
               g.fillRoundRect(r.x + pad, r.y + pad, r.width - 2 * pad, r.height - 2 * pad, Theme.s(8), Theme.s(8));
               g.dispose();
            }

            protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle r) {
               g.setColor(track);
               g.fillRect(r.x, r.y, r.width, r.height);
            }
         });
      }
   }

   /** A zero-size stand-in for scrollbar arrow buttons. */
   static final class JComponentButton extends javax.swing.JButton {
      JComponentButton() {
         setPreferredSize(new Dimension(0, 0));
         setMinimumSize(new Dimension(0, 0));
         setMaximumSize(new Dimension(0, 0));
      }
   }

   public static void browse(String url) {
      try {
         if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(new URI(url));
            return;
         }
      } catch (Exception e) {
         Log.warn("Desktop.browse failed for " + url, e);
      }
      exec(url);
   }

   public static void open(File dir) {
      dir.mkdirs();
      try {
         if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(dir);
            return;
         }
      } catch (Exception e) {
         Log.warn("Desktop.open failed for " + dir, e);
      }
      exec(dir.getAbsolutePath());
   }

   private static void exec(String target) {
      try {
         String[] cmd = Os.WINDOWS ? new String[] { "rundll32", "url.dll,FileProtocolHandler", target }
            : Os.MAC ? new String[] { "open", target } : new String[] { "xdg-open", target };
         new ProcessBuilder(cmd).start();
      } catch (Exception e) {
         Log.warn("could not open " + target, e);
      }
   }

   public static void copy(String text) {
      try {
         Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
      } catch (Exception e) {
         Log.warn("clipboard unavailable", e);
      }
   }

   /** Escape closes the dialog, the way players expect. */
   public static void escapeCloses(final javax.swing.JDialog d, final Runnable close) {
      javax.swing.JRootPane rp = d.getRootPane();
      rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke("ESCAPE"), "close");
      rp.getActionMap().put("close", new javax.swing.AbstractAction() {
         public void actionPerformed(java.awt.event.ActionEvent e) {
            close.run();
         }
      });
   }

   public static void pad(JComponent c, int t, int l, int b, int r) {
      c.setBorder(BorderFactory.createEmptyBorder(Theme.s(t), Theme.s(l), Theme.s(b), Theme.s(r)));
   }

   public static <T extends Component> T opaque(T c, Color bg) {
      if (c instanceof JComponent) {
         ((JComponent)c).setOpaque(true);
      }
      c.setBackground(bg);
      return c;
   }

   private Ui() {
   }
}
