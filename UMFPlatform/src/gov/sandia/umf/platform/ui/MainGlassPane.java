/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui;

import gov.sandia.umf.platform.ui.general.HelpNotesPanel;

import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

//http://weblogs.java.net/blog/alexfromsun/archive/2006/09/a_wellbehaved_g.html

public class MainGlassPane extends JPanel {


    ///////////
    // FIELD //
    ///////////

    private HelpNotesPanel pnlHelpNotes;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public MainGlassPane(UIController uiController) {
        pnlHelpNotes = new HelpNotesPanel(uiController);
        pnlHelpNotes.addHideHelpListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                hideHelp();
            }
        });
        setLayout(null);
        setOpaque(false);
        add(pnlHelpNotes);
    }


    ////////////////////
    // SHOW/HIDE HELP //
    ////////////////////

    public void hideHelp() {
        setVisible(false);
    }
    public void showHelp(String topic, String content) {
        setVisible(true);
        getHelpNotesPanel().setHelpNotes(topic, content);
    }
    public boolean isHelpShowing() {
        return isVisible();
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public HelpNotesPanel getHelpNotesPanel() {
        return pnlHelpNotes;
    }


    ////////////////////////
    // MOUSE EVENT FIXING //
    ////////////////////////

    // This allows both the cursors from below and the cursors
    // for the help notes panel to be shown properly.

    @Override
    public boolean contains(int x, int y) {
        if(isVisible()) {
            if(x >= pnlHelpNotes.getX() && x <= pnlHelpNotes.getX() + pnlHelpNotes.getWidth() &&
                    y >= pnlHelpNotes.getY() && y <= pnlHelpNotes.getY() + pnlHelpNotes.getHeight() ) {
                return true;
            }
            return false;
        }
        return super.contains(x, y);
    }
}
