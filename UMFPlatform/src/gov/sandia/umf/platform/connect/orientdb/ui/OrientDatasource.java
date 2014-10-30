/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.ui;

import gov.sandia.umf.platform.UMF;
import gov.sandia.umf.platform.plugins.UMFPluginManager;
import gov.sandia.umf.platform.plugins.extpoints.RecordHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import replete.util.StringUtil;

import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.command.OCommandOutputListener;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.ODatabaseThreadLocalFactory;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentPool;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.record.ODatabaseRecord;
import com.orientechnologies.orient.core.db.tool.ODatabaseImport;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

public class OrientDatasource
{
    private static final String[] systemClasses = new String[]
    {
        "ORestricted",
        "ORIDs",
        "OIdentity",
        "OUser",
        "ORole",
        "OFunction",
        "ODocumentWrapper",
        "OGraphEdge",       // System classes in relation to
        "OGraphVertex"      // a document database at least.
    };

    public class DBFactory implements ODatabaseThreadLocalFactory
    {
        public OrientConnectDetails details;

        public DBFactory (OrientConnectDetails details)
        {
            this.details = details;
        }

        public ODatabaseRecord getThreadDatabase ()
        {
            //return ODatabaseDocumentPool.global ().acquire (details.location, details.user, details.password);
            Object result = ODatabaseDocumentPool.global ().acquire (details.location, details.user, details.password);
            return (ODatabaseRecord) result;
        }
    }

    public OrientDatasource (OrientConnectDetails details)
    {
        System.out.println ("OrientDatasource ctor");
        if (! details.location.contains ("local:")  &&  ! details.location.startsWith ("remote:"))
        {
            details.location = "local:" + details.location;  // Could be smarter
        }

        if (details.location.contains ("local:"))  // matches either "local:" or "plocal:"
        {
            ODatabaseDocumentTx db = new ODatabaseDocumentTx (details.location);
        	if (! db.exists ())
        	{
                db.create ();

                OCommandOutputListener listener = new OCommandOutputListener ()
                {
                    public void onMessage (String arg0)
                    {
                        System.out.println (arg0);
                    }
                };

                try
                {
                    InputStream stream = UMF.class.getResource ("initialDB").openStream ();
                    ODatabaseImport importer = new ODatabaseImport (db, stream, listener);
                    importer.importDatabase ();
                }
                catch (IOException error)
                {
                    System.out.println (error.toString ());
                }
        	}
        	db.close ();
        }

        Orient.instance ().registerThreadDatabaseFactory (new DBFactory (details));
        ODatabaseRecordThreadLocal.INSTANCE.set (null);
    }

    public static ODatabaseDocumentTx getDB ()
    {
        ODatabaseRecord db = ODatabaseRecordThreadLocal.INSTANCE.get ();
        if (! (db instanceof ODatabaseDocumentTx))
        {
            ODatabaseRecordThreadLocal.INSTANCE.set (null);
            // At this point, our own factory should provide the correct DB type, so don't bother re-checking.
            db = ODatabaseRecordThreadLocal.INSTANCE.get ();
        }
        return (ODatabaseDocumentTx) db;
    }

    public static void releaseDB ()
    {
        if (ODatabaseRecordThreadLocal.INSTANCE.isDefined ())
        {
            ODatabaseRecord db = ODatabaseRecordThreadLocal.INSTANCE.get ();
            if (db instanceof ODatabaseDocumentTx) db.close ();
        }
    }

    public boolean isConnected ()
    {
        ODatabaseDocumentTx db = getDB ();
        return db != null  &&  ! db.isClosed ();
    }

    public void disconnect ()
    {
        Orient.instance ().registerThreadDatabaseFactory (null);
        ODatabaseRecordThreadLocal.INSTANCE.set (null);
    }

    public List<String> getClassNames ()
    {
        ODatabaseDocumentTx db = getDB ();
        List<String> ents = new ArrayList<String> ();
        List<String> sc = Arrays.asList (systemClasses);
        for (OClass clazz : db.getMetadata ().getSchema ().getClasses ())
        {
            String name = clazz.getName ();
            if (! sc.contains (name)) ents.add (clazz.getName ());
        }
        db.close ();
        return ents;
    }

    public List<OClass> getUserClasses ()
    {
        ODatabaseDocumentTx db = getDB ();
        List<OClass> ents = new ArrayList<OClass> ();
        List<String> sc = Arrays.asList (systemClasses);
        for (OClass clazz : db.getMetadata ().getSchema ().getClasses ())
        {
            String name = clazz.getName ();
            if (! sc.contains (name)) ents.add (clazz);
        }
        db.close ();
        return ents;
    }

    public List<NDoc> search (String searchText)
    {
        ODatabaseDocumentTx db = getDB ();
        List<NDoc> results = new ArrayList<NDoc>();
        searchText = searchText.trim ();
        for (String ent : getClassNames ())
        {
            RecordHandler handler = UMFPluginManager.getHandler (ent);
            if (handler != null)
            {
                if (handler.includeTypeInSearchResults(ent))
                {
                    String query = "select * from " + ent + " where ";
                    String[] searchFields = handler.getRecordTypeSearchFields (ent);
                    for (int f = 0; f < searchFields.length; f++)
                    {
                        String field = searchFields[f];
                        String critExpr = " like '%" + searchText.toUpperCase() + "%'";
                        if (searchText.equals ("")) critExpr = critExpr + " OR " + field + " == ''";  // To fix orient bug where blank values don't match against "like '%%'"
                        query += field + ".toUpperCase()" + critExpr;
                        if (f != searchFields.length - 1) query += " or ";
                    }
                    if (query.endsWith (" or ")) query = StringUtil.cut(query, " or ");
                    List<ODocument> result = db.query(new OSQLSynchQuery<ODocument>(query));
                    for (ODocument result0 : result)
                    {
                        NDoc record = new NDoc (result0);
                        if (record.getHandler ().includeRecordInSearchResults (record)) results.add (record);
                    }
                }
            }
        }
        db.close ();
        return results;
    }

    public NDoc getRecord (String className, String id)
    {
        ODatabaseDocumentTx db = getDB ();
        String query = "select * from " + className + " where @rid = ?";
        // Query does not do caching of ODocument instances, but rather
        // will return new instances even for ODocuments with the same
        // record ID returned previously.
        List<ODocument> result = db.query (new OSQLSynchQuery<ODocument> (query), new ORecordId (id));
        db.close ();
        if (result.size () == 0) return null;
        return new NDoc (result.get (0));
    }
}
