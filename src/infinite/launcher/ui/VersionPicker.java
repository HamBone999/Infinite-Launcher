package infinite.launcher.ui;

import infinite.launcher.GameRelease;
import infinite.launcher.Installer;
import infinite.launcher.Settings;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

/**
 * The version dropdown. "Latest" follows new releases automatically; picking a specific version
 * pins it, which is how a player rolls back when a release breaks something.
 */
final class VersionPicker extends JComponent {
   interface Listener {
      void picked(String value);
   }

   static final class Entry {
      final String value;          // Settings.LATEST or a client version
      final GameRelease release;   // null for installed-but-not-on-GitHub
      final boolean latest;

      Entry(String value, GameRelease release, boolean latest) {
         this.value = value;
         this.release = release;
         this.latest = latest;
      }
   }

   private final List<Entry> entries = new ArrayList<Entry>();
   private String value = Settings.LATEST;
   private GameRelease newest;
   private boolean hover;
   private Listener listener;

   VersionPicker() {
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      setPreferredSize(new Dimension(Theme.s(290), Theme.s(48)));
      addMouseListener(new MouseAdapter() {
         public void mouseEntered(MouseEvent e) {
            hover = true;
            repaint();
         }

         public void mouseExited(MouseEvent e) {
            hover = false;
            repaint();
         }

         public void mousePressed(MouseEvent e) {
            if (isEnabled()) {
               popup();
            }
         }
      });
   }

   void onPick(Listener l) {
      listener = l;
   }

   void setReleases(List<GameRelease> games, List<String> installed, String selected) {
      entries.clear();
      newest = games.isEmpty() ? null : games.get(0);
      entries.add(new Entry(Settings.LATEST, newest, true));
      List<String> listed = new ArrayList<String>();
      for (GameRelease g : games) {
         entries.add(new Entry(g.clientVersion, g, false));
         listed.add(g.clientVersion);
      }
      for (String v : installed) {
         if (!listed.contains(v)) {
            entries.add(new Entry(v, null, false));
         }
      }
      value = selected;
      repaint();
   }

   String value() {
      return value;
   }

   /** The client version "Play" will run: "latest" resolved, or the pinned one. */
   String resolved() {
      if (Settings.LATEST.equals(value)) {
         if (newest != null) {
            return newest.clientVersion;
         }
         List<String> inst = Installer.installedVersions();
         return inst.isEmpty() ? null : newestOf(inst);
      }
      return value;
   }

   private static String newestOf(List<String> vs) {
      String best = vs.get(0);
      for (String v : vs) {
         if (v.compareTo(best) > 0) {
            best = v;
         }
      }
      return best;
   }

   private String title() {
      if (Settings.LATEST.equals(value)) {
         String r = resolved();
         return "Latest" + (r == null ? "" : "  ·  " + r);
      }
      return value;
   }

   private String subtitle() {
      String r = resolved();
      if (r == null) {
         return "No versions available yet";
      }
      boolean inst = Installer.isInstalled(r);
      if (!Settings.LATEST.equals(value) && newest != null && !newest.clientVersion.equals(value)) {
         return "Pinned · newer version available" + (inst ? "" : " · not downloaded");
      }
      return inst ? "Installed, ready to play" : "Downloads when you press Play";
   }

