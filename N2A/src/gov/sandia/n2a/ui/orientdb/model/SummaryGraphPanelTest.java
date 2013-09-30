/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.model;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import replete.gui.windows.EscapeFrame;
import replete.util.GUIUtil;

public class SummaryGraphPanelTest extends EscapeFrame {

    List<Layer> layers = new ArrayList<Layer>();
    List<Bridge> bridges = new ArrayList<Bridge>();

    public SummaryGraphPanelTest() {
        super("Title Here");

        layers.add(new Layer(1, "Africa"));
        layers.add(new Layer(2, "Europe"));
        layers.add(new Layer(3, "Asia"));

        bridges.add(new Bridge(10, new Integer[]{1,3}, "Panama"));
        bridges.add(new Bridge(11, new Integer[]{2}, "Sinai"));
        bridges.add(new Bridge(12, new Integer[]{2,3}, "Baja"));
        bridges.add(new Bridge(14, new Integer[]{1,2,3}, "Bxaja"));

        setLayout(new BorderLayout());
        add(new DiagramPanel(), BorderLayout.CENTER);

        setSize(800, 800);
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) {
        SummaryGraphPanelTest frame = new SummaryGraphPanelTest();
        frame.setVisible(true);
    }

    private static int mt = 20;
    private static int mb = 20;
    private static int ml = 20;
    private static int mr = 20;
    private static int lh = 60;
    private static int bw = 40;
    private static int mil = 40;
    private static int mir = 40;

    private static final Color[] layerColors = {Color.red, Color.green, Color.blue};

    private int layerHilite = -1;
    private int bridgeHilite = -1;

    private class DiagramPanel extends JPanel {
        private Map<Integer, LayerDesc> layerRects;
        private Map<Integer, BridgeDesc> bridgeRects;

        public DiagramPanel() {
            setBackground(Color.black);

            addMouseMotionListener(new MouseMotionListener() {
                public void mouseMoved(MouseEvent e) {
                    layerHilite = -1;
                    bridgeHilite = -1;
                    Cursor cur = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);

                    for(Integer layerId : layerRects.keySet()) {
                        LayerDesc ld = layerRects.get(layerId);
                        if(ld.rect.contains(e.getX(), e.getY())) {
                            layerHilite = layerId;
                            break;
                        }
                    }

                    if(layerHilite == -1) {
                        for(Integer bridgeId : bridgeRects.keySet()) {
                            BridgeDesc bd = bridgeRects.get(bridgeId);
                            if(bd.rect.contains(e.getX(), e.getY())) {
                                bridgeHilite = bridgeId;
                                break;
                            }
                        }
                    }

                    if(layerHilite != -1 || bridgeHilite != -1) {
                        cur = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
                    }

                    repaint();
                    setCursor(cur);
                }
                public void mouseDragged(MouseEvent e) {
                }
            });
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            layerRects = new HashMap<Integer, LayerDesc>();
            bridgeRects = new HashMap<Integer, BridgeDesc>();

            int pw = getWidth();
            int ph = getHeight();

            int topLayerMid = 0;
            int botLayerMid = 0;
            int topBotLayerSep = 0;
            int layerMidSep = 0;

            if(layers.size() > 1) {
                topLayerMid = mt + (int) (lh * 0.5);
                botLayerMid = ph - mb - (int) (lh * 0.5);
                topBotLayerSep = botLayerMid - topLayerMid;
                layerMidSep = topBotLayerSep / (layers.size() - 1);
            }

            for(int li = 0; li < layers.size(); li++) {
                Layer layer = layers.get(li);
                Color clrI = layerColors[li].darker().darker();
                Color clrO = layerColors[li];
                int id = layers.get(li).id;

                if(id == layerHilite) {
                    clrO = Color.yellow;
                    clrI = clrO.darker().darker();
                }

                int layerY;

                if(layers.size() == 1) {
                    layerY = ph / 2 - lh / 2;
                } else {
                    layerY = mt + li * layerMidSep;
                }

                layerRects.put(id,
                    new LayerDesc(layer,
                        new Rectangle(ml, layerY, pw - ml - mr, lh),
                        clrI, clrO, layers.get(li).name));
            }

            int leftBridgeMid = 0;
            int rightBridgeMid = 0;
            int leftRightBridgeSep = 0;
            int bridgeMidSep = 0;

            if(bridges.size() > 1) {
                leftBridgeMid = ml + mil + (int) (bw * 0.5);
                rightBridgeMid = pw - mr - mir - (int) (bw * 0.5);
                leftRightBridgeSep = rightBridgeMid - leftBridgeMid;
                bridgeMidSep = leftRightBridgeSep / (bridges.size() - 1);
            }

