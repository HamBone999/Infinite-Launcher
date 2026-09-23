package infinite.launcher;

import java.util.LinkedHashMap;
import java.util.Map;

/** One signed-in Microsoft account and the Minecraft profile it owns. */
public final class Account {
   public String uuid = "";
   public String name = "";
   public String msRefreshToken = "";
   public String mcToken = "";
   public long mcTokenExpires;
   public String skinUrl = "";

   public boolean tokenFresh() {
      return mcToken != null && !mcToken.isEmpty() && System.currentTimeMillis() < mcTokenExpires - 10 * 60 * 1000L;
   }

   /** The second positional argument the client's main() takes. */
   public String sessionArg() {
      return "token:" + mcToken + ":" + uuid;
   }

   Map<String, Object> toJson() {
      Map<String, Object> m = new LinkedHashMap<String, Object>();
      m.put("uuid", uuid);
      m.put("name", name);
      m.put("msRefreshToken", msRefreshToken);
      m.put("mcToken", mcToken);
      m.put("mcTokenExpires", mcTokenExpires);
      m.put("skinUrl", skinUrl);
      return m;
   }

   static Account fromJson(Map<String, Object> m) {
      Account a = new Account();
      a.uuid = s(Json.str(m, "uuid"));
      a.name = s(Json.str(m, "name"));
      a.msRefreshToken = s(Json.str(m, "msRefreshToken"));
      a.mcToken = s(Json.str(m, "mcToken"));
      a.mcTokenExpires = Json.num(m, "mcTokenExpires", 0);
      a.skinUrl = s(Json.str(m, "skinUrl"));
      return a;
   }

   private static String s(String v) {
      return v == null ? "" : v;
   }
}
