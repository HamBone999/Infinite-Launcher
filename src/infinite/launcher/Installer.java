package infinite.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Puts a release on disk so it can be launched:
 *
 *   versions/<v>/Infinite.jar        the game, checked against version.json's SHA-1
 *   versions/<v>/version.json        libraries, natives and the base jar it needs
 *   versions/<v>/assets/             27 Alpha 1.0.4 textures, renamed the way the game asks
 *   versions/<v>/natives-<os>/       LWJGL and JInput native libraries
 *   libraries/                       shared maven tree, downloaded from the URLs in version.json
 *   base/a1.0.4.jar                  Mojang's own Alpha 1.0.4 client, straight from Mojang
 *
 * Nothing Mojang made is redistributed. The base jar comes from Mojang's servers, the same file
 * the official launcher downloads, and only after the player has signed in with an account that
 * owns the game.
 */
public final class Installer {
   public static final class Installed {
      public File dir;
      public File gameJar;
      public File assets;
      public File natives;
      public String mainClass;
      public List<File> libraries = new ArrayList<File>();
   }

   private static final String BASE_ID = "a1.0.4";
   private static final String BASE_SHA1 = "e5838277b3bb193e58408713f1fc6e005c5f3c0c";

   public static File dir(String clientVersion) {
      return new File(Dirs.versions(), clientVersion.replaceAll("[^A-Za-z0-9._-]", "_"));
   }

   public static boolean isInstalled(String clientVersion) {
      File d = dir(clientVersion);
      return new File(d, ".installed").isFile() && new File(d, "Infinite.jar").isFile();
   }

   public static List<String> installedVersions() {
      List<String> out = new ArrayList<String>();
      File[] kids = Dirs.versions().listFiles();
      if (kids != null) {
         for (File k : kids) {
            if (new File(k, ".installed").isFile()) {
               out.add(k.getName());
            }
         }
      }
      return out;
   }

   public static void uninstall(String clientVersion) {
      Io.deleteTree(dir(clientVersion));
   }

   /** Downloads whatever is missing and returns everything a launch needs. Safe to re-run. */
   public static Installed ensure(GameRelease r, String clientVersion, Progress p) throws IOException {
      File d = dir(clientVersion);
      if (!isInstalled(clientVersion)) {
         if (r == null) {
            throw new IOException("Version " + clientVersion + " is not installed and GitHub can't be reached to get it.");
         }
         fetchRelease(r, d, p);
      }
      Map<String, Object> manifest = Json.obj(Json.parse(Io.readText(new File(d, "version.json"))));
      Installed inst = new Installed();
      inst.dir = d;
      inst.gameJar = new File(d, "Infinite.jar");
      inst.mainClass = Json.str(manifest, "mainClass");
      if (inst.mainClass == null || inst.mainClass.isEmpty()) {
         inst.mainClass = "net.minecraft.client.Minecraft";
      }

      // Libraries
      List<Lib> libs = selectLibraries(manifest, Os.tag(), Os.arm64());
      long total = 0;
      for (Lib l : libs) {
         if (!l.file().isFile() || l.file().length() != l.size) {
            total += l.size;
         }
      }
      long[] done = { 0 };
      if (total > 0) {
         p.step("Downloading libraries (" + Io.humanBytes(total) + ")");
      }
      List<File> natives = new ArrayList<File>();
      for (Lib l : libs) {
         File f = l.file();
         if (!f.isFile() || (l.size > 0 && f.length() != l.size)) {
            Http.download(l.url, f, l.sha1, l.size, p, done, total);
         }
         (l.natives ? natives : inst.libraries).add(f);
      }

      // Base jar and the 27 textures lifted from it
      File base = baseJar(manifest, p);
      inst.assets = new File(d, "assets");
      File marker = new File(inst.assets, ".complete");
      if (!marker.isFile()) {
         p.step("Preparing textures");
         extractAssets(base, assetList(d), inst.assets);
         Io.writeText(marker, BASE_SHA1 + "\n");
      }

      // Natives, per platform and architecture so a shared folder never mixes them
      inst.natives = new File(d, "natives-" + Os.tag() + (Os.arm64() ? "-arm64" : ""));
      File nmarker = new File(inst.natives, ".complete");
      if (!nmarker.isFile()) {
         p.step("Unpacking native libraries");
         Io.deleteTree(inst.natives);
         inst.natives.mkdirs();
         for (File n : natives) {
            unpackNatives(n, inst.natives);
         }
         Io.writeText(nmarker, "ok\n");
      }
      return inst;
   }

   // ------------------------------------------------------------------ the release zip

