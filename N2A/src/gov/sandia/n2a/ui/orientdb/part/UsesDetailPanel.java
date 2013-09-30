/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.part;

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

    private UsesTablePanel pnlUsesParent;
    private UsesTablePanel pnlUsesInclude;
    private UsesTablePanel pnlUsesConnect;
    private UsesTablePanel pnlUsesModels;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public UsesDetailPanel(UIController uic, NDoc p) {
        super(uic, p);

        final JRadioButton optParent = new MRadioButton("Parent Of");
        final JRadioButton optInclude = new MRadioButton("Included By");
        final JRadioButton optConnect = new MRadioButton("Connected By");
        final JRadioButton optModel = new MRadioButton("In Models");

        final CardLayout cl = new CardLayout();
        final JPanel pnlStack = new JPanel(cl);
        pnlStack.add(pnlUsesParent = new UsesTablePanel(uiController, UsesTablePanel.Type.PARENT), optParent.getText());
        pnlStack.add(pnlUsesInclude = new UsesTablePanel(uiController, UsesTablePanel.Type.PART_ASSOC), optInclude.getText());
        pnlStack.add(pnlUsesConnect = new UsesTablePanel(uiController, UsesTablePanel.Type.PART_ASSOC), optConnect.getText());
        pnlStack.add(pnlUsesModels = new UsesTablePanel(uiController, UsesTablePanel.Type.MODEL), optModel.getText());

        Lay.BLtg(this,
            "N", Lay.FL("L",
                createLabelPanel("Uses", "uses"),
                optParent, optInclude, optConnect, optModel,
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
        optConnect.addItemListener(itemL);
        optModel.addItemListener(itemL);

        Lay.grp(optParent, optInclude, optConnect, optModel);
    }

    @Override
    public void reload() {
        /*
      pnlUsesParent.setChildren(part.getChildren());
      pnlUsesInclude.setPartAssociations(part.getUsesPA(PartAssociationType.INCLUDE));
      pnlUsesConnect.setPartAssociations(part.getUsesPA(PartAssociationType.CONNECT));
      Map<Integer, Model> models = new HashMap<Integer, Model>();
      for(Layer layer : part.getDerivedPartLayer()) {
          models.put(layer.getModelId(), layer.getModel());
      }
      for(Bridge bridge : part.getUsedInBridges()) {
          models.put(bridge.getModelId(), bridge.getModel());
      }
      pnlUsesModels.setModels(new ArrayList<Model>(models.values()));
      */ //TODO
    }
}
