import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Draws the launcher icon -- a brick block with an infinity sign -- and writes every size the
 * platforms want: PNGs for the window and Linux, a Windows .ico and a macOS .icns. Pure Java2D so
 * the build machine needs no image tools.
 *
 *   java MakeIcons <png-resource-dir> <packaging-dir> [logo.png]
 *
 * With a logo it is scaled into every size instead of the drawn brick icon.
 */
public class MakeIcons {
   public static void main(String[] a) throws Exception {
      File res = new File(a[0]);
      File pkg = new File(a[1]);
      res.mkdirs();
      pkg.mkdirs();
      BufferedImage master = a.length > 2 ? square(ImageIO.read(new File(a[2]))) : draw(1024);
      ImageIO.write(master, "png", new File(res, "logo.png"));
      for (int s : new int[] { 16, 32, 48, 64, 128, 256 }) {
         ImageIO.write(scale(master, s), "png", new File(res, "icon-" + s + ".png"));
      }
      ImageIO.write(scale(master, 512), "png", new File(pkg, "icon-512.png"));
      writeIco(master, new File(pkg, "icon.ico"), new int[] { 16, 24, 32, 48, 64, 128, 256 });
      writeIcns(master, new File(pkg, "icon.icns"));
      System.out.println("icons written");
   }

