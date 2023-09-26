/*
Copyright 2016-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.settings;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.plugins.extpoints.Settings;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.SafeTextTransferHandler;
import gov.sandia.n2a.ui.images.ImageUtil;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.MetalTheme;
import javax.swing.plaf.metal.OceanTheme;

@SuppressWarnings("serial")
public class SettingsLookAndFeel extends JPanel implements Settings
{
    public static SettingsLookAndFeel instance;
    public static float               em = 13;   // Size of M in current JTree font. Used to scale UI items such as nodes in equation graph. Static for convenience, as we could access it through instance.
    public static boolean             rescaling; // True only while we are processing a screen resolution change. Suppresses recording updated size/position info for various UI elements.

    protected Map<String, Laf> catalog = new HashMap<String, Laf> ();
    protected ButtonGroup      group   = new ButtonGroup ();
    protected ActionListener   menuListener;
    protected int              dpi;     // Last collected screen dpi value. If zero, then dpi has not yet been collected for the first time.
    protected double           inches;  // Calculated width of screen. Used only to update dpi after startup.
    protected Laf              currentLaf;
    protected float            fontScale = 1;
    protected JTextField       fieldFontScale;
    protected Set<Object>      overriddenKeys = new TreeSet<Object> ();

    public class Laf
    {
        protected LookAndFeelInfo info;
        protected MetalTheme      theme;
        protected JRadioButton    item;

        public void apply ()
        {
            try
            {
                UIDefaults defaults = UIManager.getDefaults ();
                for (Object key : overriddenKeys)
                {
                    defaults.put (key, null);
                }
                overriddenKeys = new TreeSet<Object> ();

                if (theme != null) MetalLookAndFeel.setCurrentTheme (theme);
                UIManager.setLookAndFeel (info.getClassName ());
                currentLaf = this;
                AppData.state.set (this,      "LookAndFeel");
                AppData.state.set (fontScale, "FontScale");

                // Rescale UI elements
                Toolkit tk = Toolkit.getDefaultToolkit ();
                if (dpi == 0)  // startup
                {
                    dpi    = tk.getScreenResolution ();
                    inches = tk.getScreenSize ().getWidth () / dpi;
                }
                else  // getScreenResolution() does not change after startup, so need to update dpi another way.
                {
                    dpi = (int) Math.round (tk.getScreenSize ().getWidth () / inches);
                }
                float uiScale = dpi / 96f;  // 96 is standard Windows desktop dpi, and probably the scale at which Swing was originally designed.
                float applyScale = fontScale * uiScale;

                //   Set scaled fonts.
                if (applyScale != 1)
                {
                    for (Entry<Object,Object> e : defaults.entrySet ())
                    {
                        Object key   = e.getKey ();
                        Object value = defaults.get (key);
                        if (value instanceof FontUIResource)
                        {
                            FontUIResource fr = (FontUIResource) value;
                            defaults.put (key, new FontUIResource (fr.deriveFont (fr.getSize2D () * applyScale)));
                            overriddenKeys.add (key);
                        }
                    }
                }

                //   Modify controls that shouldn't get too small
                if (uiScale > 1)
                {
                    for (String key : new String[] {"ScrollBar.width", "SplitPane.dividerSize"})
                    {
                        int value = defaults.getInt (key);
                        if (value == 0) continue;
                        defaults.put (key, (int) Math.floor (value * uiScale));
                        overriddenKeys.add (key);
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace ();
            }

            // Update em
            // Takes into account the font scaling we just did above.
            Font font = UIManager.getFont ("Tree.font");
            FontMetrics fm;
            if (MainFrame.instance == null)
            {
                // Application is still booting, so create a temporary hidden window.
                JFrame temp = new JFrame ();
                temp.setExtendedState (JFrame.ICONIFIED);
                temp.setVisible (true);  // Create graphics context.
                fm = temp.getGraphics ().getFontMetrics (font);
                temp.dispose ();  // At this moment, this is the last window. However, the app does not exit because our main thread is still running.
            }
            else
            {
                fm = MainFrame.instance.getGraphics ().getFontMetrics (font);
            }
            em = fm.stringWidth ("M");

            // Call updateUI() on GUI tree.
            if (MainFrame.instance != null) MainFrame.instance.setDimensions ();  // JFrame does not have updateUI(), so we directly update its shape.
            for (Window w : Window.getWindows ()) SwingUtilities.updateComponentTreeUI (w);  // Don't call w.pack(). It creates a mess, such as oversizing the main window.
        }

        public String toString ()
        {
            String result = info.getName ();
            if (theme != null) result += " / " + theme.getName ();
            return result;
        }
    }

    public SettingsLookAndFeel ()
    {
        instance = this;

        for (LookAndFeelInfo lafi : UIManager.getInstalledLookAndFeels ())
        {
            if (lafi.getName ().equals ("Metal"))
            {
                Laf lafTheme = new Laf ();
                lafTheme.info = lafi;
                lafTheme.theme = new OceanTheme ();
                catalog.put (lafTheme.toString (), lafTheme);

                lafTheme = new Laf ();
                lafTheme.info = lafi;
                lafTheme.theme = new DefaultMetalTheme ();
                catalog.put (lafTheme.toString (), lafTheme);
            }
            else
            {
                Laf laf = new Laf ();
                laf.info = lafi;
                catalog.put (laf.toString (), laf);
            }
        }

        menuListener = new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                String name = e.getActionCommand ();
                Laf laf = catalog.get (name);
                if (laf == null) return;
                Laf oldLaf = currentLaf;
                laf.apply ();
                if (oldLaf == currentLaf)  // failed to apply the new laf
                {
                    group.setSelected (currentLaf.item.getModel (), true);
                }
            }
        };

        JLabel labelFontScale = new JLabel ("Font Scale");
        fieldFontScale = new JTextField (Float.toString (fontScale), 10);
        fieldFontScale.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent arg0)
            {
                fontScale = Float.parseFloat (fieldFontScale.getText ());
                if (fontScale <= 0) fontScale = 1;
                if (fontScale > 5) fontScale = 5;  // Bigger than this may make the app unusable. If user really needs bigger font, they can hack the client state file directly.
                fieldFontScale.setText (Float.toString (fontScale));
                if (currentLaf != null) currentLaf.apply ();
            }
        });
        fieldFontScale.setTransferHandler (new SafeTextTransferHandler ());
        JPanel panelFontScale = Lay.FL (labelFontScale, fieldFontScale);
        panelFontScale.setAlignmentX (LEFT_ALIGNMENT);
        JPanel menu = Lay.BxL
        (
            panelFontScale,
            Box.createVerticalStrut (15)
        );

        String currentClass = UIManager.getLookAndFeel ().getClass ().getName ();
        for (Laf laf : catalog.values ())
        {
            boolean selected = currentClass.equals (laf.info.getClassName ());
            if (selected) currentLaf = laf;
            laf.item = new JRadioButton (laf.toString (), selected);
            laf.item.addActionListener (menuListener);
            laf.item.setAlignmentX (LEFT_ALIGNMENT);
            menu.add (laf.item);
            group.add (laf.item);
        }
        if (currentLaf != null) group.setSelected (currentLaf.item.getModel (), true);

        Lay.BLtg (this, "N", menu);
    }

    public void load ()
    {
        fontScale = (float) AppData.state.getOrDefault (1.0, "FontScale");
        fieldFontScale.setText (Float.toString (fontScale));

        Laf laf = null;
        String name = AppData.state.get ("LookAndFeel");
        if (! name.isEmpty ()) laf = catalog.get (name);
        if (laf == null) laf = currentLaf;  // currentLaf is set by constructor above.
        if (laf == null) return;  // but just in case we couldn't find it

        laf.apply ();
        group.setSelected (laf.item.getModel (), true);
    }

    public boolean checkRescale ()
    {
        if (rescaling) return true;
        int newDPI = (int) Math.round (Toolkit.getDefaultToolkit ().getScreenSize ().getWidth () / inches);
        if (dpi == newDPI) return false;
        rescaling = true;
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                if (currentLaf != null) currentLaf.apply ();
                rescaling = false;
            }
        });
        return true;
    }

    @Override
    public String getName ()
    {
        return "Look & Feel";
    }

    @Override
    public ImageIcon getIcon ()
    {
        return ImageUtil.getImage ("view.gif");
    }

    @Override
    public Component getPanel ()
    {
        return this;
    }

    @Override
    public Component getInitialFocus (Component panel)
    {
        return panel;
    }
}
