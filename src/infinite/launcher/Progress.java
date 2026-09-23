package infinite.launcher;

/** Where long jobs report. step() is the headline; fraction() is 0..1, or negative for "busy". */
public interface Progress {
   void step(String what);

   void fraction(double f);

   boolean cancelled();

   Progress NONE = new Progress() {
      public void step(String what) {
         Log.info(what);
      }

      public void fraction(double f) {
      }

      public boolean cancelled() {
         return false;
      }
   };
}
