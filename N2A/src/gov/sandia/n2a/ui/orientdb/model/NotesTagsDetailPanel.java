/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.model;

import gov.sandia.n2a.data.ModelOrient;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.general.NotesPanel;
import gov.sandia.umf.platform.ui.orientdb.general.TagTablePanel;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.util.Lay;

public class NotesTagsDetailPanel extends ModelEditDetailPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private NotesPanel pnlNotes;
    private TagTablePanel pnlTags;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NotesTagsDetailPanel(UIController uic, ModelOrient m) {
        super(uic, m);

        Lay.GLtg(this, 2, 1,
            Lay.BL(
                "N", Lay.hn(createLabelPanel("Notes", "notes"), "pref=[10,25]"),
                "C", pnlNotes = new NotesPanel(uiController)
            ),
            Lay.BL(
                "N", Lay.hn(createLabelPanel("Tags", "notes"), "pref=[10,25]"),
                "C", pnlTags = new TagTablePanel(uiController, m.getSource())
            ),
            "eb=10"
        );

        pnlNotes.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                model.setNotes(pnlNotes.getNotes());
                fireContentChangedNotifier();
            }
        });
        pnlTags.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                model.setTerms(pnlTags.getTags());
                fireContentChangedNotifier();
            }
        });
    }

    @Override
    public void reload() {
        pnlNotes.setNotes(model.getNotes());
        pnlTags.setTags(model.getTerms());
    }
}
