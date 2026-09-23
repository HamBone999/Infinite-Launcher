package infinite.launcher;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/** launcher.json in the data folder. Unknown keys are kept so newer launchers don't lose them. */
public final class Settings {
   public static final String LATEST = "latest";

   public int memoryMb = 2048;
   public String javaPath = "";
   public String jvmArgs = "";
   public boolean keepOpen = false;
   public boolean showConsole = false;
   public String selectedVersion = LATEST;
   public int width = 0;
   public int height = 0;
   public boolean fullscreen = false;
   public String gameDir = "";
   private Map<String, Object> raw = new LinkedHashMap<String, Object>();

   private static File file() {
      return new File(Dirs.ROOT, "launcher.json");
   }

   public static Settings load() {
      Settings s = new Settings();
      File f = file();
      if (!f.isFile()) {
         return s;
      }
      try {
         Map<String, Object> m = Json.obj(Json.parse(Io.readText(f)));
         if (m == null) {
            return s;
         }
         s.raw = m;
         s.memoryMb = (int)Math.max(512, Math.min(32768, Json.num(m, "memoryMb", s.memoryMb)));
         s.javaPath = orEmpty(Json.str(m, "javaPath"));
         s.jvmArgs = orEmpty(Json.str(m, "jvmArgs"));
         s.keepOpen = Json.bool(m, "keepOpen", s.keepOpen);
         s.showConsole = Json.bool(m, "showConsole", s.showConsole);
         String v = Json.str(m, "selectedVersion");
         s.selectedVersion = v == null || v.isEmpty() ? LATEST : v;
         s.width = (int)Json.num(m, "width", 0);
         s.height = (int)Json.num(m, "height", 0);
         s.fullscreen = Json.bool(m, "fullscreen", false);
         s.gameDir = orEmpty(Json.str(m, "gameDir"));
      } catch (Exception e) {
         Log.warn("launcher.json unreadable, using defaults", e);
      }
      return s;
   }

   public synchronized void save() {
      raw.put("memoryMb", memoryMb);
      raw.put("javaPath", javaPath);
      raw.put("jvmArgs", jvmArgs);
      raw.put("keepOpen", keepOpen);
      raw.put("showConsole", showConsole);
      raw.put("selectedVersion", selectedVersion);
      raw.put("width", width);
      raw.put("height", height);
      raw.put("fullscreen", fullscreen);
      raw.put("gameDir", gameDir);
      try {
         Io.writeText(file(), Json.write(raw));
      } catch (Exception e) {
         Log.warn("could not save launcher.json", e);
      }
   }

   private static String orEmpty(String s) {
      return s == null ? "" : s;
   }
}
