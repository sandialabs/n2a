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
import java.awt.Font;
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
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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

public class SearchPanel extends JPanel implements DefaultButtonEnabledPanel {


    ////////////
    // FIELDS //
    ////////////

    // Const

    private static final String CAT_TYPE = "Type";
    private static final String CAT_OWNER = "Owner";
    private static final String VAL_NO_VALUE = "(no value)";

    // Core

    private UIController uiController;
    private List<NDoc> allResults;
    private List<NDoc> shownResults;
    private FacetedMap facetedMap;

    // UI

    private JLabel lblResultsCount;
    private JPanel pnlPreImage;
    private JPanel pnlResults;
    private JPanel pnlFaceted;
    private NoFireComboBox cboQuery;
    private NoFireComboBoxModel mdlQuery;
    private JButton btnSearch;
    private SearchResultList lstResults;
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
        JScrollPane sp1;
        mdlQuery = new NoFireComboBoxModel ();
        Iterator<Entry<String,MNode>> queries = AppState.getState ().getNode ("Queries").iterator ();
        while (queries.hasNext ())
        {
            mdlQuery.addElement (queries.next ().getValue ().get ());
        }
        Lay.BLtg(this,
            "N", Lay.BL(
                "C", cboQuery = new NoFireComboBox(mdlQuery),
                "E", btnSearch = new MButton("&Search", ImageUtil.getImage("mag.gif")),
                "eb=10,hgap=7,opaque=false"
            ),
            "C", pnlResults = Lay.BL(
                "N", Lay.FL("R", lblResultsCount = Lay.lb(ImageUtil.getImage("pound.gif")), "opaque=false"),
                "C", Lay.SPL(
                    sp1 = Lay.sp(pnlFaceted = Lay.p("gradient", "gradclr1=white,gradclr2=200"),"eb=0"),
                    Lay.sp(lstResults = new SearchResultList(mdlResults = new DefaultListModel())),
                    "divpixel=170,hgap=5,mb=[1t,black]"
                ),
                "opaque=false"
            ),
            "vgap=0"
        );
        cboQuery.setEditable(true);
        lblResultsCount.setFont(new Font("Arial", Font.BOLD, 14));
        sp1.getVerticalScrollBar().setUnitIncrement(16);

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

        lstResults.setGenerator(new SearchResultPanelGenerator() {
            public JPanel get(Object value, int index) {
                NDoc nDoc = (NDoc) value;
                JLabel lblD;

                // TODO: Is this even possible to have this shown with the code in OrientDatasource?
                if(nDoc.getHandler() == null) {
                    JPanel pnl = Lay.BL(
                        "W", Lay.lb(ImageUtil.getImage("help.gif")),
                        "C", Lay.GL(2, 1,
                            Lay.BL(
                                "W", Lay.lb("<html><u>Unknown Record Type:</u></html>"),
                                "C", Lay.lb(nDoc.getClassName()),
                                "prefw=200,opaque=false,hgap=4"
                            ),
                            lblD = Lay.lb("Please install appropriate plug-in.", "prefw=200"),
                            "opaque=false, eb=10l"
                        )
                    );
                    lblD.setFont(lblD.getFont().deriveFont(Font.ITALIC));
                    return pnl;
                }

                SearchResultDetails details = nDoc.getHandler().getSearchResultListDetails(nDoc);
                ImageIcon icon = details.getIcon() == null ? ImageUtil.getImage("unkrecicon.gif") : details.getIcon();
                String descrip = details.getDescription() == null || details.getDescription().trim().equals("") ? "(no description)" : details.getDescription();
                JPanel pnl = Lay.BL(
                    "W", Lay.lb(icon),
                    "C", Lay.GL(2, 1,
                        Lay.BL(
                            "W", Lay.lb("<html><u>"+nDoc.getHandler().getRecordTypeDisplayName(nDoc) + ":</u></html>"),
                            "C", Lay.lb(unkIfWithHtml(details.getTitle())),
                            "prefw=200,opaque=false,hgap=4"
                        ),
                        lblD = Lay.lb(descrip, "prefw=200"),
                        "opaque=false, eb=10l"
                    ),
                    "E", Lay.GL(2, 1,
                        Lay.lb("<html>Owner: " + unkIf(details.getOwner()) + "</html>"),
                        Lay.lb("Modified: " + modified(details.getLastModified())),
                        "opaque=false,dim=[200,10],eb=10l"
                    )
                );
                lblD.setFont(lblD.getFont().deriveFont(Font.ITALIC));
                return pnl;
            }
        });

