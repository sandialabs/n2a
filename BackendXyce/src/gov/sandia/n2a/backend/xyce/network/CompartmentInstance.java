/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.network;



public class CompartmentInstance 
    extends PartInstance
{
    public double xPosition;
    public double yPosition;

    public CompartmentInstance(
        PartSetInterface pset,
        int newSerialNumber,
        double newXPosition,
        double newYPosition)
    {
        super(pset, newSerialNumber);
        xPosition = newXPosition;
        yPosition = newYPosition;
    }
    
    public Number[] getPosition() {
        Number[] pos = {xPosition,yPosition};
        return pos;
    }
}
