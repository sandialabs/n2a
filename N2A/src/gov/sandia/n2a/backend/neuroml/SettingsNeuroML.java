/*
Copyright 2018-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MTextField;
import gov.sandia.n2a.ui.settings.SettingsBackend;

import javax.swing.JLabel;
import javax.swing.JPanel;

public class SettingsNeuroML extends SettingsBackend
{
    protected MTextField fieldHome = new MTextField (40);

    public SettingsNeuroML ()
    {
        key          = "neuroml";
        iconFileName = "NeuroML.png";
    }

    @Override
    public String getName ()
    {
        return "Backend NeuroML";
    }

    @Override
    public void bind (MNode parent)
    {
        fieldHome.bind (parent, "JNML_HOME", "/usr/local/jNeuroML");
    }

    @Override
    public JPanel getEditor ()
    {
        return Lay.BxL (
            Lay.FL (new JLabel ("JNML_HOME"), fieldHome)
        );
    }
}
