/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.search;

import gov.sandia.umf.platform.AppState;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import replete.event.ChangeNotifier;
import replete.gui.controls.mnemonics.MButton;
import replete.gui.controls.nofire.NoFireComboBox;
import replete.gui.controls.nofire.NoFireComboBoxModel;
import replete.util.DateUtil;
import replete.util.GUIUtil;
import replete.util.Lay;

public class SearchPanel extends JPanel implements DefaultButtonEnabledPanel
{
    // Core

    private UIController uiController;
    private List<NDoc> allResults;
    private List<NDoc> shownResults;

    // UI

    private NoFireComboBox cboQuery;
    private NoFireComboBoxModel mdlQuery;
    private JButton btnSearch;
    private JList lstResults;
    private DefaultListModel mdlResults;


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
        Iterator<Entry<String,MNode>> queries = AppState.getState ().getNode ("Queries").iterator ();
        while (queries.hasNext ())
        {
            mdlQuery.addElement (queries.next ().getValue ().get ());
        }
        Lay.BLtg (this,
            "N", Lay.BL (
                "C", cboQuery = new NoFireComboBox(mdlQuery),
                "E", btnSearch = new MButton("&Search", ImageUtil.getImage("mag.gif")),
                "eb=10,hgap=7,opaque=false"
            ),
            "C", Lay.sp (lstResults = new JList(mdlResults = new DefaultListModel())),
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

    public String modified(Long millis) {
        if(millis == null) {
            return "<unknown>";
        }
        return DateUtil.toShortString(millis);
    }

    public String unkIf(String s) {
        return s == null || s.trim().equals("") ? "(no title)" : s;
    }
    public String unkIfWithHtml(String s) {  // For performance
        return s == null || s.trim().equals("") ? "<html><i>(no title)</i></html>" : s;
    }

    private void updateAppState ()
    {
        MNode queries = AppState.getState ().getNode ("Queries");
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
                allResults = (List<NDoc>) e.getSource ();
                shownResults = new ArrayList<NDoc> (allResults);

                // TODO: not sure it is necessary to delay this execution
                SwingUtilities.invokeLater (new Runnable ()
                {
                    public void run ()
                    {
                        cboQuery.getEditor().selectAll();
                        mdlResults.clear();
                        for (NDoc record : shownResults) mdlResults.addElement (record);
                        updateUI();
                    }
                });
            }
        });
    }

    @Override
    public JButton getDefaultButton() {
        return btnSearch;
    }

    public void doFocus() {
        cboQuery.requestFocusInWindow();
    }

    public List<NDoc> getSelectedRecords() {
        List<NDoc> records = new ArrayList<NDoc>();
        if(lstResults.getSelectedIndices().length != 0) {
            for(int i = 0; i < lstResults.getSelectedIndices().length; i++) {
                int selIndex = lstResults.getSelectedIndices()[i];
                NDoc record = (NDoc) mdlResults.get(selIndex);
                records.add(record);
            }
        }
        return records;
    }

    public List<NDoc> getSearchResults() {
        List<NDoc> results = new ArrayList<NDoc>();
        for(int i = 0; i < mdlResults.getSize(); i++) {
            results.add((NDoc) mdlResults.get(i));
        }
        return results;
    }


    ///////////
    // PAINT //
    ///////////

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setPaint(getPaint());  // Must be calculated every time b/c depends on dimensions.
        g2.fillRect(0, 0, getWidth(), getHeight());
    }

    public Paint getPaint() {
        Color bg = getBackground();
        Color darker0;
        Color darker1;
        int x = -30;
        int y = -20;
        int z = 40;
        darker0 = GUIUtil.deriveColor(GUIUtil.deriveColor(bg, x, (int)(1.5*x), x), z, z, z);

        darker1 = GUIUtil.deriveColor(darker0, y, y, y);
        Color[] c = new Color[] {darker0, bg, darker1, bg};
        float[] f = new float[] {0F, 0.2F, 0.75F, 1F};
        return new LinearGradientPaint(0, 0, getWidth(), getHeight(), f, c);
    }
}
