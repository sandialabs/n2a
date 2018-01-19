/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class SettingsGeneral extends JPanel implements Settings
{
    public JPanel addField (final String key, String description, int width, String defaultValue)
    {
        JLabel label = new JLabel (description);
        String value = AppData.state.getOrDefault ("General", key, defaultValue);
        final JTextField field = new JTextField (value, width);
        field.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent arg0)
            {
                AppData.state.set ("General", key, field.getText ());
            }
        });
        field.addFocusListener (new FocusAdapter ()
        {
            public void focusLost (FocusEvent e)
            {
                AppData.state.set ("General", key, field.getText ());
            }
        });
        return Lay.FL ("H", label, field);
    }

    public SettingsGeneral ()
    {
        JPanel constants = addField ("constants", "Model \"blessed\" to provide global constants", 40, "Constants");
        JPanel form = Lay.BxL
        (
            constants
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
