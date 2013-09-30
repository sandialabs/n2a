/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JPanel;

import replete.gui.windows.EscapeFrame;
import replete.util.Lay;

public class Test extends EscapeFrame {

    private CxnPanel pnlCxn;

    public Test() {
        JButton btnAdd = new JButton("Add");
        btnAdd.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                pnlCxn.add();
            }
        });
        Lay.BLtg(this,
            "C", Lay.sp(pnlCxn = new CxnPanel()),
            "S", Lay.FL("R", btnAdd),
            "size=[500,500],center"
        );
    }

    private class CxnPanel extends JPanel {
        private int x = 0;
        private Component glue;
        public CxnPanel() {
//            Lay.ALtg(this);
//            Lay.GLtg(this, 0, 1, "bg=white");
            Lay.BL("N", new R());
        }
        private void add() {
            R r = new R();
            if(glue != null) {
                remove(glue);
            }
            ((GridLayout) getLayout()).setRows(++x);
            add(r);
//            add(glue = Box.createVerticalGlue());
//            Lay.hn("dimh=" + (100 * x));
//            setPreferredSize(new Dimension(600, 100 * x));
//            setMaximumSize(new Dimension(600, 100 * x));
//            setMinimumSize(new Dimension(600, 100 * x));
            setBounds(0, 0, 600, x * 100);
            setSize(600, 100);
            updateUI();
        }
    }

    private class R extends JPanel {
        public R() {
            Lay.hn(this, "bg=red");
            setPreferredSize(new Dimension(100, 50));
            setMinimumSize(new Dimension(100, 50));
            setMaximumSize(new Dimension(100, 50));

            /*
            Lay.BL("C",
                Lay.GLtg(this, 3, 1,
                    Lay.BL(
                        "W", Lay.lb("Name:", "prefw=80"),
                        "C", Lay.tx(),
                        "opaque=false"
                    ),
                    Lay.BL(
                        "W", Lay.lb("Location:", "prefw=80"),
                        "C", Lay.tx(),
                        "opaque=false"
                    ),
                    Lay.GL(1, 2,
                        Lay.BL(
                            "W", Lay.lb("User:", "prefw=80"),
                            "C", Lay.tx(),
                            "opaque=false"
                        ),
                        Lay.BL(
                            "W", Lay.lb("Password:", "prefw=80"),
                            "C", Lay.tx(),
                            "opaque=false"
                        ),
                        "opaque=false"
                    ),
                    "eb=10, bg=yellow"
                ),
                "dim=[100,100], eb=10, bg=red"
            );*/
        }
    }

    public static void main(String[] args) {
        Test t = new Test();
        t.setVisible(true);
    }
}
