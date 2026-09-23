package infinite.launcher.ui;

import infinite.launcher.Os;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;

/**
 * Colours, fonts and scale. Java 8 doesn't scale Swing on high-DPI Windows or Linux screens, so
 * every pixel size in the UI goes through s(). macOS scales Retina itself.
 */
public final class Theme {
   public static final Color BG = new Color(0x131519);
   public static final Color SURFACE = new Color(0x1B1E23);
   public static final Color SURFACE_2 = new Color(0x252930);
   public static final Color SURFACE_3 = new Color(0x2F343C);
   public static final Color BORDER = new Color(0x2C3037);
   public static final Color TEXT = new Color(0xE9EBEE);
   public static final Color MUTED = new Color(0x9BA2AC);
   public static final Color FAINT = new Color(0x6B727C);
   public static final Color ACCENT = new Color(0x3D8B37);
   public static final Color ACCENT_HOVER = new Color(0x4AA242);
   public static final Color ACCENT_DOWN = new Color(0x317330);
   public static final Color OK = new Color(0x5BB974);
   public static final Color WARN = new Color(0xE3A93F);
   public static final Color ERROR = new Color(0xE5605A);
   public static final Color LINK = new Color(0x7FB3FF);

   public static final float SCALE = scale();
   private static final String FAMILY = family();

   public static int s(int px) {
      return Math.round(px * SCALE);
   }

   public static Font font(int style, int size) {
      return new Font(FAMILY, style, s(size));
   }

   public static Font mono(int style, int size) {
      return new Font(Os.WINDOWS ? "Consolas" : Os.MAC ? "Menlo" : "Monospaced", style, s(size));
   }

   public static void hints(java.awt.Graphics2D g) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
   }

   private static float scale() {
      String o = System.getProperty("infinite.ui.scale");
      if (o != null) {
         try {
            return Math.max(0.5f, Float.parseFloat(o));
         } catch (NumberFormatException ignored) {
         }
      }
      if (Os.MAC) {
         return 1f;
      }
      try {
         int dpi = Toolkit.getDefaultToolkit().getScreenResolution();
         float f = dpi / 96f;
         return Math.max(1f, Math.round(f * 4) / 4f);
      } catch (Exception e) {
         return 1f;
      }
   }

   private static String family() {
      Set<String> have = new HashSet<String>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
      for (String f : new String[] { "Segoe UI", ".SF NS Text", "SF Pro Text", "Helvetica Neue", "Ubuntu", "Cantarell", "Noto Sans",
         "DejaVu Sans", "Liberation Sans" }) {
         if (have.contains(f)) {
            return f;
         }
      }
      return Font.SANS_SERIF;
   }

   /** Metal with a dark palette, so dialogs, menus and scrollbars match the custom-drawn parts. */
   public static void install() {
      try {
         MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme() {
            public String getName() {
               return "Infinite";
            }

            protected ColorUIResource getPrimary1() {
               return new ColorUIResource(ACCENT);
            }

            protected ColorUIResource getPrimary2() {
               return new ColorUIResource(SURFACE_3);
            }

            protected ColorUIResource getPrimary3() {
               return new ColorUIResource(SURFACE_3);
            }

            protected ColorUIResource getSecondary1() {
               return new ColorUIResource(BORDER);
            }

            protected ColorUIResource getSecondary2() {
               return new ColorUIResource(SURFACE_2);
            }

            protected ColorUIResource getSecondary3() {
               return new ColorUIResource(SURFACE);
            }

            protected ColorUIResource getWhite() {
               return new ColorUIResource(SURFACE_2);
            }

            protected ColorUIResource getBlack() {
               return new ColorUIResource(TEXT);
            }

            public ColorUIResource getControlTextColor() {
               return new ColorUIResource(TEXT);
            }

            public ColorUIResource getMenuForeground() {
               return new ColorUIResource(TEXT);
            }

            public ColorUIResource getInactiveControlTextColor() {
               return new ColorUIResource(FAINT);
            }

            public FontUIResource getControlTextFont() {
               return new FontUIResource(font(Font.PLAIN, 13));
            }

            public FontUIResource getSystemTextFont() {
               return new FontUIResource(font(Font.PLAIN, 13));
            }

            public FontUIResource getUserTextFont() {
               return new FontUIResource(font(Font.PLAIN, 13));
            }

            public FontUIResource getMenuTextFont() {
               return new FontUIResource(font(Font.PLAIN, 13));
            }

            public FontUIResource getWindowTitleFont() {
               return new FontUIResource(font(Font.BOLD, 13));
            }

            public FontUIResource getSubTextFont() {
               return new FontUIResource(font(Font.PLAIN, 11));
            }
         });
         UIManager.setLookAndFeel(new MetalLookAndFeel());
      } catch (Exception ignored) {
      }
      UIManager.put("Panel.background", SURFACE);
      UIManager.put("OptionPane.background", SURFACE);
      UIManager.put("OptionPane.messageForeground", TEXT);
      UIManager.put("Label.foreground", TEXT);
      UIManager.put("CheckBox.background", SURFACE);
      UIManager.put("CheckBox.foreground", TEXT);
      UIManager.put("RadioButton.background", SURFACE);
      UIManager.put("RadioButton.foreground", TEXT);
      UIManager.put("TextField.background", SURFACE_2);
      UIManager.put("TextField.foreground", TEXT);
      UIManager.put("TextField.caretForeground", TEXT);
      UIManager.put("TextField.selectionBackground", ACCENT);
      UIManager.put("TextField.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
         BorderFactory.createEmptyBorder(s(6), s(8), s(6), s(8))));
      UIManager.put("TextArea.background", SURFACE_2);
      UIManager.put("TextArea.foreground", TEXT);
      UIManager.put("Slider.background", SURFACE);
      UIManager.put("Slider.foreground", ACCENT);
      UIManager.put("List.background", SURFACE_2);
      UIManager.put("List.foreground", TEXT);
      UIManager.put("List.selectionBackground", SURFACE_3);
      UIManager.put("List.selectionForeground", TEXT);
      UIManager.put("PopupMenu.background", SURFACE_2);
      UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(BORDER));
      UIManager.put("MenuItem.background", SURFACE_2);
      UIManager.put("MenuItem.foreground", TEXT);
      UIManager.put("MenuItem.selectionBackground", SURFACE_3);
      UIManager.put("MenuItem.selectionForeground", TEXT);
      UIManager.put("Menu.background", SURFACE_2);
      UIManager.put("Menu.foreground", TEXT);
      UIManager.put("Menu.selectionBackground", SURFACE_3);
      UIManager.put("Menu.selectionForeground", TEXT);
      UIManager.put("Separator.foreground", BORDER);
      UIManager.put("Separator.background", SURFACE_2);
      UIManager.put("ToolTip.background", SURFACE_3);
      UIManager.put("ToolTip.foreground", TEXT);
      UIManager.put("ToolTip.border", BorderFactory.createLineBorder(BORDER));
      UIManager.put("ScrollPane.background", SURFACE);
      UIManager.put("Viewport.background", SURFACE);
      UIManager.put("Button.background", SURFACE_3);
      UIManager.put("Button.foreground", TEXT);
      UIManager.put("Button.select", ACCENT_DOWN);
      UIManager.put("Button.focus", new ColorUIResource(SURFACE_3));
      UIManager.put("ProgressBar.foreground", ACCENT);
      UIManager.put("ProgressBar.background", SURFACE_3);
   }

   private Theme() {
   }
}
