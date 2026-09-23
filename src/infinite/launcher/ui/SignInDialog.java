package infinite.launcher.ui;

import infinite.launcher.Account;
import infinite.launcher.AuthException;
import infinite.launcher.Log;
import infinite.launcher.Msa;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Microsoft's device-code sign-in: show a code, the player enters it at microsoft.com/link. */
final class SignInDialog extends JDialog {
   interface Done {
      void signedIn(Account a);
   }

   private final JLabel code = new JLabel(" ", SwingConstants.CENTER);
   private final JLabel status = new JLabel(" ", SwingConstants.CENTER);
   private final FlatButton open = new FlatButton("Open microsoft.com/link", FlatButton.Kind.PRIMARY);
   private final FlatButton copy = new FlatButton("Copy code", FlatButton.Kind.SECONDARY);
   private final FlatButton retry = new FlatButton("Try again", FlatButton.Kind.SECONDARY);
   private volatile boolean cancelled;
   private volatile Msa.DeviceCode dc;
   private final Done done;
   private Timer dots;

   SignInDialog(Window owner, Done done) {
      super(owner, "Sign in", ModalityType.APPLICATION_MODAL);
      this.done = done;
      JPanel root = Ui.opaque(new JPanel(), Theme.SURFACE);
      root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
      Ui.pad(root, 26, 32, 22, 32);

      JLabel title = Ui.label("Sign in with Microsoft", Font.BOLD, 19, Theme.TEXT);
      JLabel sub = Ui.label("<html><div style='text-align:center'>Use the Microsoft account that owns Minecraft: Java Edition. "
         + "Your password goes to Microsoft, never to this launcher.</div></html>", Font.PLAIN, 12, Theme.MUTED);
      sub.setHorizontalAlignment(SwingConstants.CENTER);
      JLabel step = Ui.label("Enter this code at microsoft.com/link", Font.PLAIN, 13, Theme.TEXT);
      code.setFont(Theme.mono(Font.BOLD, 32));
      code.setForeground(Theme.TEXT);
      JPanel codeBox = Ui.opaque(new JPanel(new BorderLayout()), Theme.SURFACE_2);
      Ui.pad(codeBox, 14, 20, 14, 20);
      codeBox.add(code);
      codeBox.setMaximumSize(new Dimension(Theme.s(360), Theme.s(80)));
      status.setFont(Theme.font(Font.PLAIN, 12));
      status.setForeground(Theme.MUTED);

      JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, Theme.s(8), 0));
      buttons.setOpaque(false);
      buttons.add(open);
      buttons.add(copy);
      buttons.add(retry);
      retry.setVisible(false);
      FlatButton cancel = new FlatButton("Cancel", FlatButton.Kind.GHOST);
      JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
      bottom.setOpaque(false);
      bottom.add(cancel);

      for (javax.swing.JComponent c : new javax.swing.JComponent[] { title, sub, step, codeBox, status, buttons, bottom }) {
         c.setAlignmentX(CENTER_ALIGNMENT);
      }
      root.add(title);
      root.add(Box.createVerticalStrut(Theme.s(8)));
      root.add(sub);
      root.add(Box.createVerticalStrut(Theme.s(22)));
      root.add(step);
      root.add(Box.createVerticalStrut(Theme.s(10)));
      root.add(codeBox);
      root.add(Box.createVerticalStrut(Theme.s(16)));
      root.add(buttons);
      root.add(Box.createVerticalStrut(Theme.s(16)));
      root.add(status);
      root.add(Box.createVerticalStrut(Theme.s(14)));
      root.add(bottom);
      setContentPane(root);

      open.setEnabled(false);
      copy.setEnabled(false);
      open.addActionListener(e -> {
         if (dc != null) {
            Ui.copy(dc.userCode);
            Ui.browse(dc.browserUrl());
            setStatus("Code copied. Finish signing in on the Microsoft page", false);
         }
      });
      copy.addActionListener(e -> {
         if (dc != null) {
            Ui.copy(dc.userCode);
            setStatus("Code copied to the clipboard", false);
         }
      });
      retry.addActionListener(e -> begin());
      cancel.addActionListener(e -> close());
      addWindowListener(new WindowAdapter() {
         public void windowClosing(WindowEvent e) {
            close();
         }
      });
      setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
      Ui.escapeCloses(this, this::close);
      setResizable(false);
      setSize(Theme.s(520), Theme.s(430));
      setLocationRelativeTo(owner);
   }

   void start() {
      begin();
      setVisible(true);
   }

   private void begin() {
      cancelled = false;
      retry.setVisible(false);
      code.setText("• • • •");
      setStatus("Asking Microsoft for a code", true);
      new Thread(() -> {
         try {
            final Msa.DeviceCode d = Msa.start();
            dc = d;
            SwingUtilities.invokeLater(() -> {
               code.setText(spaced(d.userCode));
               open.setEnabled(true);
               copy.setEnabled(true);
               Ui.copy(d.userCode);
               setStatus("Waiting for you to sign in (code copied)", true);
            });
            final Account a = Msa.waitFor(d, () -> cancelled);
            SwingUtilities.invokeLater(() -> {
               stopDots();
               dispose();
               done.signedIn(a);
            });
         } catch (final AuthException e) {
            if (!cancelled) {
               Log.warn("sign-in failed: " + e.getMessage(), e.getCause());
               SwingUtilities.invokeLater(() -> fail(e.getMessage()));
            }
         }
      }, "sign-in").start();
   }

   private void fail(String msg) {
      stopDots();
      status.setForeground(Theme.ERROR);
      status.setText("<html><div style='text-align:center;width:" + Theme.s(380) + "px'>" + Markdown.esc(msg) + "</div></html>");
      pack();
      setSize(Math.max(getWidth(), Theme.s(520)), getHeight());
      setLocationRelativeTo(getOwner());
      open.setEnabled(false);
      copy.setEnabled(false);
      retry.setVisible(true);
   }

   private void setStatus(final String text, boolean animate) {
      stopDots();
      status.setForeground(Theme.MUTED);
      status.setText(text);
      if (animate) {
         final int[] n = { 0 };
         dots = new Timer(450, e -> {
            n[0] = (n[0] + 1) % 4;
            status.setText(text + "...".substring(0, n[0]) + "   ".substring(n[0]));
         });
         dots.start();
      }
   }

   private void stopDots() {
      if (dots != null) {
         dots.stop();
         dots = null;
      }
   }

   private void close() {
      cancelled = true;
      stopDots();
      dispose();
   }

   private static String spaced(String c) {
      StringBuilder b = new StringBuilder();
      for (char ch : c.toCharArray()) {
         if (b.length() > 0) {
            b.append(' ');
         }
         b.append(ch);
      }
      return b.toString();
   }
}
