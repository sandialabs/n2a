/*
Copyright 2019-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.vensim;

import gov.sandia.n2a.backend.vensim.Spreadsheet.Holder;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPart;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.linear.MatrixDense;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.undo.AddDoc;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

public class ImportJob
{
    Path                           sourceDir;  // The directory containing the file from which we are importing.
    MNode                          model              = new MVolatile ();
    String                         modelName          = "";
    List<String>                   identifiers        = new ArrayList<String> ();
    Map<String,SubscriptRange>     subscriptRanges    = new HashMap<String,SubscriptRange> ();
    Map<String,SubscriptVariable>  subscriptVariables = new HashMap<String,SubscriptVariable> ();
    List<SubscriptPart>            subscriptParts     = new ArrayList<SubscriptPart> ();
    Map<String,Lookup>             lookups            = new HashMap<String,Lookup> ();

    final SubscriptPart emptySubscriptPart = new SubscriptPart ();

    public static class UnitMapping
    {
        List<String> from = new ArrayList<String> ();
        String       to;
        public UnitMapping (String to, String... from)
        {
            this.to = to;
            for (String f : from) this.from.add (f);
        }
    }
    public static List<UnitMapping> unitMap = new ArrayList<UnitMapping> ();
    static
    {
        unitMap.add (new UnitMapping ("s",   "Seconds", "seconds", "Second", "second"));
        unitMap.add (new UnitMapping ("min", "Minutes", "minutes", "Minute", "minute"));
        unitMap.add (new UnitMapping ("h",   "Hours",   "hours",   "Hour",   "hour"));
        unitMap.add (new UnitMapping ("d",   "Days",    "days",    "Day",    "day"));
        unitMap.add (new UnitMapping ("wk",  "Weeks",   "weeks",   "Week",   "week"));
        unitMap.add (new UnitMapping ("mo",  "Months",  "months",  "Month",  "month"));
        unitMap.add (new UnitMapping ("a",   "Years",   "years",   "Year",   "year"));
        unitMap.add (new UnitMapping ("USD", "$"));
    }

    public void process (Path source)
    {
        sourceDir = source.getParent ();
        modelName = source.getFileName ().toString ();
        int index = modelName.lastIndexOf ('.');
        if (index > 0) modelName = modelName.substring (0, index);
        //modelName = NodePart.validIdentifierFrom (modelName);
        modelName = AddDoc.uniqueName (modelName);

        List<Equation> equations = new ArrayList<Equation> ();
        try (BufferedReader reader = Files.newBufferedReader (source))
        {
            // Get rid of header
            reader.readLine ();

            // Scan section and equation expressions
            String group = "";  // The default group
            int part = 0;
            String[] parts = new String[] {"", "", ""};
            String newLine = String.format ("%n");
            while (true)
            {
                String line = reader.readLine ();
                if (line == null) break;
                line = line.trim ();
                if (line.startsWith ("\\\\\\---///")  ||  line.startsWith (":GRAPH")) break;  // Terminate upon reaching sketch or graph data

                boolean equationDone = false;
                int pos = line.indexOf ("|");
                if (pos >= 0)
                {
                    equationDone = true;
                    line = line.substring (0, pos);
                }

                String[] pieces = line.split ("\\~", -1);
                for (int i = 0; i < pieces.length; i++)
                {
                    if (part < parts.length) parts[part] += pieces[i];
                    part++;
                }
                part--;  // This is a simple way to avoid counting the last piece.
                if (parts[part].endsWith ("\\"))
                {
                    parts[part] = parts[part].substring (0, parts[part].length () - 1);
                }
                else if (! equationDone)
                {
                    if (part < 2) parts[part] += " ";     // Non-comment parts should just flow onto a single line.
                    else          parts[part] += newLine; // Comment should get line breaks.
                }

                if (equationDone)
                {
                    part = 0;

                    for (int i = 0; i < 3; i++) parts[i] = parts[i].trim ();  // Get rid of leading spaces caused by blank lines.
                    boolean groupSet = false;
                    if (parts[0].startsWith ("****"))
                    {
                        parts[0] = parts[0].replace ("*", "").trim ();
                        group = parts[0];
                        groupSet = true;
                    }

                    if (! groupSet)
                    {
                        Equation e = new Equation (group, parts[0], parts[1], parts[2]);
                        if (! e.processSubscript ()  &&  ! e.processLookup ()) equations.add (e);
                    }
                    for (int i = 0; i < 3; i++) parts[i] = "";
                }
            }
        }
        catch (IOException e)
        {
        }

        // Process subscript ranges
        for (SubscriptRange    sr : subscriptRanges   .values ()) sr.expand ();
        for (SubscriptVariable sv : subscriptVariables.values ()) sv.resolve ();  // May add new subscript ranges.
        for (SubscriptRange    sr : subscriptRanges   .values ()) sr.determineSubrange ();
        for (SubscriptRange    sr : subscriptRanges   .values ()) sr.collapseSubrange ();
        boolean changed = true;
        while (changed)
        {
            changed = false;
            for (SubscriptRange sr : subscriptRanges.values ()) if (sr.associateMaps (null)) changed = true;
        }
        for (SubscriptRange sr : subscriptRanges.values ()) sr.electPart ();
        for (SubscriptRange sr : subscriptRanges.values ()) sr.process ();

        // Process lookup functions
        for (Lookup l : lookups.values ()) l.process ();

        // Process equations
        for (Equation e : equations) e.process ();
        new PrettyPrinter ().process (model);  // Parse and re-emit every line, to get rid of excess parens.
    }

    public class Equation
    {
        public List<Node> LHS;
        public String     assignment;
        public List<Node> RHS;
        public String     unit;
        public String     notes;

        public Equation (String group, String expression, String unit, String notes)
        {
            this.unit  = convertUnit (unit);
            this.notes = notes;

            List<Node> tokens = lex (expression);
            LHS = new ArrayList<Node> ();
            RHS = new ArrayList<Node> ();
            assignment = "";
            List<String> assignments = Arrays.asList ("=", "==", ":=", ":", "<->", ":IS:");
            int count = tokens.size ();
            int i;
            for (i = 0; i < count; i++)
            {
                String value = tokens.get (i).value;
                if (assignments.contains (value))
                {
                    assignment = value;
                    break;
                }
            }
            LHS = new ArrayList<Node> (tokens.subList (0, i));
            if (i < count) RHS = new ArrayList<Node> (tokens.subList (i + 1, count));

            parse (LHS);
            parse (RHS);

            // Special case for constants. Append unit.
            if (RHS.size () == 1)
            {
                Node n = RHS.get (0);
                if (n.type == Node.NUMBER) n.value += this.unit;
            }
        }

        public boolean processSubscript ()
        {
            // Label variables that have subscripts
            Node v = LHS.get (0);
            if (! assignment.isEmpty ()  &&  v.type == Node.SUBSCRIPT)
            {
                Bracket b = (Bracket) v;
                SubscriptVariable sv = subscriptVariables.get (b.value);
                if (sv == null)
                {
                    sv = new SubscriptVariable ();
                    sv.name = b.value;
                    subscriptVariables.put (sv.name, sv);
                }
                int i = 0;
                for (Node o : b.operands) sv.add (i++, o.value);
            }

            // Extract subscript declarations
            switch (assignment)
            {
                case ":":
                    SubscriptRange sr = new SubscriptRange ();
                    sr.name = concatenate (LHS);
                    subscriptRanges.put (sr.name, sr);
                    boolean mapping = false;
                    for (Node token : RHS)
                    {
                        String value = token.value;
                        if (value.equals (",")) continue;
                        if (value.equals ("->"))
                        {
                            mapping = true;
                            continue;
                        }
                        if (value.equals ("GET XLS SUBSCRIPT"))
                        {
                            Bracket xls = (Bracket) token;
                            sr.spreadsheet = xls;
                            String fileName = xls.operands.get (0).value;
                            try
                            {
                                Holder H = new Holder (sourceDir.resolve (fileName));
                                String sheetName = xls.operands.get (1).value;
                                if (H.wb.containsKey (sheetName))
                                {
                                    String first  = xls.operands.get (2).value;
                                    String last   = xls.operands.get (3).value;
                                    String prefix = xls.operands.get (4).value;

                                    String anchor = sheetName + "!" + first;
                                    H.parseA1 (first);
                                    int firstRow    = H.ar;
                                    int firstColumn = H.ac;
                                    H.parseA1 (last);
                                    int rows    = H.ar - firstRow    + 1;
                                    int columns = H.ac - firstColumn + 1;

                                    for (int r = 0; r < rows; r++)
                                    {
                                        for (int c = 0; c < columns; c++)
                                        {
                                            value = H.getString (anchor, r, c);
                                            value = firstName (NodePart.validIdentifierFrom (prefix + value));
                                            sr.memberOrder.add (value);
                                        }
                                    }
                                }
                            }
                            catch (Exception e) {e.printStackTrace ();}
                            continue;
                        }
                        if (mapping) sr.mapsTo     .add (value);
                        else         sr.memberOrder.add (value);
                    }
                    return true;

                case "<->":
                    String name = concatenate (LHS);
                    sr = subscriptRanges.get (name);
                    if (sr == null)
                    {
                        sr = new SubscriptRange ();
                        sr.name = name;
                        subscriptRanges.put (sr.name, sr);
                    }
                    for (Node token : RHS)
                    {
                        String value = token.value;
                        if (value.equals (",")) continue;
                        sr.mapsTo.add (value);
                    }
                    return true;
            }
            return false;
        }

        public boolean processLookup ()
        {
            if (! assignment.isEmpty ()) return false;
            Bracket b = (Bracket) LHS.get (0);
            Lookup l = new Lookup (b.value, b.operands);
            lookups.put (l.name, l);
            return true;
        }

        /**
            The primary function that converts the Vensim model into an MNode tree.
        **/
        public void process ()
        {
            String name  = concatenate (LHS);
            String value = concatenate (RHS);

            // Process equation
            switch (assignment)
            {
                case ":IS:":
                    model.set ("\"" + value + "\"", name);
                    break;

                case "=":
                case "==":
                case ":=":
                    // Trap simulation control variables
                    if (name.equals ("FINAL TIME"))
                    {
                        model.set ("$t<" + value, "$p");
                        break;
                    }
                    if (name.equals ("TIME STEP"))
                    {
                        model.set (value, "$t'");
                        break;
                    }
                    if (name.equals ("SAVEPER")  ||  name.equals ("INITIAL TIME")) break;

                    MNode node;
                    if (LHS.get (0).type == Node.SUBSCRIPT)
                    {
                        Bracket bracket = (Bracket) LHS.get (0);
                        SubscriptVariable sv = subscriptVariables.get (bracket.value);  // This should always return non-null.
                        SubscriptPart sp = sv.getPart ();
                        node = model.childOrCreate (sp.name, sv.name);
                        String condition = sv.condition (bracket);
                        render (RHS, sp, sv.name, condition);
                    }
                    else
                    {
                        node = model.childOrCreate (name);
                        render (RHS, emptySubscriptPart, name, "");
                    }
                    if (! notes.isEmpty ()) node.set (notes, "$meta", "notes");
                    break;
            }
        }

        public String concatenate (List<Node> tokens)
        {
            String result = "";
            for (Node t : tokens) result += t.toString ();
            return result;
        }

        public void render (List<Node> RHS, SubscriptPart subscriptPart, String key, String condition)
        {
            if (RHS.size () == 1)
            {
                Node a = RHS.get (0);
                if (a instanceof Bracket)
                {
                    String result = a.render (subscriptPart, true, key);
                    MNode node = subscriptPart.part.childOrEmpty (key);
                    if (result.isEmpty ()  &&  node.isEmpty ()) subscriptPart.part.clear (key);
                    else                                        subscriptPart.addCondition (key, result, condition);
                    return;
                }
            }

            String result = "";
            for (Node t : RHS) result += t.render (subscriptPart, false, key);
            subscriptPart.addCondition (key, result, condition);
        }
    }

    public class PrettyPrinter extends Renderer
    {
        public boolean render (Operator op)
        {
            if (op instanceof Constant)
            {
                Constant c = (Constant) op;
                if (c.value instanceof Scalar)
                {
                    result.append (c.unitValue.toString ());
                    return true;
                }
            }
            return false;
        }

        public String print (String line)
        {
            try
            {
                result.setLength (0);  // clear any previous output
                Operator.parse (line).render (this);
                return result.toString ();
            }
            catch (Exception e)
            {
                return line;
            }
        }

        public void process (MNode part)
        {
            for (MNode v : part)
            {
                if (v.key ().equals ("$meta")) continue;
                if (MPart.isPart (v))
                {
                    process (v);
                    continue;
                }

                Variable.ParsedValue pv = new Variable.ParsedValue (v.get ());
                if (! pv.expression.isEmpty ()) pv.expression = print (pv.expression);
                if (! pv.condition .isEmpty ()) pv.condition  = print (pv.condition);
                v.set (pv);

                for (MNode e : v)
                {
                    String oldKey = e.key ();
                    if (! oldKey.startsWith ("@")) continue;
                    String newValue = print (e.get ());
                    String newKey = oldKey;
                    if (! newValue.startsWith ("\"")) newKey = "@" + print (oldKey.substring (1));  // Only pretty-print the condition if this is not a string assignment. In the string-assignment case, we want to preserve spaces after the ==.
                    if (! oldKey.equals (newKey)) v.clear (oldKey);
                    v.set (newValue, newKey);
                }
            }
        }
    }

    public class SubscriptVariable
    {
        // All strings are normalized identifiers.
        public String               name;  // of variable
        public List<SubscriptRange> subscripts = new ArrayList<SubscriptRange> ();
        public SubscriptPart        part;
        public List<Integer>        matches;  // between our subscripts and ranges in part

        public void add (int position, String ID)
        {
            while (subscripts.size () <= position) subscripts.add (new SubscriptRange ());
            SubscriptRange sr = subscripts.get (position);
            if (! sr.memberSet.contains (ID))
            {
                sr.memberOrder.add (ID);
                sr.memberSet  .add (ID);
            }
        }

        public void resolve ()
        {
            for (int i = 0; i < subscripts.size (); i++)
            {
                SubscriptRange sr = subscripts.get (i);
                sr.expand ();
                sr = sr.resolve ();
                sr.part = sr;  // Tag as explicit part.
                subscripts.set (i, sr);
            }
        }

        /**
            Retrieves or creates part that should contain the given variable.
            Ignores order of multiple subscripts, as any combination can be handled with a single part.
            However, pays attention to multiple occurrences of the same subscript, as a part can have self-connection.
        **/
        public SubscriptPart getPart ()
        {
            if (part == null)
            {
                List<String> subscriptNames = new ArrayList<String> ();
                for (SubscriptRange sr : subscripts) subscriptNames.add (sr.name);
                part = find (subscriptNames);
            }
            if (part == null)
            {
                part = new SubscriptPart (subscripts);
                subscriptParts.add (part);
            }
            return part;
        }

        /**
            Takes a list of actual subscript values for this variable and generates a condition string to filter expressions.
            Assumes this variable appears in the context of its own home part.
            @param operands The string values of the subscripts, in original order.
        **/
        public String condition (Bracket b)
        {
            if (part == null) getPart ();
            if (matches == null)
            {
                List<String> subscriptNames = new ArrayList<String> ();
                for (SubscriptRange sr : subscripts) subscriptNames.add (sr.name);
                matches = part.match (subscriptNames);
            }

            String result = "";
            boolean needEndpoints = subscripts.size () > 1;
            for (int i = 0; i < b.operands.size (); i++)
            {
                String subscriptName = b.operands.get (i).value;
                int m = matches.get (i);

                String prefix = "";
                if (needEndpoints)
                {
                    char endpoint = 'A';
                    endpoint += m;
                    prefix += endpoint + ".";
                }

                SubscriptRange sr = subscriptRanges.get (subscriptName);  // Full range or subrange
                if (sr == null) sr = subscripts.get (m);  // Individual ID
                String condition = sr.condition (subscriptName, prefix);  // Handles all 3 cases (full range, subrange, ID).
                if (condition.isEmpty ()) continue;
                result += "&&" + condition;
            }
            if (! result.isEmpty ()) result = result.substring (2);
            return result;
        }

        public void dump ()
        {
            System.out.println (name);
            for (SubscriptRange sr : subscripts) System.out.println ("  " + sr.name);
        }
    }

    public class SubscriptRange
    {
        // All strings are normalized identifiers.
        public Bracket        spreadsheet;                            // Non-null only if this range was declared by a GET_XLS_SUBSCRIPT function. Node contains the original parsed function.
        public String         name;
        public SubscriptRange part;                                   // Which object to base the generated part on. Takes into account both subrange relationships and equivalence mappings.
        public SubscriptRange subrangeOf;                             // Only non-null if this is a subrange.
        public List<String>   subranges   = new ArrayList<String> (); // Names of all subscript ranges that are folded into this range. Not necessarily same as list of all known subranges.
        public List<String>   memberOrder = new ArrayList<String> ();
        public Set<String>    memberSet   = new HashSet<String> ();   // Populated by expand().
        public Set<String>    mapsTo      = new HashSet<String> ();
        public boolean        expanded;                               // Indicates this has already been processed to expand any included subranges.
        public SubscriptRange visited;

        public void expand ()
        {
            if (expanded) return;
            expanded = true;

            List<String> temp = memberOrder;
            memberOrder = new ArrayList<String> ();
            memberSet.clear ();
            for (String m : temp)
            {
                SubscriptRange sr = subscriptRanges.get (m);
                if (sr == null)  // individual ID
                {
                    if (memberSet.add (m)) memberOrder.add (m);
                }
                else  // subrange to include in this one
                {
                    if (name == null) name = sr.name;  // Special case for temporary subscript list used by SubscriptVariable. Treats first encountered subrange as our preferred range name for resolve().
                    sr.expand ();
                    for (String sm : sr.memberOrder) if (memberSet.add (sm)) memberOrder.add (sm);
                }
            }
        }

        /**
            Used by SubscriptVariable to find the official subscriptRange entry for its list of IDs.
            Creates a new SubscriptRange if none matches.
        **/
        public SubscriptRange resolve ()
        {
            // Check for direct match
            if (name != null)
            {
                SubscriptRange sr = subscriptRanges.get (name);
                if (sr != null  &&  sr.memberSet.equals (memberSet)) return sr;
            }

            // Try to find an equivalent entry
            for (SubscriptRange sr : subscriptRanges.values ())
            {
                if (sr.memberSet.equals (memberSet)) return sr;
            }

            // No equivalent entry, so add ourselves.
            name = "N2A_Part" + subscriptRanges.size ();
            subscriptRanges.put (name, this);
            return this;
        }

        public void determineSubrange ()
        {
            for (SubscriptRange sr : subscriptRanges.values ())
            {
                if (sr == this) continue;
                if (sr.memberSet.containsAll (memberSet))
                {
                    if (memberSet.size () == sr.memberSet.size ())  // members are exactly equal
                    {
                        if (memberOrder.equals (sr.memberOrder))  // exactly same order
                        {
                            mapsTo.add (sr.name);  // Treat as same part
                        }
                        else  // different order
                        {
                            // TODO: handle index mapping. Only one ordering of the IDs will correctly map to $index.
                            // Any references to alternate ranges in mappings will need to factor in an extra permutation.
                        }
                    }
                    else  // proper subset
                    {
                        subrangeOf = sr;
                    }
                    break;
                }
            }
        }

        public void collapseSubrange ()
        {
            if (subrangeOf == null) return;
            subrangeOf.collapseSubrange ();
            if (subrangeOf.subrangeOf != null) subrangeOf = subrangeOf.subrangeOf;
        }

        public boolean associateMaps (SubscriptRange from)
        {
            // Find sr to associate with, while preventing infinite recursion.
            SubscriptRange associate = null;
            SubscriptRange p = from;
            while (p != null)
            {
                if (p == this) return false;
                associate = p;
                p = p.visited;
            }
            visited = from;

            boolean result = false;
            for (String m : mapsTo)
            {
                SubscriptRange sr = subscriptRanges.get (m);
                if (sr.associateMaps (this)) result = true;
            }
            if (associate != null  &&  mapsTo.add (associate.name)) result = true;
            return result;
        }

        public void electPart ()
        {
            if (subrangeOf != null)
            {
                // If we are a substantial subrange, then fold our variables into the larger part.
                // This will result in some wasted storage/work, but might reduce the cost by avoiding connections.
                if ((float) memberSet.size () / subrangeOf.memberSet.size () > 0.5)
                {
                    part = subrangeOf.part;
                    subrangeOf.subranges.add (name);
                }
            }

            if (part == this) // Only explicit parts participate in map election.
            {
                for (String m : mapsTo)
                {
                    SubscriptRange sr = subscriptRanges.get (m);
                    sr.part = this;
                }
            }
        }

        public void process ()
        {
            if (part == null) return;

            if (spreadsheet == null)
            {
                int size = memberOrder.size ();
                if (size == 0) return;
                model.set (size, part.name, "$n");
                int digits = (int) Math.floor (Math.log10 (size)) + 1;

                if (subrangeOf == part)
                {
                    for (String m : memberOrder)
                    {
                        int i = part.memberOrder.indexOf (m);
                        String index = String.valueOf (i);
                        while (index.length () < digits) index = " " + index;
                        model.set ("\"" + m + "\"", part.name, name, "@$index==" + index);
                    }
                }
                else  // Either main part, or a map to it, so assume that indices are in simple order.
                {
                    int i = 0;
                    for (String m : memberOrder)
                    {
                        String index = String.valueOf (i++);
                        while (index.length () < digits) index = " " + index;
                        model.set ("\"" + m + "\"", part.name, name, "@$index==" + index);
                    }
                }
            }
            else
            {
                String fileName  = spreadsheet.operands.get (0).value;
                String sheetName = spreadsheet.operands.get (1).value;
                String firstCell = spreadsheet.operands.get (2).value;
                String lastCell  = spreadsheet.operands.get (3).value;
                String prefix    = spreadsheet.operands.get (4).value;

                fileName    = "\"" + fileName + "\"";
                String cell = "\"" + sheetName + "!" + firstCell + "\"";
                prefix      = "prefix=\"" + prefix + "\"";
                SpreadsheetCoordinate first = new SpreadsheetCoordinate (firstCell);
                SpreadsheetCoordinate last  = new SpreadsheetCoordinate (lastCell);
                boolean horizontal = first.row    == last.row;
                boolean vertical   = first.column == last.column;
                if (! horizontal  &&  ! vertical)
                {
                    Backend.err.get ().println ("ERROR: Can't handle 2D array of subscripts in spreadsheet");
                    throw new Backend.AbortRun ();
                }

                if (vertical)
                {
                    model.set ("spreadsheet(" + fileName + "," + cell + ",\"rowsInColumn\")",      part.name, "$n");
                    model.set ("spreadsheet(" + fileName + "," + cell + "," + prefix + ",$index)", part.name, name);
                }
                else
                {
                    model.set ("spreadsheet(" + fileName + "," + cell + ",\"columnsInRow\")",        part.name, "$n");
                    model.set ("spreadsheet(" + fileName + "," + cell + "," + prefix + ",0,$index)", part.name, name);
                }
            }
        }

        public String condition (String subscriptName, String prefix)
        {
            if (subscriptName.equals (name))
            {
                if (subrangeOf != part) return "";  // Full range, so no condition needed.
                // else fall through to the subrange condition generator below
            }
            else  // Individual ID  (or error)
            {
                int index = memberOrder.indexOf (subscriptName);
                if (index < 0) return "";
                return prefix + "$index==" + index;
            }

            // We are a proper subrange of "part"
            TreeSet<Integer> sorted = new TreeSet<Integer> ();
            for (String id : memberOrder)
            {
                int index = part.memberOrder.indexOf (id);
                sorted.add (index);
            }

            String result = "";
            int in = sorted.first ();
            int out = sorted.first ();
            for (int i : sorted)
            {
                if (i <= out + 1)  // contiguous
                {
                    out = i;  // advance out marker
                }
                else  // gap, so emit
                {
                    result += "||";
                    if (in == out) result += prefix + "$index==" + in;
                    else           result += prefix + "$index>=" + in + "&&" + prefix + "$index<=" + out;
                    in = i;
                    out = i;
                }
            }
            result += "||";
            if (in == out) result += prefix + "$index==" + in;
            else           result += prefix + "$index>=" + in + "&&" + prefix + "$index<=" + out;
            return result.substring (2);
        }

        /**
            Constrains equation to matching indices between a subrange and its containing range.
            We are the subrange, and the given range contains us.
            Assumes a binary connection where the range is "A" and the subrange is "B".
        **/
        public String conditionSubrange (SubscriptRange range)
        {
            // While order and mapping between subrange and its parent range can be arbitrary,
            // in general the subrange will be a contiguous block of the range, following the same order.
            String result = "";
            int start = -1;
            int in = -1;
            int out = -1;
            for (int i = 0; i < memberOrder.size (); i++)
            {
                String id = memberOrder.get (i);
                int index = range.memberOrder.indexOf (id);
                if (in < 0)
                {
                    start = i;
                    in = index;
                    out = index;
                }
                else if (index == out + 1)  // contiguous
                {
                    out = index;  // advance out marker
                }
                else  // gap, so emit
                {
                    result += "||";
                    if (in == out)
                    {
                        result += "A.$index==" + in + "&&B.$index==" + start;
                    }
                    else
                    {
                        result += "A.$index>=" + in + "&&" + "A.$index<=" + out + "&&A.$index==B.$index";
                        if      (start < in) result += "+" + (in - start);
                        else if (start > in) result += "-" + (start - in);
                    }
                    start = i;
                    in = index;
                    out = index;
                }
            }
            result += "||";
            if (in == out)
            {
                result += "A.$index==" + in + "&&B.$index==" + start;
            }
            else
            {
                result += "A.$index>=" + in + "&&" + "A.$index<=" + out + "&&A.$index==B.$index";
                if      (start < in) result += "+" + (in - start);
                else if (start > in) result += "-" + (start - in);
            }
            return result.substring (2);
        }

        public void dump ()
        {
            System.out.print (name);
            if (! mapsTo.isEmpty ())
            {
                System.out.print (" ->");
                for (String m : mapsTo) System.out.print (" " + m);
            }
            System.out.println ();
            System.out.println ("  part = " + part.name);
            if (subrangeOf != null) System.out.println ("  subrangeOf = " + subrangeOf.name);
            //for (String m : memberOrder) System.out.println ("  " + m);
        }
    }

    public class SubscriptPart
    {
        public String               name;
        public MNode                part;
        public List<SubscriptRange> subscripts;  // In the order they appear in the part name.

        public SubscriptPart ()  // for use only by emptySubscriptPart
        {
            name = "";
            subscripts = new ArrayList<SubscriptRange> ();
            part = model;
        }

        public SubscriptPart (List<SubscriptRange> subscripts)
        {
            this.subscripts = subscripts;

            name = "";
            for (SubscriptRange sr : subscripts) name += " X " + sr.part.name;
            name = name.substring (3);

            part = model.childOrCreate (name);
            if (subscripts.size () > 1)
            {
                char ref = 'A';
                for (SubscriptRange sr : subscripts) model.set (sr.part.name, name, ref++);
            }
        }

        public void addCondition (String key, String expression, String condition)
        {
            Variable.ParsedValue next = new Variable.ParsedValue (expression);  // extract combiner
            if (! condition.isEmpty ())
            {
                if (next.condition.isEmpty ()) next.condition = condition;
                else                           next.condition = "(" + next.condition + ")&&(" + condition + ")";
            }

            MNode v = part.child (key);
            if (v == null)
            {
                part.set (next, key);
                return;
            }

            int equationCount = Variable.equationCount (v);
            Variable.ParsedValue existing = new Variable.ParsedValue (v.get ());
            if (! existing.expression.isEmpty ()  ||  ! existing.condition.isEmpty ())
            {
                // Move existing value into multi-conditional list
                v.set (existing.expression, "@" + existing.condition);
                equationCount++;
            }

            if (equationCount == 0)
            {
                v.set (next);
            }
            else
            {
                v.set (next.combiner);
                if (! next.expression.isEmpty ()  ||  ! next.condition.isEmpty ()) v.set (next.expression, "@" + next.condition);
            }
        }

        public List<Integer> match (Bracket b)
        {
            List<String> names = new ArrayList<String> ();
            for (Node o : b.operands) names.add (o.value);
            return match (names);
        }

        /**
            Assigns position in this part to each given subscript name.
            If the returned list is shorter than the given list, then the matching failed for one or more names,
            and the list should only be used as a failure indicator.
        **/
        public List<Integer> match (List<String> names)
        {
            List<Integer> result = new ArrayList<Integer> ();  // List of part subscripts already claimed
            int count = subscripts.size ();
            for (String subscriptName : names)
            {
                // First scan for exact name match. This will help select the correct endpoint in a self-connection.
                int m;
                for (m = 0; m < count; m++)
                {
                    if (result.contains (m)) continue;
                    SubscriptRange sr = subscripts.get (m);
                    if (sr.name.equals (subscriptName)) break;
                }
                // If not found, try mappings.
                if (m >= count)
                {
                    for (m = 0; m < count; m++)
                    {
                        if (result.contains (m)) continue;
                        SubscriptRange sr = subscripts.get (m);
                        if (sr.mapsTo.contains (subscriptName)) break;     // another range that maps to sr
                        if (sr.subranges.contains (subscriptName)) break;  // subrange of sr
                        if (sr.memberSet.contains (subscriptName)) break;  // individual ID that is a member of sr
                    }
                }
                if (m < count) result.add (m);
            }
            return result;
        }

        public void dump ()
        {
            System.out.print (name + ":");
            for (SubscriptRange sr : subscripts) System.out.print (" " + sr.name);
            System.out.println ();
        }
    }

    public SubscriptPart find (List<String> subscriptNames)
    {
        int count = subscriptNames.size ();
        for (SubscriptPart sp : subscriptParts)
        {
            // Compare subscripts for equal contents, but not necessarily same order
            if (sp.subscripts.size () != count) continue;  // must have equal number of elements
            List<Integer> matches = sp.match (subscriptNames);
            if (matches.size () == count) return sp;
        }
        return null;
    }

    public SubscriptPart allocatePart (List<String> subscriptNames)
    {
        SubscriptPart result = find (subscriptNames);
        if (result == null)
        {
            List<SubscriptRange> subscripts = new ArrayList<SubscriptRange> ();
            for (String sn : subscriptNames)
            {
                SubscriptRange sr = subscriptRanges.get (sn);
                subscripts.add (sr.part);
            }
            result = new SubscriptPart (subscripts);
            subscriptParts.add (result);
        }
        return result;
    }

    public static class SpreadsheetCoordinate
    {
        public int row;
        public int column;  // integer equivalent of letter address

        public SpreadsheetCoordinate (String cellName)
        {
            cellName = cellName.replace ("\"", "").toUpperCase ();
            int pos = 0;
            int length = cellName.length ();
            for (; pos < length; pos++)
            {
                char c = cellName.charAt (pos);
                if (c < 'A') break;
                column = column * 26 + c - 'A' + 1;
            }
            column--;
            row = Integer.valueOf (cellName.substring (pos));
        }

        public int range (SpreadsheetCoordinate that)
        {
            int v = Math.abs (row    - that.row)    + 1;
            int h = Math.abs (column - that.column) + 1;
            return Math.max (h, v);
        }
    }

    public class Lookup
    {
        String name;
        Matrix A;

        public Lookup (String name, List<Node> points)
        {
            this.name = name;

            // Could sort the entries, but it seems like Vensim always stores them sorted.

            Node p0 = points.get (0);
            if (p0 instanceof Bracket)  // List of tuples
            {
                int count = points.size ();
                int first = 0;
                if (((Bracket) p0).LB.equals ("[")) first = 1;
                A = new MatrixDense (count - first, 2);
                int r = 0;
                for (int i = first; i < count; i++)
                {
                    Bracket b = (Bracket) points.get (i);
                    A.set (r, 0, Double.valueOf (b.operands.get (0).toString ()));  // Can't use Node.value, because negative numbers appear as an expression rather than simple number.
                    A.set (r, 1, Double.valueOf (b.operands.get (1).toString ()));
                    r++;
                }
            }
            else   // List of x values followed by list of y values.
            {
                int rows = points.size () / 2;
                A = new MatrixDense (rows, 2);
                for (int r = 0; r < rows; r++)
                {
                    A.set (r, 0, Double.valueOf (points.get (r     ).value));
                    A.set (r, 1, Double.valueOf (points.get (r+rows).value));
                }
            }
        }

        public void process ()
        {
            model.set (A, name);
        }
    }

    public static String uniqueKey (MNode parent, String key)
    {
        String result = key;
        int suffix = 2;
        while (parent.child (result) != null) result = key + suffix++;
        return result;
    }

    public class Node
    {
        String value;
        int    type;

        static final int OPERATOR   = 1;
        static final int IDENTIFIER = 2;
        static final int STRING     = 3;
        static final int NUMBER     = 4;
        static final int FUNCTION   = 5;
        static final int SUBSCRIPT  = 6;
        static final int EXPRESSION = 7;

        public Node (int type, String value)
        {
            this.type  = type;
            this.value = value;
        }

        public String render (SubscriptPart subscriptPart, boolean topLevel, String key)
        {
            if (type == STRING) return "\"" + value + "\"";
            if (type == OPERATOR  &&  value.equals ("=")) return "==";  // Special case for comparison operator.
            return value;
        }

        public String toString ()
        {
            return value;
        }
    }

    public class Identifier extends Node
    {
        String raw;  // the original value before converting to a valid identifier

        public Identifier (String value, String raw)
        {
            super (IDENTIFIER, value);
            this.raw = raw;
        }
    }

    // A series of nodes that should be treated as a single unit.
    public class Expression extends Node
    {
        List<Node> operands = new ArrayList<Node> ();

        public Expression (String value)
        {
            super (EXPRESSION, value);
        }

        public String render (SubscriptPart subscriptPart, boolean topLevel, String key)
        {
            String result = "";
            for (Node op : operands) result += op.render (subscriptPart, false, key);
            return result;
        }

        public void collectSubscripts (Map<String,Boolean> result)  // from a function, such as SUM
        {
            for (Node o : operands)
            {
                if (o.type == SUBSCRIPT)
                {
                    Bracket b = (Bracket) o;
                    for (Node bo : b.operands)
                    {
                        Identifier i = (Identifier) bo;  // Should always be an identifier, so no need to test.
                        boolean iterated = i.raw.endsWith ("!");
                        result.put (bo.value, iterated);
                    }
                }
                else if (o instanceof Expression)
                {
                    ((Expression) o).collectSubscripts (result);
                }
            }
        }

        public String toString ()
        {
            String result = "";
            for (Node op : operands) result += op.toString ();
            return result;
        }
    }

    public class Bracket extends Expression
    {
        int        first; // Position in the list of the first node that is part of this enclosure.
        String     LB;    // Character that opens the bracket.
        String     RB;    // Character that will close the bracket. Redundant with type, but convenient.
        Expression e;     // Currently accumulating expression, which will go into operands

        public Bracket (int first, String value, String LB)
        {
            super (value);
            this.first = first;
            this.LB    = LB;

            if (LB.equals ("[")) RB = "]";
            else                 RB = ")";

            if (! value.isEmpty ())
            {
                if (LB.equals ("[")) type = SUBSCRIPT;
                else                 type = FUNCTION;
            }
        }

        public void add (Node n)
        {
            if (e == null) e = new Expression ("");
            e.operands.add (n);
        }

        public void finishOperand ()
        {
            if (e == null) return;
            int count = e.operands.size ();
            if      (count == 1) operands.add (e.operands.get (0));
            else if (count >  1) operands.add (e);
            e = null;
        }

        public int apply (List<Node> tokens, int last)
        {
            finishOperand ();
            while (last > first) tokens.remove (last--);
            tokens.set (first, this);
            return first;
        }

        public String render (SubscriptPart subscriptPart, boolean topLevel, String key)
        {
            String  result = "";
            MNode   part   = subscriptPart.part;
            MNode   node   = part.childOrCreate (key);

            if (type == FUNCTION)
            {
                Lookup lookup = lookups.get (value);
                if (lookup != null)
                {
                    return result + "lookup(" + operands.get (0).render (subscriptPart, false, key) + "," + value + ")";
                }

                String keyPrime = key + "'";
                switch (value)
                {
                    case "LOOKUP AREA":
                        // TODO: extend Lookup class to take another scalar parameter. Also needs to handle units per Vensim documentation.
                        break;
                    case "LOOKUP BACKWARD":
                        return result + "lookup(" + operands.get (1).render (subscriptPart, false, key) + "," + operands.get (0).value + ",\"backward\")";
                    case "LOOKUP EXTRAPOLATE":
                        return result + "lookup(" + operands.get (1).render (subscriptPart, false, key) + "," + operands.get (0).value + ")";
                    case "LOOKUP FORWARD":
                        return result + "lookup(" + operands.get (1).render (subscriptPart, false, key) + "," + operands.get (0).value + ",\"forward\")";
                    case "LOOKUP INVERT":
                        return result + "lookup(" + operands.get (1).render (subscriptPart, false, key) + "," + operands.get (0).value + ",\"invert\")";
                    case "LOOKUP SLOPE":
                        return result + "lookup(" + operands.get (1).render (subscriptPart, false, key) + "," + operands.get (0).value + ",\"slope=" + operands.get(2).value + "\")";
                    case "WITH LOOKUP":
                    {
                        Bracket b = (Bracket) operands.get (1);
                        String keyAux = uniqueKey (part, key + " LOOKUP");
                        lookup = new Lookup (keyAux, b.operands);
                        return result + "lookup(" + operands.get (0).render (subscriptPart, false, key) + "," + keyAux + ")";
                    }
                    case "ABS":
                    case "COS":
                    case "EXP":
                    case "MAX":
                    case "MIN":
                    case "SIN":
                    case "SQRT":
                    case "TAN":
                        value = value.toLowerCase ();
                        break;
                    case "LN":
                        value = "log";
                        break;
                    case "LOG":
                    {    
                        Node a = operands.get (0);
                        Node b = operands.get (1);
                        operands.clear ();
                        a.render (subscriptPart, false, key);
                        b.render (subscriptPart, false, key);

                        result += "(";
                        value = "log";
                        operands.add (a);
                        result += toString ();
                        result += "/";
                        operands.set (0, b);
                        result += toString ();
                        result += ")";
                        return result;
                    }
                    case "ZIDZ":
                    {
                        String a = operands.get (0).render (subscriptPart, false, key);
                        String b = operands.get (1).render (subscriptPart, false, key);
                        if (topLevel)
                        {
                            node.set ("0",                       "@");
                            node.set ("(" + a + ")/(" + b + ")", "@" + b);
                            return result;
                        }
                        // Create auxiliary variable
                        String keyAux = uniqueKey (part, key + " ZIDZ");
                        part.set ("",                        keyAux);
                        part.set ("0",                       keyAux, "@");
                        part.set ("(" + a + ")/(" + b + ")", keyAux, "@" + b);
                        return keyAux;
                    }
                    case "IF THEN ELSE":
                    {
                        String a = operands.get (0).render (subscriptPart, false, key);
                        String b = operands.get (1).render (subscriptPart, false, key);
                        String c = operands.get (2).render (subscriptPart, false, key);
                        if (topLevel)
                        {
                            node.set (c, "@");
                            node.set (b, "@" + a);
                            return result;
                        }
                        String keyAux = uniqueKey (part, key + " IF");
                        part.set ("", keyAux);
                        part.set (c,  keyAux, "@");
                        part.set (b,  keyAux, "@" + a);
                        return keyAux;
                    }
                    case "PULSE":
                    {
                        String a = operands.get (0).render (subscriptPart, false, key);
                        String b = operands.get (1).render (subscriptPart, false, key);
                        result = "($t+$t'/2)>(" + a + ")&&($t+$t'/2)<((" + a + ")+(" + b + "))";
                        if (topLevel) return result;
                        String keyAux = uniqueKey (part, key + " PULSE");
                        part.set (result, keyAux);
                        return keyAux;
                    }
                    case "STEP":
                    {
                        String a = operands.get (0).render (subscriptPart, false, key);
                        String b = operands.get (1).render (subscriptPart, false, key);
                        result = "(($t+$t'/2)>" + b + ")*(" + a + ")";
                        if (topLevel) return result;
                        String keyAux = uniqueKey (part, key + " STEP");
                        part.set (result, keyAux);
                        return keyAux;
                    }
                    case "INTEG":
                    {
                        String rate = operands.get (0).render (subscriptPart, false, key);
                        String init = operands.get (1).render (subscriptPart, false, key);
                        if (init.equals ("0")) result = "";  // default initial condition is 0
                        else                   result = init + "@$init";
                        if (! rate.equals ("0")) part.set (rate, keyPrime);
                        return result;
                    }
                    case "INITIAL":
                    {
                        String variable = operands.get (0).render (subscriptPart, false, key);
                        return variable + "@$init";
                    }
                    case "SMOOTH":
                    case "SMOOTHI":
                    {
                        String input = operands.get (0).render (subscriptPart, false, key);
                        String delay = operands.get (1).render (subscriptPart, false, key);
                        String init  = input;
                        if (operands.size () >= 3) init = operands.get (2).render (subscriptPart, false, key);
                        if (topLevel)
                        {
                            part.set ("((" + input + ")-" + key + ")/(" + delay + ")", keyPrime);
                            return init + "@$init";
                        }
                        String keyAux = uniqueKey (part, key + " SMOOTH");
                        String keyAuxPrime = keyAux + "'";
                        part.set (init + "@$init",                                    keyAux);
                        part.set ("((" + input + ")-" + keyAux + ")/(" + delay + ")", keyAuxPrime);
                        return keyAux;
                    }
                    case "DELAY3":
                    {
                        String input = operands.get (0).render (subscriptPart, false, key);
                        String delay = operands.get (1).render (subscriptPart, false, key);
                        String init  = input;
                        if (operands.size () >= 3) init = operands.get (2).render (subscriptPart, false, key);
                        if (topLevel)
                        {
                            part.set ("(" + delay + ")/3",               key + " DL");
                            part.set (key + " DL*(" + init + ")@$init",  key + " LV3");
                            part.set (key + " DL*(" + init + ")@$init",  key + " LV2");
                            part.set (key + " DL*(" + init + ")@$init",  key + " LV1");
                            part.set (key + " LV2/" + key + " DL",       key + " RT2");
                            part.set (key + " LV1/" + key + " DL",       key + " RT1");
                            part.set (key + " RT2-" + key,               key + " LV3'");
                            part.set (key + " RT1-" + key + " RT2",      key + " LV2'");
                            part.set ("(" + input + ")-" + key + " RT1", key + " LV1'");
                            return key + " LV3/" + key + " DL";
                        }
                        String keyAux = uniqueKey (part, key + " DELAY3");
                        part.set (keyAux + " LV3/" + keyAux + " DL",    keyAux);
                        part.set ("(" + delay + ")/3",                  keyAux + " DL");
                        part.set (keyAux + " DL*(" + init + ")@$init",  keyAux + " LV3");
                        part.set (keyAux + " DL*(" + init + ")@$init",  keyAux + " LV2");
                        part.set (keyAux + " DL*(" + init + ")@$init",  keyAux + " LV1");
                        part.set (keyAux + " LV2/" + keyAux + " DL",    keyAux + " RT2");
                        part.set (keyAux + " LV1/" + keyAux + " DL",    keyAux + " RT1");
                        part.set (keyAux + " RT2-" + keyAux,            keyAux + " LV3'");
                        part.set (keyAux + " RT1-" + keyAux + " RT2",   keyAux + " LV2'");
                        part.set ("(" + input + ")-" + keyAux + " RT1", keyAux + " LV1'");
                        return keyAux;
                    }
                    case "GET XLS CONSTANTS":
                    {
                        String fileName  = operands.get (0).value;
                        String sheetName = operands.get (1).value;
                        String cell      = operands.get (2).value;

                        fileName = "\"" + fileName + "\"";
                        boolean transpose = false;
                        if (cell.endsWith ("*"))
                        {
                            transpose = true;
                            cell = cell.substring (0, cell.length () - 1);
                        }
                        cell = "\"" + sheetName + "!" + cell + "\"";

                        int indices = subscriptPart.subscripts.size ();
                        if (indices == 0)
                        {
                            return result + "spreadsheet(" + fileName + "," + cell + ")";
                        }
                        else if (indices == 1)
                        {
                            if (transpose) return result + "spreadsheet(" + fileName + "," + cell + ",$index)";
                            else           return result + "spreadsheet(" + fileName + "," + cell + ",0,$index)";
                        }
                        else  // 2 or more indices
                        {
                            // TODO: Handle ordering of indices, in cases where connection part does not match the original variable's ordering.
                            // TODO: When connection has more than 2 endpoints, indices should be the last two subscripts, not the first two.
                            // See index matching procedure in the type==SUBSCRIPT code below.
                            if (transpose) return result + "spreadsheet(" + fileName + "," + cell + ",B.$index,A.$index)";
                            else           return result + "spreadsheet(" + fileName + "," + cell + ",A.$index,B.$index)";
                        }
                    }
                    case "SUM":
                    case "PROD":
                    case "VMAX":
                    case "VMIN":
                    {
                        // Determine connection part.
                        Map<String,Boolean> iterates = new HashMap<String,Boolean> ();
                        collectSubscripts (iterates);
                        List<String> iterateNames = new ArrayList<String> (iterates.keySet ());
                        SubscriptPart sp = allocatePart (iterateNames);

                        if (topLevel) result = "";
                        else          result = key = uniqueKey (part, key + "_" + value);

                        // Prefix the target variable (held in "key") with the appropriate reference to its part, as seen from "sp".
                        if (part == model)  // In top-level model. Note that there are only two levels of parts.
                        {
                            key = "$up." + key;
                        }
                        else  // In "subscriptPart"
                        {
                            // Determine which endpoint of "sp" points to "subscriptPart".
                            char ref = 'A';
                            for (SubscriptRange sr : sp.subscripts)
                            {
                                // TODO: this only handles parts associated with single subscript.
                                if (sr.part.name.equals (subscriptPart.name))
                                {
                                    // Only allow subscripts that are not iterated (not tagged with exclamation point).
                                    Boolean iterated = iterates.get (sr.name);  // If this returns null, then the entry in "iterates" is probably a subrange that got mapped to its containing range.
                                    if (iterated == null  ||  ! iterated) break;
                                }
                                ref++;
                            }
                            key = ref + "." + key;
                        }

                        String reduction = "+";
                        if      (value.equals ("PROD")) reduction = "*";
                        else if (value.equals ("VMAX")) reduction = ">";
                        else if (value.equals ("VMIN")) reduction = "<";
                        for (Node o : operands) reduction += o.render (sp, false, key);
                        sp.part.set (reduction, key);

                        return result;
                    }
                }
            }
            else if (type == SUBSCRIPT)
            {
                // This is a subscripted variable on the RHS which is not part of a reduction (SUM, PROD, ...).
                // Thus, every subscript must match a range on the LHS (either equal, subrange, or member ID),
                // or else be an arbitrary individual ID. Cases:
                // 1) All RHS match LHS
                //    1.1) All LHS are matched -- Member of the current part.
                //    1.2) Some LHS are not matched -- Member of a connection endpoint that is referenced multiple times by the equation, each time addressing a different LHS instance.
                //         1.2.1) Exactly one RHS is matched -- Reference to simple endpoint.
                //         1.2.2) More than one RHS is matched -- Requires complex endpoint structure. Not clear how to proceed.
                // 2) Some RHS don't match LHS
                //    2.1) Non-matching subscripts are not part of LHS -- Must be individual IDs
                //         2.1.1) LHS has no subscripts -- Top part, so this is a down-reference. Must convert to conditional up-reference in RHS part.
                //         2.1.2) LHS has subscripts -- Needs more thought
                //    2.2) Non-matching subscripts are subranges of LHS which were made into a separate part -- Create connection between LHS part and RHS part to conditionally copy values.

                int countLHS = subscriptPart.subscripts.size ();
                if (countLHS == 0)  // Case 2.1.1
                {
                    // The variable is a down-reference to a contained part, which is not allowed, so convert to up-reference.
                    // Try to find the matching sub-part.
                    SubscriptVariable sv = subscriptVariables.get (value);  // This should always return non-null.
                    SubscriptPart sp = sv.getPart ();

                    if (topLevel) result = "";
                    else          result = key = uniqueKey (part, key + " SUM");
                    String reduction = value;  // Don't use actual reduction like "+", because the subscripts should be strictly individual IDs, not ranges.
                    String condition = sv.condition (this);
                    if (! condition.isEmpty ()) reduction += "@" + condition;
                    sp.part.set (reduction, "$up." + key);

                    return result;
                }
                else  // Part has subscripts.
                {
                    // Match each operand to a unique subscript in the part.
                    List<Integer> matches = subscriptPart.match (this);
                    int countMatch = matches.size ();
                    int countRHS = operands.size ();
                    if (countMatch == countRHS)  // All operands were matched.
                    {
                        if (countRHS == countLHS) return value;  // Case 1.1

                        if (countRHS == 1)  // Case 1.2.1
                        {
                            // Emit a reference to the connection endpoint.
                            char ref = 'A';
                            ref += matches.get (0);
                            return ref + "." + value;
                        }

                        // Case 1.2.2 -- Not implemented
                    }
                    else  // countMatch < countRHS  (Case 2)
                    {
                        // Check if any RHS subscript is an individual ID
                        boolean hasIDs = false;
                        for (Node op : operands)
                        {
                            if (subscriptRanges.get (op.value) == null)
                            {
                                hasIDs = true;
                                break;
                            }
                        }
                        if (hasIDs)  // Case 2.1.2
                        {
                            // Not implemented
                        }
                        else  // Case 2.2
                        {
                            // Create a connection between the LHS part and the RHS part.
                            SubscriptVariable sv = subscriptVariables.get (value);  // This should always return non-null.
                            if (subscriptPart.subscripts.size () == 1  &&  sv.subscripts.size () == 1)
                            {
                                List<String> subscriptNames = new ArrayList<String> ();
                                subscriptNames.add (subscriptPart.subscripts.get (0).name);
                                subscriptNames.add (sv.subscripts.get (0).name);
                                SubscriptPart sp = allocatePart (subscriptNames);

                                SubscriptRange A = sp.subscripts.get (0);
                                SubscriptRange B = subscriptRanges.get (operands.get (0).value);  // Use most exact subrange.
                                String condition = B.conditionSubrange (A);

                                if (topLevel) result = "";
                                else          result = key = uniqueKey (part, key + " SUM");
                                sp.addCondition ("A." + key, "B." + value, condition);
                                sp.part.set ("0", "$p", "@$connect");
                                sp.part.set ("1", "$p", "@$connect&&(" + condition + ")");
                                return result;
                            }
                            else
                            {
                                // need more general solution
                            }
                        }
                    }
                }
            }

            // Fallback
            String temp = "";
            for (Node op : operands) temp += "," + op.render (subscriptPart, false, key);
            temp = temp.substring (1);
            return result + value + LB + temp + RB;
        }

        /**
            Assuming this is a SUBSCRIPT, assemble the names into a list.
        **/
        public List<String> subscriptNames ()
        {
            List<String> subscriptNames = new ArrayList<String> ();
            for (Node o : operands) subscriptNames.add (o.value);
            return subscriptNames;
        }

        public String toString ()
        {
            String result = "";
            for (Node op : operands) result += "," + op.toString ();
            result = result.substring (1);
            return value + LB + result + RB;
        }
    }

    public void triage (String value, List<Node> nodes)
    {
        value = value.trim ();
        if (value.isEmpty ()) return;

        char c = value.charAt (0);
        if ('0' <= c  &&  c <= '9')
        {
            nodes.add (new Node (Node.NUMBER, value));
        }
        else
        {
            String raw = value;
            if (value.endsWith ("!")) value = value.substring (0, value.length () - 1);
            Node result = new Identifier (firstName (NodePart.validIdentifierFrom (value)), raw);
            nodes.add (result);
        }
    }

    /**
        Convert identifiers to valid form, while leaving operators in place.
    **/
    public List<Node> lex (String value)
    {
        List<Node> result = new ArrayList<Node> ();
        String identifier = "";
        String quote      = "";
        int length = value.length ();
        for (int i = 0; i < length; i++)
        {
            String c = value.substring (i, i+1);
            if (   c.equals ("\"")  &&  ! quote.equals ("'")
                || c.equals ("'")   &&  ! quote.equals ("\""))
            {
                if (quote.isEmpty ())
                {
                    quote = c;
                    triage (identifier, result);
                }
                else
                {
                    if (quote.equals ("'"))
                    {
                        result.add (new Node (Node.STRING, identifier));
                    }
                    else
                    {
                        // A quoted identifier must be taken literally, so don't try to remove a trailing exclamation point!
                        Node n = new Identifier (firstName (NodePart.validIdentifierFrom (identifier)), identifier);
                        result.add (n);
                    }
                    quote = "";
                }
                identifier = "";
            }
            else if (quote.isEmpty ()  &&  "+-*/()[]<>^=:,".contains (c))
            {
                if (i < length - 1)
                {
                    String next = value.substring (i+1, i+2);
                    switch (c)
                    {
                        case "=":
                            if (next.equals ("="))
                            {
                                c = "==";
                                i++;
                            }
                            break;
                        case "-":
                            if (next.equals (">"))
                            {
                                c = "->";
                                i++;
                            }
                            break;
                        case "<":
                            if (next.equals ("="))
                            {
                                c = "<=";
                                i++;
                            }
                            else if (next.equals (">"))
                            {
                                c = "!=";
                                i++;
                            }
                            else if (next.equals ("-"))
                            {
                                if (i < length - 2  &&  value.substring (i+2, i+3).equals (">"))
                                {
                                    c = "<->";
                                    i += 2;
                                }
                            }
                            break;
                        case ">":
                            if (next.equals ("="))
                            {
                                c = ">=";
                                i++;
                            }
                            break;
                        case ":":
                            if (next.equals ("="))
                            {
                                c = ":=";
                                i++;
                            }
                            else
                            {
                                int pos = value.indexOf (':', i + 2);
                                if (pos > 0)
                                {
                                    next += value.substring (i + 2, pos);
                                    if (next.equals ("AND"))
                                    {
                                        c = "&&";
                                        i += 4;
                                    }
                                    else if (next.equals ("NOT"))
                                    {
                                        c = "!";
                                        i += 4;
                                    }
                                    else if (next.equals ("OR"))
                                    {
                                        c = "||";
                                        i += 3;
                                    }
                                    else if (next.equals ("IS"))
                                    {
                                        c = ":IS:";
                                        i += 3;
                                    }
                                }
                            }
                            break;
                    }
                }
                triage (identifier, result);
                identifier = "";
                result.add (new Node (Node.OPERATOR, c));
            }
            else
            {
                identifier += c;
            }
        }
        triage (identifier, result);
        return result;
    }

    public void parse (List<Node> tokens)
    {
        // Extract subscripts
        Stack<Bracket> stack = new Stack<Bracket> ();
        for (int i = 0; i < tokens.size (); i++)
        {
            Node n = tokens.get (i);
            switch (n.value)
            {
                case "[":
                case "(":
                    Bracket s;
                    int start = i - 1;
                    Node m = null;
                    if (start >= 0) m = tokens.get (start);
                    if (m != null  &&  m.type == Node.IDENTIFIER)
                    {
                        s = new Bracket (start, m.value, n.value);
                        if (! stack.isEmpty ())
                        {
                            Expression e = stack.peek ().e;
                            e.operands.remove (e.operands.size () - 1);
                        }
                    }
                    else
                    {
                        s = new Bracket (i, "", n.value);
                    }
                    stack.push (s);
                    break;
                case "]":
                case ")":
                    while (! stack.isEmpty ())
                    {
                        s = stack.pop ();
                        i = s.apply (tokens, i);
                        if (! stack.isEmpty ()) stack.peek ().add (s);
                        if (s.RB.equals (n.value)) break;
                    }
                    break;
                case ",":
                    if (! stack.isEmpty ()) stack.peek ().finishOperand ();
                    break;
                default:
                    if (! stack.isEmpty ()) stack.peek ().add (n);
            }
        }
        while (! stack.isEmpty ())
        {
            Bracket s = stack.pop ();
            s.apply (tokens, tokens.size () - 1);
            if (! stack.isEmpty ()) stack.peek ().add (s);
        }
    }

    public String firstName (String name)
    {
        for (String n : identifiers) if (n.equalsIgnoreCase (name)) return n;
        identifiers.add (name);
        return name;
    }

    /**
        Takes common (ill-defined) Vensim units and returns UCUM equivalents.
    **/
    public String convertUnit (String unit)
    {
        unit = unit.replace ("[0,?]", "");
        for (UnitMapping m : unitMap)
        {
            for (String f : m.from) unit = unit.replace (f, m.to);
        }

        try
        {
            return UnitValue.safeUnit (UnitValue.UCUM.parse (unit));
        }
        catch (Exception e) {}

        // Hack for complex units.
        // The assumption is that the unit consists of an arbitrary unit over time.
        // Since we don't process arbitrary units, we throw that part away and keep time.
        int pos = unit.indexOf ('/');
        if (pos >= 0)
        {
            unit = unit.substring (pos);  // includes the slash
            try
            {
                return UnitValue.safeUnit (UnitValue.UCUM.parse (unit));
            }
            catch (Exception e) {}
        }

        // At this point, the unit is beyond hope.
        return "";
    }
}
