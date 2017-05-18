/*
Copyright 2013,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.font.TextAttribute;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.RootPaneContainer;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;


/**
 * The Lay class has been designed to simplify and speed the development
 * of Java Swing UI's created by hand (in the absence of a UI builder).
 *
 * The class attempts to ameliorate one of the most tedious aspects of
 * manual Swing UI construction: layout.  The class is not complex by
 * any means and serves simply to provide a shorthand method for specifying
 * the layout of a frame, dialog, or other container.  The resulting
 * code has the goal of being much easier to read and modify than
 * standard layout code.
 *
 * Furthermore, a major theme of this class is brevity, or trying enable
 * the developer to type as few characters as possible to enact the
 * desired layout.
 *
 * Traditionally laying out some Swing container involves a set of
 * code not unlike the following:
 *
 *    JPanel pnlTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
 *    JButton btn = ...
 *    ...
 *    pnlTop.add(btn);
 *    pnlTop.add(btn2);
 *    ...
 *    JPanel pnlCenter = new JPanel(new BorderLayout());
 *    JTextArea txt = ...
 *    JList lst = ...
 *    JPanel pnlMisc = createMiscPanel();
 *    pnlCenter.add(txt, BorderLayout.SOUTH);
 *    pnlCenter.add(lst, BorderLayout.EAST);
 *    pnlCenter.add(pnlMisc, BorderLayout.CENTER);
 *    pnlMisc.setBorder(
 * BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Padding
 *    txt.setBorder(BorderFactory.createCompoundBorder(
 *        BorderFactory.createEmptyBorder(0, 5, 0, 5),
 *            txt.getBorder()); // Padding
 *    ...
 *    this.setLayout(new BorderLayout());
 *    add(pnlTop, BorderLayout.NORTH);
 *    add(pnlCenter, BorderLayout.CENTER);
 *    add(new StatusBar(), BorderLayout.SOUTH);
 *
 * Above we see the creation of various JPanels merely for the purpose
 * of creating a "group" of controls that all behave according to a
 * given layout.  These JPanels are organized into a compositional
 * hierarchy to construct the final composite layout.  Although the
 * final UI at runtime will be this compositional hierarchy of
 * containers, the code we had to write to create it was very linear -
 * vertical if you will.  This makes understanding the code and changing
 * the layout more onerous than it needs to be, as the developer has to
 * transform this vertical representation of the UI code into a hierarchy
 * before they can make confidently make changes to it (they literally
 * have to look at which components are being added to which other
 * components to comprehend the hierarchy).
 *
 * Moreover, you tend to see a lot of repeated code.  Constructors, add
 * methods, borders for padding, constants (e.g. BorderLayout.CENTER,
 * FlowLayout.LEFT) all distract the UI developer from his/her main goal.
 *
 * The Lay class provides two kinds of methods:
 *
 *  - Layout Methods (start with upper case letter).  These either:
 *     1) return a new JPanel with the desired layout, or
 *        >> Methods: BxL, GL, FL, BL <<
 *     2) set the desired layout onto an existing ("target") container.
 *        >> Methods: BxLtg, GLtg, FLtg, BLtg <<
 *
 *  - Border Methods (start with lower case letter).  These either:
 *     1) construct and return a Border object, or
 *        >> Methods: cb, eb, mb <<
 *     2) take a JComponent, set a border to that JComponent and
 *         return the same JComponent.
 *        >> Methods: augb, eb, mb <<
 *
 * Let's take a look at how the above code would look if using the Lay
 * class:
 *
 *    JButton btn = ...
 *    JTextArea txt = ...
 *    JList lst = ...
 *    JPanel pnlMisc = createMiscPanel();
 *    ...
 *
 *    Lay.BLtg(this,
 *        "N", Lay.FL("L", btn, btn2),
 *        "C", Lay.BL(
 *            "E", lst,
 *            "C", Lay.eb(pnlMisc, "10"),
 *            "S", Lay.augb(txt, Lay.eb("5lr"))
 *        ),
 *        "S", new StatusBar()
 *    );
 *
 * The code that performs the grouping and layout of the controls is
 * now isolated to a single multi-line statement in this case.  There
 * is no longer any mention of JPanel, LayoutManager, or Border objects.
 * This allows the developer to focus on what's most important - the
 * controls!  Buttons, lists, trees, split panes, check boxes - those
 * are what the developer cares about.  The controls are what need to
 * have listeners attached and properties set.
 *
 * Notice that using proper indentation, the compositional hierarchy
 * of your UI is extremely evident!  You can actually see at a glance
 * what contains what and immediately construct the image of what the
 * finished container will look like in your mind.
 *
 * The short method names, even if somewhat confusing the first time
 * you see them, are essential to the simplicity of the Lay class and
 * are a reaction to the traditional method of laying out UI's requiring
 * so many characters to do relatively simple things.  Example:
 *    BorderFactory.createEmptyBorder(10, 10, 10, 10);
 * is represented in the Lay class by the statement:
 *    Lay.eb("10");
 *
 * As you can see the brevity comes not only from the short method names
 * but also from encoding as much of the layout information into string
 * literals as possible.  For example, "S" stands for BorderLayout.SOUTH
 * in Lay.BL and "10" signifies a 10-pixel-width border for along all four
 * sides, top, left, bottom, and right in Lay.eb and Lay.mb.
 *
 * Border Side Thickness Codes
 * ===========================
 * To speed the creation of borders, string codes to encode the specification
 * of the thicknesses of the four sides are used in Lay.eb and Lay.mb.
 * The code is in the following format:
 *     (Ns*)+   or regex:   ([0-9]+[tlbr]*)+
 * Where N is an integer, s is a side indicator: t, l, b, or r. Valid
 * examples of a thickness code are:
 *    "10"               t=l=b=r=10
 *    "10t"              t=10, l=b=r=0
 *    "7bt6rl"           t=b=7, l=r=6
 *    "5t10r"            t=5, r=10, l=b=0
 *    "5t4l3b2r"         t=5, l=4, b=3, r=2
 *
 * Layout UI Hints
 * ===============
 * In some situations you may still need to get a reference to one of the
 * generated JPanel objects to set some property:
 *
 *    JPanel pnlTop = Lay.FL("L", btn, btn2);
 *    pnlTop.setBackground(new Color(77, 88, 99));
 *    pnlTop.setOpaque(true);
 *    Lay.BLtg(this,
 *        "N", pnlTop,
 *        "C", Lay.BL(
 *            "E", lst,
 *            "C", Lay.eb(pnlMisc, "10"),
 *            "S", Lay.augb(txt, Lay.eb("5lr"))
 *        ),
 *        "S", new StatusBar()
 *    );
 *
 * The above code is still a great improvement over the traditional code.
 * However, to help minimize the amount of this code, common properties
 * can be set on containers using UI "hints" provided in the argument
 * list of the layout methods.  Here is the same code above using a hint:
 *
 *    Lay.BLtg(this,
 *        "N", Lay.FL("L", btn, btn2, "bg=[77,88,99],opaque=true"),
 *        "C", Lay.BL(
 *            "E", lst,
 *            "C", Lay.eb(pnlMisc, "10"),
 *            "S", Lay.augb(txt, Lay.eb("5lr"))
 *        ),
 *        "S", new StatusBar()
 *    );
 *
 * Hints are available on any of the layout methods.  Supported keys and
 * values and what methods are invoked on the container are:
 *
 *       visible=true|false     # Container.setVisible
 *       opaque=true|false      # JComponent.setOpaque
 *       bg=[R,B,G]|name        # setBackground
 *       pref=[W,H]             # setPreferredSize
 *       min=[W,H]              # setMinimumSize
 *       max=[W,H]              # setMaximumSize
 *       alignx=floatval        # setAlignmentX (often used in BoxLayouts)
 *       aligny=floatval        # setAlignmentY (often used in BoxLayouts)
 *       divpixel=intval        # JSplitPane.setDividerLocation
 *       divratio=floatval      # JSplitPane.setDividerLocation
 *       resizew=floatval       # JSplitPane.setResizeWeight
 *       enabled=true|false     # setEnabled
 *       size=[W,H]             # Window.setSize
 *       center                 # Window.setLocationRelativeTo(getParent())
 *       resizable=true|false   # Window.setResizable
 *
 * The hints can be in a single, comma-delimited string argument or multiple
 * string arguments:
 *         "bg=[155,229,100]", "size=[300,400]"
 *    -or- "bg=[155,229,100],size=[300,400]"
 *
 * Method Summary
 * ==============
 *
 * BxL(Object... cmps)
 *  - Create a JPanel with a BoxLayout (X_AXIS), add any Components
 *    in the argument list to the JPanel, and return the JPanel.
 *
 * BxLtg(Container target, Object... cmps)
 *  - Set a new BoxLayout (X_AXIS) to the container provided, add any
 *    Components in the argument list to the container, and return null.
 *
 * --> All BoxLayout Methods (BxL*):
 *    If a String is found in the argument list, and found to be
 *    any of these values (case insensitive):
 *      "X", "X_AXIS", "H", "HORIZ", "Y", "Y_AXIS", "V", "VERT"
 *    it overrides the orientation of the BoxLayout.
 *    If an Integer is found in the argument list, it overrides
 *    the orientation of the BoxLayout (e.g. BoxLayout.X_AXIS).
 *
 * GL(int rows, int cols, Object... cmps)
 *  - Create a JPanel with a GridLayout (using the dimensions provided),
 *    add any Components in the argument list to the JPanel, and
 *    return the JPanel (default gaps equal to 0).
 *
 * GLtg(Container target, int rows, int cols, Object... cmps)
 *  - Set a new GridLayout (using the axis provided) to the container
 *    provided, add any Components in the argument list to the container,
 *    and return null (default gaps equal to 0).
 *
 * FL(Object... cmps)
 *  - Create a JPanel with a FlowLayout (CENTER), add any Components
 *    in the argument list to the JPanel, and return the JPanel
 *    (default gaps equal to 5).
 *
 * FLtg(Container target, Object... cmps)
 *  -
 *
 * --> All FlowLayout Methods (FL*):
 *    If a String is found in the argument list, and found to be
 *    any of these values (case insensitive):
 *      "L", "LEFT", "R", "RIGHT", "C", "CENTER"
 *    it overrides the orientation of the FlowLayout.
 *    If an Integer is found in the argument list, it overrides
 *    the orientation of the FlowLayout (e.g. FlowLayout.LEFT).
 *
 * @author Derek Trumbo
 */

