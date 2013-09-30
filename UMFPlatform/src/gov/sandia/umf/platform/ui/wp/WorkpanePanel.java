/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.wp;

import javax.swing.JPanel;

public class WorkpanePanel extends JPanel {
/*

    ////////////
    // FIELDS //
    ////////////

    // Core

    private UIController uiController;
    private WorkpaneModel workpaneModel;

    // UI

    private IconList lstMW;
    private IconList lstRV;
    private DefaultListModel mdlMW;
    private DefaultListModel mdlRV;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public WorkpanePanel(UIController uic, WorkpaneModel wpm) {
        uiController = uic;
        workpaneModel = wpm;

        workpaneModel.addDataModelChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                GUIUtil.safe(new Runnable() {
                    public void run() {
                        rebuildListModels();
                        updateUI();
                    }
                });
            }
        });

        uiController.getDMM().addConnectListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                GUIUtil.safe(new Runnable() {
                    public void run() {
                        if(uiController.getDMM().isConnected()) {
                            rebuildListModels();
                        } else {
                            mdlMW.clear();
                            mdlRV.clear();
                        }
                    }
                });
            }
        });

        mdlMW = new DefaultListModel();
        mdlRV = new DefaultListModel();

        IconButton btnClose = new IconButton(ImageUtil.getImage("closewp2.gif"), "Hide Workpane");
        btnClose.toImageOnly();
        JLabel lblMW, lblRV;

        IconButton btnMyWorkHelp = new IconButton(ImageUtil.getImage("mywork.gif"), (String) null);
        btnMyWorkHelp.toImageOnly();
        btnMyWorkHelp.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.showHelp(MainFrame.getInstance(), "mywork");
            }
        });

        JPanel pnlMyWork = Lay.BL(
            "W", Lay.BL(
                "C", lblMW = Lay.lb("My Work"),
                "E", Lay.p(btnMyWorkHelp, "eb=4l,opaque=false"),
                "opaque=false"
            ),
            "C", Lay.p("opaque=false"),
            "E", btnClose, "eb=5,bg=[227,233,233]"
        );

        IconButton btnRecentHelp = new IconButton(ImageUtil.getImage("recent.gif"), (String) null);
        btnRecentHelp.toImageOnly();
        btnRecentHelp.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.showHelp(MainFrame.getInstance(), "recent");
            }
        });

        JPanel pnlRecent = Lay.BL(
            "W", Lay.BL(
                "C", lblRV = Lay.lb("Recently Viewed"),
                "E", Lay.p(btnRecentHelp, "eb=4l,opaque=false"),
                "opaque=false"
            ),
            "C", Lay.p("opaque=false"),
            "eb=5,bg=[227,233,233]"
        );

        Lay.GLtg(this, 2, 1,
            Lay.BL(
                "N", pnlMyWork,
                "C", Lay.sp(lstMW = new IconList(mdlMW))
            ),
            Lay.BL(
                "N", pnlRecent,
                "C", Lay.sp(lstRV = new IconList(mdlRV))
            ),
            "pref=[200,100],min=[0,0]"
        );

        btnClose.setRolloverIcon(ImageUtil.getImage("closewp.gif"));
        btnClose.setPressedIcon(ImageUtil.getImage("closewpp.gif"));
        btnClose.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.hideWorkpane();
            }
        });

        lstMW.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_DELETE) {
                    int[] idxs = lstMW.getSelectedIndices();
                    if(idxs.length != 0) {
                        int lastIdx = idxs[idxs.length - 1];
                        uiController.removeMyWork(idxs);
                        if(lastIdx >= mdlMW.getSize()) {
                            lastIdx = mdlMW.getSize() - 1;
                        }
                        lstMW.setSelectedIndex(lastIdx);
                    }
                } else if(e.getKeyCode() == KeyEvent.VK_ENTER) {
                    open((JList) e.getSource());
                }
            }
        });
        lstRV.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_DELETE) {
                    int[] idxs = lstRV.getSelectedIndices();
                    if(idxs.length != 0) {
                        int lastIdx = idxs[idxs.length - 1];
                        uiController.removeRecent(idxs);
                        if(lastIdx >= mdlRV.getSize()) {
                            lastIdx = mdlRV.getSize() - 1;
                        }
                        lstRV.setSelectedIndex(lastIdx);
                    }
                } else if(e.getKeyCode() == KeyEvent.VK_ENTER) {
                    open((JList) e.getSource());
                }
            }
        });
        lstMW.addMouseListener(openListener);
        lstRV.addMouseListener(openListener);

        lblMW.setFont(lblMW.getFont().deriveFont(Font.BOLD));
        lblRV.setFont(lblRV.getFont().deriveFont(Font.BOLD));
    }

    protected void rebuildListModels() {
        rebuildListModels(mdlMW, workpaneModel.getMyWork());
        rebuildListModels(mdlRV, workpaneModel.getRecent());
    }

    // TODO: Can make use of a custom ListCellRenderer somehow still?
    protected void rebuildListModels(DefaultListModel mdl, List<WorkpaneRecord> wpRecords) {
        mdl.clear();
        for(WorkpaneRecord record : wpRecords) {
            BeanBase bean = record.getBean();
            if(bean == null) {
                ImageIcon icon;
                try {
                    icon = ImageUtil.getImage(record.getIconName());
                } catch(Exception e) {
                    icon = ImageUtil.getImage("help.gif");
                }
                String beanString = record.getLabel();
                if(record.isError()) {
                    mdl.addElement(new IconWrapper(bean, "<html><font color='#777777'>" + beanString + " <i><b>(error)</b></i></font></html>", icon));
                } else {
                    mdl.addElement(new IconWrapper(bean, "<html><font color='#777777'>" + beanString + " <i><b>(unknown)</b></i></font></html>", icon));
                }
            } else {
                ImageIcon icon = BeanToIcon.get(bean);
                String beanString = bean.getBeanTitle();
                if(record.isError()) {
                    mdl.addElement(new IconWrapper(bean, bean.isPersisted() ?
                        "<html><font color='#777777'>" + beanString + " <i><b>(error)</b></i></font></html>" : "NEW", icon));
                } else {
                    mdl.addElement(new IconWrapper(bean, bean.isPersisted() ?
                        "<html>" + beanString + "</html>" : "NEW", icon));
                }
            }
        }
    }

    MouseListener openListener = new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
            JList lst = (JList) e.getSource();
            if(e.getClickCount() == 2) {
                open(lst);
            }
        }
    };

    private void open(JList lst) {
        if(lst.getSelectedIndices().length == 1) {
            IconWrapper iw = (IconWrapper) lst.getSelectedValue();
            BeanBase result = iw.bean;
            if(result != null) {
                uiController.openExisting(result.getClass(), result.getId());
            } else {
                Dialogs.showWarning("Cannot open this record.  It either no longer exists or there was an error loading its state.");
            }
        }
    }

    public class IconWrapper implements Iconable {
        private String text;
        private BeanBase bean;
        private Icon icon;
        public IconWrapper(BeanBase b, String txt, Icon i) {
            bean = b;
            text = txt;
            icon = i;
        }
        @Override
        public String toString() {
            return text;
        }

        @Override
        public Icon getIcon() {
            return icon;
        }
    }*/
}