            int bi = 0;
            for(Bridge bridge : bridges) {
                int min = Integer.MAX_VALUE;
                int max = Integer.MIN_VALUE;

                boolean special;
                Rectangle bridgeRect;
                if(bridge.layerIds.length > 1) {
                    List<Integer> idList = Arrays.asList(bridge.layerIds);
                    for(Integer layerId : layerRects.keySet()) {
                        if(idList.contains(layerId)) {
                            Rectangle layerRect = layerRects.get(layerId).rect;
                            if(layerRect.y < min) {
                                min = layerRect.y;
                                System.out.println("min="+min);
                            }
                            if(layerRect.y + layerRect.height > max) {
                                max = layerRect.y + layerRect.height;
                                System.out.println("max="+max);
                            }
                        }
                    }
                    int bridgeX;
                    if(bridges.size() == 1) {
                        bridgeX = pw / 2 - bw / 2;
                    } else {
                        bridgeX = ml + mil + bi * bridgeMidSep;
                    }
                    bridgeRect = new Rectangle(bridgeX, min, bw, max - min);
                    special = false;
                } else {
                    int bridgeX;
                    if(bridges.size() == 1) {
                        bridgeX = pw / 2 - 3 * bw / 2;
                    } else {
                        bridgeX = ml + mil + bi * bridgeMidSep - bw;
                    }
                    LayerDesc ld = layerRects.get(bridge.layerIds[0]);
                    Rectangle layerRect = ld.rect;
                    bridgeRect = new Rectangle(bridgeX, layerRect.y + layerRect.height, 3 * bw, bw * 2);
                    special = true;
                }


                Color clrI = Color.white;
                Color clrO = Color.gray;
                int id = bridges.get(bi).id;

                if(id == bridgeHilite) {
                    clrO = Color.yellow;
                    clrI = clrO.darker().darker();
                }

                bridgeRects.put(id, new BridgeDesc(bridge, bridgeRect, clrI, clrO, bridge.name, special));
                bi++;
            }

            for(Integer bridgeId : bridgeRects.keySet()) {
                BridgeDesc bd = bridgeRects.get(bridgeId);
                Rectangle br = bd.rect;
                if(bd.special) {
                    rect(g, br.x, br.y, bw, br.height, bd.clrI, bd.clrO);
                    rect(g, br.x + br.width - bw, br.y, bw, br.height, bd.clrI, bd.clrO);
                    rect(g, br.x, br.y + br.height - bw, br.width, bw, bd.clrI, bd.clrO);
                } else {
                    rect(g, br.x, br.y, br.width, br.height, bd.clrI, bd.clrO);
                }
            }

            for(Integer layerId : layerRects.keySet()) {
                LayerDesc ld = layerRects.get(layerId);
                Rectangle lr = ld.rect;
                rect(g, lr.x, lr.y, lr.width, lr.height, ld.clrI, ld.clrO);
                g.setFont(new Font("Arial", Font.BOLD, 24));
                int d = 100;
                g.setColor(GUIUtil.deriveColor(ld.clrO, d, d, d));
                g.drawString(ld.layer.name, lr.x + 10, lr.y + lr.height / 2 + GUIUtil.stringHeight(g) / 2 - 5);
            }

            for(Integer bridgeId : bridgeRects.keySet()) {
                BridgeDesc bd = bridgeRects.get(bridgeId);
                Rectangle br = bd.rect;
                List<Integer> idList = Arrays.asList(bd.bridge.layerIds);
                for(Integer layerId : layerRects.keySet()) {
                    if(idList.contains(layerId)) {
                        LayerDesc ld = layerRects.get(layerId);
                        Rectangle lr = ld.rect;
                        if(bd.special) {
                            gradRect(g, br.x + 1, lr.y + lr.height, bw - 1, bw, ld.clrI, bd.clrI);
                            gradRect(g, br.x + br.width - bw + 1, lr.y + lr.height, bw - 1, bw, ld.clrI, bd.clrI);
                        } else {
                            if(br.y + br.height > lr.y + lr.height) {
                                gradRect(g, br.x + 1, lr.y + lr.height, br.width - 1, bw, ld.clrI, bd.clrI);
                            }
                            if(br.y < lr.y) {
                                gradRect(g, br.x + 1, lr.y - bw + 1, br.width - 1, bw, bd.clrI, ld.clrI);
                            }
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
            ((Graphics2D) g).setPaint(new GradientPaint(x, y + 10, i, x, y + h - 10, o));
            g.fillRect(x, y, w, h);
        }

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

    private class Layer {
        public int id;
        public String name;
        public Layer(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
    private class Bridge {
        public int id;
        public Integer[] layerIds;
        public String name;
        public Bridge(int i, Integer[] layerIds, String name) {
            id = i;
            this.layerIds = layerIds;
            this.name = name;
        }
    }
}