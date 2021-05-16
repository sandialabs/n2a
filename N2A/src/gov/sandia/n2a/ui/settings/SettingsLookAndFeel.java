/*
Copyright 2016-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
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

    protected Map<String, Laf> catalog = new HashMap<String, Laf> ();
    protected ButtonGroup      group   = new ButtonGroup ();
    protected ActionListener   menuListener;
    protected Laf              currentLaf;
    protected float            fontScale = 1;
    protected JTextField       fieldFontScale;
    protected Set<Object>      overriddenKeys;

    public class Laf
    {
        protected LookAndFeelInfo info;
        protected MetalTheme      theme;
        protected JRadioButton    item;

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
                UIManager.setLookAndFeel (info.getClassName ());
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
            catch (Exception e)
            {
                e.printStackTrace ();
            }
            for (Window w : Window.getWindows ()) SwingUtilities.updateComponentTreeUI (w);  // TODO: add pack() here?
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
        JPanel panelFontScale = Lay.FL ("L", labelFontScale, fieldFontScale);
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
        return this;
    }

    @Override
    public Component getInitialFocus (Component panel)
    {
        return panel;
    }
}
