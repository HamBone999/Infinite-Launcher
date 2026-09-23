package infinite.launcher.ui;

import infinite.launcher.Account;
import infinite.launcher.Accounts;
import infinite.launcher.AuthException;
import infinite.launcher.BuildInfo;
import infinite.launcher.Dirs;
import infinite.launcher.GameProcess;
import infinite.launcher.GameRelease;
import infinite.launcher.Installer;
import infinite.launcher.Log;
import infinite.launcher.Msa;
import infinite.launcher.Progress;
import infinite.launcher.Releases;
import infinite.launcher.Runtimes;
import infinite.launcher.SelfUpdate;
import infinite.launcher.Settings;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

public final class LauncherFrame extends JFrame {
   private final Settings settings = Settings.load();
   private final Accounts accounts = Accounts.load();
   private volatile Releases releases;

   private final JPanel accountArea = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
   private final JPanel banner = new JPanel(new BorderLayout());
   private final JLabel notesTitle = Ui.label("Minecraft Infinite", Font.BOLD, 22, Theme.TEXT);
   private final JLabel notesMeta = Ui.label(" ", Font.PLAIN, 12, Theme.MUTED);
   private final JEditorPane notes = new JEditorPane();
   private final VersionPicker picker = new VersionPicker();
   private final FlatButton settingsButton = new FlatButton("Settings", FlatButton.Kind.SECONDARY);
   private final FlatButton play = new FlatButton("PLAY", FlatButton.Kind.PRIMARY);
   private final JLabel status = Ui.label(" ", Font.PLAIN, 12, Theme.MUTED);
   private final ProgressStrip progress = new ProgressStrip();

   private volatile boolean working;
   private volatile boolean cancel;
   private volatile Process game;
   private ConsoleWindow console;
   private String notesUrl;

   public LauncherFrame() {
      super("Infinite Launcher");
      setIconImages(icons());
      setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
      addWindowListener(new WindowAdapter() {
         public void windowClosing(WindowEvent e) {
            if (game != null && game.isAlive()) {
               setVisible(false);   // the game keeps running; the launcher comes back when it exits
               return;
            }
            System.exit(0);
         }
      });
      JPanel root = Ui.opaque(new JPanel(new BorderLayout()), Theme.BG);
      root.add(header(), BorderLayout.NORTH);
      root.add(center(), BorderLayout.CENTER);
      root.add(footer(), BorderLayout.SOUTH);
      setContentPane(root);
      setMinimumSize(new Dimension(Theme.s(860), Theme.s(560)));
      setSize(Theme.s(1060), Theme.s(660));
      setLocationRelativeTo(null);
      getRootPane().setDefaultButton(play);
   }

   public void open() {
      setVisible(true);
      refreshAccount();
      picker.setReleases(new ArrayList<GameRelease>(), Installer.installedVersions(), settings.selectedVersion);
      showNotes();
      setStatus("Checking for new versions", -1);
      new Thread(this::loadReleases, "releases").start();
      // New releases show up without a restart. With the cached ETag an unchanged list costs
      // nothing against GitHub's rate limit.
      new javax.swing.Timer(20 * 60 * 1000, e -> {
         if (!working) {
            new Thread(this::loadReleases, "releases").start();
         }
      }).start();
      Account a = accounts.selected();
      if (a != null && !a.tokenFresh() && Msa.configured()) {
         new Thread(() -> quietRefresh(a), "refresh").start();
      }
   }

   // ------------------------------------------------------------------ layout

