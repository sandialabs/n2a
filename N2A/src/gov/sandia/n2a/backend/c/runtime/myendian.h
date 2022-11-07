/*
Author: Fred Rothganger
Created 3/11/2006 to provide machine endian related defines and functions


Copyright 2009 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_endian_h
#define n2a_endian_h


#include <stdint.h>

#if defined (_MSC_VER)  ||  defined (__MINGW64__)
   // MSVC generally compiles to i86.  Deal with other cases (such as alpha)
   // as they come up.
#  define LITTLE_ENDIAN 1234
#  define BYTE_ORDER    LITTLE_ENDIAN
#else
#  include <endian.h>
#endif


#if defined (_MSC_VER)  &&  defined (_M_IX86)  &&  ! defined (_M_X64)


static inline uint32_t
bswap (uint32_t x)
{
  __asm
  {
	mov   eax, x
	bswap eax
	mov   x, eax
  }
  return x;
}

static inline void
bswap (uint16_t * x, uint32_t count = 1)
{
  __asm
  {
	mov  ebx, x
	mov  ecx, count
	bswap16_top:
	ror  WORD PTR [ebx], 8
	add  ebx, 2
	loop bswap16_top
  }
}

static inline void
bswap (uint32_t * x, uint32_t count = 1)
{
  __asm
  {
	mov   ebx, x
	mov   ecx, count
	bswap32_top:
	mov   eax, [ebx]
	bswap eax
	mov   [ebx], eax
	add   ebx, 4
	loop  bswap32_top
  }
}

static inline void
bswap (uint64_t * x, uint32_t count = 1)
{
  __asm
  {
	mov   ebx, x
	mov   ecx, count
	bswap32_top:
	mov   eax, [ebx]
	mov   edx, [ebx+4]
	bswap eax
	bswap edx
	mov   [ebx], edx
	mov   [ebx+4], eax
	add   ebx, 8
	loop  bswap32_top
  }
}


#elif defined (__GNUC__)  &&  (defined (__i386__)  ||  defined (_X86_)  ||  defined (__x86_64__))


#include <sys/param.h>

static inline uint32_t
bswap (uint32_t x)
{
  __asm ("bswap %0" : "=r" (x) : "0" (x));
  return x;
}

static inline void
bswap (uint16_t * x, uint32_t count = 1)
{
  __asm ("1:"
		 "rorw   $8, (%0);"
		 "add    $2, %0;"
		 "loop   1b;"
		 :
		 : "r" (x), "c" (count));
}

static inline void
bswap (uint32_t * x, uint32_t count = 1)
{
  __asm ("1:"
		 "mov    (%0), %%eax;"
		 "bswap  %%eax;"
		 "mov    %%eax, (%0);"
		 "add    $4, %0;"
		 "loop   1b;"
		 :
		 : "r" (x), "c" (count)
		 : "eax");
}

#if defined (ARCH_X86_64)  ||  defined (__x86_64__)

static inline void
bswap (uint64_t * x, uint32_t count = 1)
{
  __asm ("1:"
		 "mov    (%0), %%rax;"
		 "bswap  %%rax;"
		 "mov    %%rax, (%0);"
		 "add    $8, %0;"
		 "loop   1b;"
		 :
		 : "r" (x), "c" (count)
		 : "rax");
}

#else  // 32-bit x86

static inline void
bswap (uint64_t * x, uint32_t count = 1)
{
  __asm ("1:"
		 "mov    (%0), %%eax;"
		 "mov    4(%0), %%edx;"
		 "bswap  %%eax;"
		 "bswap  %%edx;"
		 "mov    %%edx, (%0);"
		 "mov    %%eax, 4(%0);"
		 "add    $8, %0;"
		 "loop   1b;"
		 :
		 : "r" (x), "c" (count)
		 : "eax", "edx");
}

#endif  // select x64 version of bswap(long long)


#else   // assembly sections are not available, so use generic routines


#ifndef _MSC_VER

#  include <byteswap.h>

#else

static inline uint16_t
bswap_16 (uint16_t x)
{
  return (x >> 8) | (x << 8);
}

static inline uint32_t
bswap_32 (uint32_t x)
{
  return ((x & 0xFF000000) >> 24) | ((x & 0xFF0000) >> 8) | ((x & 0xFF00) << 8) | ((x & 0xFF) << 24);
}

static inline uint64_t
bswap_64 (uint64_t x)
{
  return (((uint64_t) bswap_32 (x & 0xFFFFFFFFull)) << 32) | (bswap_32 (x >> 32));
}

#endif

static inline uint32_t
bswap (uint32_t x)
{
  return bswap_32 (x);
}

static inline void
bswap (uint16_t * x, uint32_t count = 1)
{
  uint16_t * end = x + count;
  while (x < end)
  {
	*x = bswap_16 (*x);
	x++;
  }
}

static inline void
bswap (uint32_t * x, uint32_t count = 1)
{
  uint32_t * end = x + count;
  while (x < end)
  {
	*x = bswap_32 (*x);
	x++;
  }
}

static inline void
bswap (uint64_t * x, uint32_t count = 1)
{
  uint64_t * end = x + count;
  while (x < end)
  {
	*x = bswap_64 (*x);
	x++;
  }
}


#endif  // detect compiler+architecture support for assembly sections


#endif