public class Lay {

    // ------------------------------------------------//
    // ------------ LAYOUT-RELATED METHODS ------------//
    // ------------------------------------------------//

    /////////////////////
    // ABSOLUTE LAYOUT //
    /////////////////////

    public static JPanel AL(Object... args) {
        return ALtg((Container) null, args);
    }

    public static JPanel ALtg(Container target, Object... args) {
        HintList hints = new HintList();
        List<Component> cmpsChosen = new ArrayList<Component>();

        for(Object arg : args) {
            if(arg instanceof String) {
                String str = (String) arg;
                hints.addHints(parseHints(str));

            // Save components
            } else if(arg instanceof Component) {
                cmpsChosen.add((Component) arg);
            }

            // Else ignore
        }

        // Set layout on container, add components, and set hints on container.
        Container cont = chooseContainer(target, hints);
        cont.setLayout(null);
        for(Component c : cmpsChosen) {
            cont.add(c);
        }
        setHints(cont, hints);
        return (target == null) ? (JPanel) cont : null;
    }


    ///////////////
    // BOXLAYOUT //
    ///////////////

    // Shorthand Literals
    private static Map<String, Integer> blAxis = new HashMap<String, Integer>();
    static {
        blAxis.put("X", BoxLayout.X_AXIS);
        blAxis.put("X_AXIS", BoxLayout.X_AXIS);
        blAxis.put("H", BoxLayout.X_AXIS);
        blAxis.put("HORIZ", BoxLayout.X_AXIS);
        blAxis.put("Y", BoxLayout.Y_AXIS);
        blAxis.put("Y_AXIS", BoxLayout.Y_AXIS);
        blAxis.put("V", BoxLayout.Y_AXIS);
        blAxis.put("VERT", BoxLayout.Y_AXIS);
    }

    // Layout Methods
    public static JPanel BxL(Object... args) {
        return BxLtg((Container) null, args);
    }

    public static JPanel BxLtg(Container target, Object... args) {
        HintList hints = new HintList();
        List<Component> cmpsChosen = new ArrayList<Component>();
        int axisChosen = BoxLayout.Y_AXIS;  // More common of a default

        for(Object arg : args) {
            if(arg instanceof String) {
                String str = (String) arg;

                // String axis override
                if(blAxis.get(str.toUpperCase()) != null) {
                    axisChosen = blAxis.get(str.toUpperCase());

                    // Add any hints
                } else {
                    hints.addHints(parseHints(str));
                }

                // Integer axis override
            } else if(arg instanceof Integer) {
                axisChosen = (Integer) arg;

                // Save components
            } else if(arg instanceof Component) {
                cmpsChosen.add((Component) arg);
            }

            // Else ignore
        }

        // Set layout on container, add components, and set hints on container.
        Container cont = chooseContainer(target, hints);
        BoxLayout bl = new BoxLayout(cont, axisChosen);
        cont.setLayout(bl);
        for(Component c : cmpsChosen) {
            cont.add(c);
        }
        setHints(cont, hints);
        return (target == null) ? (JPanel) cont : null;
    }


    // //////////////
    // GRIDLAYOUT //
    // //////////////

    // Layout Methods
    public static JPanel GL(int rows, int cols, Object... args) {
        return GLtg((Container) null, rows, cols, args);
    }

    public static JPanel GLtg(Container target, int rows, int cols, Object... args) {
        HintList hints = new HintList();
        List<Component> cmpsChosen = new ArrayList<Component>();

        for(Object arg : args) {

            // Add any hints
            if(arg instanceof String) {
                hints.addHints(parseHints((String) arg));

            // Add components
            } else if(arg instanceof Component) {
                cmpsChosen.add((Component) arg);
            }

            // Else ignore
        }

        Container cont = chooseContainer(target, hints);
        GridLayout gl = new GridLayout(rows, cols, 0, 0);
        cont.setLayout(gl);
        for(Component c : cmpsChosen) {
            cont.add(c);
        }
        setHints(cont, hints);
        return (target == null) ? (JPanel) cont : null;
    }


    // //////////////
    // FLOWLAYOUT //
    // //////////////

