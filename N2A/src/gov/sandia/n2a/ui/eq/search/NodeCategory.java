/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.search;

@SuppressWarnings("serial")
public class NodeCategory extends NodeBase
{
    public NodeCategory (String name)
    {
        setUserObject (name);
    }

    @Override
    public boolean isLeaf ()
    {
        return false;
    }

    @Override
    public String getCategory ()
    {
        String result = "";
        for (String key : getKeyPath ()) result += "/" + key;
        if (result.isEmpty ()) return result;
        return result.substring (1);
    }
}
