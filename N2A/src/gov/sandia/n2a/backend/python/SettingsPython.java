/*
Copyright 2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.python;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MTextField;
import gov.sandia.n2a.ui.settings.SettingsBackend;

import javax.swing.JLabel;
import javax.swing.JPanel;

public class SettingsPython extends SettingsBackend
{
    protected MTextField fieldPython = new MTextField (40);

    public SettingsPython ()
    {
        key          = "python";
        iconFileName = "python-16.png";
    }

    @Override
    public String getName ()
    {
        return "Backend Python";
    }

    @Override
    public void bind (MNode parent)
    {
        fieldPython.bind (parent, "path", "python");
    }

    @Override
    public JPanel getEditor ()
    {
        return Lay.BxL (
            Lay.FL (new JLabel ("Python path"), fieldPython)
        );
    }
}
