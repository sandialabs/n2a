/*
Copyright 2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.xyce;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MTextField;
import gov.sandia.n2a.ui.settings.SettingsBackend;

import javax.swing.JLabel;
import javax.swing.JPanel;

public class SettingsXyce extends SettingsBackend
{
    protected MTextField fieldXyce = new MTextField (40);

    public SettingsXyce ()
    {
        key          = "xyce";
        iconFileName = "xyce-16.png";
    }

    @Override
    public String getName ()
    {
        return "Backend Xyce";
    }

    @Override
    public void bind (MNode parent)
    {
        fieldXyce.bind (parent, "path", "xyce");
    }

    @Override
    public JPanel getEditor ()
    {
        return Lay.BxL (
            Lay.FL (new JLabel ("Xyce path"), fieldXyce)
        );
    }
}
