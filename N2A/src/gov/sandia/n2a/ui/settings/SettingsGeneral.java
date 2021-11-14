/*
Copyright 2017-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.settings;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.plugins.extpoints.Settings;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.images.ImageUtil;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

@SuppressWarnings("serial")
public class SettingsGeneral extends JPanel implements Settings
{
    public JPanel addField (final String key, String description, int width, String defaultValue)
    {
        JLabel label = new JLabel (description);
        String value = AppData.state.getOrDefault (defaultValue, "General", key);
        final JTextField field = new JTextField (value, width);
        field.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent arg0)
            {
                AppData.state.set (field.getText (), "General", key);
            }
        });
        field.addFocusListener (new FocusAdapter ()
        {
            public void focusLost (FocusEvent e)
            {
                AppData.state.set (field.getText (), "General", key);
            }
        });
        return Lay.BL ("W", Lay.FL ("H", label, field));
    }

    public JPanel addFieldSystemProperty (final String key, String description, int width, String systemPropertyName)
    {
        String value = AppData.state.get ("General", key);
        if (! value.isEmpty ()) System.setProperty (systemPropertyName, value);

        JLabel label = new JLabel (description);
        final JTextField field = new JTextField (value, width);
        field.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent arg0)
            {
                String value = field.getText ();
                AppData.state.set (value, "General", key);
                if (! value.isEmpty ()) System.setProperty (systemPropertyName, value);
            }
        });
        field.addFocusListener (new FocusAdapter ()
        {
            public void focusLost (FocusEvent e)
            {
                String value = field.getText ();
                AppData.state.set (value, "General", key);
                if (! value.isEmpty ()) System.setProperty (systemPropertyName, value);
            }
        });
        return Lay.BL ("W", Lay.FL ("H", label, field));
    }

    public JPanel addCombo (final String key, String description, int defaultIndex, String... choices)
    {
        JLabel label = new JLabel (description);
        String value = AppData.state.getOrDefault (choices[defaultIndex], "General", key);
        final JComboBox<String> field = new JComboBox<String> (choices);
        field.setSelectedItem (value);
        field.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent arg0)
            {
                AppData.state.set (field.getSelectedItem (), "General", key);
            }
        });
        field.addFocusListener (new FocusAdapter ()
        {
            public void focusLost (FocusEvent e)
            {
                AppData.state.set (field.getSelectedItem (), "General", key);
            }
        });
        return Lay.BL ("W", Lay.FL ("H", label, field));
    }

    public SettingsGeneral ()
    {
        JPanel constants = addField ("constants", "Model that provides global constants", 40, "Constants");
        JPanel dimension = addCombo ("dimension", "How to handle inconsistent dimensions", 1, "Don't check", "Warning", "Error");
        JPanel snapshot  = addCombo ("snapshot", "Which models to save in snapshot when starting a simulation", 1, "No snapshot", "Only main model", "All referenced models");
        JPanel proxyHost     = addFieldSystemProperty ("httpsProxyHost",    "HTTPS Proxy Host", 40, "https.proxyHost");
        JPanel proxyPort     = addFieldSystemProperty ("httpsProxyPort",    "HTTPS Proxy Port",  5, "https.proxyPort");
        JPanel nonProxyHosts = addFieldSystemProperty ("httpNonProxyHosts", "Non-Proxy Hosts",  40, "http.nonProxyHosts");
        JPanel form = Lay.BxL
        (
            constants,
            dimension,
            snapshot,
            proxyHost,
            proxyPort,
            nonProxyHosts
        );
        Lay.BLtg (this, "N", form);
    }

    @Override
    public String getName ()
    {
        return "General";
    }

    @Override
    public ImageIcon getIcon ()
    {
        return ImageUtil.getImage ("properties.gif");
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