    // Shorthand Literals
    private static Map<String, Integer> flAlign = new HashMap<String, Integer>();
    static {
        flAlign.put("L", FlowLayout.LEFT);
        flAlign.put("LEFT", FlowLayout.LEFT);
        flAlign.put("R", FlowLayout.RIGHT);
        flAlign.put("RIGHT", FlowLayout.RIGHT);
        flAlign.put("C", FlowLayout.CENTER);
        flAlign.put("CENTER", FlowLayout.CENTER);
    }

    // Layout Methods
    public static JPanel FL(Object... args) {
        return FLtg((Container) null, args);
    }

    public static JPanel FLtg(Container target, Object... args) {
        HintList hints = new HintList();
        List<Component> cmpsChosen = new ArrayList<Component>();
        FlowLayout fl = new FlowLayout(FlowLayout.CENTER, 5, 5);

        for(Object arg : args) {
            if(arg instanceof String) {
                String str = (String) arg;

                // String axis override
                if(flAlign.get(str.toUpperCase()) != null) {
                    fl.setAlignment(flAlign.get(str.toUpperCase()));

                    // Add any hints
                } else {
                    hints.addHints(parseHints(str));
                }

                // Integer axis override
            } else if(arg instanceof Integer) {
                fl.setAlignment((Integer) arg);

                // Save components
            } else {
                cmpsChosen.add((Component) arg);
            }

            // Else ignore
        }

        // Set layout on container, add components, and set hints on container.
        Container cont = chooseContainer(target, hints);
        cont.setLayout(fl);
        for(Component c : cmpsChosen) {
            cont.add(c);
        }
        setHints(cont, hints);
        return (target == null) ? (JPanel) cont : null;
    }


    // //////////////
    // WRAPLAYOUT //
    // //////////////

    // Layout Methods
    public static JPanel WL(Object... args) {
        return WLtg((Container) null, args);
    }

    public static JPanel WLtg(Container target, Object... args) {
        HintList hints = new HintList();
        List<Component> cmpsChosen = new ArrayList<Component>();
        WrapLayout fl = new WrapLayout(FlowLayout.CENTER, 5, 5);

        for(Object arg : args) {
            if(arg instanceof String) {
                String str = (String) arg;

                // String axis override
                if(flAlign.get(str.toUpperCase()) != null) {
                    fl.setAlignment(flAlign.get(str.toUpperCase()));

                // Add any hints
                } else {
                    hints.addHints(parseHints(str));
                }

            // Integer axis override
            } else if(arg instanceof Integer) {
                fl.setAlignment((Integer) arg);

            // Save components
            } else if(arg instanceof Component) {
                cmpsChosen.add((Component) arg);

            } else if(arg instanceof ImageIcon) {
                if(target instanceof JFrame) {
                    JFrame frame = (JFrame) target;
                    frame.setIconImage(((ImageIcon) arg).getImage ());
                } else if(target instanceof JDialog) {
                    JDialog dlg = (JDialog) target;
                    dlg.setIconImage(((ImageIcon) arg).getImage ());
                }
            }

            // Else ignore
        }

        // Set layout on container, add components, and set hints on container.
        Container cont = chooseContainer(target, hints);
        cont.setLayout(fl);
        for(Component c : cmpsChosen) {
            cont.add(c);
        }
        setHints(cont, hints);
        return (target == null) ? (JPanel) cont : null;
    }


    // ////////////////
    // BORDERLAYOUT //
    // ////////////////

    // Shorthand Literals
    private static Map<String, String> blDirs = new HashMap<String, String>();
    static {
        blDirs.put("N", BorderLayout.NORTH);
        blDirs.put("NORTH", BorderLayout.NORTH);
        blDirs.put("E", BorderLayout.EAST);
        blDirs.put("EAST", BorderLayout.EAST);
        blDirs.put("S", BorderLayout.SOUTH);
        blDirs.put("SOUTH", BorderLayout.SOUTH);
        blDirs.put("W", BorderLayout.WEST);
        blDirs.put("WEST", BorderLayout.WEST);
        blDirs.put("C", BorderLayout.CENTER);
        blDirs.put("CENTER", BorderLayout.CENTER);
    }

    // Layout Methods
    public static JPanel BL(Object... args) {
        return BLtg((Container) null, args);
    }

    public static JPanel BLtg(Container target, Object... args) {
        HintList hints = new HintList();
        Map<Component, String> cmpsChosen = new LinkedHashMap<Component, String>();

        for(int a = 0; a < args.length; a++) {
            Object arg = args[a];
            if(arg instanceof Component) {
                Component cmp = (Component) arg;
                String area = BorderLayout.CENTER;

                if(a < args.length - 1) {
                    Object obj2 = args[a + 1];
                    if(obj2 instanceof String) {
                        String str = (String) obj2;
                        if(blDirs.get(str.toUpperCase()) != null) {
                            area = blDirs.get(str.toUpperCase());
                            a++;
                        }
                    }
                }

                cmpsChosen.put(cmp, area);

            } else if(arg instanceof String) {
                String str = (String) arg;
                if(blDirs.get(str.toUpperCase()) != null) {
                    String area = blDirs.get(str.toUpperCase());
                    if(a < args.length - 1) {
                        Object obj2 = args[a + 1];
                        if(obj2 instanceof Component) {
                            Component cmp = (Component) obj2;
                            cmpsChosen.put(cmp, area);
                            a++;
                        }
                    }
                } else {
                    hints.addHints(parseHints(str));
                }

            } else if(arg instanceof ImageIcon) {
                if(target instanceof JFrame) {
                    JFrame frame = (JFrame) target;
                    frame.setIconImage (((ImageIcon) arg).getImage ());
                } else if(target instanceof JDialog) {
                    JDialog dlg = (JDialog) target;
                    dlg.setIconImage (((ImageIcon) arg).getImage ());
                }
            }

            // Else ignore
        }

        Container cont = chooseContainer(target, hints);
        BorderLayout bl = new BorderLayout();
        cont.setLayout(bl);
        for(Component cmp : cmpsChosen.keySet()) {
            cont.add(cmp, cmpsChosen.get(cmp));
        }

        setHints(cont, hints);
        return (target == null) ? (JPanel) cont : null;
    }


    // /////////////////
    // GRIDBAGLAYOUT //
    // /////////////////

    // Layout Methods
    public static JPanel GBL(Object... args) {
        return GBLtg((Container) null, args);
    }

    public static JPanel GBLtg(Container target, Object... args) {
        HintList hints = new HintList();
        List<Component> cmpsChosen = new ArrayList<Component>();

        for(Object arg : args) {

            // Add any hints
            if(arg instanceof String) {
                hints.addHints(parseHints((String) arg));

            // Add components
            } else if(arg instanceof Component) {
                cmpsChosen.add((Component) arg);
            }

            // Else ignore
        }

        Container cont = chooseContainer(target, hints);
        GridBagLayout gl = new GridBagLayout();
        cont.setLayout(gl);
        for(Component c : cmpsChosen) {
            cont.add(c);
        }
        setHints(cont, hints);
        return (target == null) ? (JPanel) cont : null;
    }


    // //////////////
    // SPLIT PANE //
    // //////////////

