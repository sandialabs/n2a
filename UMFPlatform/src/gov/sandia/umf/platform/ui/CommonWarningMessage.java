/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui;

import replete.gui.windows.Dialogs;

public class CommonWarningMessage {
    public static void showInvalidVariable(String ent) {
        Dialogs.showWarning(ent + " does not match valid pattern.  A valid pattern is\n    LETTER(LETTER|DIGIT)*\nwhere\n    LETTER=[A-Za-z_]\nand\n    DIGIT=[0-9]\nExamples:\n    NaIC0\n    abc123xyz\n    Z0_X1\n    _1");
    }
}
