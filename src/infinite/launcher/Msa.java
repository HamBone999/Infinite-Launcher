package infinite.launcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Microsoft sign-in, the chain every Java Edition launcher uses:
 *
 *   Microsoft (device code, OAuth 2)  ->  Xbox Live user token  ->  XSTS token for
 *   api.minecraftservices.com  ->  Minecraft access token  ->  profile (name + UUID)
 *
 * Device code rather than a browser redirect: nothing listens on a local port, it works behind
 * any firewall, and the player types a short code into microsoft.com/link, which is the same page
 * consoles use.
 */
public final class Msa {
   private static final String AUTHORITY = "https://login.microsoftonline.com/consumers/oauth2/v2.0/";
   private static final String SCOPE = "XboxLive.signin offline_access";

   public static final class DeviceCode {
      public final String deviceCode;
      public final String userCode;
      public final String verificationUri;
      public final long expiresAt;
      public int interval;

      DeviceCode(Map<String, Object> m) {
         deviceCode = Json.str(m, "device_code");
         userCode = Json.str(m, "user_code");
         String uri = Json.str(m, "verification_uri");
         verificationUri = uri == null ? "https://www.microsoft.com/link" : uri;
         expiresAt = System.currentTimeMillis() + Json.num(m, "expires_in", 900) * 1000L;
         interval = (int)Math.max(1, Json.num(m, "interval", 5));
      }

      /** microsoft.com/link accepts the code pre-filled. */
      public String browserUrl() {
         return verificationUri.contains("microsoft.com/link") ? verificationUri + "?otc=" + userCode : verificationUri;
      }
   }

   public interface Cancel {
      boolean cancelled();
   }

   public static boolean configured() {
      return !BuildInfo.MSA_CLIENT_ID.isEmpty();
   }

   public static DeviceCode start() throws AuthException {
      requireClientId();
      Map<String, String> form = new LinkedHashMap<String, String>();
      form.put("client_id", BuildInfo.MSA_CLIENT_ID);
      form.put("scope", SCOPE);
      try {
         Map<String, Object> m = Http.postForm(AUTHORITY + "devicecode", form);
         if (m == null || m.containsKey("error")) {
            throw new AuthException("Microsoft refused the sign-in request: " + describe(m), false);
         }
         return new DeviceCode(m);
      } catch (IOException e) {
         throw new AuthException("Could not reach Microsoft: " + e.getMessage(), e);
      }
   }

   /** Waits for the player to finish on microsoft.com, then signs all the way in to Minecraft. */
   public static Account waitFor(DeviceCode dc, Cancel cancel) throws AuthException {
      Map<String, String> form = new LinkedHashMap<String, String>();
      form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
      form.put("client_id", BuildInfo.MSA_CLIENT_ID);
      form.put("device_code", dc.deviceCode);
      while (true) {
         if (cancel.cancelled()) {
            throw new AuthException("Sign-in cancelled.", false);
         }
         if (System.currentTimeMillis() > dc.expiresAt) {
            throw new AuthException("The code expired before sign-in finished. Try again.", false);
         }
         sleep(dc.interval * 1000L, cancel);
         Map<String, Object> m;
         try {
            m = Http.postForm(AUTHORITY + "token", form);
         } catch (IOException e) {
            continue;   // a dropped poll is not a failed sign-in
         }
         String err = Json.str(m, "error");
         if (err == null) {
            return fromMicrosoft(Json.str(m, "access_token"), Json.str(m, "refresh_token"));
         }
         if (err.equals("authorization_pending")) {
            continue;
         }
         if (err.equals("slow_down")) {
            dc.interval += 5;
            continue;
         }
         if (err.equals("authorization_declined")) {
            throw new AuthException("Sign-in was declined on the Microsoft page.", false);
         }
         if (err.equals("expired_token")) {
            throw new AuthException("The code expired before sign-in finished. Try again.", false);
         }
         throw new AuthException("Microsoft sign-in failed: " + describe(m), false);
      }
   }

   /** New Minecraft token from the stored Microsoft refresh token. No browser involved. */
   public static Account refresh(Account a) throws AuthException {
      requireClientId();
      if (a.msRefreshToken == null || a.msRefreshToken.isEmpty()) {
         throw new AuthException("Sign in again to continue.", true);
      }
      Map<String, String> form = new LinkedHashMap<String, String>();
      form.put("grant_type", "refresh_token");
      form.put("client_id", BuildInfo.MSA_CLIENT_ID);
      form.put("refresh_token", a.msRefreshToken);
      form.put("scope", SCOPE);
      Map<String, Object> m;
      try {
         m = Http.postForm(AUTHORITY + "token", form);
      } catch (IOException e) {
         throw new AuthException("Could not reach Microsoft: " + e.getMessage(), e);
      }
      if (m.containsKey("error")) {
         throw new AuthException("Your Microsoft sign-in has expired. Sign in again.", true);
      }
      return fromMicrosoft(Json.str(m, "access_token"), Json.str(m, "refresh_token"));
   }

