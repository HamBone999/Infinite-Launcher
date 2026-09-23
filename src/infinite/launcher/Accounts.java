package infinite.launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * accounts.json. It holds Microsoft refresh tokens, so on macOS and Linux it is made readable by
 * the owner only; on Windows %APPDATA% is already per-user.
 */
public final class Accounts {
   private final List<Account> list = new ArrayList<Account>();
   private String selected = "";

   private static File file() {
      return new File(Dirs.ROOT, "accounts.json");
   }

   public static Accounts load() {
      Accounts a = new Accounts();
      File f = file();
      if (!f.isFile()) {
         return a;
      }
      try {
         Map<String, Object> m = Json.obj(Json.parse(Io.readText(f)));
         List<Object> arr = Json.arr(m.get("accounts"));
         if (arr != null) {
            for (Object o : arr) {
               Map<String, Object> am = Json.obj(o);
               if (am != null) {
                  a.list.add(Account.fromJson(am));
               }
            }
         }
         String sel = Json.str(m, "selected");
         a.selected = sel == null ? "" : sel;
      } catch (Exception e) {
         Log.warn("accounts.json unreadable", e);
      }
      return a;
   }

   public synchronized void save() {
      Map<String, Object> m = new LinkedHashMap<String, Object>();
      List<Object> arr = new ArrayList<Object>();
      for (Account a : list) {
         arr.add(a.toJson());
      }
      m.put("selected", selected);
      m.put("accounts", arr);
      File f = file();
      try {
         Io.writeText(f, Json.write(m));
         if (!Os.WINDOWS) {
            f.setReadable(false, false);
            f.setReadable(true, true);
            f.setWritable(false, false);
            f.setWritable(true, true);
         }
      } catch (Exception e) {
         Log.warn("could not save accounts.json", e);
      }
   }

   public synchronized List<Account> all() {
      return new ArrayList<Account>(list);
   }

   public synchronized Account selected() {
      for (Account a : list) {
         if (a.uuid.equals(selected)) {
            return a;
         }
      }
      return list.isEmpty() ? null : list.get(0);
   }

   public synchronized void select(Account a) {
      selected = a.uuid;
      save();
   }

   /** Adds or replaces by UUID, and selects it. */
   public synchronized void put(Account a) {
      for (int i = 0; i < list.size(); i++) {
         if (list.get(i).uuid.equals(a.uuid)) {
            list.set(i, a);
            selected = a.uuid;
            save();
            return;
         }
      }
      list.add(a);
      selected = a.uuid;
      save();
   }

   public synchronized void remove(Account a) {
      for (int i = 0; i < list.size(); i++) {
         if (list.get(i).uuid.equals(a.uuid)) {
            list.remove(i);
            break;
         }
      }
      if (selected.equals(a.uuid)) {
         selected = list.isEmpty() ? "" : list.get(0).uuid;
      }
      save();
   }
}