   private static void fetchRelease(GameRelease r, File d, Progress p) throws IOException {
      p.step("Downloading Minecraft Infinite " + r.clientVersion + " (" + Io.humanBytes(r.assetSize) + ")");
      File zip = new File(Dirs.cache(), r.assetName);
      if (!zip.isFile() || zip.length() != r.assetSize) {
         Http.download(r.assetUrl, zip, null, r.assetSize, p, new long[] { 0 }, r.assetSize);
      }
      p.step("Installing " + r.clientVersion);
      p.fraction(-1);
      File staging = new File(d.getPath() + ".staging");
      Io.deleteTree(staging);
      staging.mkdirs();
      ZipFile z = new ZipFile(zip);
      try {
         copyOut(z, "version.json", new File(staging, "version.json"), true);
         copyOut(z, "Infinite.jar", new File(staging, "Infinite.jar"), true);
         copyOut(z, "InfiniteLoader.jar", new File(staging, "InfiniteLoader.jar"), false);
      } finally {
         z.close();
      }
      Map<String, Object> manifest = Json.obj(Json.parse(Io.readText(new File(staging, "version.json"))));
      if (Json.num(manifest, "formatVersion", 0) != 1) {
         throw new IOException("Release " + r.clientVersion + " has a manifest this launcher doesn't understand. Update the launcher.");
      }
      String want = null;
      List<Object> files = Json.arr(manifest.get("files"));
      if (files != null) {
         for (Object o : files) {
            Map<String, Object> f = Json.obj(o);
            if ("Infinite.jar".equals(Json.str(f, "path"))) {
               want = Json.str(f, "sha1");
            }
         }
      }
      if (want != null) {
         String got = Io.sha1(new File(staging, "Infinite.jar"));
         if (!got.equalsIgnoreCase(want)) {
            throw new IOException("Infinite.jar in " + r.assetName + " does not match its own manifest (" + got + " vs " + want + ")");
         }
      }
      Properties info = new Properties();
      info.setProperty("tag", r.tag);
      info.setProperty("asset", r.assetName);
      info.setProperty("installed", String.valueOf(System.currentTimeMillis()));
      OutputStream o = new FileOutputStream(new File(staging, ".installed"));
      try {
         info.store(o, "Minecraft Infinite " + r.clientVersion);
      } finally {
         o.close();
      }
      Io.deleteTree(d);
      if (!staging.renameTo(d)) {
         throw new IOException("Could not move " + staging + " into place");
      }
      zip.delete();
   }

   /** Copies the entry whose name is `leaf` at the top of the zip or one folder down. */
   private static void copyOut(ZipFile z, String leaf, File to, boolean required) throws IOException {
      ZipEntry found = null;
      Enumeration<? extends ZipEntry> en = z.entries();
      while (en.hasMoreElements()) {
         ZipEntry e = en.nextElement();
         String n = e.getName();
         if (!e.isDirectory() && (n.equals(leaf) || (n.endsWith("/" + leaf) && n.indexOf('/') == n.length() - leaf.length() - 1))) {
            found = e;
            break;
         }
      }
      if (found == null) {
         if (required) {
            throw new IOException("The release zip has no " + leaf);
         }
         return;
      }
      InputStream in = z.getInputStream(found);
      OutputStream out = new FileOutputStream(to);
      try {
         Io.copy(in, out, null, 0);
      } finally {
         out.close();
         in.close();
      }
   }

   // ------------------------------------------------------------------ libraries

   static final class Lib {
      String name;
      String path;
      String url;
      String sha1;
      long size;
      boolean natives;

      File file() {
         return new File(Dirs.libraries(), path.replace('/', File.separatorChar));
      }
   }

   /**
    * Artifacts, plus the natives for this platform. On arm64 a library's "<os>-arm64" natives are
    * preferred; the plain "<os>" build is only used for a library that has no arm64 build anywhere
    * in the manifest. (LWJGL's arm64 Mac natives live in a separate "legacyfix" entry; taking both
    * would unpack an Intel and an Apple dylib under the same name.)
    */
   static List<Lib> selectLibraries(Map<String, Object> manifest, String os, boolean arm) {
      List<Lib> out = new ArrayList<Lib>();
      List<Object> libs = Json.arr(manifest.get("libraries"));
      if (libs == null) {
         return out;
      }
      Set<String> hasArm = new HashSet<String>();
      if (arm) {
         for (Object o : libs) {
            Map<String, Object> l = Json.obj(o);
            if (Json.obj(Json.path(l, "natives", os + "-arm64")) != null) {
               hasArm.add(groupArtifact(Json.str(l, "name")));
            }
         }
      }
      for (Object o : libs) {
         Map<String, Object> l = Json.obj(o);
         if (l == null) {
            continue;
         }
         Map<String, Object> art = Json.obj(l.get("artifact"));
         if (art != null) {
            out.add(lib(Json.str(l, "name"), art, false));
         }
         Map<String, Object> nat = Json.obj(l.get("natives"));
         if (nat == null) {
            continue;
         }
         Map<String, Object> pick = null;
         if (arm) {
            pick = Json.obj(nat.get(os + "-arm64"));
            if (pick == null && !hasArm.contains(groupArtifact(Json.str(l, "name")))) {
               pick = Json.obj(nat.get(os));
            }
         } else {
            pick = Json.obj(nat.get(os));
         }
         if (pick != null) {
            out.add(lib(Json.str(l, "name"), pick, true));
         }
      }
      return out;
   }

