/*
Copyright 2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.linear;

import gov.sandia.n2a.language.type.Matrix;

/**
    Interface for using matrix factorizations.

    The factorization work is typically done by the constructor.
    It takes the matrix to be factored, then sets up internal state
    with results of the factorization. This can later be used by
    solve() and invert().

    By default, the functions of this class will not damage the matrices
    passed as parameters. Furthermore, the class promises to keep its
    internal state in a reusable form, so for example solve() can be
    called multiple times. However, a subclass may allow you to pass a
    flag that revokes these promises for the sake of memory efficiency.
**/
public interface Factorization
{
    /**
        Resets all internal state and stores the factorization of A.
    **/
    public void factorize (Matrix A);

    /**
        Solve AX=B, where A is the matrix represented by this factorization.
        @return X
    **/
    public Matrix solve (Matrix B);

    /**
        @return The inverse of A, the matrix represented by this factorization.
        If A is not square, returns the pseudo-inverse.
        If A is not invertible, returns null.
    **/
    public Matrix invert ();
}
