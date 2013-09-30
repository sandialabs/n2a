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
import gov.sandia.umf.platform.ui.general.NotesPanel;
import gov.sandia.umf.platform.ui.orientdb.general.TagTablePanel;
import gov.sandia.umf.platform.ui.orientdb.general.TermValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.util.Lay;

// TODO: There are so many copies of "NotesTagsDetailPanel" - need
// to get rid of when we have concept of "Common" record information.

public class NotesTagsDetailPanel extends RecordEditDetailPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private NotesPanel pnlNotes;
    private TagTablePanel pnlTags;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NotesTagsDetailPanel(UIController uic, NDoc p) {
        super(uic, p);

        Lay.GLtg(this, 2, 1,
            Lay.BL(
                "N", Lay.hn(createLabelPanel("Notes", "notes"), "pref=[10,25]"),
                "C", pnlNotes = new NotesPanel(uiController)
            ),
            Lay.BL(
                "N", Lay.hn(createLabelPanel("Tags", "notes"), "pref=[10,25]"),
                "C", pnlTags = new TagTablePanel(uiController, p)
            ),
            "eb=10"
        );

        pnlNotes.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                record.set("notes", pnlNotes.getNotes());
                fireContentChangedNotifier();
            }
        });
        pnlTags.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                List<TermValue> tags = pnlTags.getTags();
                record.getMetadata().clear();
                for(TermValue tag : tags) {
                    record.setMetadata(tag.getTerm(), tag.getValue());
                }
                fireContentChangedNotifier();
            }
        });
    }


    //////////
    // MISC //
    //////////

    @Override
    public void reload() {
        pnlNotes.setNotes((String) record.get("notes", ""));
        List<TermValue> tags = new ArrayList<TermValue>();
        Map<String, Object> metad = record.getMetadata();
        for(String key : metad.keySet()) {
            Object value = metad.get(key);
            if(value instanceof String) {
                tags.add(new TermValue(key, (String) value));
            }
        }
        pnlTags.setTags(tags);
    }
}