   private static String groupArtifact(String name) {
      if (name == null) {
         return "";
      }
      String[] p = name.split(":");
      return p.length >= 2 ? p[0] + ":" + p[1] : name;
   }

   private static Lib lib(String name, Map<String, Object> m, boolean natives) {
      Lib l = new Lib();
      l.name = name;
      l.path = Json.str(m, "path");
      l.url = Json.str(m, "url");
      l.sha1 = Json.str(m, "sha1");
      l.size = Json.num(m, "size", 0);
      l.natives = natives;
      return l;
   }

   private static void unpackNatives(File jar, File into) throws IOException {
      ZipFile z = new ZipFile(jar);
      try {
         Enumeration<? extends ZipEntry> en = z.entries();
         while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            String n = e.getName();
            if (e.isDirectory() || n.startsWith("META-INF/") || n.indexOf('/') >= 0) {
               continue;
            }
            InputStream in = z.getInputStream(e);
            OutputStream out = new FileOutputStream(new File(into, n));
            try {
               Io.copy(in, out, null, 0);
            } finally {
               out.close();
               in.close();
            }
         }
      } finally {
         z.close();
      }
   }

   // ------------------------------------------------------------------ Alpha 1.0.4

   private static File baseJar(Map<String, Object> manifest, Progress p) throws IOException {
      Map<String, Object> req = Json.obj(Json.path(manifest, "requires", "baseJar"));
      String id = req == null ? BASE_ID : Json.str(req, "id");
      String sha1 = req == null ? BASE_SHA1 : Json.str(req, "sha1");
      long size = req == null ? 0 : Json.num(req, "size", 0);
      File f = new File(Dirs.base(), id + ".jar");
      if (f.isFile() && sha1.equalsIgnoreCase(Io.sha1(f))) {
         return f;
      }
      p.step("Downloading Minecraft " + id + " from Mojang");
      Map<String, Object> vm = Json.obj(Http.getJson("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json", null));
      String url = null;
      for (Object o : Json.arr(vm.get("versions"))) {
         Map<String, Object> v = Json.obj(o);
         if (id.equals(Json.str(v, "id"))) {
            url = Json.str(v, "url");
         }
      }
      if (url == null) {
         throw new IOException("Mojang's version list has no " + id);
      }
      Map<String, Object> client = Json.obj(Json.path(Http.getJson(url, null), "downloads", "client"));
      Http.download(Json.str(client, "url"), f, sha1, size > 0 ? size : Json.num(client, "size", 0), p, new long[] { 0 },
         Json.num(client, "size", 0));
      return f;
   }

   /** destination -> path inside the base jar, from the release's own loader, else the launcher's copy. */
   private static Map<String, String> assetList(File versionDir) throws IOException {
      InputStream in = null;
      ZipFile z = null;
      File loader = new File(versionDir, "InfiniteLoader.jar");
      if (loader.isFile()) {
         z = new ZipFile(loader);
         ZipEntry e = z.getEntry("mojang-assets.txt");
         if (e != null) {
            in = z.getInputStream(e);
         }
      }
      if (in == null) {
         in = Installer.class.getResourceAsStream("/infinite/launcher/mojang-assets.txt");
      }
      Map<String, String> out = new LinkedHashMap<String, String>();
      try {
         BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
         String line;
         while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
               continue;
            }
            int arrow = line.indexOf("<-");
            if (arrow >= 0) {
               out.put(line.substring(0, arrow).trim(), line.substring(arrow + 2).trim());
            } else {
               out.put(line, line);
            }
         }
      } finally {
         in.close();
         if (z != null) {
            z.close();
         }
      }
      return out;
   }

   private static void extractAssets(File base, Map<String, String> list, File into) throws IOException {
      ZipFile z = new ZipFile(base);
      try {
         for (Map.Entry<String, String> e : list.entrySet()) {
            ZipEntry ze = z.getEntry(e.getValue());
            if (ze == null) {
               throw new IOException("Alpha 1.0.4 jar is missing " + e.getValue());
            }
            File out = new File(into, e.getKey().replace('/', File.separatorChar));
            out.getParentFile().mkdirs();
            InputStream in = z.getInputStream(ze);
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

   private Installer() {
   }
}