    // Shorthand Literals
    private static Map<String, Integer> splOrien = new HashMap<String, Integer>();
    static {
        splOrien.put("X", JSplitPane.HORIZONTAL_SPLIT);
        splOrien.put("X_AXIS", JSplitPane.HORIZONTAL_SPLIT);
        splOrien.put("H", JSplitPane.HORIZONTAL_SPLIT);
        splOrien.put("HORIZ", JSplitPane.HORIZONTAL_SPLIT);
        splOrien.put("Y", JSplitPane.VERTICAL_SPLIT);
        splOrien.put("Y_AXIS", JSplitPane.VERTICAL_SPLIT);
        splOrien.put("V", JSplitPane.VERTICAL_SPLIT);
        splOrien.put("VERT", JSplitPane.VERTICAL_SPLIT);
    }

    // Layout Methods
    public static JSplitPane SPL(Object... args) {
        return SPL(JSplitPane.HORIZONTAL_SPLIT, args);
    }

    public static JSplitPane SPL(int orien, Object... args) {
        HintList hints = new HintList();
        int orienChosen = orien;
        List<Component> cmpsChosen = new ArrayList<Component>();

        for(Object arg : args) {
            if(arg instanceof String) {
                String str = (String) arg;

                // String orientation override
                if(splOrien.get(str.toUpperCase()) != null) {
                    orienChosen = splOrien.get(str.toUpperCase());

                    // Add any hints
                } else {
                    hints.addHints(parseHints(str));
                }

                // Integer orientation override
            } else if(arg instanceof Integer) {
                orienChosen = (Integer) arg;

                // Save components
            } else if(arg instanceof Component) {
                cmpsChosen.add((Component) arg);
            }

            // Else ignore
        }

        // Add components and set hints on container.
        Component c1 = cmpsChosen.size() > 0 ? cmpsChosen.get(0) : null;
        Component c2 = cmpsChosen.size() > 1 ? cmpsChosen.get(1) : null;
        JSplitPane spl = new JSplitPane(orienChosen, c1, c2);
        setHints(spl, hints);
        return spl;
    }


    // ------------------------------------------------//
    // ------------ BORDER-RELATED METHODS ------------//
    // ------------------------------------------------//


    // ///////////////
    // BASIC PANEL //
    // ///////////////

    public static JPanel p(Object... args) {
        HintList hints = new HintList();
        LayoutManager mgr = null;
        for(Object arg : args) {
            if(arg instanceof String) {
                hints.addHints(parseHints((String) arg));
            } else if(arg instanceof LayoutManager) {
                mgr = (LayoutManager) arg;
            }
        }
        Container cont = chooseContainer(null, hints);
        cont.setLayout(mgr == null ? new BorderLayout() : mgr);
        setHints(cont, hints);
        return (JPanel) cont;
    }

    public static JPanel p(JComponent c, Object... args) {
        // return BL("C", c, args); Would have been nice but args gets converted into Object[1]
        HintList hints = new HintList();
        for(Object arg : args) {
            if(arg instanceof String) {
                hints.addHints(parseHints((String) arg));
            }
        }
        Container cont = chooseContainer(null, hints);
        BorderLayout bl = new BorderLayout();
        cont.setLayout(bl);
        cont.add(c, BorderLayout.CENTER);
        setHints(cont, hints);
        return (JPanel) cont;
    }


    // ///////////////////////////
    // AUGMENT/COMPOUND BORDER //
    // ///////////////////////////

    public static Border brd(String code) {
        if(code.startsWith("eb=")) {
            return eb(code.substring(3));
            // } else if(code.startsWith("mb=")) { // Needs more thought
            // return mb(code.substring(3));
        }
        return null;
    }

    public static JComponent augb(JComponent c, String code) {
        return augb(c, brd(code));
    }

    public static JComponent augb(JComponent c, Border outer) {
        c.setBorder(BorderFactory.createCompoundBorder(outer, c.getBorder()));
        return c;
    }

    public static Border cb(Border outer, Border inner) {
        return BorderFactory.createCompoundBorder(outer, inner);
    }


    // ////////////////
    // EMPTY BORDER //
    // ////////////////

    public static Border eb() {
        return BorderFactory.createEmptyBorder();
    }

    public static Border eb(String code) {
        Sides sides = new Sides(code);
        Border border = BorderFactory.createEmptyBorder(
            sides.top, sides.left, sides.bottom, sides.right);
        return border;
    }

    public static JComponent eb(JComponent cmp, String code, Object... args) {
        Sides sides = new Sides(code);
        Border border = BorderFactory.createEmptyBorder(
            sides.top, sides.left, sides.bottom, sides.right);
        cmp.setBorder(border);
        HintList hints = new HintList();
        for(Object arg : args) {
            if(arg instanceof String) {
                hints.addHints(parseHints((String) arg));
            }
        }
        setHints(cmp, hints);
        return cmp;
    }


    // ////////////////
    // MATTE BORDER //
    // ////////////////

    public static Border mb(String code, Color clr) {
        Sides sides = new Sides(code);
        Border border = BorderFactory.createMatteBorder(
            sides.top, sides.left, sides.bottom, sides.right, clr);
        return border;
    }

    public static JComponent mb(JComponent cmp, String code, Color clr, Object... args) {
        Sides sides = new Sides(code);
        Border border = BorderFactory.createMatteBorder(
            sides.top, sides.left, sides.bottom, sides.right, clr);
        cmp.setBorder(border);
        HintList hints = new HintList();
        for(Object arg : args) {
            if(arg instanceof String) {
                hints.addHints(parseHints((String) arg));
            }
        }
        setHints(cmp, hints);
        return cmp;
    }


    // ///////////////
    // SCROLL PANE //
    // ///////////////

    // More possibilities here.
    public static JScrollPane sp(Component cmp) {
        return sp(cmp, new Object[0]);
    }

    public static JScrollPane sp(Component cmp, Object... args) {
        JScrollPane scr = new JScrollPane(cmp);
        HintList hints = new HintList();
        for(Object arg : args) {
            if(arg instanceof String) {
                hints.addHints(parseHints((String) arg));
            }
        }
        setHints(scr, hints);
        return scr;
    }


    // ////////////////
    // BORDER SIDES //
    // ////////////////

    private static class Sides {
        public int top;
        public int left;
        public int bottom;
        public int right;

