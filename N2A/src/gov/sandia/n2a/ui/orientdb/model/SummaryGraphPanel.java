/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.orientdb.model;

import gov.sandia.n2a.data.Bridge;
import gov.sandia.n2a.data.Layer;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import replete.util.GUIUtil;

public class SummaryGraphPanel extends JPanel {


    // //////////
    // FIELDS //
    // //////////

    // Const

    // private static final Color CT = new Color(158, 221, 255);
    private static final Color[] layerColors = {Color.red, Color.green, Color.blue, Color.orange, Color.cyan, Color.pink, Color.magenta};
    private static final int MT = 20;
    private static final int MB = 20;
    private static final int ML = 20;
    private static final int MR = 20;
    private static final int LH = 30;  // 60
    private static final int BW = 20;  // 40
    private static final int MIL = 40;
    private static final int MIR = 40;

    // Core

    // private DataModel dataModel;

    // State

    // private part;
    private Layer layerHilite = null;
    private Bridge bridgeHilite = null;

    private Map<Layer, LayerDesc> layerRects;
    private Map<Bridge, BridgeDesc> bridgeRects;

    private List<Layer> layers;
    private List<Bridge> bridges;

    private boolean error;


    // ///////////////
    // CONSTRUCTOR //
    // ///////////////

    public SummaryGraphPanel() {
        setBackground(Color.black);
        // dataModel = dmdl;
        // setBackground(Color.gray);
        // setBorder(BorderFactory.createMatteBorder(2, 2, 2, 2, Color.black));

        addMouseMotionListener(new MouseMotionListener() {
            public void mouseMoved(MouseEvent e) {
                layerHilite = null;
                bridgeHilite = null;
                Cursor cur = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);

                for(Layer layer : layerRects.keySet()) {
                    LayerDesc ld = layerRects.get(layer);
                    if(ld.rect.contains(e.getX(), e.getY())) {
                        layerHilite = layer;
                        break;
                    }
                }

                if(layerHilite == null) {
                    for(Bridge bridge : bridgeRects.keySet()) {
                        BridgeDesc bd = bridgeRects.get(bridge);
                        if(bd.rect.contains(e.getX(), e.getY())) {
                            bridgeHilite = bridge;
                            break;
                        }
                    }
                }

                if(layerHilite != null || bridgeHilite != null) {
                    cur = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
                }

                repaint();
                setCursor(cur);
            }

            public void mouseDragged(MouseEvent e) {
            }
        });
    }


    // //////////////
    // PAINT COMP //
    // //////////////

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        layerRects = new HashMap<Layer, LayerDesc>();
        bridgeRects = new HashMap<Bridge, BridgeDesc>();

        int pw = getWidth();
        int ph = getHeight();

        int topLayerMid = 0;
        int botLayerMid = 0;
        int topBotLayerSep = 0;
        int layerMidSep = 0;
        
        // hack?  not sure why setData isn't getting called before this
        if (layers == null) {
            return;
        }

        if(layers.size() > 1) {
            topLayerMid = MT + (int) (LH * 0.5);
            botLayerMid = ph - MB - (int) (LH * 0.5);
            topBotLayerSep = botLayerMid - topLayerMid;
            layerMidSep = topBotLayerSep / (layers.size() - 1);
        }

        for(int li = 0; li < layers.size(); li++) {
            Layer layer = layers.get(li);
            Color clrI = layerColors[li % layerColors.length].darker().darker();
            Color clrO = layerColors[li % layerColors.length];

            if(layer == layerHilite) {
                clrO = Color.yellow;
                clrI = clrO.darker().darker();
            }

            int layerY;

            if(layers.size() == 1) {
                layerY = ph / 2 - LH / 2;
            } else {
                layerY = MT + li * layerMidSep;
            }

            layerRects.put(layer,
                new LayerDesc(layer,
                    new Rectangle(ML, layerY, pw - ML - MR, LH),
                    clrI, clrO, layers.get(li).getName()));
        }

        int leftBridgeMid = 0;
        int rightBridgeMid = 0;
        int leftRightBridgeSep = 0;
        int bridgeMidSep = 0;

        if(bridges.size() > 1) {
            leftBridgeMid = ML + MIL + (int) (BW * 0.5);
            rightBridgeMid = pw - MR - MIR - (int) (BW * 0.5);
            leftRightBridgeSep = rightBridgeMid - leftBridgeMid;
            bridgeMidSep = leftRightBridgeSep / (bridges.size() - 1);
        }

        int bi = 0;
        for(Bridge bridge : bridges) {
            Map<String, Layer> layerMap = bridge.getAliasLayerMap();
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;

            boolean special;
            Rectangle bridgeRect;
            if(layerMap.size() > 1) {
                for(Layer layer : layerRects.keySet()) {
                    if(layerMap.values().contains(layer)) {
                        Rectangle layerRect = layerRects.get(layer).rect;
                        if(layerRect.y < min) {
                            min = layerRect.y;
//                            System.out.println("min=" + min);
                        }
                        if(layerRect.y + layerRect.height > max) {
                            max = layerRect.y + layerRect.height;
//                            System.out.println("max=" + max);
                        }
                    }
                }
                int bridgeX;
                if(bridges.size() == 1) {
                    bridgeX = pw / 2 - BW / 2;
                } else {
                    bridgeX = ML + MIL + bi * bridgeMidSep;
                }
                bridgeRect = new Rectangle(bridgeX, min, BW, max - min);
                special = false;
            } else {
                int bridgeX;
                if(bridges.size() == 1) {
                    bridgeX = pw / 2 - 3 * BW / 2;
                } else {
                    bridgeX = ML + MIL + bi * bridgeMidSep - BW;
                }
                LayerDesc ld = layerRects.get(layerMap.values().toArray()[0]);
                Rectangle layerRect = ld.rect;
                bridgeRect = new Rectangle(bridgeX, layerRect.y + layerRect.height, 3 * BW, BW * 2);
                special = true;
            }


            Color clrI = Color.white;
            Color clrO = Color.gray;
            Bridge b = bridges.get(bi);

            if(b == bridgeHilite) {
                clrO = Color.yellow;
                clrI = clrO.darker().darker();
            }

            bridgeRects.put(b, new BridgeDesc(bridge, bridgeRect, clrI, clrO, bridge.getName(),
                special));
            bi++;
        }

        for(Bridge bridge : bridgeRects.keySet()) {
            BridgeDesc bd = bridgeRects.get(bridge);
            Rectangle br = bd.rect;
            if(bd.special) {
                rect(g, br.x, br.y, BW, br.height, bd.clrI, bd.clrO);
                rect(g, br.x + br.width - BW, br.y, BW, br.height, bd.clrI, bd.clrO);
                rect(g, br.x, br.y + br.height - BW, br.width, BW, bd.clrI, bd.clrO);
            } else {
                rect(g, br.x, br.y, br.width, br.height, bd.clrI, bd.clrO);
            }
        }

        for(Layer layer : layerRects.keySet()) {
            LayerDesc ld = layerRects.get(layer);
            Rectangle lr = ld.rect;
            rect(g, lr.x, lr.y, lr.width, lr.height, ld.clrI, ld.clrO);
            g.setFont(new Font("Arial", Font.BOLD, 24));
            int d = 100;
            g.setColor(GUIUtil.deriveColor(ld.clrO, d, d, d));
            g.drawString(ld.layer.getName(), lr.x + 10,
                lr.y + lr.height / 2 + GUIUtil.stringHeight(g) / 2 - 5);
        }

        for(Bridge bridge : bridgeRects.keySet()) {
            BridgeDesc bd = bridgeRects.get(bridge);
            Rectangle br = bd.rect;
            Map<String, Layer> layerMap = bd.bridge.getAliasLayerMap();
            for(Layer layer : layerRects.keySet()) {
                if(layerMap.values().contains(layer)) {
                    LayerDesc ld = layerRects.get(layer);
                    Rectangle lr = ld.rect;
                    if(bd.special) {
                        gradRect(g, br.x + 1, lr.y + lr.height, BW - 1, BW, ld.clrI, bd.clrI);
                        gradRect(g, br.x + br.width - BW + 1, lr.y + lr.height, BW - 1, BW,
                            ld.clrI, bd.clrI);
                    } else {
                        if(br.y + br.height > lr.y + lr.height) {
                            gradRect(g, br.x + 1, lr.y + lr.height, br.width - 1, BW, ld.clrI,
                                bd.clrI);
                        }
                        if(br.y < lr.y) {
                            gradRect(g, br.x + 1, lr.y - BW + 1, br.width - 1, BW, bd.clrI, ld.clrI);
                        }

                        /*
                         * if((br.y + br.height / 2) > (lr.y + lr.height / 2)) { gradRect(g, br.x +
                         * 1, lr.y + lr.height, br.width - 1, BW, ld.clrI, bd.clrI); } else {
                         * gradRect(g, br.x + 1, lr.y - BW + 1, br.width - 1, BW, bd.clrI, ld.clrI);
                         * }
                         */
                    }
                }
            }
        }
    }

    private void rect(Graphics g, int x, int y, int w, int h, Color i, Color o) {
        g.setColor(i);
        g.fillRect(x, y, w, h);
        g.setColor(o);
        g.drawRect(x, y, w, h);
    }

    private void gradRect(Graphics g, int x, int y, int w, int h, Color i, Color o) {
        ((Graphics2D) g).setPaint(new GradientPaint(x, y + 1, i, x, y + h - 1, o));   // +10, -10
        g.fillRect(x, y, w, h);
    }


    // ////////////
    // MUTATORS //
    // ////////////

    public void setData(List<Layer> lay, List<Bridge> brid) {
        layers = lay;
        bridges = brid;
        repaint();
    }

    public void setStateClear() {
        error = false;
        repaint();
    }

    public void setStateError() {
        error = true;
        repaint();
    }

    public void rebuild() {
        repaint();
    }


    // /////////////////
    // INNER CLASSES //
    // /////////////////

    private class LayerDesc {
        public Layer layer;
        public Rectangle rect;
        public Color clrI;
        public Color clrO;
        public String text;

        public LayerDesc(Layer lay, Rectangle r, Color i, Color o, String t) {
            layer = lay;
            rect = r;
            clrI = i;
            clrO = o;
            text = t;
        }
    }

    private class BridgeDesc {
        public Bridge bridge;
        public Rectangle rect;
        public Color clrI;
        public Color clrO;
        public String text;
        public boolean special;

        public BridgeDesc(Bridge b, Rectangle r, Color i, Color o, String t, boolean spec) {
            bridge = b;
            rect = r;
            clrI = i;
            clrO = o;
            text = t;
            special = spec;
        }
    }
}
