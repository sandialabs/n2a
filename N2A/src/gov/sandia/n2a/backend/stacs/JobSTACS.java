/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.stacs;

import gov.sandia.n2a.backend.internal.InternalBackend;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.host.Remote;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Backend.AbortRun;
import gov.sandia.n2a.ui.jobs.NodeJob;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JobSTACS extends Thread
{
    public    MNode       job;
    protected EquationSet digestedModel;

    public    Host env;
    protected Path localJobDir;
    protected Path jobDir;     // remote or local

    protected long seed;
    protected int  processes;
    
    public JobSTACS (MNode job)
    {
        super ("STACS Job");
        this.job = job;
    }

    public void run ()
    {
        localJobDir = Host.getJobDir (Host.getLocalResourceDir (), job);
        Path errPath = localJobDir.resolve ("err");
        try {Backend.err.set (new PrintStream (new FileOutputStream (errPath.toFile (), true), false, "UTF-8"));}
        catch (Exception e) {}

        try
        {
            Files.createFile (localJobDir.resolve ("started"));
            MNode model = NodeJob.getModel (job);

            env              = Host.get (job);
            Path resourceDir = env.getResourceDir ();  // remote or local
            jobDir           = Host.getJobDir (resourceDir, job);  // Unlike localJobDir (which is created by MDir), this may not exist until we explicitly create it.

            Files.createDirectories (jobDir);  // digestModel() might write to a remote file (params), so we need to ensure the dir exists first.
            digestedModel = new EquationSet (model);
            InternalBackend.digestModel (digestedModel);

            String duration = digestedModel.metadata.get ("duration");
            if (! duration.isEmpty ()) job.set (duration, "duration");

            seed = model.getOrDefault (System.currentTimeMillis (), "$metadata", "seed");
            job.set (seed, "seed");

            int cores = 1;
            if (! (env instanceof Remote)) cores = env.getProcessorTotal ();
            cores     = job.getOrDefault (cores, "host", "cores");
            int nodes = job.getOrDefault (1,     "host", "nodes");
            processes = cores * nodes;

            generateConfigs ();

            // The simulator could append to the same error file, so we need to close the file before submitting.
            PrintStream ps = Backend.err.get ();
            if (ps != System.err)
            {
                ps.close ();
                Backend.err.remove ();
                job.set (Host.size (errPath), "errSize");
            }

            List<List<String>> commands = new ArrayList<List<String>> ();

            List<String> charmrun = new ArrayList<String> ();
            charmrun.add ("charmrun");
            charmrun.add ("+p" + processes);

            List<String> command = new ArrayList<String> (charmrun);
            command.add (env.config.getOrDefault ("genet", "backend", "stacs", "genet"));
            command.add ("config.yml");
            commands.add (command);

            command = new ArrayList<String> (charmrun);
            command.add (env.config.getOrDefault ("stacs", "backend", "stacs", "stacs"));
            command.add ("config.yml");
            commands.add (command);

            env.submitJob (job, false, commands);
        }
        catch (Exception e)
        {
            if (! (e instanceof AbortRun)) e.printStackTrace (Backend.err.get ());

            try {Files.copy (new ByteArrayInputStream ("failure".getBytes ("UTF-8")), localJobDir.resolve ("finished"));}
            catch (Exception f) {}
        }

        // If an exception occurred, the error file will still be open.
        PrintStream ps = Backend.err.get ();
        if (ps != System.err) ps.close ();
    }

    public void generateConfigs () throws IOException
    {
        try (BufferedWriter writer = Files.newBufferedWriter (jobDir.resolve ("config.yml")))
        {
            writer.write ("# simulation\n");
            writer.write ("runmode : \"simulate\"\n");
            writer.write ("randseed: " + seed + "\n");
            writer.write ("plastic : yes\n");
            writer.write ("episodic: no\n");
            writer.write ("rpcport : \"/stacs/rpc\"\n");
            writer.write ("rpcpause: no\n");
            writer.write ("\n");
            writer.write ("# network\n");
            writer.write ("netwkdir: \"network\"\n");
            writer.write ("netparts: " + processes + "\n");
            writer.write ("netfiles: " + processes + "\n");
            writer.write ("filebase: \"config\"\n");
            writer.write ("fileload: \"\"\n");
            writer.write ("filesave: \".out\"\n");
            writer.write ("recordir: \"record\"\n");
            writer.write ("groupdir: \"group\"\n");
            writer.write ("\n");
            writer.write ("# timing\n");
            writer.write ("tstep   : 1.0\n");
            writer.write ("teventq : 20.0\n");
            writer.write ("tdisplay: 1000.0\n");
            writer.write ("trecord : 1000.0\n");
            writer.write ("tsave   : 60000.0\n");
            writer.write ("tmax    : 180000.0\n");
            writer.write ("\n");
            writer.write ("# groups\n");
            writer.write ("grpactive: [izhiex, izhiin]\n");
            writer.write ("grpmother: [izhiex]\n");
            writer.write ("grpanchor: [exnostdp]\n");
            writer.write ("grpminlen: 7\n");
            writer.write ("grpmaxdur: 150.0\n");
            writer.write ("grpvtxmin: 0.0\n");
            writer.write ("grpvtxmax: 1.0\n");
        }

        // Create basic directory structure
        Path network = jobDir .resolve ("network");
        Path record  = network.resolve ("record");
        Path graph   = network.resolve ("config.graph");
        Path model   = network.resolve ("config.model");
        Files.createDirectories (record);
        Files.createSymbolicLink (jobDir.resolve ("record"),       record);
        Files.createSymbolicLink (jobDir.resolve ("config.graph"), graph);
        Files.createSymbolicLink (jobDir.resolve ("config.model"), model);

        try (BufferedWriter writer = Files.newBufferedWriter (graph))
        {
            writer.write ("vertex:\n");
            writer.write ("  - modname: izhiex\n");
            writer.write ("    order: 800\n");
            writer.write ("    shape: circle  # uniform circle\n");
            writer.write ("    radius: 50.0  # of radius 50\n");
            writer.write ("    coord: [0.0, 0.0, 0.0]\n");
            writer.write ("  - modname: izhiin\n");
            writer.write ("    order: 200\n");
            writer.write ("    shape: circle  # uniform circle\n");
            writer.write ("    radius: 50.0  # of radius 50\n");
            writer.write ("    coord: [0.0, 0.0, 0.0]\n");
            writer.write ("  - modname: izhithal\n");
            writer.write ("    order: 1\n");
            writer.write ("    shape: point\n");
            writer.write ("    coord: [0.0, 0.0, 0.0]\n");
            writer.write ("\n");
            writer.write ("edge:\n");
            writer.write ("  - source: izhiex\n");
            writer.write ("    target: [izhiex, izhiin]\n");
            writer.write ("    modname: exstdp\n");
            writer.write ("    cutoff: 100.0\n");
            writer.write ("    connect:\n");
            writer.write ("      - type: uniform\n");
            writer.write ("        prob: 0.1\n");
            writer.write ("  - source: izhiin\n");
            writer.write ("    target: [izhiex]\n");
            writer.write ("    modname: instdp\n");
            writer.write ("    cutoff: 100.0\n");
            writer.write ("    connect:\n");
            writer.write ("      - type: uniform\n");
            writer.write ("        prob: 0.125\n");
            writer.write ("  - source: izhithal\n");
            writer.write ("    target: [izhiex, izhiin]\n");
            writer.write ("    modname: thampl\n");
            writer.write ("    cutoff: 0.0\n");
            writer.write ("    connect:\n");
            writer.write ("      - type: uniform\n");
            writer.write ("        prob: 1.0\n");
        }

        try (BufferedWriter writer = Files.newBufferedWriter (model))
        {
            writer.write ("---\n");
            writer.write ("type: vertex\n");
            writer.write ("modname: izhiex\n");
            writer.write ("modtype: 10\n");
            writer.write ("param:\n");
            writer.write ("  - name: a\n");
            writer.write ("    value: 0.02\n");
            writer.write ("  - name: b\n");
            writer.write ("    value: 0.2\n");
            writer.write ("  - name: c\n");
            writer.write ("    value: -65\n");
            writer.write ("  - name: d\n");
            writer.write ("    value: 8\n");
            writer.write ("state:\n");
            writer.write ("  - name: v\n");
            writer.write ("    type: constant\n");
            writer.write ("    value: -65.0\n");
            writer.write ("  - name: u\n");
            writer.write ("    type: constant\n");
            writer.write ("    value: -13.0\n");
            writer.write ("  - name: I\n");
            writer.write ("    type: constant\n");
            writer.write ("    value: 0.0\n");
            writer.write ("  - name: I_app\n");
            writer.write ("    type: constant\n");
            writer.write ("    value: 0.0\n");
            writer.write ("...\n");
            writer.write ("\n");
            writer.write ("---\n");
            writer.write ("type: vertex\n");
            writer.write ("modname: izhiin\n");
            writer.write ("modtype: 10\n");
            writer.write ("param:\n");
            writer.write ("  - name: a\n");
            writer.write ("    value: 0.1\n");
            writer.write ("  - name: b\n");
            writer.write ("    value: 0.2\n");
            writer.write ("  - name: c\n");
            writer.write ("    value: -65\n");
            writer.write ("  - name: d\n");
            writer.write ("    value: 2\n");
            writer.write ("state:\n");
            writer.write ("  - name: v\n");
            writer.write ("    type: constant\n");
            writer.write ("    value: -65.0\n");
            writer.write ("  - name: u\n");
            writer.write ("    type: constant\n");
            writer.write ("    value: -13.0\n");
            writer.write ("  - name: I\n");
            writer.write ("    type: constant\n");
            writer.write ("    value: 0.0\n");
            writer.write ("  - name: I_app\n");
            writer.write ("    type: constant\n");
            writer.write ("    value: 0.0\n");
            writer.write ("...\n");
            writer.write ("\n");
            writer.write ("---\n");
            writer.write ("type: vertex\n");
            writer.write ("modname: izhithal\n");
            writer.write ("modtype: 100\n");
            writer.write ("param:\n");
            writer.write ("  - name: rng\n");
            writer.write ("    value: 1000\n");
            writer.write ("  - name: ampl\n");
            writer.write ("    value: 20\n");
            writer.write ("...\n");
            writer.write ("\n");
            writer.write ("---\n");
            writer.write ("type: edge\n");
            writer.write ("modname: exstdp\n");
            writer.write ("modtype: 12\n");
            writer.write ("param:\n");
            writer.write ("  - name: wmax\n");
            writer.write ("    value: 10\n");
            writer.write ("  - name: tau\n");
            writer.write ("    value: 20\n");
            writer.write ("  - name: update\n");
            writer.write ("    value: 1000\n");
            writer.write ("state:\n");
            writer.write ("  - name: delay\n");
            writer.write ("    type: uniform interval\n");
            writer.write ("    rep: tick\n");
            writer.write ("    min: 1.0\n");
            writer.write ("    max: 20.0\n");
            writer.write ("    int: 1.0\n");
            writer.write ("  - name: weight\n");
            writer.write ("    type: constant\n");
            writer.write ("    value: 6.0\n");
            writer.write ("  - name: wdelta\n");
            writer.write ("    type: constant\n");
            writer.write ("    value: 0.0\n");
            writer.write ("  - name: ptrace\n");
            writer.write ("    type: constant\n");
            writer.write ("    value: 0.0\n");
            writer.write ("  - name: ntrace\n");
            writer.write ("    type: constant\n");
            writer.write ("    value: 0.0\n");
            writer.write ("  - name: ptlast\n");
            writer.write ("    type: constant\n");
            writer.write ("    rep: tick\n");
            writer.write ("    value: 0.0\n");
            writer.write ("  - name: ntlast\n");
            writer.write ("    type: constant\n");
            writer.write ("    rep: tick\n");
            writer.write ("    value: 0.0\n");
            writer.write ("...\n");
            writer.write ("\n");
            writer.write ("---\n");
            writer.write ("type: edge\n");
            writer.write ("modname: instdp\n");
            writer.write ("modtype: 13\n");
            writer.write ("param:\n");
            writer.write ("state:\n");
            writer.write ("  - name: delay\n");
            writer.write ("    type: constant\n");
            writer.write ("    rep: tick\n");
            writer.write ("    value: 1.0\n");
            writer.write ("  - name: weight\n");
            writer.write ("    type: constant\n");
            writer.write ("    value: -5.0\n");
            writer.write ("...\n");
            writer.write ("\n");
            writer.write ("---\n");
            writer.write ("type: edge\n");
            writer.write ("modname: thampl\n");
            writer.write ("modtype: 11\n");
            writer.write ("param:\n");
            writer.write ("state:\n");
            writer.write ("  - name: delay\n");
            writer.write ("    type: constant\n");
            writer.write ("    rep: tick\n");
            writer.write ("    value: 1.0\n");
            writer.write ("...\n");
        }
    }
}