        public Sides(String code) {
            String patStr = "\\s*([0-9]+)([tlbr]*)\\s*";
            if(!code.matches("(?:" + patStr + ")+")) {
                throw new IllegalArgumentException("Invalid empty border code: " + code);
            }
            Pattern p = Pattern.compile(patStr);
            Matcher m = p.matcher(code.toLowerCase());
            while(m.find()) {
                String thick = m.group(1);
                int width = Integer.parseInt(thick);
                String sides = m.group(2);
                if(sides.length() == 0) {
                    top = left = bottom = right = width;
                } else {
                    for(int c = 0; c < sides.length(); c++) {
                        switch(sides.charAt(c)) {
                            case 't': top = width;    break;
                            case 'l': left = width;   break;
                            case 'b': bottom = width; break;
                            case 'r': right = width;  break;
                        }
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "top=" + top + ",left=" + left + ",bottom=" + bottom + ",right=" + right;
        }
    }


    // ----------------------------------------------------//
    // ------------ COMPONENT CREATION METHODS ------------//
    // ----------------------------------------------------//

    ///////////////
    // CHECK BOX //
    ///////////////

    public static JCheckBox chk() {
        return new JCheckBox();
    }
    public static JCheckBox chk(Object text, Object... args) {
        HintList hints = new HintList();
        boolean selected = false;
        Icon icon = null;
        for(Object arg : args) {
            if(arg instanceof String) {
                hints.addHints(parseHints((String) arg));
            } else if(arg instanceof Icon) {
                icon = (Icon) arg;
            } else if(arg instanceof Boolean) {
                selected = (Boolean) arg;
            }
        }
        JCheckBox chk;
        if(text instanceof Icon) {
            chk = new JCheckBox((Icon) text, selected);
        } else if(icon != null) {
            chk = new JCheckBox(text.toString(), icon, selected);
        } else {
            chk = new JCheckBox(text.toString(), selected);
        }
        setHints(chk, hints);
        return chk;
    }


    ///////////
    // LABEL //
    ///////////

    public static JLabel lb() {
        return new JLabel();
    }
    public static JLabel lb(Object text, Object... args) {
        HintList hints = new HintList();
        Icon icon = null;
        int horizAlignText = SwingConstants.LEADING;
        int horizAlignImage = SwingConstants.CENTER;
        for(Object arg : args) {
            if(arg instanceof String) {
                hints.addHints(parseHints((String) arg));
            } else if(arg instanceof Icon) {
                icon = (Icon) arg;
            } else if(arg instanceof Integer) {
                horizAlignText = (Integer) arg;
                horizAlignImage = (Integer) arg;
            }
        }
        JLabel lbl;
        if(text instanceof Icon) {
            lbl = new JLabel((Icon) text, horizAlignImage);
        } else if(icon != null) {
            lbl = new JLabel(text.toString(), icon, horizAlignText);
        } else {
            lbl = new JLabel(text.toString(), horizAlignText);
        }
        setHints(lbl, hints);
        return lbl;
    }


    ////////////////
    // TEXT FIELD //
    ////////////////

    public static JTextField tx() {
        return new JTextField();
    }
    public static JTextField tx(Object text, Object... args) {
        HintList hints = new HintList();
        for(Object arg : args) {
            if(arg instanceof String) {
                hints.addHints(parseHints((String) arg));
            }
        }
        final JTextField txt = new JTextField ("" + text);
        if (hints.contains ("selectall"))
        {
            txt.addFocusListener (new FocusAdapter ()
            {
                @Override
                public void focusGained (FocusEvent e)
                {
                    txt.selectAll ();
                }
            });
        }
        setHints(txt, hints);
        return txt;
    }


    ///////////////
    // COMBO BOX //
    ///////////////

    public static JComboBox<?> cb() {
        return new JComboBox ();
    }
    public static JComboBox cb(Object... args) {
        // TODO: Can add special hint for this method so
        // that subsequent strings can be interpreted as
        // elements for the combo box array model.
        HintList hints = new HintList();
        List aModel = new ArrayList();
        ComboBoxModel cModel = null;
        boolean strElems = false;
        for(Object arg : args) {
            if(arg instanceof String) {
                if(((String)arg).equalsIgnoreCase("!strelems")) {
                    strElems = true;
                }
                if(strElems) {
                    aModel.add(arg);
                } else {
                    hints.addHints(parseHints((String) arg));
                }
            } else if(arg != null) {
                if(arg.getClass().isArray()) {
                    for(int a = 0; a < Array.getLength(arg); a++) {
                        aModel.add(Array.get(arg, a));
                    }
                } else {
                    aModel.add(arg);
                }
            }
            if(arg instanceof ComboBoxModel) {
                cModel = (ComboBoxModel) arg;
            }
        }
        JComboBox cbo;
        if (cModel != null) cbo = new JComboBox(cModel);
        else                cbo = new JComboBox(aModel.toArray());
        if (hints.contains ("white")) cbo.setBackground (Color.white);
        setHints(cbo, hints);
        return cbo;
    }


    // --------------------------------------------//
    // ------------ SUPPORTING METHODS ------------//
    // --------------------------------------------//


    // /////////////////
    // LAYOUT HELPER //
    // /////////////////

    private static Container chooseContainer(Container target, HintList hints) {
        if (target == null)
        {
            if (hints.contains ("gradient")) return new GradientPanel ();
            return new JPanel();
        }

        // We need layout / hierachy operations to happen on the
        // content pane of dialogs & frames.
        if(isContentPaneHolder(target)) {
            return ((RootPaneContainer) target).getContentPane();
        }

        return target;
    }


    // ////////////////
    // LAYOUT HINTS //
    // ////////////////

    private static boolean debugOn = false;

    public static void debug ()
    {
        debugOn = true;
    }

    private static abstract class HintProcessor {
        public static final int WIN = 0;
        public static final int NONWIN = 1;
        public static final int BOTH = 2;
        public int applicableTo;
        public HintProcessor()  {this(NONWIN);}
        public HintProcessor(int appTo)  {applicableTo = appTo;}
        abstract void process(String value, Component comp, HintList allHints) throws Exception;
    }

    public static boolean isInt (String s)
    {
        try
        {
            Integer.parseInt (s);
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    // Keys must be lower case
    private static Map<String, HintProcessor> hintProcessors = new HashMap<String, HintProcessor>();
    static {

        // Window Hints

        hintProcessors.put("center", new HintProcessor(HintProcessor.WIN) {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                if(cmp instanceof Window) {
                    Window w = ((Window) cmp);
                    if(value != null) {
                        if(isInt(value)) {
                            int i = Integer.parseInt(value);
                            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                            GraphicsDevice[] gds = ge.getScreenDevices();
                            if(i > 0 && i <= gds.length) {
                                GraphicsDevice gd = gds[i - 1];
                                DisplayMode dm = gd.getDisplayMode();
                                int left = gd.getDefaultConfiguration().getBounds().x + (dm.getWidth() - w.getWidth()) / 2;
                                int top = (dm.getHeight() - w.getHeight()) / 2;
                                w.setLocation(left, top);
                                return;
                            }
                        }
                    }
                    if(value == null) {
                        value = "true";  // This could be done at layer above this for all boolean values.
                    }
                    w.setLocationRelativeTo(cmp.getParent());
                } else if(cmp instanceof JTextField) {
                    ((JTextField) cmp).setHorizontalAlignment(JTextField.CENTER);
                }
            }
        });
        hintProcessors.put("dco", new HintProcessor(HintProcessor.WIN) {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                if(cmp instanceof Dialog) {
                    // Exit on close not allowed for dialogs.
                    if(value.equalsIgnoreCase("dispose")) {
                        ((JDialog) cmp).setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                    } else if(value.equalsIgnoreCase("nothing")) {
                        ((JDialog) cmp).setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                    } else if(value.equalsIgnoreCase("hide")) {
                        ((JDialog) cmp).setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
                    }
                } else {
                    if(value.equalsIgnoreCase("exit")) {
                        ((JFrame) cmp).setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                    } else if(value.equalsIgnoreCase("dispose")) {
                        ((JFrame) cmp).setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                    } else if(value.equalsIgnoreCase("nothing")) {
                        ((JFrame) cmp).setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                    } else if(value.equalsIgnoreCase("hide")) {
                        ((JFrame) cmp).setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
                    }
                }
            }
        });
        hintProcessors.put("loc", new HintProcessor(HintProcessor.WIN) {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                int[] nums = parseNumberList(value);
                if(nums.length == 2) {
                    int x = nums[0];
                    int y = nums[1];
                    cmp.setLocation(x, y);
                }
            }
        });
        hintProcessors.put("resizable", new HintProcessor(HintProcessor.WIN) {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                if(value == null) {
                    value = "true";  // This could be done at layer above this for all boolean values.
                }
                if(cmp instanceof Dialog) {
                    ((Dialog) cmp).setResizable(Boolean.parseBoolean(value));
                } else {
                    ((Frame) cmp).setResizable(Boolean.parseBoolean(value));
                }
            }
        });
        hintProcessors.put("size", new HintProcessor(HintProcessor.WIN) {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                int[] nums = parseNumberList(value);
                if(cmp instanceof JLabel || cmp instanceof JTextComponent) {
                    // TODO: actually, should be non-window components.
                    Font f = cmp.getFont();
                    if(nums.length == 1) {
                        cmp.setFont(f.deriveFont((float) nums[0]));
                    }
                    return;
                }
                if(nums.length == 2) {
                    int width = nums[0];
                    int height = nums[1];
                    cmp.setSize(width, height);
                }
            }
        });

