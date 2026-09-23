package infinite.launcher;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON reader and writer. Objects are LinkedHashMaps, arrays Lists, numbers Doubles or Longs. */
public final class Json {
   private final String s;
   private int i;

   private Json(String s) {
      this.s = s;
   }

   public static Object parse(String text) {
      Json p = new Json(text);
      p.ws();
      Object v = p.value();
      p.ws();
      if (p.i != p.s.length()) {
         throw p.err("trailing characters");
      }
      return v;
   }

   @SuppressWarnings("unchecked")
   public static Map<String, Object> obj(Object o) {
      return o instanceof Map ? (Map<String, Object>)o : null;
   }

   @SuppressWarnings("unchecked")
   public static List<Object> arr(Object o) {
      return o instanceof List ? (List<Object>)o : null;
   }

   public static String str(Map<String, Object> m, String k) {
      Object v = m == null ? null : m.get(k);
      return v == null ? null : v instanceof String ? (String)v : String.valueOf(v);
   }

   public static long num(Map<String, Object> m, String k, long def) {
      Object v = m == null ? null : m.get(k);
      if (v instanceof Number) {
         return ((Number)v).longValue();
      }
      if (v instanceof String) {
         try {
            return Long.parseLong((String)v);
         } catch (NumberFormatException e) {
            return def;
         }
      }
      return def;
   }

   public static boolean bool(Map<String, Object> m, String k, boolean def) {
      Object v = m == null ? null : m.get(k);
      return v instanceof Boolean ? (Boolean)v : def;
   }

   /** Walks a path of object keys: path(root, "a", "b") is root.a.b, or null anywhere along the way. */
   public static Object path(Object root, String... keys) {
      Object cur = root;
      for (String k : keys) {
         Map<String, Object> m = obj(cur);
         if (m == null) {
            return null;
         }
         cur = m.get(k);
      }
      return cur;
   }

   private RuntimeException err(String what) {
      return new IllegalArgumentException("JSON " + what + " at " + i);
   }

   private void ws() {
      while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
         i++;
      }
   }

   private Object value() {
      if (i >= s.length()) {
         throw err("unexpected end");
      }
      char c = s.charAt(i);
      if (c == '{') {
         return object();
      } else if (c == '[') {
         return array();
      } else if (c == '"') {
         return string();
      } else if (s.startsWith("true", i)) {
         i += 4;
         return Boolean.TRUE;
      } else if (s.startsWith("false", i)) {
         i += 5;
         return Boolean.FALSE;
      } else if (s.startsWith("null", i)) {
         i += 4;
         return null;
      } else {
         return number();
      }
   }

   private Map<String, Object> object() {
      Map<String, Object> m = new LinkedHashMap<String, Object>();
      i++;
      ws();
      if (i < s.length() && s.charAt(i) == '}') {
         i++;
         return m;
      }
      while (true) {
         ws();
         if (i >= s.length() || s.charAt(i) != '"') {
            throw err("expected key");
         }
         String k = string();
         ws();
         if (i >= s.length() || s.charAt(i) != ':') {
            throw err("expected ':'");
         }
         i++;
         ws();
         m.put(k, value());
         ws();
         if (i >= s.length()) {
            throw err("unterminated object");
         }
         char c = s.charAt(i++);
         if (c == '}') {
            return m;
         }
         if (c != ',') {
            throw err("expected ',' or '}'");
         }
      }
   }

   private List<Object> array() {
      List<Object> l = new ArrayList<Object>();
      i++;
      ws();
      if (i < s.length() && s.charAt(i) == ']') {
         i++;
         return l;
      }
      while (true) {
         ws();
         l.add(value());
         ws();
         if (i >= s.length()) {
            throw err("unterminated array");
         }
         char c = s.charAt(i++);
         if (c == ']') {
            return l;
         }
         if (c != ',') {
            throw err("expected ',' or ']'");
         }
      }
   }

   private String string() {
      StringBuilder b = new StringBuilder();
      i++;
      while (true) {
         if (i >= s.length()) {
            throw err("unterminated string");
         }
         char c = s.charAt(i++);
         if (c == '"') {
            return b.toString();
         }
         if (c != '\\') {
            b.append(c);
            continue;
         }
         char e = s.charAt(i++);
         switch (e) {
            case 'n': b.append('\n'); break;
            case 't': b.append('\t'); break;
            case 'r': b.append('\r'); break;
            case 'b': b.append('\b'); break;
            case 'f': b.append('\f'); break;
            case 'u':
               b.append((char)Integer.parseInt(s.substring(i, i + 4), 16));
               i += 4;
               break;
            default: b.append(e);
         }
      }
   }

   private Object number() {
      int start = i;
      while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
         i++;
      }
      String n = s.substring(start, i);
      if (n.isEmpty()) {
         throw err("unexpected character '" + s.charAt(start) + "'");
      }
      if (n.indexOf('.') < 0 && n.indexOf('e') < 0 && n.indexOf('E') < 0) {
         try {
            return Long.valueOf(n);
         } catch (NumberFormatException ignored) {
         }
      }
      return Double.valueOf(n);
   }

   // ------------------------------------------------------------------ writing

   public static String write(Object v) {
      StringBuilder b = new StringBuilder();
      write(b, v, 0);
      return b.append('\n').toString();
   }

   private static void write(StringBuilder b, Object v, int depth) {
      if (v == null) {
         b.append("null");
      } else if (v instanceof String) {
         quote(b, (String)v);
      } else if (v instanceof Number || v instanceof Boolean) {
         b.append(v);
      } else if (v instanceof Map) {
         Map<?, ?> m = (Map<?, ?>)v;
         if (m.isEmpty()) {
            b.append("{}");
            return;
         }
         b.append("{\n");
         Iterator<? extends Map.Entry<?, ?>> it = m.entrySet().iterator();
         while (it.hasNext()) {
            Map.Entry<?, ?> e = it.next();
            indent(b, depth + 1);
            quote(b, String.valueOf(e.getKey()));
            b.append(": ");
            write(b, e.getValue(), depth + 1);
            b.append(it.hasNext() ? ",\n" : "\n");
         }
         indent(b, depth);
         b.append('}');
      } else if (v instanceof List) {
         List<?> l = (List<?>)v;
         if (l.isEmpty()) {
            b.append("[]");
            return;
         }
         b.append("[\n");
         for (int k = 0; k < l.size(); k++) {
            indent(b, depth + 1);
            write(b, l.get(k), depth + 1);
            b.append(k + 1 < l.size() ? ",\n" : "\n");
         }
         indent(b, depth);
         b.append(']');
      } else {
         quote(b, v.toString());
      }
   }

   private static void indent(StringBuilder b, int depth) {
      for (int k = 0; k < depth; k++) {
         b.append("  ");
      }
   }

   private static void quote(StringBuilder b, String s) {
      b.append('"');
      for (int k = 0; k < s.length(); k++) {
         char c = s.charAt(k);
         switch (c) {
            case '"': b.append("\\\""); break;
            case '\\': b.append("\\\\"); break;
            case '\n': b.append("\\n"); break;
            case '\r': b.append("\\r"); break;
            case '\t': b.append("\\t"); break;
            default:
               if (c < 0x20) {
                  b.append(String.format("\\u%04x", (int)c));
               } else {
                  b.append(c);
               }
         }
      }
      b.append('"');
   }
}
