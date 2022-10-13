/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

/**
    Marks a function as something that can visit the elements of a matrix to produce
    a new matrix. Used by C backend for code generation. Some visitable functions
    might have several different prototypes. Only the single-parameter form is visitable.
**/
public interface MatrixVisitable
{
}
