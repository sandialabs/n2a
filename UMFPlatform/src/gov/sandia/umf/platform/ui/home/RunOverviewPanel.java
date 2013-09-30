/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.home;

import gov.sandia.umf.platform.ui.UIController;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import replete.util.Lay;

public class RunOverviewPanel extends JPanel {
    private UIController uiController;
    private JTable tblRuns;
    private RunOverviewTableModel mdlRuns = new RunOverviewTableModel();
    public RunOverviewPanel(UIController uic) {
        uiController = uic;
//        Query query = Query.create().eq("Owner", User.getName());
//        mdlRuns.setResults(Run.get(query));
        Lay.BLtg(this, "C", Lay.sp(tblRuns = new JTable(mdlRuns)),
            "bg=white,augb=mb(1,black)"
        );
        tblRuns.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(SwingUtilities.isLeftMouseButton(e) &&
                        e.getClickCount() == 2 &&
                            tblRuns.rowAtPoint(e.getPoint()) != -1 &&
                            tblRuns.getSelectedRowCount() != 0) {
                    doOpen();
                }
            }
        });
   }

    private void doOpen() {
//        int row = tblRuns.getSelectedRow();
//        if(row != -1) {
//            Run run = mdlRuns.getResult(row);
//            uiController.openExisting(Run.class, run.getId());
//        }
    }
}
