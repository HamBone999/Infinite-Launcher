package infinite.launcher;

/** One game release on GitHub that carries a client zip. */
public final class GameRelease {
   public String tag;
   public String title;
   /** "1.0-410926": the version inside the zip, which is what gets installed. */
   public String clientVersion;
   /** ISO-8601, as GitHub gives it. */
   public String published;
   public String notes;
   public String pageUrl;
   public String assetName;
   public String assetUrl;
   public long assetSize;

   public String date() {
      if (published == null || published.length() < 10) {
         return "";
      }
      String[] months = { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };
      try {
         int m = Integer.parseInt(published.substring(5, 7));
         int d = Integer.parseInt(published.substring(8, 10));
         return months[m - 1] + " " + d + ", " + published.substring(0, 4);
      } catch (RuntimeException e) {
         return published.substring(0, 10);
      }
   }

   @Override
   public String toString() {
      return clientVersion;
   }
}
