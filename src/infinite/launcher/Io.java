package infinite.launcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

/** File helpers. Every write goes to a temporary file first and is renamed into place. */
public final class Io {
   public static byte[] readAll(InputStream in) throws IOException {
      try {
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         copy(in, out, null, 0);
         return out.toByteArray();
      } finally {
         in.close();
      }
   }

   public static String readText(File f) throws IOException {
      return new String(readAll(new FileInputStream(f)), "UTF-8");
   }

   public static void writeText(File f, String text) throws IOException {
      writeBytes(f, text.getBytes("UTF-8"));
   }

   public static void writeBytes(File f, byte[] data) throws IOException {
      File parent = f.getAbsoluteFile().getParentFile();
      if (parent != null) {
         parent.mkdirs();
      }
      File tmp = new File(parent, f.getName() + ".tmp");
      OutputStream out = new FileOutputStream(tmp);
      try {
         out.write(data);
      } finally {
         out.close();
      }
      replace(tmp, f);
   }

   /** Rename, replacing the target. Windows will not rename over an existing file. */
   public static void replace(File from, File to) throws IOException {
      if (to.exists() && !to.delete()) {
         throw new IOException("Could not replace " + to + " -- is it open in another program?");
      }
      if (!from.renameTo(to)) {
         throw new IOException("Could not move " + from + " to " + to);
      }
   }

   public interface Counter {
      void add(long bytes);
   }

   public static long copy(InputStream in, OutputStream out, Counter counter, long limit) throws IOException {
      byte[] buf = new byte[65536];
      long total = 0;
      int n;
      while ((n = in.read(buf)) > 0) {
         out.write(buf, 0, n);
         total += n;
         if (counter != null) {
            counter.add(n);
         }
         if (limit > 0 && total > limit) {
            throw new IOException("Download is larger than expected (" + total + " bytes)");
         }
      }
      return total;
   }

   public static String sha1(File f) throws IOException {
      return digest(f, "SHA-1");
   }

   public static String sha256(File f) throws IOException {
      return digest(f, "SHA-256");
   }

   private static String digest(File f, String alg) throws IOException {
      try {
         MessageDigest md = MessageDigest.getInstance(alg);
         InputStream in = new FileInputStream(f);
         try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
               md.update(buf, 0, n);
            }
         } finally {
            in.close();
         }
         return hex(md.digest());
      } catch (java.security.NoSuchAlgorithmException e) {
         throw new IOException(e);
      }
   }

   public static String hex(byte[] b) {
      StringBuilder s = new StringBuilder(b.length * 2);
      for (byte x : b) {
         s.append(Character.forDigit((x >> 4) & 15, 16)).append(Character.forDigit(x & 15, 16));
      }
      return s.toString();
   }

   public static void deleteTree(File f) {
      if (f == null || !f.exists()) {
         return;
      }
      File[] kids = f.isDirectory() && !isSymlink(f) ? f.listFiles() : null;
      if (kids != null) {
         for (File k : kids) {
            deleteTree(k);
         }
      }
      f.delete();
   }

   static boolean isSymlink(File f) {
      return java.nio.file.Files.isSymbolicLink(f.toPath());
   }

   public static String humanBytes(long b) {
      if (b < 1024) {
         return b + " B";
      }
      if (b < 1024 * 1024) {
         return (b / 1024) + " KB";
      }
      return String.format("%.1f MB", b / 1048576.0);
   }

   private Io() {
   }
}
