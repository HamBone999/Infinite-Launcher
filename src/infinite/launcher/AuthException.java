package infinite.launcher;

/** A sign-in failure with a message a player can act on. */
public final class AuthException extends Exception {
   /** True when signing in again from scratch is the fix (refresh token dead or revoked). */
   public final boolean needsSignIn;

   public AuthException(String message, boolean needsSignIn) {
      super(message);
      this.needsSignIn = needsSignIn;
   }

   public AuthException(String message, Throwable cause) {
      super(message, cause);
      this.needsSignIn = false;
   }
}
