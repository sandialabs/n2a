/*
Copyright 2021-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import java.nio.file.Path;

import gov.sandia.n2a.eqset.EquationSet.NonzeroIterable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.plugins.ExtensionPoint;

/**
    Adds support to the C backend for one or more language functions.
    Most of the functions in this interface are named after the corresponding
    function in JobC that they extend. The exact form of extension varies
    between functions. In most cases, the extension will be called somewhere
    in the middle of processing the regular JobC function.
**/
public interface ProvideOperator extends ExtensionPoint
{
    /**
        Does all the tasks to prepare library support for the function(s)
        provided by this extension.
        This includes making a resource directory, unpacking resources and
        compiling them. When called, the C runtime will already be unpacked,
        but not necessarily compiled. To allow for changes in configuration, this
        function is called each time the C backend is run. The provider should
        implement its own guard to limit number of compiles per application run.
        See JobC.runtimeBuilt for an example.
        @param job The job associated with the model currently going into
        simulation. Important attributes include job.T, the name of numeric
        type in use ("float", "double", "int"). Several versions of the support
        code may be built. The provider should use a naming scheme that allows
        many different builds to reside in the same directory, for example by
        calling JobC.objectName().
        @return True if a new library file was built. False if the existing library
        file is unchanged. See the function library(JobC).
    **/
    default public boolean rebuildRuntime (JobC job) throws Exception
    {
        return false;
    }

    /**
        @return Path to the object that should be linked into the C runtime.
        Can be either a simple object file or a static library.
        Should not be a dynamic-link library. Instead, if dynamic linking
        is in use, the provided object will be incorporated into the runtime's main
        library. OK to return null.
    **/
    default public Path library (JobC job) throws Exception
    {
        return null;
    }

    /**
        @return Full path to header file that should be included in the model source code.
        The include will come after all C runtime includes, but possibly before STL and
        standard library includes. This path will be split, so that the last component
        is placed in an include statement, and the entire parent path is given as an
        include directory. This allows the include file to pull in other includes in
        the same or lower directory. OK to return null.
    **/
    default public Path include (JobC job) throws Exception
    {
        return null;
    }

    /**
        Inserts any special code generation for the extension function(s).
        @param renderer The renderer instance that is building the model.
        The renderer calls this function inside its own render() function ahead
        of standard functions. This will let the extension override anything it needs to.
        @return null if operator is not recognized. Otherwise, boolean value to
        return from renderer.render().
    **/
    default public Boolean render (RendererC renderer, Operator op)
    {
        return null;
    }

    /**
        @return null if operator is not recognized. Otherwise, boolean value to
        return from visitor function (true to continue visiting; false to stop).
    **/
    default public Boolean assignNames (RendererC renderer, Operator op)
    {
        return null;
    }

    default public void generateStatic (RendererC renderer)
    {
    }

    default public void generateMainInitializers (RendererC renderer)
    {
    }

    /**
        @return null if operator is not recognized. Otherwise, boolean value to
        return from visitor function (true to continue visiting; false to stop).
    **/
    default public Boolean prepareStaticObjects (Operator op, RendererC context, String pad)
    {
        return null;
    }

    /**
        @return null if operator is not recognized. Otherwise, boolean value to
        return from visitor function (true to continue visiting; false to stop).
    **/
    default public Boolean prepareDynamicObjects (Operator op, RendererC context, boolean init, String pad)
    {
        return null;
    }

    /**
        @return true if the operator was recognized/processed
    **/
    default public boolean getIterator (NonzeroIterable nzit, RendererC context)
    {
        return false;
    }
}
