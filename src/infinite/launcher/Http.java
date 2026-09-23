package infinite.launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

/** HttpURLConnection, wrapped just enough. Java 8 has nothing better built in. */
public final class Http {
   public static final class Response {
      public final int status;
      public final byte[] body;
      public final Map<String, String> headers;

      Response(int status, byte[] body, Map<String, String> headers) {
         this.status = status;
         this.body = body;
         this.headers = headers;
      }

      public String text() {
         try {
            return new String(body, "UTF-8");
         } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
         }
      }

      public Object json() {
         return Json.parse(text());
      }

      public boolean ok() {
         return status >= 200 && status < 300;
      }
   }

   public static final class HttpException extends IOException {
      public final int status;
      public final String body;

      HttpException(String url, int status, String body) {
         super("HTTP " + status + " from " + host(url) + (body == null || body.isEmpty() ? "" : ": " + trim(body)));
         this.status = status;
         this.body = body;
      }

      private static String trim(String b) {
         b = b.replaceAll("\\s+", " ").trim();
         return b.length() > 300 ? b.substring(0, 300) + "..." : b;
      }
   }

   public static Response request(String method, String url, Map<String, String> headers, byte[] body) throws IOException {
      IOException last = null;
      for (int attempt = 0; attempt < 3; attempt++) {
         try {
            return once(method, url, headers, body);
         } catch (HttpException e) {
            if (e.status < 500) {
               throw e;
            }
            last = e;
         } catch (java.net.UnknownHostException e) {
            throw new IOException("No internet connection (could not find " + e.getMessage() + ")", e);
         } catch (IOException e) {
            last = e;
         }
         sleep(700L * (attempt + 1));
      }
      throw last;
   }

   private static Response once(String method, String url, Map<String, String> headers, byte[] body) throws IOException {
      HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
      c.setConnectTimeout(15000);
      c.setReadTimeout(30000);
      c.setRequestMethod(method);
      c.setRequestProperty("User-Agent", BuildInfo.userAgent());
      if (headers != null) {
         for (Map.Entry<String, String> h : headers.entrySet()) {
            c.setRequestProperty(h.getKey(), h.getValue());
         }
      }
      if (body != null) {
         c.setDoOutput(true);
         OutputStream o = c.getOutputStream();
         try {
            o.write(body);
         } finally {
            o.close();
         }
      }
      int status = c.getResponseCode();
      InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
      byte[] data = in == null ? new byte[0] : Io.readAll(in);
      Map<String, String> hs = new LinkedHashMap<String, String>();
      for (Map.Entry<String, java.util.List<String>> e : c.getHeaderFields().entrySet()) {
         if (e.getKey() != null && !e.getValue().isEmpty()) {
            hs.put(e.getKey().toLowerCase(java.util.Locale.ROOT), e.getValue().get(0));
         }
      }
      Response r = new Response(status, data, hs);
      if (status >= 400) {
         throw new HttpException(url, status, r.text());
      }
      return r;
   }

   public static Object getJson(String url, Map<String, String> headers) throws IOException {
      Map<String, String> h = new LinkedHashMap<String, String>();
      h.put("Accept", "application/json");
      if (headers != null) {
         h.putAll(headers);
      }
      return request("GET", url, h, null).json();
   }

   public static Object postJson(String url, Object json, Map<String, String> headers) throws IOException {
      Map<String, String> h = new LinkedHashMap<String, String>();
      h.put("Content-Type", "application/json");
      h.put("Accept", "application/json");
      if (headers != null) {
         h.putAll(headers);
      }
      return request("POST", url, h, Json.write(json).getBytes("UTF-8")).json();
   }

   /** Form POST that returns the parsed body even on 4xx: OAuth reports its states as errors. */
   public static Map<String, Object> postForm(String url, Map<String, String> form) throws IOException {
      StringBuilder b = new StringBuilder();
      for (Map.Entry<String, String> e : form.entrySet()) {
         if (b.length() > 0) {
            b.append('&');
         }
         b.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(e.getValue(), "UTF-8"));
      }
      Map<String, String> h = new LinkedHashMap<String, String>();
      h.put("Content-Type", "application/x-www-form-urlencoded");
      h.put("Accept", "application/json");
      try {
         return Json.obj(request("POST", url, h, b.toString().getBytes("UTF-8")).json());
      } catch (HttpException e) {
         try {
            Map<String, Object> m = Json.obj(Json.parse(e.body));
            if (m != null && m.containsKey("error")) {
               return m;
            }
         } catch (RuntimeException ignored) {
         }
         throw e;
      }
   }

   /**
    * Download to a file, checking size and SHA-1 when known. Goes to name.part first, so an
    * interrupted download never leaves a truncated file where a good one is expected.
    */
   public static void download(String url, File dest, String sha1, long size, final Progress p, final long[] done, final long total)
         throws IOException {
      dest.getAbsoluteFile().getParentFile().mkdirs();
      File part = new File(dest.getPath() + ".part");
      IOException last = null;
      for (int attempt = 0; attempt < 3; attempt++) {
         if (p != null && p.cancelled()) {
            throw new IOException("Cancelled");
         }
         final long before = done == null ? 0 : done[0];
         try {
            HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setRequestProperty("User-Agent", BuildInfo.userAgent());
            int status = c.getResponseCode();
            if (status >= 400) {
               InputStream err = c.getErrorStream();
               throw new HttpException(url, status, err == null ? "" : new String(Io.readAll(err), "UTF-8"));
            }
            InputStream in = c.getInputStream();
            OutputStream out = new FileOutputStream(part);
            try {
               Io.copy(in, out, new Io.Counter() {
                  public void add(long bytes) {
                     if (done != null) {
                        done[0] += bytes;
                        if (p != null && total > 0) {
                           p.fraction(Math.min(1.0, done[0] / (double)total));
                        }
                     }
                     if (p != null && p.cancelled()) {
                        throw new RuntimeException(new IOException("Cancelled"));
                     }
                  }
               }, size > 0 ? size + 1 : 0);
            } catch (RuntimeException re) {
               if (re.getCause() instanceof IOException) {
                  throw (IOException)re.getCause();
               }
               throw re;
            } finally {
               out.close();
               in.close();
            }
            if (size > 0 && part.length() != size) {
               throw new IOException("Size mismatch for " + dest.getName() + ": got " + part.length() + ", expected " + size);
            }
            if (sha1 != null && !sha1.isEmpty()) {
               String got = Io.sha1(part);
               if (!got.equalsIgnoreCase(sha1)) {
                  throw new IOException("Checksum mismatch for " + dest.getName() + ": got " + got + ", expected " + sha1);
               }
            }
            Io.replace(part, dest);
            return;
         } catch (HttpException e) {
            part.delete();
            if (e.status < 500) {
               throw e;
            }
            last = e;
         } catch (java.net.UnknownHostException e) {
            part.delete();
            throw new IOException("No internet connection (could not find " + e.getMessage() + ")", e);
         } catch (IOException e) {
            part.delete();
            if ("Cancelled".equals(e.getMessage())) {
               throw e;
            }
            last = e;
         }
         if (done != null) {
            done[0] = before;
         }
         sleep(1000L * (attempt + 1));
      }
      throw last;
   }

   static String host(String url) {
      try {
         return new URL(url).getHost();
      } catch (Exception e) {
         return url;
      }
   }

   private static void sleep(long ms) {
      try {
         Thread.sleep(ms);
      } catch (InterruptedException ignored) {
         Thread.currentThread().interrupt();
      }
   }

   private Http() {
   }
}
