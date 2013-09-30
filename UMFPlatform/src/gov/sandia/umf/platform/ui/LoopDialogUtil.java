/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui;


public class LoopDialogUtil {
/*    public static void showLoopDialog(UIController uiController, DataModelException dme, PartXType type, String contextError) {
        DataModelLoopException dmle;
        if(dme instanceof DataModelLoopException) {
            dmle = (DataModelLoopException) dme;
        } else if(dme.getCause() instanceof DataModelLoopException) {
            dmle = (DataModelLoopException) dme.getCause();
        } else {
            throw new RuntimeException("Could not show loop dialog!");
        }
        String ctxError = contextError != null ? contextError : dme.getMessage();
        JLabel lblMessage = new JLabel(
            "<html>" + ctxError + "  A loop exists in the parent and/or include hierarchy of this " +
            type.toString().toLowerCase() +
            ".  This error must be fixed before these parts can be used for computational purposes.</html>"
        );
        lblMessage.setPreferredSize(GUIUtil.getHTMLJLabelPreferredSize(lblMessage, 350, true));
        String[] lines = new String[dmle.getParts().size()];
        for(int i = 0; i < lines.length; i++) {
            lines[i] = DataModelLoopException.getErrorLine(dmle, i);
        }
        JList lst = new JList(lines);
        JPanel pnl = Lay.BL(
            "N", Lay.hn(lblMessage, "eb=5"),
            "C", Lay.sp(lst, "augb=eb(5)")
        );
        JOptionPane.showMessageDialog(uiController.getParentRef(), pnl, "Error Updating Summary", JOptionPane.ERROR_MESSAGE);
    }*/
}
