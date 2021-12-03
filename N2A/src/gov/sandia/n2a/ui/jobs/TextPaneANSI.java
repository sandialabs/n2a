/*
Copyright 2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.Color;

import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.EditorKit;
import javax.swing.text.Element;
import javax.swing.text.ParagraphView;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

/**
    Allows user to set text that contains ANSI escape sequences and get colored output.
**/
@SuppressWarnings("serial")
public class TextPaneANSI extends JTextPane
{
    public static final Color gray50     = Color.getHSBColor (0,      0, 0.5f);
    public static final Color gray75     = Color.getHSBColor (0,      0, 0.75f);
    public static final Color red50      = Color.getHSBColor (0,      1, 0.5f);
    public static final Color yellow50   = Color.getHSBColor (1f / 6, 1, 0.5f);
    public static final Color green50    = Color.getHSBColor (2f / 6, 1, 0.5f);
    public static final Color cyan50     = Color.getHSBColor (3f / 6, 1, 0.5f);
    public static final Color blue50     = Color.getHSBColor (4f / 6, 1, 0.5f);
    public static final Color magenta50  = Color.getHSBColor (5f / 6, 1, 0.5f);
    public static final Color yellow80   = Color.getHSBColor (1f / 6, 1, 0.8f);
    public static final Color red100     = Color.getHSBColor (0,      1, 1);
    public static final Color yellow100  = Color.getHSBColor (1f / 6, 1, 1);
    public static final Color green100   = Color.getHSBColor (2f / 6, 1, 1);
    public static final Color cyan100    = Color.getHSBColor (3f / 6, 1, 1);
    public static final Color blue100    = Color.getHSBColor (4f / 6, 1, 1);
    public static final Color magenta100 = Color.getHSBColor (5f / 6, 1, 1);
    public static final Color standard[] = {Color.black, red50, green50, yellow50, blue50, magenta50, cyan50, gray75, gray50, red100, green100, yellow100, blue100, magenta100, cyan100, Color.white};

    public void setText (String t)
    {
        super.setText ("");
        append (t);
    }