        btnSearch.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                search();
            }
        });
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
        String query = (String) cboQuery.getSelectedItem();
        if(query == null) {
            query = "";
        }
        query = query.trim();
        for(int i = mdlQuery.getSize() - 1; i >= 0; i--) {
            if(mdlQuery.getElementAt(i).equals(query)) {
                mdlQuery.removeElementAtNoFire(i);
            }
        }
        mdlQuery.insertElementAtNoFire(query, 0);
        updateAppState();
        cboQuery.setSelectedItemNoFire(query);
        uiController.searchDb(query, new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                allResults = (List<NDoc>) e.getSource();
                shownResults = new ArrayList<NDoc>(allResults);
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        // Create faceted map from original complete query results.
                        facetedMap = new FacetedMap();
                        for(NDoc doc : allResults) {
                            String owner = doc.get("$owner");
                            facetedMap.add(CAT_OWNER, owner, doc);
                            facetedMap.add(CAT_TYPE, doc.getHandler().getRecordTypeDisplayName(doc), doc);
                            Map<String, Object> meta = doc.getMetadata();
                            for(String key : meta.keySet()) {
                                facetedMap.add(key, meta.get(key).toString(), doc);
                            }
                        }
                        facetedMap.updateNoValues(allResults);

                        // Rebuild the faceted check box panel.
                        BoxLayout l1 = new BoxLayout(pnlFaceted, BoxLayout.Y_AXIS);
                        pnlFaceted.setLayout(l1);
                        pnlFaceted.removeAll();
                        boolean first = true;
                        for(String category : facetedMap.keySet()) {
                            Map<String, ItemGlob> catMap = facetedMap.get(category);

                            JPanel pnlCat = Lay.p(first ? "" : "eb=10t");
                            pnlCat.setOpaque(false);
                            pnlCat.setAlignmentX(-1);
                            BoxLayout l = new BoxLayout(pnlCat, BoxLayout.Y_AXIS);
                            pnlCat.setLayout(l);
                            JLabel lbl = Lay.lb("<html><i>"+category+"</i><html>", "bg=FFFDAA,opaque=true,eb=2,augb=mb(1,#D2B3DA)");
                            pnlCat.add(lbl);
                            lbl.setAlignmentX(0);
                            for(String item : catMap.keySet()) {
                                ItemGlob glob = catMap.get(item);
                                JCheckBox chk;
                                if(item.equals(VAL_NO_VALUE)) {
                                    chk = new JCheckBox("<html><i>" + item + " (" + glob.records.size() + ")</i></html>");
                                } else {
                                    chk = new JCheckBox(item + " (" + glob.records.size() + ")");
                                }
                                chk.setSelected(true);
                                chk.setOpaque(false);
                                chk.addActionListener(new ActionListener() {
                                    public void actionPerformed(ActionEvent e) {
                                        changeResultsShown();
                                    }
                                });
                                pnlCat.add(chk);
                                chk.setAlignmentX(-1);
                                facetedMap.setCheckBox(category, item, chk);
                            }
                            pnlFaceted.add(pnlCat);
                            first = false;
                        }
                        cboQuery.getEditor().selectAll();
                        updateResultsShown();
                        updateUI();
                    }
                });
            }
        });
    }

    private void changeResultsShown() {
        shownResults.clear();
        shownResults.addAll(allResults);
        for(String cat : facetedMap.keySet()) {
            for(String item : facetedMap.get(cat).keySet()) {
                ItemGlob glob = facetedMap.get(cat).get(item);
                List<NDoc> records = glob.records;
                JCheckBox chk = glob.checkBox;
                if(!chk.isSelected()) {
                    shownResults.removeAll(records);
                }
            }
        }
        updateResultsShown();
    }

    private void updateResultsShown() {
        mdlResults.clear();
        for(NDoc record : shownResults) {
            mdlResults.addElement(record);
        }
        lblResultsCount.setText("<html>Records Displayed: <font color='#005511'>" + shownResults.size() + "</font></html>");
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


    ///////////////////
    // INNER CLASSES //
    ///////////////////

    private class FacetedMap extends TreeMap<String, Map<String, ItemGlob>> {
        public FacetedMap() {
            super(new Comparator<String>() {
                public int compare(String o1, String o2) {
                    if(o1.equals(CAT_TYPE) && o2.equals(CAT_TYPE)) {
                        return 0;
                    } else if(o1.equals(CAT_TYPE)) {
                        return -1;
                    } else if(o2.equals(CAT_TYPE)) {
                        return 1;
                    }
                    if(o1.equals(CAT_OWNER) && o2.equals(CAT_OWNER)) {
                        return 0;
                    } else if(o1.equals(CAT_OWNER)) {
                        return -1;
                    } else if(o2.equals(CAT_OWNER)) {
                        return 1;
                    }
                    return o1.compareTo(o2);
                }
            });
        }
        public void updateNoValues(List<NDoc> docs) {
            for(NDoc doc : docs) {
                Set<String> keys = doc.getMetadata().keySet();
                for(String cat : keySet()) {
                    if(cat.equals(CAT_OWNER) || cat.equals(CAT_TYPE)) {
                        continue;
                    }
                    if(!keys.contains(cat) || doc.getMetadata(cat) == null) {
                        get(cat).get(VAL_NO_VALUE).records.add(doc);
                    }
                }
            }
        }
        public void add(String category, String item, NDoc doc) {
            if(item == null) {
                item = VAL_NO_VALUE;
            }
            Map<String, ItemGlob> catMap = get(category);
            if(catMap == null) {
                catMap = new TreeMap<String, ItemGlob>();
                put(category, catMap);
                if(!category.equals(CAT_OWNER) && !category.equals(CAT_TYPE)) {
                    catMap.put(VAL_NO_VALUE, new ItemGlob());
                }
            }
            ItemGlob glob = catMap.get(item);
            if(glob == null) {
                glob = new ItemGlob();
                catMap.put(item, glob);
            }
            glob.records.add(doc);
        }
        public void setCheckBox(String category, String item, JCheckBox chk) {
            Map<String, ItemGlob> catMap = get(category);
            ItemGlob glob = catMap.get(item);
            glob.checkBox = chk;
        }
    }

    private class ItemGlob {
        public List<NDoc> records = new ArrayList<NDoc>();
        public JCheckBox checkBox;
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
