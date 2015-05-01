/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.data;

import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.ui.ensemble.domains.Parameter;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;

import java.util.ArrayList;
import java.util.List;

public class PartOrient implements Part {

    private NDoc source;

    public PartOrient(String name, String owner, String notes,
            String type, NDoc parentDoc) {
        source = new NDoc("gov.sandia.umf.n2a$Part");
        source.set("name", name);
        source.set("$owner", owner);
        source.set("notes", notes);
        source.set("type", type.toString());
        source.set("parent", parentDoc);
    }

    public PartOrient(NDoc nDoc) {
        source = nDoc;
    }

    @Override
    public String getName() {
        return source.get("name");
    }
    @Override
    public Part getParent() {
        return new PartOrient((NDoc) source.get("parent"));
    }
    @Override
    public void setEqs(List<NDoc> list) {
        source.set("eqs", list);
    }
    @Override
    public void setSelectedParameters (ParameterDomain domain)
    {
        List<NDoc> eqns = getEqs();
        for(Parameter param : domain.getParameters()) {
            System.out.println("   " + param);
            NDoc origEq = findEq(eqns, param);
            if (origEq != null) {
                eqns.remove(origEq);
            }
            // reconstruct equation string from parameter, add to list
            NDoc neq = new NDoc("gov.sandia.umf.n2a$Equation");
            neq.set("value", getEq(param));
            eqns.add(neq);
        }
        setEqs(eqns);
    }
    private String getEq(Parameter param) {
        StringBuilder result = new StringBuilder();
        String[] parts = param.getKey().toString().split(" ");
        result.append(parts[0]);
        int annStart = 1;
        if (parts.length>1 && parts[1].equals("{+=}")) {
            annStart = 2;
            result.append(" += " + param.getDefaultValue().toString());
        }
        else {
            result.append(" = " + param.getDefaultValue().toString());
        }
        for (int i=annStart; i<parts.length; i++) {
            result.append(" " + parts[i]);
        }
        return result.toString();
    }
    private NDoc findEq(List<NDoc> eqns, Parameter param)
    {
        for (NDoc eq : eqns)
        {
            try
            {
                EquationEntry ee = new EquationEntry (eq);
                if (ee.variable.nameString ().equals (param.getKey ().toString ())) return eq;
            }
            catch (Exception e)
            {
            }
        }
        return null;
    }
    @Override
    public List<NDoc> getEqs() {
        List<NDoc> eqs = source.getAndSetValid("eqs", new ArrayList<NDoc>(), List.class);
        return eqs;
    }
    @Override
    public NDoc getSource() {
        return source;
    }
    @Override
    public Part copy() {
        Part partCopy = new PartOrient(source.copy());
        List<NDoc> eqsDocs = new ArrayList<NDoc>();
        for(NDoc eq : getEqs()) {
            NDoc eqCopy = eq.copy();
            eqCopy.save();       // TODO: Necessary?  Maybe orient should handle this.
            eqsDocs.add(eqCopy);
        }
        partCopy.setEqs(eqsDocs);
        return partCopy;
    }
}