/*
Copyright 2020-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MTextField;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.settings.SettingsBackend;

import javax.swing.JLabel;
import javax.swing.JPanel;

public class SettingsC extends SettingsBackend
{
    protected MTextField fieldCpp = new MTextField (40);

    public SettingsC ()
    {
        key  = "c";
        icon = ImageUtil.getImage ("BackendC.png");
    }

    @Override
    public String getName ()
    {
        return "Backend C";
    }

    @Override
    public void bind (MNode parent)
    {
        fieldCpp.bind (parent, "cxx", "g++");
    }

    @Override
    public JPanel getEditor ()
    {
        return Lay.BxL (
            Lay.BL ("W", Lay.FL ("H", new JLabel ("Compiler path"), fieldCpp))
        );
    }
}
