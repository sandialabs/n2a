/*
Copyright 2016,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.settings;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.plugins.extpoints.Settings;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.SafeTextTransferHandler;
import gov.sandia.n2a.ui.images.ImageUtil;

import java.awt.Component;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.MetalTheme;
import javax.swing.plaf.metal.OceanTheme;

public class SettingsLookAndFeel implements Settings
{
    public static SettingsLookAndFeel instance;

    protected Map<String, Laf> catalog = new HashMap<String, Laf> ();
    protected JPanel           menu    = new JPanel ();
    protected ButtonGroup      group   = new ButtonGroup ();
    protected ActionListener   menuListener;
    protected Laf              currentLaf;
    protected float            fontScale = 1;
    protected JTextField       fieldFontScale;
    protected Set<Object>      overriddenKeys;

    public class Laf
    {
        protected LookAndFeel  instance;
        protected MetalTheme   theme;
        protected JRadioButton item;

        public void getFonts ()
        {
        }

        public void apply ()
        {
            try
            {
                if (overriddenKeys != null)
                {
                    UIDefaults defaults = UIManager.getDefaults ();
                    for (Object key : overriddenKeys)
                    {
                        defaults.put (key, null);
                    }
                    overriddenKeys = null;
                }

                if (theme != null) MetalLookAndFeel.setCurrentTheme (theme);
                UIManager.setLookAndFeel (instance);
                currentLaf = this;
                AppData.state.set (this,      "LookAndFeel");
                AppData.state.set (fontScale, "FontScale");

                // Set scaled fonts.
                if (fontScale != 1)
                {
                    overriddenKeys = new TreeSet<Object> ();
                    UIDefaults defaults = UIManager.getDefaults ();
                    for (Entry<Object,Object> e : defaults.entrySet ())
                    {
                        Object key   = e.getKey ();
                        Object value = defaults.get (key);
                        if (value instanceof FontUIResource)
                        {
                            FontUIResource fr = (FontUIResource) value;
                            defaults.put (key, new FontUIResource (fr.deriveFont (fr.getSize2D () * fontScale)));
                            overriddenKeys.add (key);
                        }
                    }
                }
            }
            catch (UnsupportedLookAndFeelException e)
            {
            }
            for (Window w : Window.getWindows ()) SwingUtilities.updateComponentTreeUI (w);  // TODO: add pack() here?
        }

        public String toString ()
        {
            String result = instance.getName ();
            if (theme != null) result += " / " + theme.getName ();
            return result;
        }
    }

    public SettingsLookAndFeel ()
    {
        instance = this;

        Set<String> potential = new TreeSet<String> ();
        potential.add ("javax.swing.plaf.metal.MetalLookAndFeel");
        potential.add ("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
        potential.add ("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        potential.add ("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
        potential.add ("com.sun.java.swing.plaf.mac.MacLookAndFeel");
        potential.add ("com.apple.laf.AquaLookAndFeel");

        // Add any other LAF's that might be installed on the host, but not present in the above list.
        for (LookAndFeelInfo lafi : UIManager.getInstalledLookAndFeels ())
        {
            potential.add (lafi.getClassName ());
        }

        for (String className : potential)
        {
            // Instantiate the object associated with the class.
            Laf laf = new Laf ();
            try
            {
                ClassLoader loader = Thread.currentThread ().getContextClassLoader ();
                Class<?> lafClass = Class.forName (className, true, loader);
                laf.instance = (LookAndFeel) lafClass.newInstance ();
            }
            catch (Exception e)
            {
                continue;  // Skip if cannot instantiate.
            }
            if (! laf.instance.isSupportedLookAndFeel ()) continue;  // Skip if not supported on this platform.

            if (laf.instance instanceof MetalLookAndFeel)
            {
                Laf lafTheme = new Laf ();
                lafTheme.instance = laf.instance;
                lafTheme.theme = new OceanTheme ();
                lafTheme.getFonts ();
                catalog.put (lafTheme.toString (), lafTheme);

                lafTheme = new Laf ();
                lafTheme.instance = laf.instance;
                lafTheme.theme = new DefaultMetalTheme ();
                lafTheme.getFonts ();
                catalog.put (lafTheme.toString (), lafTheme);
            }
            else
            {
                laf.getFonts ();
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

        menu.setLayout (new BoxLayout (menu, BoxLayout.Y_AXIS));
        String currentClass = UIManager.getLookAndFeel ().getClass ().getName ();
        for (Laf laf : catalog.values ())
        {
            boolean selected = currentClass.equals (laf.instance.getClass ().getName ());
            if (selected) currentLaf = laf;
            laf.item = new JRadioButton (laf.toString (), selected);
            laf.item.addActionListener (menuListener);
            menu.add (laf.item);
            group.add (laf.item);
        }
        if (currentLaf != null) group.setSelected (currentLaf.item.getModel (), true);

        JLabel labelFontScale = new JLabel ("Font Scale:");
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
        menu.add (Lay.FL (labelFontScale, fieldFontScale));
    }

    public void load ()
    {
        fontScale = (float) AppData.state.getOrDefault (1.0, "FontScale");
        fieldFontScale.setText (Float.toString (fontScale));

        String name = AppData.state.get ("LookAndFeel");
        if (name.isEmpty ()) return;
        Laf laf = catalog.get (name);
        if (laf == null) return;
        laf.apply ();
        group.setSelected (laf.item.getModel (), true);
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
        return menu;
    }

    @Override
    public Component getInitialFocus (Component panel)
    {
        return panel;
    }
}
