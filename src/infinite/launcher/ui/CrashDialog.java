package infinite.launcher.ui;

import infinite.launcher.Dirs;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Shown when the game exits with an error, with the end of its log. */
final class CrashDialog extends JDialog {
   CrashDialog(Window owner, int code, long ms, List<String> tail) {
      super(owner, "Minecraft Infinite stopped", ModalityType.APPLICATION_MODAL);
      JPanel root = Ui.opaque(new JPanel(new BorderLayout(0, Theme.s(12))), Theme.SURFACE);
      Ui.pad(root, 22, 24, 18, 24);
      JPanel head = new JPanel(new BorderLayout(0, Theme.s(4)));
      head.setOpaque(false);
      head.add(Ui.label("The game closed unexpectedly", Font.BOLD, 17, Theme.TEXT), BorderLayout.NORTH);
      head.add(Ui.label("Exit code " + code + " after " + (ms / 1000) + " s. The end of its log is below; the whole "
         + "log is game-latest.log in the logs folder.", Font.PLAIN, 12, Theme.MUTED), BorderLayout.SOUTH);
      StringBuilder b = new StringBuilder();
      int from = Math.max(0, tail.size() - 80);
      for (int i = from; i < tail.size(); i++) {
         b.append(tail.get(i)).append('\n');
      }
      final JTextArea text = new JTextArea(b.toString());
      text.setEditable(false);
      text.setFont(Theme.mono(Font.PLAIN, 11));
      text.setBackground(Theme.BG);
      text.setForeground(new java.awt.Color(0xC9CDD3));
      Ui.pad(text, 8, 10, 8, 10);
      JScrollPane sp = new JScrollPane(text);
      Ui.thin(sp, Theme.BG);
      FlatButton logs = new FlatButton("Open logs folder", FlatButton.Kind.SECONDARY);
      FlatButton copy = new FlatButton("Copy log", FlatButton.Kind.SECONDARY);
      FlatButton close = new FlatButton("Close", FlatButton.Kind.PRIMARY);
      logs.addActionListener(e -> Ui.open(Dirs.logs()));
      copy.addActionListener(e -> Ui.copy(text.getText()));
      close.addActionListener(e -> dispose());
      JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.s(8), 0));
      bar.setOpaque(false);
      bar.add(logs);
      bar.add(copy);
      bar.add(close);
      root.add(head, BorderLayout.NORTH);
      root.add(sp, BorderLayout.CENTER);
      root.add(bar, BorderLayout.SOUTH);
      setContentPane(root);
      Ui.escapeCloses(this, this::dispose);
      setSize(Theme.s(820), Theme.s(520));
      setLocationRelativeTo(owner);
      text.setCaretPosition(text.getDocument().getLength());
   }
}
