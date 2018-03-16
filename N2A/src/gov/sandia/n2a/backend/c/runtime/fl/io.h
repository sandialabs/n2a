#ifndef n2a_io_h
#define n2a_io_h

#ifdef N2A_SPINNAKER
#  define N2A_THROW(message) exit (1);  // Don't bother with message, because string takes up space.
#else
#  define N2A_THROW(message) throw message;
#endif

#endif
