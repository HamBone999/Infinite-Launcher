package infinite.launcher.ui;

import infinite.launcher.Account;
import infinite.launcher.Dirs;
import infinite.launcher.Http;
import infinite.launcher.Log;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** The player's face, cut from their skin the way the game draws it: 8x8 face plus the hat layer. */
final class Heads {
   static BufferedImage load(Account a, int size) {
      if (a == null) {
         return placeholder("?", size);
      }
      try {
         File dir = new File(Dirs.cache(), "skins");
         dir.mkdirs();
         File f = new File(dir, a.uuid + ".png");
         if (!f.isFile() || System.currentTimeMillis() - f.lastModified() > 24L * 3600 * 1000) {
            if (a.skinUrl != null && !a.skinUrl.isEmpty()) {
               Http.download(a.skinUrl, f, null, 0, null, null, 0);
            }
         }
         if (f.isFile()) {
            BufferedImage skin = ImageIO.read(f);
            if (skin != null && skin.getWidth() >= 64) {
               return face(skin, size);
            }
         }
      } catch (Exception e) {
         Log.warn("skin for " + a.name + " unavailable: " + e.getMessage(), null);
      }
      return placeholder(a.name.isEmpty() ? "?" : a.name.substring(0, 1).toUpperCase(), size);
   }

   private static BufferedImage face(BufferedImage skin, int size) {
      int u = skin.getWidth() / 64;
      BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = out.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
      g.drawImage(skin, 0, 0, size, size, 8 * u, 8 * u, 16 * u, 16 * u, null);
      g.drawImage(skin, 0, 0, size, size, 40 * u, 8 * u, 48 * u, 16 * u, null);
      g.dispose();
      return out;
   }

   static BufferedImage placeholder(String letter, int size) {
      BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = out.createGraphics();
      Theme.hints(g);
      g.setColor(Theme.SURFACE_3);
      g.fillRect(0, 0, size, size);
      g.setColor(Theme.MUTED);
      g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size / 2));
      FontMetrics fm = g.getFontMetrics();
      g.drawString(letter, (size - fm.stringWidth(letter)) / 2, (size - fm.getHeight()) / 2 + fm.getAscent());
      g.setColor(new Color(0, 0, 0, 40));
      g.drawRect(0, 0, size - 1, size - 1);
      g.dispose();
      return out;
   }

   private Heads() {
   }
}
