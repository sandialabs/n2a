/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.stacs;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MTextField;
import gov.sandia.n2a.ui.settings.SettingsBackend;

import javax.swing.JLabel;
import javax.swing.JPanel;

public class SettingsSTACS extends SettingsBackend
{
    protected MTextField fieldStacs = new MTextField (40);

    public SettingsSTACS ()
    {
        key          = "stacs";
        iconFileName = "stacs.png";
    }

    @Override
    public String getName ()
    {
        return "Backend STACS";
    }

    @Override
    public void bind (MNode parent)
    {
        fieldStacs.bind (parent, "stacs", "stacs");
    }

    @Override
    public JPanel getEditor ()
    {
        return Lay.BxL (
            Lay.FL (new JLabel ("STACS path"), fieldStacs)
        );
    }
}
