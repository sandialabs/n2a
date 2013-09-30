/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.ensemble.tree;

import gov.sandia.umf.platform.ui.ensemble.images.ImageUtil;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;

import replete.gui.controls.ChangedDocumentListener;
import replete.gui.controls.IconButton;
import replete.gui.controls.SelectAllTextField;
import replete.gui.controls.simpletree.TNode;
import replete.util.Lay;

public class FilterableParameterTreePanel extends JPanel {

    ////////////
    // FIELDS //
    ////////////

    private JTextField txtFilter;
    private JButton btnClear;
    private ParameterTree treParams;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public FilterableParameterTreePanel(TNode nRoot) {
        treParams = new ParameterTree(nRoot);


        Lay.BLtg(this,
            "N", Lay.BL(
                "W", Lay.lb(ImageUtil.getImage("mag.gif"), "eb=6r"),
                "C", txtFilter = new SelectAllTextField(),
                "E", Lay.p(btnClear = new IconButton(ImageUtil.getImage("remove.gif")), "eb=5l"),
                "eb=5tlr"
             ),
             "C", Lay.p(Lay.sp(treParams), "eb=5")
        );

        txtFilter.getDocument().addDocumentListener(new ChangedDocumentListener() {
            @Override
            public void documentChanged(DocumentEvent e) {
                btnClear.setEnabled(!txtFilter.getText().equals(""));
                treParams.filter(txtFilter.getText());
            }
        });

        txtFilter.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_DOWN) {
                    treParams.requestFocusInWindow();
                    if(treParams.getSelectionCount() == 0) {
                        treParams.setSelectionRow(0);
                    }
                }
            }
        });

        treParams.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_UP && treParams.getSelectionCount() != 0 && treParams.getSelectionRows()[0] == 0) {
                    txtFilter.requestFocusInWindow();
                }
            }
        });

        btnClear.setEnabled(false);
        btnClear.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                txtFilter.setText("");
                btnClear.setEnabled(false);
            }
        });
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    // TODO: Change one day to more encapsulated view.
    public ParameterTree getTree() {
        return treParams;
    }
}