   private JPanel header() {
      JPanel h = Ui.opaque(new JPanel(new BorderLayout()), Theme.SURFACE);
      h.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
         BorderFactory.createEmptyBorder(Theme.s(12), Theme.s(22), Theme.s(12), Theme.s(22))));
      JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.s(12), 0));
      brand.setOpaque(false);
      Image logo = icon(128);
      if (logo != null) {
         brand.add(new JLabel(new ImageIcon(logo.getScaledInstance(Theme.s(48), Theme.s(48), Image.SCALE_SMOOTH))));
      }
      JPanel words = new JPanel(new BorderLayout());
      words.setOpaque(false);
      words.add(Ui.label("Minecraft Infinite", Font.BOLD, 17, Theme.TEXT), BorderLayout.NORTH);
      words.add(Ui.label("Launcher " + BuildInfo.VERSION, Font.PLAIN, 11, Theme.FAINT), BorderLayout.SOUTH);
      brand.add(words);
      h.add(brand, BorderLayout.WEST);
      accountArea.setOpaque(false);
      h.add(accountArea, BorderLayout.EAST);
      return h;
   }

   private JPanel center() {
      JPanel c = Ui.opaque(new JPanel(new BorderLayout()), Theme.BG);
      banner.setVisible(false);
      c.add(banner, BorderLayout.NORTH);

      JPanel card = Ui.opaque(new JPanel(new BorderLayout(0, Theme.s(14))), Theme.BG);
      Ui.pad(card, 22, 30, 8, 30);
      JPanel head = new JPanel(new BorderLayout(0, Theme.s(4)));
      head.setOpaque(false);
      head.add(notesTitle, BorderLayout.NORTH);
      head.add(notesMeta, BorderLayout.SOUTH);
      notesMeta.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      notesMeta.addMouseListener(new MouseAdapter() {
         public void mouseClicked(MouseEvent e) {
            if (notesUrl != null) {
               Ui.browse(notesUrl);
            }
         }
      });
      card.add(head, BorderLayout.NORTH);

      notes.setEditable(false);
      notes.setOpaque(true);
      notes.setBackground(Theme.BG);
      HTMLEditorKit kit = new HTMLEditorKit();
      StyleSheet css = kit.getStyleSheet();
      css.addRule("body { color: #D2D6DC; font-family: '" + Theme.font(Font.PLAIN, 13).getFamily() + "'; font-size: "
         + Theme.s(13) + "pt; margin: 0; }");
      css.addRule("p { margin-top: 0; margin-bottom: " + Theme.s(9) + "px; }");
      css.addRule("h2 { color: #FFFFFF; font-size: " + Theme.s(16) + "pt; margin-top: " + Theme.s(14) + "px; margin-bottom: "
         + Theme.s(6) + "px; }");
      css.addRule("h3 { color: #FFFFFF; font-size: " + Theme.s(14) + "pt; margin-top: " + Theme.s(12) + "px; margin-bottom: "
         + Theme.s(4) + "px; }");
      css.addRule("ul, ol { margin-left: " + Theme.s(18) + "px; margin-top: 0; margin-bottom: " + Theme.s(8) + "px; }");
      css.addRule("li { margin-bottom: " + Theme.s(5) + "px; }");
      css.addRule("code { font-family: monospaced; color: #E9C46A; }");
      css.addRule("pre { font-family: monospaced; color: #E0E3E7; background-color: #22262C; padding: " + Theme.s(8) + "px; }");
      css.addRule("a { color: #7FB3FF; text-decoration: none; }");
      css.addRule("blockquote { color: #9BA2AC; margin-left: " + Theme.s(12) + "px; }");
      css.addRule("b { color: #FFFFFF; }");
      notes.setEditorKit(kit);
      notes.addHyperlinkListener(e -> {
         if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
            Ui.browse(e.getURL().toString());
         }
      });
      JScrollPane sp = new JScrollPane(notes);
      Ui.thin(sp, Theme.BG);
      card.add(sp, BorderLayout.CENTER);
      c.add(card, BorderLayout.CENTER);
      return c;
   }

   private JPanel footer() {
      JPanel f = Ui.opaque(new JPanel(new BorderLayout(Theme.s(22), 0)), Theme.SURFACE);
      f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
         BorderFactory.createEmptyBorder(Theme.s(16), Theme.s(22), Theme.s(18), Theme.s(22))));

      JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.s(10), 0));
      left.setOpaque(false);
      JPanel pickerCol = new JPanel(new BorderLayout());
      pickerCol.setOpaque(false);
      pickerCol.add(Ui.caption("Version"), BorderLayout.NORTH);
      pickerCol.add(picker, BorderLayout.CENTER);
      left.add(pickerCol);
      JPanel settingsCol = new JPanel(new BorderLayout());
      settingsCol.setOpaque(false);
      settingsCol.add(Ui.caption(" "), BorderLayout.NORTH);
      settingsButton.setPreferredSize(new Dimension(settingsButton.getPreferredSize().width, Theme.s(48)));
      settingsCol.add(settingsButton, BorderLayout.CENTER);
      left.add(settingsCol);
      f.add(left, BorderLayout.WEST);

      JPanel mid = new JPanel();
      mid.setOpaque(false);
      mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
      mid.add(Box.createVerticalGlue());
      status.setAlignmentX(LEFT_ALIGNMENT);
      progress.setAlignmentX(LEFT_ALIGNMENT);
      progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.s(5)));
      mid.add(status);
      mid.add(Box.createVerticalStrut(Theme.s(8)));
      mid.add(progress);
      mid.add(Box.createVerticalStrut(Theme.s(4)));
      progress.setVisible(false);
      f.add(mid, BorderLayout.CENTER);

      play.setFont(Theme.font(Font.BOLD, 19));
      play.setPreferredSize(new Dimension(Theme.s(220), Theme.s(60)));
      JPanel right = new JPanel(new BorderLayout());
      right.setOpaque(false);
      right.add(Ui.caption(" "), BorderLayout.NORTH);
      right.add(play, BorderLayout.CENTER);
      f.add(right, BorderLayout.EAST);

      picker.onPick(v -> {
         settings.selectedVersion = v;
         settings.save();
         showNotes();
         updatePlayLabel();
      });
      settingsButton.addActionListener(e -> new SettingsDialog(this, settings, new SettingsDialog.Hooks() {
         public void saved() {
            picker.repaint();
            updatePlayLabel();
         }

         public void checkLauncherUpdate() {
            new Thread(() -> checkLauncher(true), "launcher-update").start();
         }
      }).setVisible(true));
      play.addActionListener(e -> onPlay());
      return f;
   }

   // ------------------------------------------------------------------ account

   private void refreshAccount() {
      accountArea.removeAll();
      final Account a = accounts.selected();
      if (a == null) {
         FlatButton b = new FlatButton("Sign in with Microsoft", FlatButton.Kind.SECONDARY);
         b.addActionListener(e -> signIn(null));
         accountArea.add(b);
      } else {
         JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.s(10), 0));
         chip.setOpaque(false);
         final JLabel head = new JLabel(new ImageIcon(Heads.placeholder(a.name.isEmpty() ? "?" : a.name.substring(0, 1), Theme.s(34))));
         JPanel words = new JPanel(new BorderLayout());
         words.setOpaque(false);
         words.add(Ui.label(a.name, Font.BOLD, 14, Theme.TEXT), BorderLayout.NORTH);
         words.add(Ui.label("Microsoft account  ▾", Font.PLAIN, 11, Theme.FAINT), BorderLayout.SOUTH);
         chip.add(head);
         chip.add(words);
         chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
         chip.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
               accountMenu().show(chip, 0, chip.getHeight() + Theme.s(6));
            }
         });
         accountArea.add(chip);
         new Thread(() -> {
            final BufferedImage img = Heads.load(a, Theme.s(34));
            SwingUtilities.invokeLater(() -> head.setIcon(new ImageIcon(img)));
         }, "skin").start();
      }
      accountArea.revalidate();
      accountArea.repaint();
      updatePlayLabel();
   }

   private JPopupMenu accountMenu() {
      JPopupMenu m = new JPopupMenu();
      final Account cur = accounts.selected();
      for (final Account a : accounts.all()) {
         if (cur != null && a.uuid.equals(cur.uuid)) {
            continue;
         }
         JMenuItem sw = new JMenuItem("Switch to " + a.name);
         sw.addActionListener(e -> {
            accounts.select(a);
            refreshAccount();
         });
         m.add(sw);
      }
      JMenuItem add = new JMenuItem("Add another account…");
      add.addActionListener(e -> signIn(null));
      m.add(add);
      if (cur != null) {
         m.addSeparator();
         JMenuItem out = new JMenuItem("Sign out of " + cur.name);
         out.addActionListener(e -> {
            accounts.remove(cur);
            refreshAccount();
         });
         m.add(out);
      }
      for (java.awt.Component c : m.getComponents()) {
         c.setFont(Theme.font(Font.PLAIN, 13));
         if (c instanceof JMenuItem) {
            ((JMenuItem)c).setBorder(BorderFactory.createEmptyBorder(Theme.s(7), Theme.s(10), Theme.s(7), Theme.s(16)));
         }
      }
      return m;
   }

   private void signIn(final Runnable then) {
      if (!Msa.configured()) {
         JOptionPane.showMessageDialog(this, "This build of the launcher has no Microsoft app ID, so it can't sign in yet.",
            "Sign in", JOptionPane.WARNING_MESSAGE);
         return;
      }
      new SignInDialog(this, a -> {
         accounts.put(a);
         refreshAccount();
         setStatus("Signed in as " + a.name, 0);
         if (then != null) {
            then.run();
         }
      }).start();
   }

   private void quietRefresh(Account a) {
      try {
         Account fresh = Msa.refresh(a);
         accounts.put(fresh);
         SwingUtilities.invokeLater(this::refreshAccount);
      } catch (AuthException e) {
         Log.warn("background refresh for " + a.name + ": " + e.getMessage(), null);
         if (e.needsSignIn) {
            SwingUtilities.invokeLater(() -> showBanner("Your Microsoft sign-in for " + a.name + " has expired.", "Sign in",
               () -> signIn(null), Theme.WARN));
         }
      }
   }

   // ------------------------------------------------------------------ releases and notes

   private void loadReleases() {
      final Releases rs = Releases.fetch();
      releases = rs;
      SwingUtilities.invokeLater(() -> {
         picker.setReleases(rs.games, Installer.installedVersions(), settings.selectedVersion);
         showNotes();
         updatePlayLabel();
         if (rs.offline) {
            showBanner("Can't reach GitHub. Installed versions still play; new ones can't be downloaded until you're back online.",
               "Retry", () -> {
                  hideBanner();
                  setStatus("Checking for new versions", -1);
                  new Thread(this::loadReleases, "releases").start();
               }, Theme.WARN);
         }
         if (!working) {
            setStatus(rs.games.isEmpty() && rs.offline ? "Offline" : "Ready", 0);
         }
      });
      checkLauncher(false);
   }

   private void checkLauncher(boolean manual) {
      Releases rs = releases != null && !manual ? releases : Releases.fetch();
      final String v = SelfUpdate.fetchIfNewer(rs, Progress.NONE);
      SwingUtilities.invokeLater(() -> {
         if (v != null) {
            showBanner("Launcher " + v + " is ready. Restart to finish updating.", "Restart now", () -> {
               try {
                  SelfUpdate.restart();
               } catch (IOException ex) {
                  JOptionPane.showMessageDialog(this, ex.getMessage(), "Update", JOptionPane.INFORMATION_MESSAGE);
               }
            }, Theme.ACCENT);
         } else if (manual) {
            JOptionPane.showMessageDialog(this, "You have the newest launcher (" + BuildInfo.VERSION + ").", "Launcher update",
               JOptionPane.INFORMATION_MESSAGE);
         }
      });
   }

   private void showNotes() {
      String cv = picker.resolved();
      GameRelease r = releases == null || cv == null ? null : releases.find(cv);
      if (cv == null) {
         notesTitle.setText("Minecraft Infinite");
         notesMeta.setText(releases == null ? "Loading releases…" : "No versions found");
         notesUrl = null;
         setNotes("<p>" + (releases == null ? "Fetching the release list from GitHub." : "Nothing has been released yet, or GitHub "
            + "couldn't be reached.") + "</p>");
         return;
      }
      notesTitle.setText("Minecraft Infinite " + cv);
      if (r == null) {
         notesMeta.setText(Installer.isInstalled(cv) ? "Installed" : " ");
         notesUrl = null;
         setNotes("<p>" + (releases == null ? "Loading release notes…" : "Release notes aren't available for this version.")
            + "</p>");
         return;
      }
      notesUrl = r.pageUrl;
      notesMeta.setText("<html>Released " + r.date() + "  ·  " + Markdown.esc(r.tag) + "  ·  <font color='#7FB3FF'>"
         + "View on GitHub</font></html>");
      String body = r.notes == null || r.notes.trim().isEmpty() ? "<p>No release notes.</p>" : Markdown.toHtml(r.notes);
      setNotes(body);
   }

   private void setNotes(String html) {
      notes.setText("<html><body>" + html + "</body></html>");
      notes.setCaretPosition(0);
   }

   // ------------------------------------------------------------------ banner and status

   private void showBanner(String text, String action, final Runnable onAction, Color accent) {
      banner.removeAll();
      banner.setBackground(Theme.SURFACE_2);
      banner.setOpaque(true);
      banner.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, Theme.s(4), 1, 0, accent),
         BorderFactory.createEmptyBorder(Theme.s(9), Theme.s(18), Theme.s(9), Theme.s(14))));
      banner.add(Ui.label(text, Font.PLAIN, 13, Theme.TEXT), BorderLayout.CENTER);
      JPanel acts = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.s(6), 0));
      acts.setOpaque(false);
      if (action != null) {
         FlatButton b = new FlatButton(action, FlatButton.Kind.SECONDARY);
         b.addActionListener(e -> onAction.run());
         acts.add(b);
      }
      FlatButton x = new FlatButton("✕", FlatButton.Kind.GHOST);
      x.addActionListener(e -> hideBanner());
      acts.add(x);
      banner.add(acts, BorderLayout.EAST);
      banner.setVisible(true);
      banner.revalidate();
      banner.repaint();
   }

   private void hideBanner() {
      banner.setVisible(false);
   }

   private void setStatus(String text, double frac) {
      status.setForeground(Theme.MUTED);
      status.setText(text);
      progress.setVisible(frac != 0);
      progress.set(frac);
   }

   private void setError(String text) {
      status.setForeground(Theme.ERROR);
      status.setText("<html><div style='width:" + Theme.s(330) + "px'>" + Markdown.esc(text) + "</div></html>");
      progress.setVisible(false);
      progress.set(0);
   }

   private void updatePlayLabel() {
      if (working) {
         return;
      }
      if (game != null && game.isAlive()) {
         play.setText("PLAYING");
         play.setSubtitle(null);
         play.setEnabled(false);
         return;
      }
      play.setEnabled(true);
      play.setKind(FlatButton.Kind.PRIMARY);
      play.setText("PLAY");
      String cv = picker.resolved();
      if (accounts.selected() == null) {
         play.setSubtitle("Sign in to play");
      } else if (cv != null && !Installer.isInstalled(cv)) {
         play.setSubtitle("Downloads " + cv + " first");
      } else {
         play.setSubtitle(null);
      }
   }

   private void setWorking(boolean w) {
      working = w;
      picker.setEnabled(!w);
      settingsButton.setEnabled(!w);
      if (w) {
         play.setText("CANCEL");
         play.setSubtitle(null);
         play.setKind(FlatButton.Kind.SECONDARY);
      } else {
         updatePlayLabel();
      }
   }

   // ------------------------------------------------------------------ play

   private void onPlay() {
      if (working) {
         cancel = true;
         setStatus("Cancelling", -1);
         return;
      }
      if (game != null && game.isAlive()) {
         return;
      }
      final Account a = accounts.selected();
      if (a == null) {
         signIn(this::onPlay);
         return;
      }
      final String cv = picker.resolved();
      if (cv == null) {
         setError("No version to play yet. Check your internet connection and try again.");
         return;
      }
      final GameRelease r = releases == null ? null : releases.find(cv);
      cancel = false;
      setWorking(true);
      new Thread(() -> launch(a, cv, r), "launch").start();
   }

   private void launch(Account a, String cv, GameRelease r) {
      Progress p = new Progress() {
         private String step = "";

         public void step(final String what) {
            step = what;
            Log.info(what);
            SwingUtilities.invokeLater(() -> setStatus(what, -1));
         }

         public void fraction(final double f) {
            SwingUtilities.invokeLater(() -> {
               status.setText(step + (f >= 0 ? "  ·  " + (int)(f * 100) + "%" : ""));
               progress.setVisible(true);
               progress.set(f);
            });
         }

         public boolean cancelled() {
            return cancel;
         }
      };
      try {
         Account acct = a;
         if (!a.tokenFresh()) {
            p.step("Signing in as " + a.name);
            try {
               acct = Msa.refresh(a);
               accounts.put(acct);
               SwingUtilities.invokeLater(this::refreshAccount);
            } catch (AuthException e) {
               if (e.needsSignIn) {
                  SwingUtilities.invokeLater(() -> {
                     setWorking(false);
                     setError(e.getMessage());
                     signIn(this::onPlay);
                  });
                  return;
               }
               if (!askOffline(e.getMessage())) {
                  SwingUtilities.invokeLater(() -> {
                     setWorking(false);
                     setError(e.getMessage());
                  });
                  return;
               }
            }
         }
         Installer.Installed inst = Installer.ensure(r, cv, p);
         File java = Runtimes.java(settings, p);
         if (cancel) {
            throw new IOException("Cancelled");
         }
         List<String> cmd = GameProcess.command(java, inst, acct.name, acct.sessionArg(), settings);
         p.step("Starting Minecraft Infinite " + cv);
         final boolean showConsole = settings.showConsole;
         if (showConsole) {
            SwingUtilities.invokeLater(() -> {
               if (console == null) {
                  console = new ConsoleWindow();
               }
               console.setVisible(true);
            });
         }
         final Process proc = GameProcess.start(cmd, Dirs.game(settings), new GameProcess.Listener() {
            public void line(String line) {
               if (console != null) {
                  console.append(line);
               }
            }

            public void exited(final int code, final long ms, final List<String> tail) {
               SwingUtilities.invokeLater(() -> gameExited(code, ms, tail));
            }
         });
         SwingUtilities.invokeLater(() -> gameStarted(proc, cv));
      } catch (final IOException e) {
         Log.warn("launch failed", e);
         SwingUtilities.invokeLater(() -> {
            setWorking(false);
            if ("Cancelled".equals(e.getMessage())) {
               setStatus("Cancelled", 0);
            } else {
               setError(e.getMessage());
            }
         });
      } catch (final RuntimeException e) {
         Log.warn("launch failed", e);
         SwingUtilities.invokeLater(() -> {
            setWorking(false);
            setError("Something went wrong: " + e);
         });
      }
   }

   private boolean askOffline(final String why) {
      final boolean[] yes = { false };
      try {
         SwingUtilities.invokeAndWait(() -> yes[0] = JOptionPane.showConfirmDialog(this, why + "\n\nPlay anyway? Singleplayer "
            + "works; servers will refuse the connection until you're signed in again.", "Can't sign in",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION);
      } catch (Exception ignored) {
      }
      return yes[0];
   }

   private void gameStarted(Process proc, String cv) {
      game = proc;
      working = false;
      picker.setEnabled(true);
      settingsButton.setEnabled(true);
      setStatus("Playing Minecraft Infinite " + cv, 0);
      picker.repaint();
      updatePlayLabel();
      if (!settings.keepOpen) {
         setVisible(false);
      }
   }

   private void gameExited(int code, long ms, List<String> tail) {
      game = null;
      if (!isVisible()) {
         setVisible(true);
         toFront();
      }
      updatePlayLabel();
      if (code == 0 || code == 130 || code == 143) {
         setStatus("Ready", 0);
      } else {
         setError("The game stopped with exit code " + code + ".");
         new CrashDialog(this, code, ms, tail).setVisible(true);
      }
   }

   // ------------------------------------------------------------------ icons

   private static List<Image> icons() {
      List<Image> out = new ArrayList<Image>();
      for (int s : new int[] { 16, 32, 48, 64, 128, 256 }) {   // generated from branding/logo.png
         Image i = icon(s);
         if (i != null) {
            out.add(i);
         }
      }
      return out;
   }

   static Image icon(int size) {
      try {
         java.io.InputStream in = LauncherFrame.class.getResourceAsStream("/infinite/launcher/icon-" + size + ".png");
         return in == null ? null : ImageIO.read(in);
      } catch (IOException e) {
         return null;
      }
   }
}