    public void append (String t)
    {
        int count = t.length ();
        SimpleAttributeSet attributes = new SimpleAttributeSet ();
        for (int b = 0; b < count; b++)
        {
            int e = t.indexOf (27, b);
            if (e < 0)
            {
                append (t.substring (b), attributes);
                break;
            }
            if (b < e) append (t.substring (b, e), attributes);  // There is some text to append.
            if (e >= count) break;  // no more text left

            // Find end of escape sequence.
            e++;
            b = t.indexOf ('m', e);
            if (b < 0) break;  // escape sequence cut off by end of string
            String sequence = t.substring (e, b);  // Does not include initial ESC or ending m.
            if (sequence.startsWith ("["))
            {
                String[] codes = sequence.substring (1).split (";");
                for (int i = 0; i < codes.length; i++)
                {
                    String code = codes[i];
                    switch (code)
                    {
                        case "0":
                            attributes.removeAttributes (attributes);
                            break;
                        case "1":
                            attributes.addAttribute (StyleConstants.Bold, true);
                            break;
                        case "3":
                            attributes.addAttribute (StyleConstants.Italic, true);
                            break;
                        case "4":
                            attributes.addAttribute (StyleConstants.Underline, true);
                            break;
                        case "9":
                            attributes.addAttribute (StyleConstants.StrikeThrough, true);
                            break;
                        case "22":
                            attributes.removeAttribute (StyleConstants.Bold);
                            break;
                        case "23":
                            attributes.removeAttribute (StyleConstants.Italic);
                            break;
                        case "24":
                            attributes.removeAttribute (StyleConstants.Underline);
                            break;
                        case "29":
                            attributes.removeAttribute (StyleConstants.StrikeThrough);
                            break;
                        case "30":
                            attributes.addAttribute (StyleConstants.Foreground, Color.black);
                            break;
                        case "31":
                            attributes.addAttribute (StyleConstants.Foreground, Color.red);
                            break;
                        case "32":
                            attributes.addAttribute (StyleConstants.Foreground, Color.green);
                            break;
                        case "33":
                            attributes.addAttribute (StyleConstants.Foreground, yellow80);
                            break;
                        case "34":
                            attributes.addAttribute (StyleConstants.Foreground, Color.blue);
                            break;
                        case "35":
                            attributes.addAttribute (StyleConstants.Foreground, Color.magenta);
                            break;
                        case "36":
                            attributes.addAttribute (StyleConstants.Foreground, Color.cyan);
                            break;
                        case "37":
                            attributes.addAttribute (StyleConstants.Foreground, Color.white);
                            break;
                        case "38":
                            i++;
                            if (codes[i] == "2")
                            {
                                i++;
                                Color c = interpretRGB (codes, i);
                                attributes.addAttribute (StyleConstants.Foreground, c);
                                i += 2;
                            }
                            else if (codes[i] == "5")
                            {
                                i++;
                                Color c = interpret256 (codes[i]);
                                attributes.addAttribute (StyleConstants.Foreground, c);
                            }
                            break;
                        case "39":
                            attributes.removeAttribute (StyleConstants.Foreground);
                            break;
                        case "40":
                            attributes.addAttribute (StyleConstants.Background, Color.black);
                            break;
                        case "41":
                            attributes.addAttribute (StyleConstants.Background, Color.red);
                            break;
                        case "42":
                            attributes.addAttribute (StyleConstants.Background, Color.green);
                            break;
                        case "43":
                            attributes.addAttribute (StyleConstants.Background, Color.yellow);
                            break;
                        case "44":
                            attributes.addAttribute (StyleConstants.Background, Color.blue);
                            break;
                        case "45":
                            attributes.addAttribute (StyleConstants.Background, Color.magenta);
                            break;
                        case "46":
                            attributes.addAttribute (StyleConstants.Background, Color.cyan);
                            break;
                        case "47":
                            attributes.addAttribute (StyleConstants.Background, Color.white);
                            break;
                        case "48":
                            i++;
                            if (codes[i] == "2")
                            {
                                i++;
                                Color c = interpretRGB (codes, i);
                                attributes.addAttribute (StyleConstants.Background, c);
                                i += 2;
                            }
                            else if (codes[i] == "5")
                            {
                                i++;
                                Color c = interpret256 (codes[i]);
                                attributes.addAttribute (StyleConstants.Background, c);
                            }
                            break;
                        case "49":
                            attributes.removeAttribute (StyleConstants.Background);
                            break;
                    }
                }
            }
        }
    }

    public Color interpretRGB (String[] codes, int i)
    {
        int r = Integer.valueOf (codes[i++]);
        int g = Integer.valueOf (codes[i++]);
        int b = Integer.valueOf (codes[i]);
        return new Color (r, g, b);
    }

    public Color interpret256 (String code)
    {
        int c = Integer.valueOf (code);
        if (c < 16) return standard[c];
        if (c >= 232) return Color.getHSBColor (0, 0, (c - 232) / 32f);

        c -= 16;
        int r = c / 36;
        c %= 36;
        int g = c / 6;
        int b = c % 6;

        return new Color (r / 5f, g / 5f, b / 5f);
    }

    public void append (String s, AttributeSet attributes)
    {
        int length = getDocument ().getLength ();
        try {getDocument ().insertString (length, s, attributes);}
        catch (BadLocationException e) {}
    }

    // =======================================================================
    // The rest of this code is here to do one simple thing: suppress line wrapping.

    public EditorKit createDefaultEditorKit ()
    {
        return new WrapEditorKit ();
    }

    public static class WrapEditorKit extends StyledEditorKit implements ViewFactory
    {
        ViewFactory defaultFactory;

        public ViewFactory getViewFactory ()
        {
            defaultFactory = super.getViewFactory ();
            return this;
        }

        public View create (Element elem)
        {
            View result = defaultFactory.create (elem);
            if (result instanceof ParagraphView) return new NoWrapParagraphView (elem);
            return result;
        }
    }

    public static class NoWrapParagraphView extends ParagraphView
    {
        public NoWrapParagraphView (Element elem)
        {
            super (elem);
        }

        public void layout (int width, int height)
        {
            super.layout (Integer.MAX_VALUE, height);
        }

        public float getMinimumSpan (int axis)
        {
            return super.getPreferredSpan (axis);
        }
    }
}
