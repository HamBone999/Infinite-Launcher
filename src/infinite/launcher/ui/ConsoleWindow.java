package infinite.launcher.ui;

import infinite.launcher.Dirs;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;

/** Live game output. Lines are batched onto the screen a few times a second. */
final class ConsoleWindow extends JFrame {
   private final JTextArea text = new JTextArea();
   private final ConcurrentLinkedQueue<String> pending = new ConcurrentLinkedQueue<String>();

   ConsoleWindow() {
      super("Game console — Minecraft Infinite");
      text.setEditable(false);
      text.setFont(Theme.mono(Font.PLAIN, 12));
      text.setBackground(Theme.BG);
      text.setForeground(new java.awt.Color(0xC9CDD3));
      text.setCaretColor(Theme.TEXT);
      Ui.pad(text, 8, 10, 8, 10);
      JScrollPane sp = new JScrollPane(text);
      Ui.thin(sp, Theme.BG);
      FlatButton log = new FlatButton("Open log folder", FlatButton.Kind.SECONDARY);
      FlatButton copy = new FlatButton("Copy all", FlatButton.Kind.SECONDARY);
      FlatButton clear = new FlatButton("Clear", FlatButton.Kind.GHOST);
      log.addActionListener(e -> Ui.open(Dirs.logs()));
      copy.addActionListener(e -> Ui.copy(text.getText()));
      clear.addActionListener(e -> text.setText(""));
      JPanel bar = Ui.opaque(new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8)), Theme.SURFACE);
      bar.add(clear);
      bar.add(copy);
      bar.add(log);
      getContentPane().add(sp, BorderLayout.CENTER);
      getContentPane().add(bar, BorderLayout.SOUTH);
      setSize(Theme.s(900), Theme.s(520));
      new Timer(150, e -> flush()).start();
   }

   void append(String line) {
      pending.add(line);
   }

   private void flush() {
      if (pending.isEmpty()) {
         return;
      }
      StringBuilder b = new StringBuilder();
      String l;
      while ((l = pending.poll()) != null) {
         b.append(l).append('\n');
      }
      text.append(b.toString());
      int lines = text.getLineCount();
      if (lines > 5000) {
         try {
            text.replaceRange("", 0, text.getLineEndOffset(lines - 5000));
         } catch (BadLocationException ignored) {
         }
      }
      text.setCaretPosition(text.getDocument().getLength());
   }

   static File latestLog() {
      return new File(Dirs.logs(), "game-latest.log");
   }
}
