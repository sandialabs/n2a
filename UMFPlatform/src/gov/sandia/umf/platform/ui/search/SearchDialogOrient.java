/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.search;

import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.gui.controls.mnemonics.MButton;
import replete.gui.windows.EscapeDialog;
import replete.util.Lay;

public class SearchDialogOrient extends EscapeDialog {


    ////////////
    // FIELDS //
    ////////////

    // Const

    public static final int CANCEL = 0;
    public static final int SEARCH = 1;

    // Core

    private UIController uiController;

    // UI

    private SearchPanel pnlSearch;

    // Misc

    private int result = CANCEL;
    private List<MNode> searchResults = null;


    //////////////////
    // CONSTRUCTORS //
    //////////////////

    public SearchDialogOrient (JFrame parent, String title, UIController uic, SearchType searchType, Icon selectIcon, int sel)
    {
        super (parent, "Search: " + title, true);
        init (uic, searchType, selectIcon, sel);
    }

    public SearchDialogOrient (JDialog parent, String title, UIController uic, SearchType searchType, Icon selectIcon, int sel)
    {
        super (parent, "Search: " + title, true);
        init (uic, searchType, selectIcon, sel);
    }

    private void init(UIController uic, SearchType searchType, Icon selectIcon, int sel)
    {
        uiController = uic;

        setIconImage(ImageUtil.getImage("mag.gif").getImage());

        pnlSearch = new SearchPanel(uiController);  // PREV RESULTS
        pnlSearch.addSelectRecordListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                doSelect();
            }
        });

        Icon si = (selectIcon == null ? ImageUtil.getImage("parent.gif") : selectIcon);
        final JButton btnSelect = new MButton("Selec&t", si);
        btnSelect.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doSelect();
            }
        });
        btnSelect.setEnabled(false);

        JButton btnCancel = new MButton("&Cancel", ImageUtil.getImage("cancel.gif"));
        btnCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                closeWindow();
            }
        });

        getRootPane().setDefaultButton(pnlSearch.getDefaultButton());

        Lay.BLtg(this,
            "C", pnlSearch,
            "S", Lay.FL("R", btnSelect, btnCancel),
            "size=[700,350],center"
        );

        pnlSearch.addSelectionChangedListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent arg0)
            {
                btnSelect.setEnabled (pnlSearch.getSelectedRecords ().size() != 0);
            }
        });
    }


    ////////////
    // ACTION //
    ////////////

    private void doSelect ()
    {
        result = SEARCH;
        searchResults = pnlSearch.getSelectedRecords ();
        closeWindow();
    }


    ///////////////
    // ACCESSORS //
    ///////////////

    public int getResult ()
    {
        return result;
    }

    public List<MNode> getSelectedRecords ()
    {
        return searchResults;
    }
}
