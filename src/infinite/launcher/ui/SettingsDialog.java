package infinite.launcher.ui;

import infinite.launcher.BuildInfo;
import infinite.launcher.Dirs;
import infinite.launcher.Installer;
import infinite.launcher.Io;
import infinite.launcher.Settings;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;

final class SettingsDialog extends JDialog {
   interface Hooks {
      void saved();

      void checkLauncherUpdate();
   }

   private final Settings s;
   private final JSlider memory;
   private final JLabel memoryLabel = Ui.label("", Font.BOLD, 13, Theme.TEXT);
   private final JCheckBox fullscreen = check("Start in fullscreen");
   private final JTextField width = new JTextField(5);
   private final JTextField height = new JTextField(5);
   private final JTextField javaField = new JTextField();
   private final JTextField jvmArgs = new JTextField();
   private final JCheckBox keepOpen = check("Keep the launcher open while playing");
   private final JCheckBox console = check("Show the game console");
   private final DefaultListModel<String> versions = new DefaultListModel<String>();

   SettingsDialog(Window owner, Settings settings, final Hooks hooks) {
      super(owner, "Settings", ModalityType.APPLICATION_MODAL);
      this.s = settings;
      int maxMb = Math.max(2048, Math.min(16384, (int)(physicalMb() - 1024) / 512 * 512));
      memory = new JSlider(1024, maxMb, Math.max(1024, Math.min(maxMb, s.memoryMb)));
      memory.setMajorTickSpacing(512);
      memory.setSnapToTicks(true);
      memory.setOpaque(false);
      memory.addChangeListener(e -> memoryLabel.setText(gb(memory.getValue())));
      memoryLabel.setText(gb(memory.getValue()));
      fullscreen.setSelected(s.fullscreen);
      width.setText(s.width > 0 ? String.valueOf(s.width) : "");
      height.setText(s.height > 0 ? String.valueOf(s.height) : "");
      javaField.setText(s.javaPath);
      jvmArgs.setText(s.jvmArgs);
      keepOpen.setSelected(s.keepOpen);
      console.setSelected(s.showConsole);

      JPanel form = Ui.opaque(new WidthTracking(new GridBagLayout()), Theme.SURFACE);
      Ui.pad(form, 22, 26, 10, 26);
      int row = 0;

      JPanel mem = row(memory, memoryLabel);
      row = add(form, row, "Memory", mem, "How much RAM the game may use. 2 GB is plenty; more helps with big view distances.");

      JPanel win = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.s(6), 0));
      win.setOpaque(false);
      win.add(fullscreen);
      win.add(Box.gap(12));
      win.add(Ui.label("Size", Font.PLAIN, 12, Theme.MUTED));
      win.add(width);
      win.add(Ui.label("×", Font.PLAIN, 12, Theme.MUTED));
      win.add(height);
      row = add(form, row, "Game window", win, "Leave the size empty for the game's default (854 × 480).");

      FlatButton browse = new FlatButton("Browse…", FlatButton.Kind.SECONDARY);
      FlatButton reset = new FlatButton("Use bundled", FlatButton.Kind.GHOST);
      browse.addActionListener(e -> {
         JFileChooser fc = new JFileChooser();
         fc.setDialogTitle("Choose a java or javaw executable, or a Java 8 folder");
         fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
         if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            javaField.setText(fc.getSelectedFile().getAbsolutePath());
         }
      });
      reset.addActionListener(e -> javaField.setText(""));
      JPanel javaRow = new JPanel(new BorderLayout(Theme.s(6), 0));
      javaRow.setOpaque(false);
      javaRow.add(javaField, BorderLayout.CENTER);
      JPanel jb = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.s(6), 0));
      jb.setOpaque(false);
      jb.add(browse);
      jb.add(reset);
      javaRow.add(jb, BorderLayout.EAST);
      row = add(form, row, "Java", javaRow, "Empty uses the Java 8 that came with the launcher. The game needs Java 8.");
      row = add(form, row, "Java arguments", jvmArgs, "Advanced. Added to the java command before the game starts.");

      JPanel play = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
      play.setOpaque(false);
      JPanel stack = new JPanel(new java.awt.GridLayout(2, 1, 0, Theme.s(4)));
      stack.setOpaque(false);
      stack.add(keepOpen);
      stack.add(console);
      play.add(stack);
      row = add(form, row, "While playing", play, null);

      FlatButton gameDir = new FlatButton("Game folder", FlatButton.Kind.SECONDARY);
      FlatButton root = new FlatButton("Launcher folder", FlatButton.Kind.SECONDARY);
      FlatButton logs = new FlatButton("Logs", FlatButton.Kind.SECONDARY);
      gameDir.addActionListener(e -> Ui.open(Dirs.game(s)));
      root.addActionListener(e -> Ui.open(Dirs.ROOT));
      logs.addActionListener(e -> Ui.open(Dirs.logs()));
      JPanel folders = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.s(6), 0));
      folders.setOpaque(false);
      folders.add(gameDir);
      folders.add(root);
      folders.add(logs);
      row = add(form, row, "Folders", folders, "Worlds, screenshots, options and mods live in the game folder, shared by every version.");

      refreshVersions();
      final JList<String> vlist = new JList<String>(versions);
      vlist.setVisibleRowCount(4);
      vlist.setFont(Theme.font(Font.PLAIN, 12));
      JScrollPane vsp = new JScrollPane(vlist);
      Ui.thin(vsp, Theme.SURFACE_2);
      vsp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
      vsp.setPreferredSize(new Dimension(Theme.s(300), Theme.s(96)));
      FlatButton remove = new FlatButton("Remove", FlatButton.Kind.SECONDARY);
      remove.addActionListener(e -> {
         String v = vlist.getSelectedValue();
         if (v != null) {
            Installer.uninstall(v.split(" ")[0]);
            refreshVersions();
         }
      });
      JPanel vrow = new JPanel(new BorderLayout(Theme.s(8), 0));
      vrow.setOpaque(false);
      vrow.add(vsp, BorderLayout.CENTER);
      JPanel vb = new JPanel(new BorderLayout());
      vb.setOpaque(false);
      vb.add(remove, BorderLayout.NORTH);
      vrow.add(vb, BorderLayout.EAST);
      row = add(form, row, "Versions", vrow, "Removing a version frees space; it downloads again if you pick it.");

      FlatButton check = new FlatButton("Check for launcher update", FlatButton.Kind.SECONDARY);
      check.addActionListener(e -> hooks.checkLauncherUpdate());
      JPanel about = new JPanel(new BorderLayout(0, Theme.s(6)));
      about.setOpaque(false);
      about.add(Ui.label("Infinite Launcher " + BuildInfo.VERSION + "  ·  Java " + System.getProperty("java.version"), Font.PLAIN, 12, Theme.MUTED), BorderLayout.NORTH);
      JPanel cb = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
      cb.setOpaque(false);
      cb.add(check);
      about.add(cb, BorderLayout.SOUTH);
      row = add(form, row, "About", about, null);

      GridBagConstraints filler = new GridBagConstraints();
      filler.gridy = row;
      filler.weighty = 1;
      form.add(new JPanel() {
         {
            setOpaque(false);
         }
      }, filler);

      JScrollPane sp = new JScrollPane(form);
      Ui.thin(sp, Theme.SURFACE);
      sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

      FlatButton cancel = new FlatButton("Cancel", FlatButton.Kind.GHOST);
      FlatButton save = new FlatButton("Save", FlatButton.Kind.PRIMARY);
      cancel.addActionListener(e -> dispose());
      save.addActionListener(e -> {
         apply();
         hooks.saved();
         dispose();
      });
      JPanel bar = Ui.opaque(new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.s(8), Theme.s(12))), Theme.SURFACE);
      bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
      bar.add(cancel);
      bar.add(save);
      getRootPane().setDefaultButton(save);

      getContentPane().setBackground(Theme.SURFACE);
      getContentPane().add(sp, BorderLayout.CENTER);
      getContentPane().add(bar, BorderLayout.SOUTH);
      Ui.escapeCloses(this, this::dispose);
      setSize(Theme.s(720), Theme.s(640));
      setLocationRelativeTo(owner);
   }

   private void apply() {
      s.memoryMb = memory.getValue();
      s.fullscreen = fullscreen.isSelected();
      s.width = parse(width.getText());
      s.height = parse(height.getText());
      if (s.width <= 0 || s.height <= 0) {
         s.width = 0;
         s.height = 0;
      }
      s.javaPath = javaField.getText().trim();
      s.jvmArgs = jvmArgs.getText().trim();
      s.keepOpen = keepOpen.isSelected();
      s.showConsole = console.isSelected();
      s.save();
   }

   private void refreshVersions() {
      versions.clear();
      List<String> inst = Installer.installedVersions();
      java.util.Collections.sort(inst, java.util.Collections.reverseOrder());
      for (String v : inst) {
         versions.addElement(v + "   " + Io.humanBytes(size(Installer.dir(v))));
      }
      if (inst.isEmpty()) {
         versions.addElement("(none yet)");
      }
   }

   private static long size(File f) {
      if (f.isFile()) {
         return f.length();
      }
      long n = 0;
      File[] kids = f.listFiles();
      if (kids != null) {
         for (File k : kids) {
            n += size(k);
         }
      }
      return n;
   }

   private static int add(JPanel form, int row, String label, JComponent control, String hint) {
      GridBagConstraints l = new GridBagConstraints();
      l.gridx = 0;
      l.gridy = row;
      l.anchor = GridBagConstraints.NORTHWEST;
      l.insets = new Insets(Theme.s(8), 0, Theme.s(4), Theme.s(22));
      JLabel lab = Ui.label(label, Font.BOLD, 13, Theme.TEXT);
      lab.setPreferredSize(new Dimension(Theme.s(130), lab.getPreferredSize().height));
      form.add(lab, l);
      GridBagConstraints c = new GridBagConstraints();
      c.gridx = 1;
      c.gridy = row;
      c.weightx = 1;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.anchor = GridBagConstraints.WEST;
      c.insets = new Insets(Theme.s(4), 0, Theme.s(6), 0);
      form.add(control, c);
      if (hint != null) {
         GridBagConstraints h = new GridBagConstraints();
         h.gridx = 1;
         h.gridy = row + 1;
         h.anchor = GridBagConstraints.WEST;
         h.fill = GridBagConstraints.HORIZONTAL;
         h.insets = new Insets(0, Theme.s(2), Theme.s(14), 0);
         form.add(Ui.label(hint, Font.PLAIN, 11, Theme.FAINT), h);
      } else {
         GridBagConstraints h = new GridBagConstraints();
         h.gridx = 1;
         h.gridy = row + 1;
         h.insets = new Insets(0, 0, Theme.s(10), 0);
         form.add(Box.gap(1), h);
      }
      return row + 2;
   }

   private static JPanel row(JComponent a, JComponent b) {
      JPanel p = new JPanel(new BorderLayout(Theme.s(12), 0));
      p.setOpaque(false);
      p.add(a, BorderLayout.CENTER);
      b.setPreferredSize(new Dimension(Theme.s(64), b.getPreferredSize().height));
      p.add(b, BorderLayout.EAST);
      return p;
   }

   private static JCheckBox check(String text) {
      JCheckBox c = new JCheckBox(text);
      c.setOpaque(false);
      c.setFont(Theme.font(Font.PLAIN, 13));
      c.setForeground(Theme.TEXT);
      c.setFocusPainted(false);
      return c;
   }

   private static String gb(int mb) {
      return mb % 1024 == 0 ? (mb / 1024) + " GB" : String.format("%.1f GB", mb / 1024.0);
   }

   private static int parse(String t) {
      try {
         return Integer.parseInt(t.trim());
      } catch (NumberFormatException e) {
         return 0;
      }
   }

   private static long physicalMb() {
      try {
         Object os = ManagementFactory.getOperatingSystemMXBean();
         Method m = os.getClass().getMethod("getTotalPhysicalMemorySize");
         m.setAccessible(true);
         return ((Number)m.invoke(os)).longValue() / 1048576L;
      } catch (Exception e) {
         return 8192;
      }
   }

   /** A panel that fills the scroll pane's width instead of scrolling sideways. */
   static final class WidthTracking extends JPanel implements javax.swing.Scrollable {
      WidthTracking(java.awt.LayoutManager l) {
         super(l);
      }

      public Dimension getPreferredScrollableViewportSize() {
         return getPreferredSize();
      }

      public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) {
         return Theme.s(18);
      }

      public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) {
         return r.height;
      }

      public boolean getScrollableTracksViewportWidth() {
         return true;
      }

      public boolean getScrollableTracksViewportHeight() {
         return false;
      }
   }

   /** Fixed-size spacer. */
   static final class Box {
      static JComponent gap(int px) {
         JPanel p = new JPanel();
         p.setOpaque(false);
         p.setPreferredSize(new Dimension(Theme.s(px), 1));
         return p;
      }
   }
}
