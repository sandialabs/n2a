#ifndef n2a_io_h
#define n2a_io_h

#ifdef N2A_SPINNAKER

  // Don't bother with message, because string takes up space.
  // Don't worry about including sark.h, because io.h will always be included later than it.
# define N2A_THROW(message) rt_error (RTE_ABORT);

#else

# define N2A_THROW(message) throw message;

#endif

#endif
