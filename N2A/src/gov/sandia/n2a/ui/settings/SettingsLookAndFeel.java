/*
Copyright 2016,2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
 */

package gov.sandia.n2a.ui.settings;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.plugins.extpoints.Settings;
import gov.sandia.n2a.ui.images.ImageUtil;

import java.awt.Component;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.UIManager.LookAndFeelInfo;
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

    public class Laf
    {
        protected LookAndFeel  instance;
        protected MetalTheme   theme;
        protected JRadioButton item;

        public void apply ()
        {
            if (theme != null) MetalLookAndFeel.setCurrentTheme (theme);
            try
            {
                UIManager.setLookAndFeel (instance);
                currentLaf = this;
                AppData.state.set ("LookAndFeel", this);
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
                catalog.put (lafTheme.toString (), lafTheme);

                lafTheme = new Laf ();
                lafTheme.instance = laf.instance;
                lafTheme.theme = new DefaultMetalTheme ();
                catalog.put (lafTheme.toString (), lafTheme);
            }
            else
            {
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
    }

    public void load ()
    {
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
