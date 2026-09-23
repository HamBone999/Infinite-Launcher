package infinite.launcher.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JButton;

/** A flat, rounded button in one of three weights. */
public class FlatButton extends JButton {
   public enum Kind { PRIMARY, SECONDARY, GHOST }

   private Kind kind;
   private boolean hover;
   private String subtitle;

   public FlatButton(String text, Kind kind) {
      super(text);
      this.kind = kind;
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setOpaque(false);
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      setFont(Theme.font(kind == Kind.PRIMARY ? Font.BOLD : Font.PLAIN, 13));
      setForeground(Theme.TEXT);
      addMouseListener(new MouseAdapter() {
         public void mouseEntered(MouseEvent e) {
            hover = true;
            repaint();
         }

         public void mouseExited(MouseEvent e) {
            hover = false;
            repaint();
         }
      });
   }

   public void setKind(Kind k) {
      kind = k;
      repaint();
   }

   public void setSubtitle(String s) {
      subtitle = s;
      repaint();
   }

   @Override
   public Dimension getPreferredSize() {
      if (isPreferredSizeSet()) {
         return super.getPreferredSize();
      }
      FontMetrics fm = getFontMetrics(getFont());
      return new Dimension(fm.stringWidth(getText()) + Theme.s(32), Math.max(Theme.s(34), fm.getHeight() + Theme.s(14)));
   }

   @Override
   protected void paintComponent(Graphics g0) {
      Graphics2D g = (Graphics2D)g0.create();
      Theme.hints(g);
      boolean down = getModel().isArmed() && getModel().isPressed();
      boolean on = isEnabled();
      Color bg;
      Color fg = Theme.TEXT;
      if (kind == Kind.PRIMARY) {
         bg = !on ? Theme.SURFACE_3 : down ? Theme.ACCENT_DOWN : hover ? Theme.ACCENT_HOVER : Theme.ACCENT;
         fg = on ? Color.WHITE : Theme.FAINT;
      } else if (kind == Kind.SECONDARY) {
         bg = !on ? Theme.SURFACE_2 : down ? Theme.BORDER : hover ? new Color(0x383E47) : Theme.SURFACE_3;
         fg = on ? Theme.TEXT : Theme.FAINT;
      } else {
         bg = down ? Theme.SURFACE_3 : hover && on ? Theme.SURFACE_2 : null;
         fg = on ? Theme.TEXT : Theme.FAINT;
      }
      int arc = Theme.s(10);
      if (bg != null) {
         g.setColor(bg);
         g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
      }
      g.setFont(getFont());
      FontMetrics fm = g.getFontMetrics();
      g.setColor(fg);
      if (subtitle == null) {
         int x = (getWidth() - fm.stringWidth(getText())) / 2;
         int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
         g.drawString(getText(), x, y);
      } else {
         Font sub = Theme.font(Font.PLAIN, 11);
         FontMetrics sm = g.getFontMetrics(sub);
         int total = fm.getHeight() + sm.getHeight() - Theme.s(2);
         int top = (getHeight() - total) / 2;
         g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, top + fm.getAscent());
         g.setFont(sub);
         g.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 190));
         g.drawString(subtitle, (getWidth() - sm.stringWidth(subtitle)) / 2, top + fm.getHeight() - Theme.s(2) + sm.getAscent());
      }
      g.dispose();
   }
}