   @Override
   protected void paintComponent(Graphics g0) {
      Graphics2D g = (Graphics2D)g0.create();
      Theme.hints(g);
      int arc = Theme.s(10);
      g.setColor(!isEnabled() ? Theme.SURFACE_2 : hover ? new Color(0x383E47) : Theme.SURFACE_3);
      g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
      int pad = Theme.s(14);
      Font f1 = Theme.font(Font.BOLD, 14);
      Font f2 = Theme.font(Font.PLAIN, 11);
      FontMetrics m1 = g.getFontMetrics(f1);
      FontMetrics m2 = g.getFontMetrics(f2);
      int total = m1.getHeight() + m2.getHeight() - Theme.s(3);
      int top = (getHeight() - total) / 2;
      g.setFont(f1);
      g.setColor(isEnabled() ? Theme.TEXT : Theme.FAINT);
      g.drawString(title(), pad, top + m1.getAscent());
      g.setFont(f2);
      g.setColor(Theme.MUTED);
      g.drawString(subtitle(), pad, top + m1.getHeight() - Theme.s(3) + m2.getAscent());
      // chevron
      int cx = getWidth() - pad - Theme.s(5);
      int cy = getHeight() / 2;
      int w = Theme.s(5);
      Path2D p = new Path2D.Float();
      p.moveTo(cx - w, cy - w / 2f);
      p.lineTo(cx, cy + w / 2f);
      p.lineTo(cx + w, cy - w / 2f);
      g.setStroke(new java.awt.BasicStroke(Theme.s(2), java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
      g.setColor(Theme.MUTED);
      g.draw(p);
      g.dispose();
   }

   private void popup() {
      final JPopupMenu menu = new JPopupMenu();
      menu.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
      final DefaultListModel<Entry> model = new DefaultListModel<Entry>();
      int sel = 0;
      for (int i = 0; i < entries.size(); i++) {
         model.addElement(entries.get(i));
         if (entries.get(i).value.equals(value)) {
            sel = i;
         }
      }
      final JList<Entry> list = new JList<Entry>(model);
      list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      list.setBackground(Theme.SURFACE_2);
      list.setCellRenderer(new Renderer());
      list.setSelectedIndex(sel);
      list.addMouseListener(new MouseAdapter() {
         public void mouseReleased(MouseEvent e) {
            Entry en = list.getSelectedValue();
            menu.setVisible(false);
            if (en != null) {
               value = en.value;
               repaint();
               if (listener != null) {
                  listener.picked(value);
               }
            }
         }
      });
      list.addMouseMotionListener(new MouseAdapter() {
         public void mouseMoved(MouseEvent e) {
            int i = list.locationToIndex(e.getPoint());
            if (i >= 0) {
               list.setSelectedIndex(i);
            }
         }
      });
      JScrollPane sp = new JScrollPane(list);
      Ui.thin(sp, Theme.SURFACE_2);
      int rowH = Theme.s(46);
      sp.setPreferredSize(new Dimension(Math.max(getWidth(), Theme.s(320)), Math.min(entries.size(), 8) * rowH + 2));
      menu.add(sp);
      list.ensureIndexIsVisible(sel);
      menu.show(this, 0, -sp.getPreferredSize().height - Theme.s(6));
   }

   private final class Renderer extends JPanel implements ListCellRenderer<Entry> {
      private final JLabel name = new JLabel();
      private final JLabel detail = new JLabel();
      private final JLabel badge = new JLabel();

      Renderer() {
         super(new BorderLayout());
         JPanel left = new JPanel(new BorderLayout());
         left.setOpaque(false);
         name.setFont(Theme.font(Font.BOLD, 13));
         detail.setFont(Theme.font(Font.PLAIN, 11));
         detail.setForeground(Theme.MUTED);
         left.add(name, BorderLayout.NORTH);
         left.add(detail, BorderLayout.SOUTH);
         badge.setFont(Theme.font(Font.BOLD, 10));
         add(left, BorderLayout.CENTER);
         add(badge, BorderLayout.EAST);
         setBorder(BorderFactory.createEmptyBorder(Theme.s(6), Theme.s(12), Theme.s(6), Theme.s(12)));
         setPreferredSize(new Dimension(Theme.s(300), Theme.s(46)));
      }

      public Component getListCellRendererComponent(JList<? extends Entry> l, Entry e, int index, boolean selected, boolean focus) {
         setBackground(selected ? Theme.SURFACE_3 : Theme.SURFACE_2);
         boolean current = e.value.equals(value);
         String cv = e.latest ? (e.release == null ? null : e.release.clientVersion) : e.value;
         name.setForeground(Theme.TEXT);
         if (e.latest) {
            name.setText("Latest" + (cv == null ? "" : "  ·  " + cv));
            detail.setText("Always the newest release");
         } else {
            name.setText(e.value);
            detail.setText(e.release == null ? "Installed · no longer on GitHub" : "Released " + e.release.date());
         }
         boolean inst = cv != null && Installer.isInstalled(cv);
         badge.setText(current ? "SELECTED" : inst ? "INSTALLED" : "");
         badge.setForeground(current ? Theme.ACCENT_HOVER : Theme.OK);
         return this;
      }
   }
}
