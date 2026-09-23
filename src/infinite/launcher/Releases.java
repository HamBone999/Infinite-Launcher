package infinite.launcher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads GitHub releases. Game versions come from the game repository: any release with a client
 * zip attached (Infinite-1.0-XXXXXX.zip, and the older unhyphenated spellings) is one, so nothing
 * about publishing the game changes. Launcher updates come from the launcher's own repository: a
 * release tagged vX.Y.Z (or launcher-vX.Y.Z) with InfiniteLauncher.jar attached.
 *
 * The list is cached with its ETag. A 304 costs nothing against GitHub's 60-an-hour anonymous
 * limit, and the cache is what makes the launcher usable offline.
 */
public final class Releases {
   private static final Pattern CLIENT_ZIP = Pattern.compile("(?i)^infinite[-_.]?1\\.?0[-_.]?(\\d{6})\\.zip$");
   private static final Pattern LAUNCHER_TAG = Pattern.compile("^(?:launcher-)?v(\\d+(?:\\.\\d+)*(?:-[\\w.]+)?)$");

   public final List<GameRelease> games = new ArrayList<GameRelease>();
   public LauncherRelease newestLauncher;
   /** True when GitHub couldn't be reached and this came from the cache. */
   public boolean offline;

   public static final class LauncherRelease {
      public String version;
      public String url;
      public long size;
      public String sha256;
      public String notes;
   }

   public GameRelease latest() {
      return games.isEmpty() ? null : games.get(0);
   }

   public GameRelease find(String clientVersion) {
      for (GameRelease r : games) {
         if (r.clientVersion.equals(clientVersion)) {
            return r;
         }
      }
      return null;
   }

   public static Releases fetch() {
      boolean[] offline = { false };
      String games = list(BuildInfo.REPO, "releases", offline);
      Releases rs = new Releases();
      rs.offline = offline[0];
      if (games != null) {
         try {
            rs.parse(Json.arr(Json.parse(games)), false);
         } catch (RuntimeException e) {
            Log.warn("could not read the release list", e);
         }
      }
      if (!BuildInfo.LAUNCHER_REPO.equals(BuildInfo.REPO)) {
         String launchers = list(BuildInfo.LAUNCHER_REPO, "launcher-releases", new boolean[1]);
         if (launchers != null) {
            try {
               rs.parse(Json.arr(Json.parse(launchers)), true);
            } catch (RuntimeException e) {
               Log.warn("could not read the launcher release list", e);
            }
         }
      }
      return rs;
   }

   /** The release list JSON for a repository, from GitHub or, failing that, the cache. */
   private static String list(String repo, String name, boolean[] offline) {
      File cache = new File(Dirs.cache(), name + ".json");
      File etagFile = new File(Dirs.cache(), name + ".etag");
      String body = null;
      try {
         Map<String, String> h = new LinkedHashMap<String, String>();
         h.put("Accept", "application/vnd.github+json");
         h.put("X-GitHub-Api-Version", "2022-11-28");
         if (cache.isFile() && etagFile.isFile()) {
            h.put("If-None-Match", Io.readText(etagFile).trim());
         }
         Http.Response r = Http.request("GET", "https://api.github.com/repos/" + repo + "/releases?per_page=100", h, null);
         if (r.status == 304) {
            body = Io.readText(cache);
         } else {
            body = r.text();
            Io.writeText(cache, body);
            String etag = r.headers.get("etag");
            if (etag != null) {
               Io.writeText(etagFile, etag);
            }
         }
      } catch (Http.HttpException e) {
         if (e.status == 404) {
            Log.info(repo + " has no releases yet");
            return null;
         }
         Log.warn("GitHub releases for " + repo + " unavailable: " + e.getMessage(), null);
         offline[0] = true;
         body = readCache(cache);
      } catch (IOException e) {
         Log.warn("GitHub releases for " + repo + " unavailable: " + e.getMessage(), null);
         offline[0] = true;
         body = readCache(cache);
      }
      return body;
   }

   private static String readCache(File cache) {
      try {
         return cache.isFile() ? Io.readText(cache) : null;
      } catch (IOException e) {
         return null;
      }
   }

   void parse(List<Object> list, boolean launcherRepo) {
      if (list == null) {
         return;
      }
      List<GameRelease> all = new ArrayList<GameRelease>();
      for (Object o : list) {
         Map<String, Object> r = Json.obj(o);
         if (r == null || Json.bool(r, "draft", false)) {
            continue;
         }
         String tag = Json.str(r, "tag_name");
         List<Object> assets = Json.arr(r.get("assets"));
         if (tag == null || assets == null) {
            continue;
         }
         Matcher lt = LAUNCHER_TAG.matcher(tag);
         boolean isLauncherTag = launcherRepo ? lt.matches() : lt.matches() && tag.startsWith("launcher-");
         for (Object ao : assets) {
            Map<String, Object> a = Json.obj(ao);
            String name = Json.str(a, "name");
            if (name == null) {
               continue;
            }
            if (isLauncherTag && name.equals("InfiniteLauncher.jar")) {
               LauncherRelease l = new LauncherRelease();
               l.version = lt.group(1);
               l.url = Json.str(a, "browser_download_url");
               l.size = Json.num(a, "size", 0);
               String digest = Json.str(a, "digest");
               l.sha256 = digest != null && digest.startsWith("sha256:") ? digest.substring(7) : null;
               l.notes = Json.str(r, "body");
               if (newestLauncher == null || BuildInfo.compare(l.version, newestLauncher.version) > 0) {
                  newestLauncher = l;
               }
               continue;
            }
            Matcher m = CLIENT_ZIP.matcher(name);
            if (!launcherRepo && m.matches()) {
               GameRelease g = new GameRelease();
               g.tag = tag;
               g.title = Json.str(r, "name");
               g.clientVersion = "1.0-" + m.group(1);
               g.published = Json.str(r, "published_at");
               g.notes = Json.str(r, "body");
               g.pageUrl = Json.str(r, "html_url");
               g.assetName = name;
               g.assetUrl = Json.str(a, "browser_download_url");
               g.assetSize = Json.num(a, "size", 0);
               all.add(g);
               break;
            }
         }
      }
      // Newest first. Two releases have reused an older client zip; the version is what gets
      // installed, so each appears once, under the newest release that shipped it.
      java.util.Collections.sort(all, new java.util.Comparator<GameRelease>() {
         public int compare(GameRelease a, GameRelease b) {
            return String.valueOf(b.published).compareTo(String.valueOf(a.published));
         }
      });
      Set<String> seen = new LinkedHashSet<String>();
      for (GameRelease g : all) {
         if (seen.add(g.clientVersion)) {
            games.add(g);
         }
      }
   }
}
