/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.search;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
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

    // Global

    public static List<NDoc> prevResults;

    // Core

    private UIController uiController;

    // UI

    private SearchPanel pnlSearch;

    // Misc

    private int result = CANCEL;
    private List<NDoc> searchResults = null;


    //////////////////
    // CONSTRUCTORS //
    //////////////////

    public SearchDialogOrient(JFrame parent, String title, UIController uic, SearchType searchType, Icon selectIcon, int sel, List<NDoc> given) {
        super(parent, "Search: " + title, true);
        init(uic, searchType, selectIcon, sel, given);
    }
    public SearchDialogOrient(JDialog parent, String title, UIController uic, SearchType searchType, Icon selectIcon, int sel, List<NDoc> given) {
        super(parent, "Search: " + title, true);
        init(uic, searchType, selectIcon, sel, given);
    }

    private void init(UIController uic, SearchType searchType, Icon selectIcon, int sel, List<NDoc> given) {
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
            public void stateChanged(ChangeEvent arg0) {
                List<NDoc> selectedRecords = pnlSearch.getSelectedRecords();
                btnSelect.setEnabled(selectedRecords.size() != 0);
            }
        });
    }


    ////////////
    // ACTION //
    ////////////

    private void doSelect() {
        List<NDoc> records = pnlSearch.getSelectedRecords();

        for(NDoc record : records) {
/*            if(record.exists()) {
                record.revert(); todo
                String validMsg = (validator == null ? null : validator.validate(record));
                if(validMsg != null) {
                    Dialogs.showWarning(validMsg, "Invalid Selection");
                    return;
                }
            } else {
                uiController.couldNotFind();
                return;
            }*/
        }

        result = SEARCH;
        searchResults = records;
        prevResults = pnlSearch.getSearchResults();
        closeWindow();
    }


    ///////////////
    // ACCESSORS //
    ///////////////

    public int getResult() {
        return result;
    }

    public List<NDoc> getSelectedRecords() {
        return searchResults;
    }
}
