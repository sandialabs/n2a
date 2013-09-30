/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.ref;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditDetailPanel;
import gov.sandia.umf.platform.ui.UIController;

import java.awt.CardLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JPanel;
import javax.swing.JRadioButton;

import replete.gui.controls.mnemonics.MRadioButton;
import replete.util.Lay;

public class UsesDetailPanel extends RecordEditDetailPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private UsesTablePanel pnlUsesCited;  // TODO: A little weird to be using this UsesTablePanel class the way it is designed...
//    private UsesTablePanel pnlUsesInclude;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public UsesDetailPanel(UIController uic, NDoc r) {
        super(uic, r);

        final JRadioButton optParent = new MRadioButton("Cited By");
        final JRadioButton optInclude = new MRadioButton("???Other");

        final CardLayout cl = new CardLayout();
        final JPanel pnlStack = new JPanel(cl);
        pnlStack.add(pnlUsesCited = new UsesTablePanel(uiController, UsesTablePanel.Type.PARENT), optParent.getText());
//        pnlStack.add(pnlUsesInclude = new UsesTablePanel(dataModel, uiController, UsesTablePanel.Type.PART_ASSOC), optInclude.getText());
//        pnlStack.add(pnlUsesConnect = new UsesTablePanel(dataModel, uiController, UsesTablePanel.Type.PART_ASSOC), optConnect.getText());
//        pnlStack.add(pnlUsesModels = new UsesTablePanel(dataModel, uiController, UsesTablePanel.Type.MODEL), optModel.getText());

        Lay.BLtg(this,
            "N", Lay.FL("L",
                createLabelPanel("Uses", "uses"),
                optParent, optInclude,
                "pref=[10,25],hgap=0,vgap=0"
            ),
            "C", pnlStack,
            "eb=10"
        );

        optParent.setSelected(true);

        ItemListener itemL = new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                cl.show(pnlStack, ((JRadioButton) e.getSource()).getText());
            }
        };
        optParent.addItemListener(itemL);
        optInclude.addItemListener(itemL);

        Lay.grp(optParent, optInclude);
    }

    @Override
    public void reload() {
/*        List<EquationReferenceX> eqRefs = record.getEqs();
        List<PartX> citedBy = new ArrayList<PartX>();
        Set<Integer> partIds = new HashSet<Integer>();
        for(EquationReferenceX eqRef : eqRefs) {
            EquationX eq = eqRef.getEq();
            PartX partX = eq.getPart().get(0);
            if(!partIds.contains(partX.getId())) {
                citedBy.add(partX);
                partIds.add(partX.getId());
            }
        }
        pnlUsesCited.setChildren(citedBy);*/
    }
}
