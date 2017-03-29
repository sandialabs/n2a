/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
 */

package gov.sandia.n2a.ui;

import gov.sandia.n2a.db.AppData;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.MetalTheme;
import javax.swing.plaf.metal.OceanTheme;

public class LafManager
{
    protected static Map<String, Laf> catalog = new HashMap<String, Laf> ();
    protected static JMenu            menu    = new JMenu ("Look&Feel");
    protected static ButtonGroup      group   = new ButtonGroup ();
    protected static ActionListener   menuListener;
    protected static Laf              currentLaf;

    public static class Laf
    {
        protected LookAndFeel          instance;
        protected MetalTheme           theme;
        protected JRadioButtonMenuItem item;

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

    static
    {
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

        String currentClass = UIManager.getLookAndFeel ().getClass ().getName ();
        for (Laf laf : catalog.values ())
        {
            boolean selected = currentClass.equals (laf.instance.getClass ().getName ());
            if (selected) currentLaf = laf;
            laf.item = new JRadioButtonMenuItem (laf.toString (), selected);
            laf.item.addActionListener (menuListener);
            menu.add (laf.item);
            group.add (laf.item);
        }
        if (currentLaf != null) group.setSelected (currentLaf.item.getModel (), true);
    }

    public static JMenu getMenu ()
    {
        return menu;
    }

    public static void load ()
    {
        String name = AppData.state.get ("LookAndFeel");
        if (name.isEmpty ()) return;
        Laf laf = catalog.get (name);
        if (laf == null) return;
        laf.apply ();
        group.setSelected (laf.item.getModel (), true);
    }
}
