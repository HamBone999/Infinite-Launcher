package infinite.launcher;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Unpacks the Java runtime archives: .zip on Windows, .tar.gz elsewhere (symlinks and exec bits kept). */
final class Archives {
   static void unzip(File zip, File into) throws IOException {
      ZipFile z = new ZipFile(zip);
      try {
         Enumeration<? extends ZipEntry> en = z.entries();
         while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            File out = safe(into, e.getName());
            if (e.isDirectory()) {
               out.mkdirs();
               continue;
            }
            out.getParentFile().mkdirs();
            InputStream in = z.getInputStream(e);
            OutputStream o = new FileOutputStream(out);
            try {
               Io.copy(in, o, null, 0);
            } finally {
               o.close();
               in.close();
            }
         }
      } finally {
         z.close();
      }
   }

   static void untarGz(File tgz, File into) throws IOException {
      InputStream in = new BufferedInputStream(new GZIPInputStream(new FileInputStream(tgz), 65536), 65536);
      try {
         byte[] hdr = new byte[512];
         String longName = null;
         String longLink = null;
         while (true) {
            if (!readFully(in, hdr)) {
               return;
            }
            if (allZero(hdr)) {
               return;
            }
            String name = str(hdr, 0, 100);
            int mode = (int)octal(hdr, 100, 8);
            long size = octal(hdr, 124, 12);
            char type = (char)hdr[156];
            String link = str(hdr, 157, 100);
            if ("ustar".equals(str(hdr, 257, 5))) {
               String prefix = str(hdr, 345, 155);
               if (!prefix.isEmpty()) {
                  name = prefix + "/" + name;
               }
            }
            if (type == 'L' || type == 'K' || type == 'x' || type == 'g') {
               byte[] data = new byte[(int)size];
               readFully(in, data);
               skip(in, pad(size));
               String text = new String(data, "UTF-8");
               if (type == 'L') {
                  longName = text.replace("\0", "");
               } else if (type == 'K') {
                  longLink = text.replace("\0", "");
               } else if (type == 'x') {
                  String p = pax(text, "path");
                  if (p != null) {
                     longName = p;
                  }
                  String lp = pax(text, "linkpath");
                  if (lp != null) {
                     longLink = lp;
                  }
               }
               continue;
            }
            if (longName != null) {
               name = longName;
               longName = null;
            }
            if (longLink != null) {
               link = longLink;
               longLink = null;
            }
            File out = safe(into, name);
            if (type == '5') {
               out.mkdirs();
            } else if (type == '2') {
               out.getParentFile().mkdirs();
               out.delete();
               Files.createSymbolicLink(out.toPath(), new File(link).toPath());
            } else if (type == '0' || type == '\0' || type == '7') {
               out.getParentFile().mkdirs();
               OutputStream o = new FileOutputStream(out);
               try {
                  long left = size;
                  byte[] buf = new byte[65536];
                  while (left > 0) {
                     int n = in.read(buf, 0, (int)Math.min(buf.length, left));
                     if (n < 0) {
                        throw new IOException("Truncated archive");
                     }
                     o.write(buf, 0, n);
                     left -= n;
                  }
               } finally {
                  o.close();
               }
               if ((mode & 0100) != 0) {
                  out.setExecutable(true, false);
               }
               skip(in, pad(size));
               continue;
            }
            skip(in, size + pad(size));
         }
      } finally {
         in.close();
      }
   }

   private static String pax(String text, String key) {
      for (String rec : text.split("\n")) {
         int sp = rec.indexOf(' ');
         int eq = rec.indexOf('=');
         if (sp > 0 && eq > sp && rec.substring(sp + 1, eq).equals(key)) {
            return rec.substring(eq + 1);
         }
      }
      return null;
   }

   /** Refuses entries that would land outside the target ("zip slip"). */
   private static File safe(File root, String name) throws IOException {
      File f = new File(root, name);
      String r = root.getCanonicalPath() + File.separator;
      String c = f.getCanonicalPath();
      if (!(c + File.separator).startsWith(r) && !c.equals(root.getCanonicalPath())) {
         throw new IOException("Archive entry escapes its folder: " + name);
      }
      return f;
   }

   private static long pad(long size) {
      return (512 - size % 512) % 512;
   }

   private static boolean readFully(InputStream in, byte[] b) throws IOException {
      int off = 0;
      while (off < b.length) {
         int n = in.read(b, off, b.length - off);
         if (n < 0) {
            if (off == 0) {
               return false;
            }
            throw new IOException("Truncated archive");
         }
         off += n;
      }
      return true;
   }

   private static void skip(InputStream in, long n) throws IOException {
      while (n > 0) {
         long s = in.skip(n);
         if (s <= 0) {
            if (in.read() < 0) {
               return;
            }
            s = 1;
         }
         n -= s;
      }
   }

   private static boolean allZero(byte[] b) {
      for (byte x : b) {
         if (x != 0) {
            return false;
         }
      }
      return true;
   }

   private static String str(byte[] b, int off, int len) {
      int end = off;
      while (end < off + len && b[end] != 0) {
         end++;
      }
      try {
         return new String(b, off, end - off, "UTF-8");
      } catch (java.io.UnsupportedEncodingException e) {
         throw new IllegalStateException(e);
      }
   }

   private static long octal(byte[] b, int off, int len) {
      if ((b[off] & 0x80) != 0) {   // GNU base-256 for big sizes
         long v = 0;
         for (int i = off + 1; i < off + len; i++) {
            v = (v << 8) | (b[i] & 0xff);
         }
         return v;
      }
      String s = str(b, off, len).trim();
      return s.isEmpty() ? 0 : Long.parseLong(s, 8);
   }

   private Archives() {
   }
}
