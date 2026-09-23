package infinite.launcher;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Finds the Java 8 the game needs. The game's mod stack (LaunchWrapper + Mixin) is Java 8 only.
 *
 * In order: the path set in Settings; the Java the launcher itself is running on, when that is 8
 * (it is in every packaged build -- the installers bundle one); otherwise a runtime the launcher
 * downloads once into runtime/: Eclipse Temurin 8, or Azul Zulu 8 on Apple Silicon Macs, where
 * Temurin publishes no Java 8.
 */
public final class Runtimes {
   public static File java(Settings s, Progress p) throws IOException {
      if (s.javaPath != null && !s.javaPath.trim().isEmpty()) {
         File f = new File(s.javaPath.trim());
         if (f.isDirectory()) {
            f = bin(f);
         }
         if (f == null || !f.isFile()) {
            throw new IOException("The Java set in Settings doesn't exist: " + s.javaPath);
         }
         return f;
      }
      if (System.getProperty("java.specification.version", "").equals("1.8")) {
         File f = bin(new File(System.getProperty("java.home")));
         if (f != null) {
            return f;
         }
      }
      return managed(p);
   }

   /** javaw on Windows: java.exe started from a windowed program opens a console window. */
   static File bin(File home) {
      File[] tries = Os.WINDOWS
         ? new File[] { new File(home, "bin/javaw.exe"), new File(home, "bin/java.exe") }
         : new File[] { new File(home, "bin/java"), new File(home, "Contents/Home/bin/java") };
      for (File t : tries) {
         if (t.isFile()) {
            return t;
         }
      }
      return null;
   }

   private static File managed(Progress p) throws IOException {
      boolean arm = Os.arm64();
      String key = "java8-" + Os.tag() + (arm ? "-arm64" : "-x64");
      File dir = new File(Dirs.runtimes(), key);
      File done = new File(dir, ".complete");
      if (done.isFile()) {
         File f = find(dir);
         if (f != null) {
            return f;
         }
      }
      String url;
      String sha256;
      long size;
      String name;
      if (Os.MAC && arm) {
         List<Object> list = Json.arr(Http.getJson("https://api.azul.com/metadata/v1/zulu/packages/?java_version=8&os=macos"
            + "&arch=aarch64&java_package_type=jre&archive_type=tar.gz&javafx_bundled=false&latest=true&release_status=ga"
            + "&availability_types=CA", null));
         if (list == null || list.isEmpty()) {
            throw new IOException("No Java 8 for Apple Silicon was offered by Azul");
         }
         Map<String, Object> pkg = Json.obj(list.get(0));
         Map<String, Object> det = Json.obj(Http.getJson("https://api.azul.com/metadata/v1/zulu/packages/"
            + Json.str(pkg, "package_uuid"), null));
         url = Json.str(det, "download_url");
         sha256 = Json.str(det, "sha256_hash");
         size = Json.num(det, "size", 0);
         name = Json.str(det, "name");
      } else {
         String os = Os.WINDOWS ? "windows" : Os.MAC ? "mac" : "linux";
         String a = arm ? "aarch64" : "x64";
         List<Object> list = Json.arr(Http.getJson("https://api.adoptium.net/v3/assets/latest/8/hotspot?os=" + os
            + "&architecture=" + a + "&image_type=jre&vendor=eclipse", null));
         if (list == null || list.isEmpty()) {
            throw new IOException("No Java 8 runtime is published for " + os + " " + a);
         }
         Map<String, Object> pkg = Json.obj(Json.path(list.get(0), "binary", "package"));
         url = Json.str(pkg, "link");
         sha256 = Json.str(pkg, "checksum");
         size = Json.num(pkg, "size", 0);
         name = Json.str(pkg, "name");
      }
      p.step("Downloading Java 8 (" + Io.humanBytes(size) + ", one time)");
      File archive = new File(Dirs.cache(), name);
      Http.download(url, archive, null, size, p, new long[] { 0 }, size);
      if (sha256 != null && !sha256.isEmpty() && !Io.sha256(archive).equalsIgnoreCase(sha256)) {
         archive.delete();
         throw new IOException("Java download failed its checksum. Try again.");
      }
      p.step("Unpacking Java 8");
      p.fraction(-1);
      Io.deleteTree(dir);
      dir.mkdirs();
      if (name.endsWith(".zip")) {
         Archives.unzip(archive, dir);
      } else {
         Archives.untarGz(archive, dir);
      }
      archive.delete();
      File f = find(dir);
      if (f == null) {
         throw new IOException("The Java download didn't contain a java executable");
      }
      f.setExecutable(true, false);
      Io.writeText(done, url + "\n");
      return f;
   }

   /** The unpacked archive has one top folder, and on macOS a Contents/Home inside that. */
   private static File find(File dir) {
      File direct = bin(dir);
      if (direct != null) {
         return direct;
      }
      File[] kids = dir.listFiles();
      if (kids != null) {
         for (File k : kids) {
            if (k.isDirectory()) {
               File f = bin(k);
               if (f != null) {
                  return f;
               }
            }
         }
      }
      return null;
   }

   private Runtimes() {
   }
}
