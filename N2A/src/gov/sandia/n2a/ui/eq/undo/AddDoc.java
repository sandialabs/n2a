/*
Copyright 2016,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.UUID;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.PanelModel;

public class AddDoc extends Undoable
{
    public    String  name;  // public so we can use the name in a potential Outsource operation
    protected boolean fromSearchPanel;
    protected int     index;  // 0 by default
    protected MNode   saved;
    public    boolean wasShowing = true;

    public AddDoc ()
    {
        this ("New Model", new MVolatile ());
    }

    public AddDoc (String name)
    {
        this (name, new MVolatile ());
    }

    public AddDoc (String name, MNode saved)
    {
        this.saved = saved;

        PanelModel mep = PanelModel.instance;
        fromSearchPanel = mep.panelSearch.list.isFocusOwner ();  // Otherwise, assume focus is on equation tree

        // Determine unique name in database
        MNode models = AppData.models;
        MNode existing = models.child (name);
        if (existing == null)
        {
            this.name = name;
        }
        else
        {
            name += " ";
            int suffix = 2;
            while (true)
            {
                if (existing.length () == 0) break;  // no children, so still a virgin
                this.name = name + suffix;
                existing = models.child (this.name);
                if (existing == null) break;
                suffix++;
            }
        }

        // Insert UUID, if given doc does not already have one.
        MNode uuid = saved.childOrCreate ("$metadata", "uuid");
        if (uuid.get ().isEmpty ()) uuid.set (generateUUID ());
    }

    public void undo ()
    {
        super.undo ();
        destroy (name, fromSearchPanel);
    }

    public static int destroy (String name, boolean fromSearchPanel)
    {
        MNode doc = AppData.models.child (name);
        PanelModel mep = PanelModel.instance;
        mep.panelEquations.recordDeleted (doc);
        mep.panelMRU.removeDoc (doc);
        int result = mep.panelSearch.removeDoc (doc);
        String uuid = doc.get ("$metadata", "uuid");
        if (! uuid.isEmpty ()) AppData.set (UUID.fromString (uuid), null);
        ((MDoc) doc).delete ();
        mep.panelSearch.lastSelection = Math.min (mep.panelSearch.model.size () - 1, result);
        if (fromSearchPanel)
        {
            mep.panelSearch.list.setSelectedIndex (mep.panelSearch.lastSelection);
            mep.panelSearch.list.requestFocusInWindow ();
        }
        else
        {
            mep.panelEquations.tree.requestFocusInWindow ();
        }
        return result;
    }

    public void redo ()
    {
        super.redo ();
        create (name, saved, 0, fromSearchPanel, wasShowing);
    }

    public static int create (String name, MNode saved, int index, boolean fromSearchPanel, boolean wasShowing)
    {
        MDoc doc = (MDoc) AppData.models.set (name, "");
        MPart fold = new MPart (doc);
        fold.merge (saved);  // By merging indirectly, through MPart, we get rid of nodes which duplicate inherited values.
        AppData.set (UUID.fromString (doc.get ("$metadata", "uuid")), doc);

        PanelModel mep = PanelModel.instance;
        mep.panelMRU.insertDoc (doc);
        int result = mep.panelSearch.insertDoc (doc, index);
        if (wasShowing) mep.panelEquations.loadRootFromDB (doc);
        mep.panelSearch.lastSelection = index;
        if (fromSearchPanel)
        {
            if (wasShowing) mep.panelEquations.tree.clearSelection ();
            mep.panelSearch.list.setSelectedIndex (result);
            mep.panelSearch.list.requestFocusInWindow ();
        }
        else
        {
            mep.panelEquations.tree.requestFocusInWindow ();
        }
        return result;
    }

 
    // UUID generation -------------------------------------------------------

    protected static int sequence;
    protected static byte[] mac;
    static
    {
        SecureRandom rng = new SecureRandom ();
        rng.setSeed (System.currentTimeMillis ());
        byte[] initialCount = new byte[2];
        rng.nextBytes (initialCount);
        sequence = (initialCount[1] << 8) + initialCount[0];

        boolean useRealAddress = false;  // Should be a user setting. Embedding someone's MAC address could be a privacy issue, so for now don't do it.
        if (useRealAddress)
        {
            try
            {
                Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces ();
                while (en.hasMoreElements ())
                {
                    NetworkInterface ni = en.nextElement ();
                    if (! ni.isLoopback ())
                    {
                        mac = ni.getHardwareAddress ();
                        if (mac != null  &&  mac.length == 6) break;
                    }
                }
            }
            catch (SocketException e)
            {
            }
        }
        if (mac == null  ||  mac.length != 6)
        {
            mac = new byte[6];
            rng.nextBytes (mac);
            mac[0] |= 0x01;  // set the multicast bit, to indicate fake address
        }
    }

    public static synchronized UUID generateUUID ()
    {
        byte[] result = new byte[16];

        long time = (System.currentTimeMillis () + 12219292800000l) * 10000;  // add offset from 1582/10/15 to 1970/1/1, then convert to units of 100ns
        result[3] = (byte)   time;
        result[2] = (byte)  (time >>= 8);
        result[1] = (byte)  (time >>= 8);
        result[0] = (byte)  (time >>= 8);
        result[5] = (byte)  (time >>= 8);
        result[4] = (byte)  (time >>= 8);
        result[7] = (byte)  (time >>= 8);
        result[6] = (byte) ((time >>= 8) & 0x0F | 0x10);  // UUID version 1

        sequence++;
        result[9] = (byte)  sequence;
        result[8] = (byte) (sequence >> 8 & 0x0F | 0x80);  // set variant to RFC4122

        for (int i = 0; i < 6; i++) result[15-i] = mac[i];

        return new UUID (byteArrayToLong (result, 0), byteArrayToLong (result, 8));
    }

    public static long byteArrayToLong (byte[] array, int start)
    {
        ByteBuffer buffer = ByteBuffer.allocate (Long.BYTES);
        for (int i = start; i < start + 8; i++) buffer.put (array[i]);
        buffer.flip ();
        return buffer.getLong ();
    }
}