   private static Account fromMicrosoft(String msAccess, String msRefresh) throws AuthException {
      try {
         // Xbox Live user token
         Map<String, Object> props = new LinkedHashMap<String, Object>();
         props.put("AuthMethod", "RPS");
         props.put("SiteName", "user.auth.xboxlive.com");
         props.put("RpsTicket", "d=" + msAccess);
         Map<String, Object> req = new LinkedHashMap<String, Object>();
         req.put("Properties", props);
         req.put("RelyingParty", "http://auth.xboxlive.com");
         req.put("TokenType", "JWT");
         Map<String, Object> xbl = Json.obj(Http.postJson("https://user.auth.xboxlive.com/user/authenticate", req, null));
         String xblToken = Json.str(xbl, "Token");
         String uhs = userHash(xbl);

         // XSTS for Minecraft services
         Map<String, Object> xprops = new LinkedHashMap<String, Object>();
         xprops.put("SandboxId", "RETAIL");
         List<Object> tokens = new ArrayList<Object>();
         tokens.add(xblToken);
         xprops.put("UserTokens", tokens);
         Map<String, Object> xreq = new LinkedHashMap<String, Object>();
         xreq.put("Properties", xprops);
         xreq.put("RelyingParty", "rp://api.minecraftservices.com/");
         xreq.put("TokenType", "JWT");
         Map<String, Object> xsts;
         try {
            xsts = Json.obj(Http.postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xreq, null));
         } catch (Http.HttpException e) {
            throw new AuthException(xstsError(e.body), false);
         }

         // Minecraft
         Map<String, Object> mreq = new LinkedHashMap<String, Object>();
         mreq.put("identityToken", "XBL3.0 x=" + uhs + ";" + Json.str(xsts, "Token"));
         Map<String, Object> mc;
         try {
            mc = Json.obj(Http.postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mreq, null));
         } catch (Http.HttpException e) {
            if (e.status == 403 && e.body != null && e.body.toLowerCase().contains("app registration")) {
               throw new AuthException("Mojang has not approved this launcher's Microsoft app ID yet, so Minecraft "
                  + "refuses the sign-in. Nothing is wrong with your account.", false);
            }
            throw e;
         }
         Account a = new Account();
         a.msRefreshToken = msRefresh;
         a.mcToken = Json.str(mc, "access_token");
         a.mcTokenExpires = System.currentTimeMillis() + Json.num(mc, "expires_in", 86400) * 1000L;
         return withProfile(a);
      } catch (AuthException e) {
         throw e;
      } catch (IOException e) {
         throw new AuthException("Sign-in failed: " + e.getMessage(), e);
      } catch (RuntimeException e) {
         throw new AuthException("Sign-in failed: unexpected reply (" + e.getMessage() + ")", e);
      }
   }

   private static Account withProfile(Account a) throws IOException, AuthException {
      Map<String, String> auth = new LinkedHashMap<String, String>();
      auth.put("Authorization", "Bearer " + a.mcToken);
      Map<String, Object> p;
      try {
         p = Json.obj(Http.getJson("https://api.minecraftservices.com/minecraft/profile", auth));
      } catch (Http.HttpException e) {
         if (e.status == 404) {
            throw new AuthException("This Microsoft account doesn't own Minecraft: Java Edition, or hasn't picked a "
               + "username yet. Buy or set it up at minecraft.net, then sign in again.", false);
         }
         throw e;
      }
      a.uuid = Json.str(p, "id");
      a.name = Json.str(p, "name");
      List<Object> skins = Json.arr(p.get("skins"));
      if (skins != null) {
         for (Object o : skins) {
            Map<String, Object> s = Json.obj(o);
            if ("ACTIVE".equals(Json.str(s, "state"))) {
               a.skinUrl = Json.str(s, "url").replace("http://", "https://");
            }
         }
      }
      return a;
   }

   private static String userHash(Map<String, Object> xbl) {
      List<Object> xui = Json.arr(Json.path(xbl, "DisplayClaims", "xui"));
      return Json.str(Json.obj(xui.get(0)), "uhs");
   }

   /** Xbox's refusals come as numeric XErr codes. These are the ones players actually hit. */
   static String xstsError(String body) {
      long code = 0;
      try {
         code = Json.num(Json.obj(Json.parse(body)), "XErr", 0);
      } catch (RuntimeException ignored) {
      }
      if (code == 2148916233L) {
         return "This Microsoft account has no Xbox profile yet. Sign in once at minecraft.net or xbox.com to create one, then try again.";
      }
      if (code == 2148916235L) {
         return "Xbox Live is not available in your country, so Microsoft accounts from there can't sign in.";
      }
      if (code == 2148916236L || code == 2148916237L) {
         return "This account needs adult verification on xbox.com before it can sign in.";
      }
      if (code == 2148916238L) {
         return "This account belongs to someone under 18. An adult has to add it to a Microsoft family group first.";
      }
      return "Xbox Live refused the sign-in (" + (code == 0 ? "no error code" : "XErr " + code) + ").";
   }

   private static String describe(Map<String, Object> m) {
      if (m == null) {
         return "no reply";
      }
      String d = Json.str(m, "error_description");
      String e = Json.str(m, "error");
      if (d != null) {
         for (String stop : new String[] { "\r\n", "\n", " Trace ID:", " Correlation ID:", " Timestamp:" }) {
            int cut = d.indexOf(stop);
            d = cut > 0 ? d.substring(0, cut) : d;
         }
      }
      if (d != null && d.contains("AADSTS70002")) {
         return "the launcher's Microsoft app doesn't allow this kind of sign-in yet (\"Allow public client flows\" is "
            + "off in its Azure app registration)";
      }
      if ("unauthorized_client".equals(e) || "invalid_client".equals(e)) {
         return "this launcher's Microsoft app ID isn't valid (" + (d == null ? e : d) + ")";
      }
      return (e == null ? "" : e) + (d == null ? "" : " -- " + d);
   }

   private static void requireClientId() throws AuthException {
      if (!configured()) {
         throw new AuthException("This build of the launcher has no Microsoft app ID, so it can't sign in. "
            + "Set msa.clientId in launcher.properties and rebuild.", false);
      }
   }

   private static void sleep(long ms, Cancel cancel) {
      long end = System.currentTimeMillis() + ms;
      while (System.currentTimeMillis() < end && !cancel.cancelled()) {
         try {
            Thread.sleep(200);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
         }
      }
   }

   private Msa() {
   }
}
