/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

/**
    Takes boolean values as input. Specifically, only checks whether value is
    zero or non-zero. Ignores the specific value of a non-zero input.
**/
public interface OperatorLogicalInput extends OperatorLogical
{
}
