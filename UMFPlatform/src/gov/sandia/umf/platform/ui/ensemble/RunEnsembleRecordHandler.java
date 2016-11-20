/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.ensemble;

import java.awt.Component;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.RecordHandler;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;

import javax.swing.ImageIcon;
import javax.swing.JTabbedPane;

import replete.util.Lay;

public class RunEnsembleRecordHandler implements RecordHandler
{
    @Override
    public ImageIcon getIcon ()
    {
        return ImageUtil.getImage("analysis.gif");
    }

    @Override
    public String getName ()
    {
        return "Analysis";
    }

    @Override
    public Component getPanel ()
    {
        return new TestRecordPanel ();
    }

    @Override
    public Component getInitialFocus (Component panel)
    {
        return null;
    }

    private class TestRecordPanel extends JTabbedPane
    {
        public TestRecordPanel ()
        {
            MNode doc = null;  // TODO: Obtain currently selected document from Model tab. This should probably be a system-wide property held in UIController, or perhaps AppData
            System.out.println ("RunEnsembleRecordHandler.TestRecordPanel doc = " + doc);

            ParameterSpecGroupSet groups = new ParameterSpecGroupSet (doc);

            addTab("Parameterization",
                Lay.BL(
                    "W", Lay.hn(new ParameterSpecGroupsPanel(groups), "prefw=400"),
                    "C", Lay.p())
            );
            addTab("Outputs", Lay.p());
        }
    }
}
