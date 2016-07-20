/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.search;

import java.awt.Color;
import java.awt.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.border.Border;

import replete.gui.controls.EmptyMessageList;
import replete.util.Lay;

public class SearchResultList extends EmptyMessageList {


    ////////////
    // FIELDS //
    ////////////

    private ListCellRenderer renderer = new CustomRenderer();
    private SearchResultPanelGenerator panelGenerator;


    //////////////////
    // CONSTRUCTORS //
    //////////////////

    public SearchResultList() {
        init();
    }
    public SearchResultList(ListModel dataModel) {
        super(dataModel);
        init();
    }
    public SearchResultList(Object[] listData) {
        super(listData);
        init();
    }
    public SearchResultList(Vector<?> listData) {
        super(listData);
        init();
    }

    private void init() {
        setCellRenderer(renderer);
    }

    public void setGenerator(SearchResultPanelGenerator gen) {
        panelGenerator = gen;
    }


    /////////////////
    // INNER CLASS //
    /////////////////

    public class CustomRenderer implements ListCellRenderer {

        private Map<Object, JPanel> panelCache = new HashMap<Object, JPanel>();
        private Color lightGreen = new Color(255, 255, 255);
        private Color lightBlue = Lay.clr("C2EBFF");
        private Color lightYellow = Lay.clr("FFFDAA");

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JPanel pnl = panelCache.get(value);
            if(pnl == null) {
                pnl = panelGenerator.get(value, index);
                panelCache.put(value, pnl);
            }

            if(isSelected) {
                pnl.setBackground(lightYellow);
                pnl.setOpaque(true);
            } else {
                if(index % 2 == 1) {
                    pnl.setBackground(lightGreen);
                } else {
                    pnl.setBackground(lightBlue);
                }
                pnl.setOpaque(true);
            }

            Border empty = BorderFactory.createEmptyBorder(7, 7, 7, 7);
            if(cellHasFocus) {
                pnl.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.black),empty));
            } else {
                pnl.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(1,1,1,1), empty));
            }

            return pnl;
        }
    }
}
