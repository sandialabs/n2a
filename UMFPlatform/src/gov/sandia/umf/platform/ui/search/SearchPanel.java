/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.search;

import gov.sandia.umf.platform.AppState;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import replete.event.ChangeNotifier;
import replete.gui.controls.mnemonics.MButton;
import replete.gui.controls.nofire.NoFireComboBox;
import replete.gui.controls.nofire.NoFireComboBoxModel;
import replete.util.Lay;

public class SearchPanel extends JPanel implements DefaultButtonEnabledPanel
{
    // Core

    private UIController uiController;

    // UI

    private NoFireComboBox cboQuery;
    private NoFireComboBoxModel mdlQuery;
    private JButton btnSearch;
    private JList<MNode> lstResults;
    private DefaultListModel<MNode> mdlResults;


    ///////////////
    // NOTIFIERS //
    ///////////////

    protected ChangeNotifier selectRecordNotifier = new ChangeNotifier (this);

    public void addSelectRecordListener (ChangeListener listener)
    {
        selectRecordNotifier.addListener (listener);
    }

    protected void fireSelectRecordNotifier ()
    {
        selectRecordNotifier.fireStateChanged ();
    }

    protected ChangeNotifier selectionChangedNotifier = new ChangeNotifier (this);

    public void addSelectionChangedListener (ChangeListener listener)
    {
        selectionChangedNotifier.addListener (listener);
    }

    protected void fireSelectionChangedNotifier ()
    {
        selectionChangedNotifier.fireStateChanged ();
    }

    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public SearchPanel (UIController uic)
    {
        uiController = uic;
        mdlQuery = new NoFireComboBoxModel ();
        for (MNode query : AppState.getState ().childOrCreate ("Queries"))
        {
            mdlQuery.addElement (query.get ());
        }

        lstResults = new JList<MNode> (mdlResults = new DefaultListModel<MNode> ());
        lstResults.setCellRenderer (new MNodeRenderer ());

        Lay.BLtg (this,
            "N", Lay.BL (
                "C", cboQuery = new NoFireComboBox(mdlQuery),
                "E", btnSearch = new MButton("&Search", ImageUtil.getImage("mag.gif")),
                "eb=10,hgap=7,opaque=false"
            ),
            "C", Lay.sp (lstResults),
            "opaque=false"
        );
        cboQuery.setEditable(true);

        btnSearch.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                search ();
            }
        });

        KeyListener downToTableListener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if(mdlResults.size() != 0) {
                        lstResults.requestFocusInWindow();
                        if(lstResults.getSelectedIndex() == -1) {
                            lstResults.setSelectedIndex(0);
                        }
                    }
                }
            }
        };

        btnSearch.addKeyListener(downToTableListener);

        KeyListener upToQueryListener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_UP) {
                    if(lstResults.getSelectedIndex() == 0) {
                        cboQuery.requestFocusInWindow();
                    }
                }
            }
        };

        lstResults.addKeyListener(upToQueryListener);

        lstResults.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int index = lstResults.getSelectedIndex();
                if(e.getClickCount() > 1 && index != -1) {
                    fireSelectRecordNotifier();
                }
            }
        });

        lstResults.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if(lstResults.getSelectedIndex() != -1) {
                        fireSelectRecordNotifier();
                    }
                    e.consume();
                }
            }
        });

        lstResults.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent arg0) {
                fireSelectionChangedNotifier();
            }
        });
    }

    private void updateAppState ()
    {
        MNode queries = AppState.getState ().childOrCreate ("Queries");
        queries.clear ();
        for (int i = 0; i < mdlQuery.getSize (); i++)
        {
            queries.set ((String) mdlQuery.getElementAt (i), String.valueOf (i));
        }
    }

    public void search ()
    {
        String query = (String) cboQuery.getSelectedItem ();
        if (query == null) query = "";
        query = query.trim ();
        for (int i = mdlQuery.getSize () - 1; i >= 0; i--)
        {
            if (mdlQuery.getElementAt (i).equals (query)) mdlQuery.removeElementAtNoFire(i);
        }
        mdlQuery.insertElementAtNoFire (query, 0);
        updateAppState ();
        cboQuery.setSelectedItemNoFire (query);

        uiController.searchDb (query, new ChangeListener ()
        {
            public void stateChanged (ChangeEvent e)
            {
                mdlResults.clear ();
                List<MNode> results = (List<MNode>) e.getSource ();
                for (MNode record : results) mdlResults.addElement (record);
                cboQuery.getEditor().selectAll ();
                updateUI ();
            }
        });
    }

    @Override
    public JButton getDefaultButton ()
    {
        return btnSearch;
    }

    public void doFocus ()
    {
        cboQuery.requestFocusInWindow ();
    }

    public List<MNode> getSelectedRecords ()
    {
        List<MNode> result = new ArrayList<MNode> ();
        for (int index : lstResults.getSelectedIndices ())
        {
            result.add ((MNode) mdlResults.get (index));
        }
        return result;
    }

    public class MNodeRenderer extends JLabel implements ListCellRenderer<MNode>
    {
        public Component getListCellRendererComponent (JList<? extends MNode> list, MNode node, int index, boolean isSelected, boolean cellHasFocus)
        {
            String name = node.get ("$metadata", "name");
            if (name.isEmpty ()) name = node.get ();
            setText (name);
            return this;
        }
    }
}