        // Both Window & Non-Window hint

        hintProcessors.put("visible", new HintProcessor(HintProcessor.BOTH) {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                if(value == null) {
                    value = "true";  // This could be done at layer above this for all boolean values.
                }
                cmp.setVisible(Boolean.parseBoolean(value));
            }
        });


        // Non-Window Hints

        hintProcessors.put("fg", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                cmp.setForeground(clr(value));
            }
        });
        hintProcessors.put("bg", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                cmp.setBackground(clr(value));
                ((JComponent) cmp).setOpaque(true);
            }
        });
        hintProcessors.put("opaque", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                if(value == null) {
                    value = "true";  // This could be done at layer above this for all boolean values.
                }
                ((JComponent) cmp).setOpaque(Boolean.parseBoolean(value));
            }
        });
        hintProcessors.put("ttt", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                ((JComponent) cmp).setToolTipText(value);
            }
        });
        hintProcessors.put("eb", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                ((JComponent) cmp).setBorder(eb(value));
            }
        });
        hintProcessors.put("mb", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                int comma = value.indexOf(",");
                String code = value.substring(value.indexOf("[") + 1, comma).toString();
                String color = value.substring(comma + 1, value.indexOf("]")).trim();
                ((JComponent) cmp).setBorder(mb(code, clr(color)));
            }
        });
        hintProcessors.put("augb", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                String[] parts = parseFunctionCall(value);
                if(parts[0].equals("eb")) {
                    augb((JComponent) cmp, eb(parts[1]));
                } else if(parts[0].equals("mb")) {
                    augb((JComponent) cmp, mb(parts[1], clr(parts[2])));
                }
            }
        });
        hintProcessors.put("dim", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                int[] nums = parseNumberList(value);
                if(nums.length == 2) {
                    Dimension d = new Dimension(nums[0], nums[1]);
                    cmp.setMinimumSize(d);
                    cmp.setMaximumSize(d);
                    cmp.setPreferredSize(d);
                }
            }
        });
        hintProcessors.put("dimw", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                int w = Integer.parseInt(value);
                Dimension dim = new Dimension(w, cmp.getMinimumSize().height);
                cmp.setMinimumSize(dim);
                dim = new Dimension(w, cmp.getMaximumSize().height);
                cmp.setMaximumSize(dim);
                dim = new Dimension(w, cmp.getPreferredSize().height);
                cmp.setPreferredSize(dim);
            }
        });
        hintProcessors.put("dimh", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                int h = Integer.parseInt(value);
                Dimension dim = new Dimension(cmp.getMinimumSize().width, h);
                cmp.setMinimumSize(dim);
                dim = new Dimension(cmp.getMaximumSize().width, h);
                cmp.setMaximumSize(dim);
                dim = new Dimension(cmp.getPreferredSize().width, h);
                cmp.setPreferredSize(dim);
            }
        });
        hintProcessors.put("pref", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                int[] nums = parseNumberList(value);
                if(nums.length == 2) {
                    cmp.setPreferredSize(new Dimension(nums[0], nums[1]));
                }
            }
        });
        hintProcessors.put("prefw", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                Dimension dim = new Dimension(Integer.parseInt(value), cmp.getPreferredSize().height);
                cmp.setPreferredSize(dim);
            }
        });
        hintProcessors.put("prefh", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                Dimension dim = new Dimension(cmp.getPreferredSize().width, Integer.parseInt(value));
                cmp.setPreferredSize(dim);
            }
        });
        hintProcessors.put("min", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                int[] nums = parseNumberList(value);
                if(nums.length == 2) {
                    cmp.setMinimumSize(new Dimension(nums[0], nums[1]));
                }
            }
        });
        hintProcessors.put("minw", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                Dimension dim = new Dimension(Integer.parseInt(value), cmp.getMinimumSize().height);
                cmp.setMinimumSize(dim);
            }
        });
        hintProcessors.put("minh", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                Dimension dim = new Dimension(cmp.getMinimumSize().width, Integer.parseInt(value));
                cmp.setMinimumSize(dim);
            }
        });
        hintProcessors.put("max", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                int[] nums = parseNumberList(value);
                if(nums.length == 2) {
                    cmp.setMaximumSize(new Dimension(nums[0], nums[1]));
                }
            }
        });
        hintProcessors.put("maxw", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                Dimension dim = new Dimension(Integer.parseInt(value), cmp.getMaximumSize().height);
                cmp.setMaximumSize(dim);
            }
        });
        hintProcessors.put("maxh", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                Dimension dim = new Dimension(cmp.getMaximumSize().width, Integer.parseInt(value));
                cmp.setMaximumSize(dim);
            }
        });
        hintProcessors.put("alignx", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                ((JComponent) cmp).setAlignmentX(Float.parseFloat(value));
            }
        });
        hintProcessors.put("aligny", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                ((JComponent) cmp).setAlignmentY(Float.parseFloat(value));
            }
        });
        hintProcessors.put("divpixel", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                ((JSplitPane) cmp).setDividerLocation(Integer.parseInt(value));
            }
        });
        hintProcessors.put("divratio", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                ((JSplitPane) cmp).setDividerLocation(Double.parseDouble(value));
            }
        });
        hintProcessors.put("resizew", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                ((JSplitPane) cmp).setResizeWeight(Double.parseDouble(value));
            }
        });

        hintProcessors.put("enabled", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                if(value == null) {
                    value = "true";  // This could be done at layer above this for all boolean values.
                }
                cmp.setEnabled(Boolean.parseBoolean(value));
            }
        });
        hintProcessors.put("editable", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                if(value == null) {
                    value = "true";  // This could be done at layer above this for all boolean values.
                }
                ((JTextComponent) cmp).setEditable(Boolean.parseBoolean(value));
            }
        });
        hintProcessors.put("htext", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                if(value.equalsIgnoreCase("left")) {
                    ((JLabel) cmp).setHorizontalTextPosition(SwingConstants.LEFT);
                } else if(value.equalsIgnoreCase("center")) {
                    ((JLabel) cmp).setHorizontalTextPosition(SwingConstants.CENTER);
                } else if(value.equalsIgnoreCase("right")) {
                    ((JLabel) cmp).setHorizontalTextPosition(SwingConstants.RIGHT);
                } else if(value.equalsIgnoreCase("leading")) {
                    ((JLabel) cmp).setHorizontalTextPosition(SwingConstants.LEADING);
                } else if(value.equalsIgnoreCase("trailing")) {
                    ((JLabel) cmp).setHorizontalTextPosition(SwingConstants.TRAILING);
                }
            }
        });
        hintProcessors.put("cursor", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                if(value.equalsIgnoreCase("hand")) {
                    cmp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
            }
        });
        hintProcessors.put("halign", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                if(value.equalsIgnoreCase("left")) {
                    ((JLabel) cmp).setHorizontalAlignment(SwingConstants.LEFT);
                } else if(value.equalsIgnoreCase("center")) {
                    ((JLabel) cmp).setHorizontalAlignment(SwingConstants.CENTER);
                } else if(value.equalsIgnoreCase("right")) {
                    ((JLabel) cmp).setHorizontalAlignment(SwingConstants.RIGHT);
                } else if(value.equalsIgnoreCase("leading")) {
                    ((JLabel) cmp).setHorizontalAlignment(SwingConstants.LEADING);
                } else if(value.equalsIgnoreCase("trailing")) {
                    ((JLabel) cmp).setHorizontalAlignment(SwingConstants.TRAILING);
                }
            }
        });
        hintProcessors.put("valign", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                if(value.equalsIgnoreCase("top")) {
                    ((JLabel) cmp).setVerticalAlignment(SwingConstants.TOP);
                } else if(value.equalsIgnoreCase("center")) {
                    ((JLabel) cmp).setVerticalAlignment(SwingConstants.CENTER);
                } else if(value.equalsIgnoreCase("bottom")) {
                    ((JLabel) cmp).setVerticalAlignment(SwingConstants.BOTTOM);
                }
            }
        });
        hintProcessors.put("plain", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                cmp.setFont(cmp.getFont().deriveFont(Font.PLAIN));
            }
        });
        hintProcessors.put("italic", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                cmp.setFont(cmp.getFont().deriveFont(Font.ITALIC));
            }
        });
        hintProcessors.put("bold", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                cmp.setFont(cmp.getFont().deriveFont(Font.BOLD));
            }
        });
        hintProcessors.put("underline", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                Font original = cmp.getFont();
                Map attributes = original.getAttributes();
                attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
                cmp.setFont(original.deriveFont(attributes));
            }
        });
        hintProcessors.put("hsb", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                if(value.equalsIgnoreCase("always")) {
                    ((JScrollPane) cmp)
                        .setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
                } else if(value.equalsIgnoreCase("never")) {
                    ((JScrollPane) cmp)
                        .setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                } else if(value.equalsIgnoreCase("asneeded")) {
                    ((JScrollPane) cmp)
                        .setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                }
            }
        });
        hintProcessors.put("vsb", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                if(value.equalsIgnoreCase("always")) {
                    ((JScrollPane) cmp)
                        .setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
                } else if(value.equalsIgnoreCase("never")) {
                    ((JScrollPane) cmp)
                        .setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
                } else if(value.equalsIgnoreCase("asneeded")) {
                    ((JScrollPane) cmp)
                        .setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
                }
            }
        });
        hintProcessors.put("hgap", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                LayoutManager mgr = ((Container) cmp).getLayout();
                if(mgr instanceof FlowLayout) {
                    ((FlowLayout) mgr).setHgap(Integer.parseInt(value));
                } else if(mgr instanceof GridLayout) {
                    ((GridLayout) mgr).setHgap(Integer.parseInt(value));
                } else if(mgr instanceof BorderLayout) {
                    ((BorderLayout) mgr).setHgap(Integer.parseInt(value));
                }
            }
        });
        hintProcessors.put("vgap", new HintProcessor() {
            @Override
            void process(String value, Component cmp, HintList allHints) throws Exception {
                LayoutManager mgr = ((Container) cmp).getLayout();
                if(mgr instanceof FlowLayout) {
                    ((FlowLayout) mgr).setVgap(Integer.parseInt(value));
                } else if(mgr instanceof GridLayout) {
                    ((GridLayout) mgr).setVgap(Integer.parseInt(value));
                } else if(mgr instanceof BorderLayout) {
                    ((BorderLayout) mgr).setVgap(Integer.parseInt(value));
                }
            }
        });
        hintProcessors.put("gradient", new HintProcessor() {
            @Override
            void process(String value, Component comp, HintList allHints) throws Exception {
                String clr1 = allHints.get("gradclr1");
                String clr2 = allHints.get("gradclr2");
                if(clr1 != null && clr2 == null) {
                    ((GradientPanel) comp).setColor(clr(clr1));
                } else if(clr2 != null && clr1 == null) {
                    ((GradientPanel) comp).setColor(clr(clr2));
                } else if(clr2 != null && clr1 != null) {
                    ((GradientPanel) comp).setColors(clr(clr1), clr(clr2));
                }
            }
        });
        hintProcessors.put ("borders", new HintProcessor ()
        {
            @Override
            void process (String value, Component comp, HintList allHints) throws Exception
            {
                if (comp instanceof JTabbedPane)
                {
                    Component[] tabs = ((JTabbedPane) comp).getComponents ();
                    for (int i = 0; i < tabs.length; i++)
                    {
                        Component tab = tabs[i];
                        if (tab instanceof JComponent) ((JComponent) tab).setBorder (Lay.eb ("2"));
                    }
                }
            }
        });
    }

    private static String[] parseFunctionCall(String value) {
        String clrPat = "([a-zA-Z0-9]+)\\(([a-zA-Z0-9#]+)(?:,([a-zA-Z0-9#]+|\\[[^\\]]*\\]))?\\)";
        Pattern p = Pattern.compile(clrPat);
        Matcher m = p.matcher(value);
        if(m.matches()) {
            return new String[] {m.group(1), m.group(2), m.group(3)};
        }
        return new String[0];
    }

    private static int[] parseNumberList(String value) {
        String numberListPattern =
            "^\\[?\\s*([0-9]+)(?:\\s*,\\s*([0-9]+))?(?:\\s*,\\s*([0-9]+))?\\s*\\]?$";
        Pattern p = Pattern.compile(numberListPattern);
        Matcher m = p.matcher(value);
        List<Integer> numList = new ArrayList<Integer>();
        if(m.find()) {
            for(int i = 1; i <= m.groupCount(); i++) {
                String grp = m.group(i);
                if(grp != null) {
                    numList.add(Integer.parseInt(grp));
                }
            }
        }
        int[] nums = new int[numList.size()];
        int i = 0;
        for(Integer num : numList) {
            nums[i++] = num;
        }
        return nums;
    }

    public static class HintPair {
        public String key;
        public String value;

        public HintPair(String key, String value) {
            super();
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return key + (value != null ? " = " + value : "");
        }
    }

    public static class HintList extends ArrayList<HintPair> {
        private Map<String, String> map = new HashMap<String, String>();
        public void add(String key, String value) {
            map.put(key, value);
            super.add(new HintPair(key, value));
        }
        public String get(String key) {
            return map.get(key);
        }
        public boolean contains(String key) {
            return map.containsKey(key);
        }
        public void addHints(HintList lst) {
            map.putAll(lst.getMap());
            super.addAll(lst);
        }
        public Map<String, String> getMap() {
            return map;
        }
    }

    // Valid formats supported by the regex:
    // key => For center
    // key=val => For enabled=false, bg=yellow
    // key=[N,N,N] => For bg=[R,G,B], size=[W,H]
    // key=val(val) => For augb=eb(code)
    // key=val(val,val) => For augb=mb(code,clr)
    // key=val(val,[N,N,N]) => For augb=mb(code,[R,G,B])
    public static HintList parseHints(String s) {
        HintList hints = new HintList(); // Order that hints appear in the string is important
        String keyValuePat =
            "[a-zA-Z0-9]+\\s*(?:=\\s*(?:[a-zA-Z0-9]+(?:\\.[0-9]+)?(?:\\([a-zA-Z0-9#]+(?:,(?:[a-zA-Z0-9#]+|\\[[^\\]]*\\]))?\\))?|\\[[^\\]]*\\]))?";
        Pattern p = Pattern.compile("\\s*" + keyValuePat + "(?:\\s*,\\s*" + keyValuePat + ")*\\s*");
        Matcher m = p.matcher(s);
        if(m.matches()) {
            Pattern p2 = Pattern.compile("(" + keyValuePat + ")");
            m = p2.matcher(s);
            while(m.find()) {
                String kv = m.group(1);
                int eq = kv.indexOf('=');
                String key, value;
                if(eq != -1) {
                    key = kv.substring(0, eq).trim();
                    value = kv.substring(eq + 1).trim();
                } else {
                    key = kv;
                    value = null;
                }
                hints.add(key, value);
            }
        }
        return hints;
    }

    private static HintList globalHints = new HintList();
    public static void addGlobalHints(String hints) {
        globalHints.addHints(parseHints(hints));
    }
    public static void addGlobalHint(String K, String V) {
        globalHints.add(K, V);
    }
    public static void clearGlobalHints() {
        globalHints.clear();
    }

    private static void setHints(Component cmp, HintList hints) {
        HintList finalHints = new HintList();
        finalHints.addHints(globalHints);
        finalHints.addHints(hints);
        for(HintPair hint : finalHints) {
            String key = hint.key;
            String value = hint.value;
            String lowerKey = key.toLowerCase();
            if(hintProcessors.containsKey(lowerKey)) {
                HintProcessor hp = hintProcessors.get(lowerKey);
                Component cmpSelected = null;
                try {

                    // If this hint is for windows only, then
                    // make sure that if the original component is a
                    // window's content pane, we choose the window
                    // itself to which to apply the hint.
                    if(hp.applicableTo == HintProcessor.WIN) {
                        cmpSelected = cmp;
                        if(isContentPane(cmpSelected)) {
                            while(!(cmpSelected instanceof Window)) {
                                cmpSelected = cmpSelected.getParent();
                            }
                        }

                        // If this hint is allowed to be applied to either
                        // windows or non-windows, make sure content panes
                        // are replaced with their parent windows. This
                        // assumes however that a developer would never
                        // explicitly send in a content pane panel wanting
                        // these types of hints applied (this could be
                        // thought out better). However, right now this
                        // meets the need of 'visible' being applied to
                        // both Windows and non-content pane panels inside
                        // contained by the window.
                    } else if(hp.applicableTo == HintProcessor.BOTH) {
                        cmpSelected = cmp;
                        if(isContentPane(cmpSelected)) {
                            while(!(cmpSelected instanceof Window)) {
                                cmpSelected = cmpSelected.getParent();
                            }
                        }

                        // If this hint is for non-window components only,
                        // Make sure we apply it to the window's content pane
                        // IF original component is a window.
                    } else {
                        cmpSelected = cmp;
                        if(isContentPaneHolder(cmpSelected)) {
                            cmpSelected = ((RootPaneContainer) cmpSelected).getContentPane();
                        }
                    }

                    hp.process(value, cmpSelected, hints);
                    if(debugOn) {
                        System.out.println(
                            "Applied hint (" + lowerKey + "=" + value + ") to [" + cmpSelected + "]");
                    }
                } catch(Exception e) {
                    // Soft fail (ClassCastException, NumberFormatException most common)
                    if(debugOn) {
                        System.err.println(
                            "Hint error (" + lowerKey + "=" + value + ") to [" + cmpSelected + "]");
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static boolean isContentPane(Component c) {
        return c.getParent() instanceof JLayeredPane;
    }

    private static boolean isContentPaneHolder(Component c) {
        return c instanceof RootPaneContainer;
    }

    public static Component hn(Object... args) {
        HintList hints = new HintList();
        List<Component> comps = new ArrayList<Component>();
        // Add any hints
        for(Object arg : args) {
            if(arg instanceof String) {
                hints.addHints(parseHints((String) arg));
            } else if(arg instanceof Component) {
                comps.add((Component) arg);
            }
        }
        for(Component comp : comps) {
            setHints(comp, hints);
        }
        if(comps.size() == 0) {
            return null;
        }
        return comps.get(0);
    }


    // ////////
    // MISC //
    // ////////

    public static void match(Component argRef, Component... args) {
        for(Component arg : args) {
            arg.setMinimumSize(argRef.getMinimumSize());
            arg.setMaximumSize(argRef.getMaximumSize());
            arg.setPreferredSize(argRef.getPreferredSize());
        }
    }

    public static void grp(AbstractButton... btns) {
        ButtonGroup group = new ButtonGroup();
        for(AbstractButton btn : btns) {
            group.add(btn);
        }
    }

    public static String clr(Color clr) {
        return "[" + clr.getRed() + "," + clr.getGreen() + "," + clr.getBlue() + "]";
    }

    public static Color clr(String value) {
        Color clr;
        int[] nums = parseNumberList(value);
        try {
            if(nums.length == 3) {
                int red = nums[0];
                int green = nums[1];
                int blue = nums[2];
                clr = new Color(red, green, blue);
            } else if(nums.length == 1 || nums.length == 2) {
                int gray = nums[0];
                clr = new Color(gray, gray, gray);
            } else {
                Field field = Class.forName("java.awt.Color").getField(value);
                clr = (Color) field.get(null);
            }
        } catch(Exception ee) {
            clr = getColorFromHex(value);
        }
        if(clr == null) {
            throw new RuntimeException("Value '" + value + "' not recognized as a color");
        }
        return clr;
    }

    public static Color getColorFromHex(String code) {
        String clrPat = "^#?([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$";
        Pattern p = Pattern.compile(clrPat);
        Matcher m = p.matcher(code);
        if(m.find()) {
            String grp = m.group(1);
            int red, green, blue;
            if(grp.length() == 3) {
                red = Integer.parseInt("" + grp.charAt(0) + grp.charAt(0), 16);
                green = Integer.parseInt("" + grp.charAt(1) + grp.charAt(1), 16);
                blue = Integer.parseInt("" + grp.charAt(2) + grp.charAt(2), 16);
            } else {
                red = Integer.parseInt(grp.substring(0, 2), 16);
                green = Integer.parseInt(grp.substring(2, 4), 16);
                blue = Integer.parseInt(grp.substring(4, 6), 16);
            }
            return new Color(red, green, blue);
        }
        return null;
    }
}