   static BufferedImage draw(int n) {
      BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      float m = n * 0.04f;
      Shape tile = new RoundRectangle2D.Float(m, m, n - 2 * m, n - 2 * m, n * 0.22f, n * 0.22f);
      g.setClip(tile);

      // Bricks: 8 rows, alternate rows offset by half a brick, a little colour noise per brick.
      Color[] reds = { new Color(0xB5452B), new Color(0xC24E31), new Color(0xA9402A), new Color(0xBC5335), new Color(0xAE482D) };
      Random rnd = new Random(7);
      int rows = 8;
      float rh = n / (float)rows;
      float bw = n / 3.5f;
      float mortar = n / 72f;
      g.setColor(new Color(0xCFC7BD));
      g.fillRect(0, 0, n, n);
      for (int r = 0; r < rows; r++) {
         float off = (r % 2 == 0) ? 0 : -bw / 2;
         for (float x = off; x < n; x += bw) {
            g.setColor(reds[rnd.nextInt(reds.length)]);
            g.fill(new java.awt.geom.Rectangle2D.Float(x + mortar / 2, r * rh + mortar / 2, bw - mortar, rh - mortar));
            // top-left highlight and bottom shadow on each brick, like the block texture
            g.setColor(new Color(255, 255, 255, 28));
            g.fill(new java.awt.geom.Rectangle2D.Float(x + mortar / 2, r * rh + mortar / 2, bw - mortar, rh * 0.18f));
            g.setColor(new Color(0, 0, 0, 38));
            g.fill(new java.awt.geom.Rectangle2D.Float(x + mortar / 2, r * rh + rh * 0.78f, bw - mortar, rh * 0.22f - mortar / 2));
         }
      }
      // light from the top, vignette at the edges
      g.setPaint(new GradientPaint(0, 0, new Color(255, 230, 200, 40), 0, n, new Color(20, 5, 0, 90)));
      g.fillRect(0, 0, n, n);
      g.setPaint(new RadialGradientPaint(n / 2f, n / 2f, n * 0.75f, new float[] { 0.45f, 1f },
         new Color[] { new Color(0, 0, 0, 0), new Color(0, 0, 0, 120) }));
      g.fillRect(0, 0, n, n);

      // Infinity: a lemniscate of Bernoulli, outlined dark then filled cream.
      Path2D inf = new Path2D.Float();
      float cx = n / 2f, cy = n / 2f, aa = n * 0.36f;
      for (int i = 0; i <= 720; i++) {
         double t = i * Math.PI * 2 / 720;
         double d = 1 + Math.sin(t) * Math.sin(t);
         double x = aa * Math.cos(t) / d;
         double y = aa * Math.sin(t) * Math.cos(t) / d;
         if (i == 0) {
            inf.moveTo(cx + x, cy + y * 1.15);
         } else {
            inf.lineTo(cx + x, cy + y * 1.15);
         }
      }
      inf.closePath();
      g.setStroke(new BasicStroke(n * 0.155f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.setColor(new Color(0, 0, 0, 70));
      g.translate(0, n * 0.018);
      g.draw(inf);
      g.translate(0, -n * 0.018);
      g.setColor(new Color(0x3A1409));
      g.draw(inf);
      g.setStroke(new BasicStroke(n * 0.095f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.setPaint(new GradientPaint(0, cy - aa * 0.5f, new Color(0xFFF8EC), 0, cy + aa * 0.5f, new Color(0xF2D9B8)));
      g.draw(inf);

      // rim
      g.setClip(null);
      g.setComposite(AlphaComposite.SrcOver);
      g.setStroke(new BasicStroke(n * 0.012f));
      g.setColor(new Color(0, 0, 0, 90));
      g.draw(tile);
      g.dispose();
      return img;
   }

   /** Centres a non-square logo on a transparent square. */
   static BufferedImage square(BufferedImage src) {
      int n = Math.max(src.getWidth(), src.getHeight());
      BufferedImage out = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = out.createGraphics();
      g.drawImage(src, (n - src.getWidth()) / 2, (n - src.getHeight()) / 2, null);
      g.dispose();
      return out;
   }

   /** Halving steps then a final bilinear pass: much cleaner than one big jump. */
   static BufferedImage scale(BufferedImage src, int size) {
      BufferedImage cur = src;
      int w = src.getWidth();
      while (w / 2 >= size) {
         w /= 2;
         cur = resize(cur, w);
      }
      return w == size ? cur : resize(cur, size);
   }

   static BufferedImage resize(BufferedImage src, int size) {
      BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = out.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.drawImage(src, 0, 0, size, size, null);
      g.dispose();
      return out;
   }

   static byte[] png(BufferedImage i) throws Exception {
      ByteArrayOutputStream b = new ByteArrayOutputStream();
      ImageIO.write(i, "png", b);
      return b.toByteArray();
   }

   /** ICO with PNG-compressed entries (Windows Vista and later). */
   static void writeIco(BufferedImage master, File f, int[] sizes) throws Exception {
      byte[][] data = new byte[sizes.length][];
      for (int i = 0; i < sizes.length; i++) {
         data[i] = png(scale(master, sizes[i]));
      }
      ByteArrayOutputStream b = new ByteArrayOutputStream();
      le16(b, 0);
      le16(b, 1);
      le16(b, sizes.length);
      int offset = 6 + 16 * sizes.length;
      for (int i = 0; i < sizes.length; i++) {
         b.write(sizes[i] >= 256 ? 0 : sizes[i]);
         b.write(sizes[i] >= 256 ? 0 : sizes[i]);
         b.write(0);
         b.write(0);
         le16(b, 1);
         le16(b, 32);
         le32(b, data[i].length);
         le32(b, offset);
         offset += data[i].length;
      }
      for (byte[] d : data) {
         b.write(d);
      }
      FileOutputStream o = new FileOutputStream(f);
      o.write(b.toByteArray());
      o.close();
   }

   /** ICNS with PNG entries (macOS 10.7 and later). */
   static void writeIcns(BufferedImage master, File f) throws Exception {
      String[] types = { "icp4", "icp5", "icp6", "ic07", "ic08", "ic09", "ic10" };
      int[] sizes = { 16, 32, 64, 128, 256, 512, 1024 };
      ByteArrayOutputStream body = new ByteArrayOutputStream();
      DataOutputStream d = new DataOutputStream(body);
      for (int i = 0; i < types.length; i++) {
         byte[] p = png(scale(master, sizes[i]));
         d.writeBytes(types[i]);
         d.writeInt(p.length + 8);
         d.write(p);
      }
      DataOutputStream o = new DataOutputStream(new FileOutputStream(f));
      o.writeBytes("icns");
      o.writeInt(body.size() + 8);
      o.write(body.toByteArray());
      o.close();
   }

   static void le16(ByteArrayOutputStream b, int v) {
      b.write(v & 255);
      b.write((v >> 8) & 255);
   }

   static void le32(ByteArrayOutputStream b, int v) {
      le16(b, v & 0xffff);
      le16(b, (v >>> 16) & 0xffff);
   }
}
