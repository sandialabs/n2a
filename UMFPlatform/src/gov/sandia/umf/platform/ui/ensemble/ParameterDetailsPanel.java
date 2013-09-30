/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.ensemble;

import javax.swing.JLabel;
import javax.swing.JPanel;

import replete.util.DateUtil;
import replete.util.Lay;

public class ParameterDetailsPanel extends JPanel {
    private JLabel lblRunCount;
    private JLabel lblRunEstimatedTime;
    private String lblStyle = "fg=white";
    private String lblDVDescStyle = "prefw=85";
    private String lblDVStyle = "fg=B5ECFF,size=16";
    private JPanel pnlLeft;

    public ParameterDetailsPanel(String pnlStyle) {
        Lay.addGlobalHints("opaque=false");
        Lay.BLtg(this,
            "C", pnlLeft = Lay.BxL("Y",
                Lay.BL(
                    "W", Lay.lb("Default Value:", lblStyle, lblDVDescStyle),
                    "C", Lay.lb(" ", lblStyle, lblDVStyle)
                ),
                Lay.BL(
                    "W", Lay.lb("Description:", lblStyle, "prefw=85,valign=top"),
                    "C", Lay.lb(" ", lblStyle)
                )
            ),
            "E", Lay.BxL("Y",
                Lay.BL(
                    "W", Lay.lb("# Runs:", lblStyle, "prefw=80"),
                    "C", lblRunCount = Lay.lb("E", lblStyle)
                ),
                Lay.BL(
                    "W", Lay.lb("Est. Duration:", lblStyle, "prefw=80,valign=top"),
                    "C", lblRunEstimatedTime = Lay.lb("D", lblStyle)
                )
            ),
            pnlStyle
        );
        Lay.clearGlobalHints();
    }

    public void clearLeft() {
        pnlLeft.removeAll();
        pnlLeft.updateUI();
    }

    public void clearLeft(String clearedMessage) {
        pnlLeft.removeAll();
        pnlLeft.add(
            Lay.BL(
                "W", Lay.lb(clearedMessage, "fg=white"),
                "C", Lay.lb(" "),
                "opaque=false"
            )
        );
        pnlLeft.updateUI();
    }

    public void addLeft(String cap, Object value, boolean emphasis, boolean html, boolean alignTop) {
        if(html) {
            value = value == null ? null : "<html>" + value.toString() + "</html>";
        }
        value = value == null ? "<unspecified>" : value.toString();

        JLabel lbl;
        pnlLeft.add(
            Lay.BL(
                "W", lbl = Lay.lb(cap + ":", "fg=white,prefw=85"),
                "C", Lay.lb(value.toString(), "fg=white", emphasis ? lblDVStyle : ""),
                "opaque=false"
            )
        );
        if(alignTop) {
            lbl.setVerticalAlignment(JLabel.TOP);
        }
        pnlLeft.updateUI();
    }

    public void updateRun(long runCount, long estDurMillis) {
        if(runCount == 0) {
            runCount = 1;
        }
        lblRunCount.setText("" + runCount);
        lblRunEstimatedTime.setText(getElapsedEnsembleEstimate(runCount, estDurMillis));
    }

    public static String getElapsedEnsembleEstimate(long runCount, long estDurMillis) {
        if(estDurMillis == -1) {
            return "<unknown>";
        }
        return DateUtil.toElapsedString(runCount * estDurMillis);
    }
}
