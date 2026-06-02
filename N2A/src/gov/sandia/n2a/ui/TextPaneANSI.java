/*
Copyright 2021-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui;

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
    public static final Color gray50     = new Color (128, 128, 128);
    public static final Color gray75     = new Color (192, 192, 192);
    public static final Color red50      = Utility.hueWithTrueLightness (0,        0.5f);
    public static final Color yellow70   = new Color (160, 160, 0);  // Difficult to make yellow look good on white background.
    public static final Color green50    = Utility.hueWithTrueLightness (2f   / 6, 0.5f);
    public static final Color cyan50     = Utility.hueWithTrueLightness (3f   / 6, 0.5f);
    public static final Color blue50     = Utility.hueWithTrueLightness (4f   / 6, 0.5f);
    public static final Color magenta50  = Utility.hueWithTrueLightness (5f   / 6, 0.5f);
    public static final Color standard[] = {Color.black, red50, green50, yellow70, blue50, magenta50, cyan50, gray75, gray50, Color.red, Color.green, Color.yellow, Color.blue, Color.magenta, Color.cyan, Color.white};

    public    int                lastLine;  // Character position just after last newline. Set by setText() and append().
    protected SimpleAttributeSet attributes = new SimpleAttributeSet ();
    protected boolean            inEscape;
    protected String             sequence;

    public void setText (String t)
    {
        super.setText ("");
        attributes.removeAttributes (attributes);  // Clear the attributes.
        inEscape = false;
        sequence = "";
        append (t);
    }

    public void append (String t)
    {
        int count = t.length ();
        for (int b = 0; b < count; b++)
        {
            int e = b;
            if (! inEscape)
            {
                e = t.indexOf (27, b);
                if (e < 0)  // No more escapes in [b,end], so copy the rest of t.
                {
                    String nextBlock = t.substring (b);
                    updateLastLine (nextBlock);
                    append (nextBlock, attributes);
                    break;
                }
                if (b < e)  // There is some text before escape.
                {
                    String nextBlock = t.substring (b, e);
                    updateLastLine (nextBlock);
                    append (nextBlock, attributes);
                }
                inEscape = true;
                e++;
            }

            // Find end of escape sequence.
            b = e;
            while (b < count)
            {
                char c = t.charAt (b);
                if (c >= 0x40  &&  c <= 0x7E  &&  c != '[') break;  // final byte. The test for '[' is not strictly correct, but it is never a terminator for any sequence we care about.
                b++;
            }
            if (b >= count)  // Reached end of string without finding final byte.
            {
                sequence += t.substring (e);
                break;  // escape sequence cut off by end of string
            }
            sequence += t.substring (e, b);  // Does not include initial ESC or final byte.
            if (sequence.startsWith ("[")  &&  t.charAt (b) == 'm')
            {
                String[] codes = sequence.substring (1).split (";");
                for (int i = 0; i < codes.length; i++)
                {
                    String code = codes[i];
                    switch (code)
                    {
                        case "":
                        case "0":
                            attributes.removeAttributes (attributes);
                            break;
                        case "1":
                        case "01":
                            attributes.addAttribute (StyleConstants.Bold, true);
                            break;
                        case "3":
                        case "03":
                            attributes.addAttribute (StyleConstants.Italic, true);
                            break;
                        case "4":
                        case "04":
                            attributes.addAttribute (StyleConstants.Underline, true);
                            break;
                        case "9":
                        case "09":
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
                        case "31":
                        case "32":
                        case "33":
                        case "34":
                        case "35":
                        case "36":
                        case "37":
                            attributes.addAttribute (StyleConstants.Foreground, standard[Integer.valueOf (code) - 30]);
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
                        case "41":
                        case "42":
                        case "43":
                        case "44":
                        case "45":
                        case "46":
                        case "47":
                            attributes.addAttribute (StyleConstants.Background, standard[Integer.valueOf (code) - 40]);
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
                        case "90":
                        case "91":
                        case "92":
                        case "93":
                        case "94":
                        case "95":
                        case "96":
                        case "97":
                            attributes.addAttribute (StyleConstants.Foreground, standard[Integer.valueOf (code) - 90 + 8]);  // +8 for "bright" values
                            break;
                        case "100":
                        case "101":
                        case "102":
                        case "103":
                        case "104":
                        case "105":
                        case "106":
                        case "107":
                            attributes.addAttribute (StyleConstants.Background, standard[Integer.valueOf (code) - 100 + 8]);
                            break;
                    }
                }
            }
            inEscape = false;
            sequence = "";
        }
    }

    public void updateLastLine (String nextBlock)
    {
        int pos = nextBlock.lastIndexOf ('\n');
        if (pos >= 0) lastLine = getDocument ().getLength () + pos + 1;
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
        if (c >= 232)
        {
            c = (int) Math.round ((c - 231) * 10.2);
            return new Color (c, c, c);
        }

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
