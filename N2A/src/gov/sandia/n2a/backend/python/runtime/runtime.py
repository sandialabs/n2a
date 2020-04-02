import numpy
import math

def gaussian(sigma=1):
    if isinstance(sigma, numpy.ndarry):
        rows = sigma.shape[0]
        cols = 1
        if len(sigma.shape) > 1: cols = sigma.shape[1]
        if cols == 1:
            result = numpy.zeros(rows)
            for r in range(rows): result[r] = sigma[r,0] * random.gauss(0,1)
            return result
        result = numpy.zeros(cols)
        for i in range(cols): result[i] = random.gauss(0,1)
        return sigma * result
    return random.gauss(0,1) * sigma

def uniform(sigma=1):
    if isinstance(sigma, numpy.ndarry):
        rows = sigma.shape[0]
        cols = 1
        if len(sigma.shape) > 1: cols = sigma.shape[1]
        if cols == 1:
            result = numpy.zeros(rows)
            for r in range(rows): result[r] = sigma[r,0] * random.uniform()
            return result
        result = numpy.zeros(cols)
        for i in range(cols): result[i] = random.uniform()
        return sigma * result
    return random.uniform() * sigma

def grid(i, nx, ny, nz):
    sx = ny * nz  # stride x

    # compute xyz in stride order
    result = numpy.zeros((3))
    result[0] = ((i // sx) + 0.5) / nx  # (i // sx) is an integer operation, so remainder is truncated.
    i %= sx
    result[1] = ((i // nz) + 0.5) / ny
    result[2] = ((i %  nz) + 0.5) / nz
    return result

def gridRaw(i, nx, ny, nz):
    sx = ny * nz  # stride x

    # compute xyz in stride order
    result = numpy.zeros((3))
    result[0] = i // sx
    i %= sx
    result[1] = i // nz
    result[2] = i %  nz
    return result
