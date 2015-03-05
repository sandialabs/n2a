/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.data;

import gov.sandia.n2a.language.ParsedEquation;
import gov.sandia.n2a.language.gen.ParseException;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.plugins.Parameterizable;
import gov.sandia.umf.platform.plugins.PlatformRecord;
import gov.sandia.umf.platform.plugins.Run;
import gov.sandia.umf.platform.plugins.RunEnsemble;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;
import gov.sandia.umf.platform.ui.orientdb.general.TermValue;

import java.util.List;

public interface Model extends PlatformRecord, Parameterizable/*, UMFRunnable*/ {
    public void setName(String name);
    public String getName();
    public void setNotes(String notes);
    public String getNotes();
    public void setTerms(List<TermValue> tags);
    public List<TermValue> getTerms();
    public void setLayers(List<Layer> layers);
    public List<Layer> getLayers();
    public boolean existsLayerName(String name);
    public void setBridges(List<Bridge> bridges);
    public List<Bridge> getBridges() ;
    public boolean existsBridgeName(String name);
    public void setInputEqs(List<NDoc> equations);
    public List<NDoc> getInputEqs();
    public void setOutputEqs(List<NDoc> equations);
    public List<NDoc> getOutputEqs();
    public List<ParsedEquation> getParsedOutputEqs() throws ParseException;
    public List<Run> getRuns();
    public NDoc getSource();
    public ParameterDomain getAllParameters();
    public void setSelectedParameters(ParameterDomain domains);
    public Model copy();
    public void setRunEnsembles(List<RunEnsemble> ensembles);
    public List<RunEnsemble> getRunEnsembles();
}
