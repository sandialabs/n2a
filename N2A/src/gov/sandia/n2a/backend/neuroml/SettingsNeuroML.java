/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.plugins.extpoints.Settings;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.SafeTextTransferHandler;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.net.URL;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

@SuppressWarnings("serial")
public class SettingsNeuroML extends JPanel implements Settings
{
    ImageIcon icon;

    public static class Item extends JPanel
    {
        JLabel     label;
        JTextField field;

        public Item (String text, String key, String defaultValue, int length)
        {
            label = new JLabel (text);
            field = new JTextField (AppData.state.getOrDefault ("BackendNeuroML", key, defaultValue), length);
            field.addActionListener (new ActionListener ()
            {
                public void actionPerformed (ActionEvent arg0)
                {
                    AppData.state.set ("BackendNeuroML", key, field.getText ());
                }
            });
            field.addFocusListener (new FocusAdapter ()
            {
                public void focusLost (FocusEvent e)
                {
                    AppData.state.set ("BackendNeuroML", key, field.getText ());
                }
            });
            field.setTransferHandler (new SafeTextTransferHandler ());

            Lay.FLtg (this, "L", label, field);
        }
    }

    public SettingsNeuroML ()
    {
        setName ("Backend NeuroML");  // Use JPanel to fulfill Settings.getName()

        JPanel box = Lay.BxL ("V",
            new Item ("JNML_HOME", "JNML_HOME", "/usr/local/jNeuroML", 40)
        );
        Lay.BLtg (this,
            "N", box
        );
    }

    @Override
    public ImageIcon getIcon ()
    {
        if (icon == null)
        {
            URL imageURL = getClass ().getResource ("NeuroML.png");
            if (imageURL != null) icon = new ImageIcon (imageURL);
        }
        return icon;  // Can be null, if we fail to load the image.
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
