package infinite.launcher.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub release notes to the HTML 3.2 Swing can show. Headings, lists, quotes, code, links,
 * bold and italics -- what release notes actually use. Anything else passes through as text.
 */
final class Markdown {
   private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)");
   private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
   private static final Pattern ITALIC = Pattern.compile("(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![*\\w])");
   private static final Pattern CODE = Pattern.compile("`([^`]+)`");
   private static final Pattern BARE_URL = Pattern.compile("(?<![\"=>])(https?://[^\\s<]+[^\\s<.,;:!?)])");

   static String toHtml(String md) {
      StringBuilder out = new StringBuilder();
      String[] lines = md.replace("\r\n", "\n").split("\n");
      boolean inCode = false;
      String list = null;
      boolean para = false;
      for (String raw : lines) {
         String line = raw.replace("\t", "    ");
         if (line.trim().startsWith("```")) {
            if (inCode) {
               out.append("</pre>");
               inCode = false;
            } else {
               para = close(out, para);
               list = endList(out, list);
               out.append("<pre>");
               inCode = true;
            }
            continue;
         }
         if (inCode) {
            out.append(esc(line)).append('\n');
            continue;
         }
         String t = line.trim();
         if (t.isEmpty()) {
            para = close(out, para);
            list = endList(out, list);
            continue;
         }
         if (t.matches("^(-{3,}|\\*{3,}|_{3,})$")) {
            para = close(out, para);
            list = endList(out, list);
            out.append("<hr>");
            continue;
         }
         Matcher h = Pattern.compile("^(#{1,6})\\s+(.*)$").matcher(t);
         if (h.matches()) {
            para = close(out, para);
            list = endList(out, list);
            int lvl = Math.min(3, h.group(1).length() + 1);
            out.append("<h").append(lvl).append('>').append(inline(h.group(2))).append("</h").append(lvl).append('>');
            continue;
         }
         Matcher ul = Pattern.compile("^[-*+]\\s+(.*)$").matcher(t);
         Matcher ol = Pattern.compile("^\\d+[.)]\\s+(.*)$").matcher(t);
         if (ul.matches() || ol.matches()) {
            para = close(out, para);
            String want = ul.matches() ? "ul" : "ol";
            if (!want.equals(list)) {
               list = endList(out, list);
               out.append('<').append(want).append('>');
               list = want;
            }
            out.append("<li>").append(inline(ul.matches() ? ul.group(1) : ol.group(1))).append("</li>");
            continue;
         }
         if (t.startsWith(">")) {
            para = close(out, para);
            list = endList(out, list);
            String q = t.replaceFirst("^>\\s?", "").replaceFirst("^\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\\]\\s*", "");
            if (!q.isEmpty()) {
               out.append("<blockquote>").append(inline(q)).append("</blockquote>");
            }
            continue;
         }
         if (list != null && raw.startsWith("  ")) {
            out.append(' ').append(inline(t));
            continue;
         }
         list = endList(out, list);
         if (!para) {
            out.append("<p>");
            para = true;
         } else {
            out.append(' ');
         }
         out.append(inline(t));
      }
      close(out, para);
      endList(out, list);
      if (inCode) {
         out.append("</pre>");
      }
      return out.toString();
   }

   private static boolean close(StringBuilder out, boolean para) {
      if (para) {
         out.append("</p>");
      }
      return false;
   }

   private static String endList(StringBuilder out, String list) {
      if (list != null) {
         out.append("</").append(list).append('>');
      }
      return null;
   }

   private static String inline(String s) {
      s = esc(s);
      s = CODE.matcher(s).replaceAll("<code>$1</code>");
      s = LINK.matcher(s).replaceAll("<a href=\"$2\">$1</a>");
      s = BARE_URL.matcher(s).replaceAll("<a href=\"$1\">$1</a>");
      s = BOLD.matcher(s).replaceAll("<b>$1</b>");
      s = ITALIC.matcher(s).replaceAll("<i>$1</i>");
      return s;
   }

   static String esc(String s) {
      return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
   }

   private Markdown() {
   }
}
