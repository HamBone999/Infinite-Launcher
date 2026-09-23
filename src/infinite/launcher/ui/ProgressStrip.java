package infinite.launcher.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;
import javax.swing.Timer;

/** A thin progress bar; negative values run an indeterminate sweep. */
final class ProgressStrip extends JComponent {
   private double value = 0;
   private int phase;
   private final Timer anim = new Timer(30, e -> {
      phase = (phase + 3) % 200;
      repaint();
   });

   ProgressStrip() {
      setPreferredSize(new Dimension(Theme.s(100), Theme.s(5)));
   }

   void set(double v) {
      value = v;
      if (v < 0 && !anim.isRunning()) {
         anim.start();
      } else if (v >= 0 && anim.isRunning()) {
         anim.stop();
      }
      repaint();
   }

   @Override
   protected void paintComponent(Graphics g0) {
      Graphics2D g = (Graphics2D)g0.create();
      Theme.hints(g);
      int w = getWidth(), h = getHeight();
      g.setColor(Theme.SURFACE_3);
      g.fillRoundRect(0, 0, w, h, h, h);
      g.setColor(Theme.ACCENT);
      if (value < 0) {
         int bw = w / 4;
         int x = (int)((phase / 200.0) * (w + bw)) - bw;
         g.setClip(0, 0, w, h);
         g.fillRoundRect(x, 0, bw, h, h, h);
      } else if (value > 0) {
         g.fillRoundRect(0, 0, (int)(w * Math.min(1, value)), h, h, h);
      }
      g.dispose();
   }
}
