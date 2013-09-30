/*
Author: Fred Rothganger
Created 10/22/2009


Copyright 2010 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the GNU Lesser General Public License.  See the file LICENSE
for details.
*/


#ifndef fl_blasproto_h
#define fl_blasproto_h

#ifdef HAVE_BLAS

extern "C"
{
  void daxpy_ (const int &    n,
			   const double & alpha,
			   double         x[],
			   const int &    incx,
			   double         y[],
			   const int &    incy);

  double ddot_ (const int & n,
				double      x[],
				const int & incx,
				double      y[],
				const int & incy);

  void dgemm_ (const char &   transA,
			   const char &   transB,
			   const int &    m,
			   const int &    n,
			   const int &    k,
			   const double & alpha,
			   double         a[],
			   const int &    lda,
			   double         b[],
			   const int &    ldb,
			   const double & beta,
			   double         c[],
			   const int &    ldc);

  double dnrm2_ (const int & n,
				 double      x[],
				 const int & incx);

  void dscal_ (const int &    n,
			   const double & alpha,
			   double         x[],
			   const int &    incx);

  void dtrmm_ (const char &   side,
			   const char &   uplo,
			   const char &   transa,
			   const char &   diag,
			   const int &    m,
			   const int &    n,
			   const double & alpha,
			   const double   a[],
			   const int &    lda,
			   double         b[],
			   const int &    ldb);

  void dtrsm_ (const char &   side,
			   const char &   uplo,
			   const char &   transa,
			   const char &   diag,
			   const int &    m,
			   const int &    n,
			   const double & alpha,
			   const double   a[],
			   const int &    lda,
			   double         b[],
			   const int &    ldb);

  void saxpy_ (const int &   n,
			   const float & alpha,
			   float         x[],
			   const int &   incx,
			   float         y[],
			   const int &   incy);

  float sdot_ (const int & n,
			   float       x[],
			   const int & incx,
			   float       y[],
			   const int & incy);

  void sgemm_ (const char &  transA,
			   const char &  transB,
			   const int &   m,
			   const int &   n,
			   const int &   k,
			   const float & alpha,
			   float         a[],
			   const int &   lda,
			   float         b[],
			   const int &   ldb,
			   const float & beta,
			   float         c[],
			   const int &   ldc);

  float snrm2_ (const int & n,
				float       x[],
				const int & incx);

  void sscal_ (const int &   n,
			   const float & alpha,
			   float         x[],
			   const int &   incx);

  void strmm_ (const char &  side,
			   const char &  uplo,
			   const char &  transa,
			   const char &  diag,
			   const int &   m,
			   const int &   n,
			   const float & alpha,
			   const float   a[],
			   const int &   lda,
			   float         b[],
			   const int &   ldb);

  void strsm_ (const char &  side,
			   const char &  uplo,
			   const char &  transa,
			   const char &  diag,
			   const int &   m,
			   const int &   n,
			   const float & alpha,
			   const float   a[],
			   const int &   lda,
			   float         b[],
			   const int &   ldb);
}

namespace fl
{
  // These functions are used in templates with numeric types other than
  // those covered by the BLAS.  IE: bool is not any of {float, double,
  // complex<float>, complex<double>}.  It is simpler just to implement the
  // routine here rather than check for acceptable numeric types in the
  // templates that call it.
  // None of the generic implementations in this section fully immitate the
  // behavior of the real BLAS routines.  Specifically, they don't handle
  // negative increments correctly, and they don't early-out on certain
  // combinations of parameters that should produce no change to operands
  // (for example, scaling by 1 or by 0 in certain circumstances).

  template<class T>
  inline void
  axpy (const int & n,
		const T &   alpha,
		T           x[],
		const int & incx,
		T           y[],
		const int & incy)
  {
	T * end = x + n * incx;
	while (x != end)
	{
	  *y += *x * alpha;
	  x += incx;
	  y += incy;
	}
  }

  template<>
  inline void
  axpy (const int &    n,
		const double & alpha,
		double         x[],
		const int &    incx,
		double         y[],
		const int &    incy)
  {
	daxpy_ (n, alpha, x, incx, y, incy);
  }

  template<>
  inline void
  axpy (const int &   n,
		const float & alpha,
		float         x[],
		const int &   incx,
		float         y[],
		const int &   incy)
  {
	saxpy_ (n, alpha, x, incx, y, incy);
  }

  template<class T>
  inline T
  dot (const int & n,
	   T           x[],
	   const int & incx,
	   T           y[],
	   const int & incy)
  {
	T result = (T) 0;
	T * end = x + n * incx;
	while (x != end)
	{
	  result += *x * *y;
	  x += incx;
	  y += incy;
	}
	return result;
  }

  template<>
  inline double
  dot (const int & n,
	   double      x[],
	   const int & incx,
	   double      y[],
	   const int & incy)
  {
	return ddot_ (n, x, incx, y, incy);
  }

  template<>
  inline float
  dot (const int & n,
	   float       x[],
	   const int & incx,
	   float       y[],
	   const int & incy)
  {
	return sdot_ (n, x, incx, y, incy);
  }

  template<class T>
  inline void
  gemm (const char & transA,
		const char & transB,
		const int &  m,
		const int &  n,
		const int &  k,
		const T &    alpha,
		T            a[],
		const int &  lda,
		T            b[],
		const int &  ldb,
		const T &    beta,
		T            c[],
		const int &  ldc)
  {
	int AstrideR = 1;
	int AstrideC = lda;
	if (transA == 'T'  ||  transA == 't'  ||  transA == 'C'  ||  transA == 'c')
	{
	  AstrideR = lda;
	  AstrideC = 1;
	}

	int BstrideR = 1;
	int BstrideC = ldb;
	if (transB == 'T'  ||  transB == 't'  ||  transB == 'C'  ||  transB == 'c')
	{
	  BstrideR = ldb;
	  BstrideC = 1;
	}

	T * end = c + n * ldc;
	int CstepC = ldc - m;
	while (c != end)
	{
	  T * aa = a;
	  T * columnEnd = c + m;
	  while (c != columnEnd)
	  {
		register T element = (T) 0;
		T * i = aa;
		T * j = b;
		T * rowEnd = i + k * AstrideC;
		while (i != rowEnd)
		{
		  element += *i * *j;
		  i += AstrideC;
		  j += BstrideR;
		}
		*c = alpha * element + beta * *c;
		aa += AstrideR;
		c++;
	  }
	  b += BstrideC;
	  c += CstepC;
	}
  }

  template<>
  inline void
  gemm (const char &   transA,
		const char &   transB,
		const int &    m,
		const int &    n,
		const int &    k,
		const double & alpha,
		double         a[],
		const int &    lda,
		double         b[],
		const int &    ldb,
		const double & beta,
		double         c[],
		const int &    ldc)
  {
	dgemm_ (transA, transB, m, n, k, alpha, a, lda, b, ldb, beta, c, ldc);
  }

  template<>
  inline void
  gemm (const char &  transA,
		const char &  transB,
		const int &   m,
		const int &   n,
		const int &   k,
		const float & alpha,
		float         a[],
		const int &   lda,
		float         b[],
		const int &   ldb,
		const float & beta,
		float         c[],
		const int &   ldc)
  {
	sgemm_ (transA, transB, m, n, k, alpha, a, lda, b, ldb, beta, c, ldc);
  }

  template<class T>
  inline T
  nrm2 (const int & n,
		T           x[],
		const int & incx)
  {
	T result = (T) 0;
	T * end = x + n * incx;
	while (x != end)
	{
	  result += *x * *x;
	  x += incx;
	}
	return (T) std::sqrt (result);
  }

  template<>
  inline double
  nrm2 (const int & n,
		double      x[],
		const int & incx)
  {
	return dnrm2_ (n, x, incx);
  }

  template<>
  inline float
  nrm2 (const int & n,
		float       x[],
		const int & incx)
  {
	return snrm2_ (n, x, incx);
  }

  template<class T>
  inline void
  scal (const int & n,
		const T &   alpha,
		T           x[],
		const int & incx)
  {
	T * end = x + n * incx;
	while (x != end)
	{
	  *x *= alpha;
	  x += incx;
	}
  }

  template<>
  inline void
  scal (const int &    n,
		const double & alpha,
		double         x[],
		const int &    incx)
  {
	dscal_ (n, alpha, x, incx);
  }

  template<>
  inline void
  scal (const int &   n,
		const float & alpha,
		float         x[],
		const int &   incx)
  {
	sscal_ (n, alpha, x, incx);
  }

  template<class T>
  inline void
  trsm (const char & side,
		const char & uplo,
		const char & transa,
		const char & diag,
		const int &  m,
		const int &  n,
		const T &    alpha,
		const T      a[],
		const int &  lda,
		T            b[],
		const int &  ldb)
  {
	throw "Generic trsm() not yet implemented.";
  }

  template<>
  inline void
  trsm (const char &   side,
		const char &   uplo,
		const char &   transa,
		const char &   diag,
		const int &    m,
		const int &    n,
		const double & alpha,
		const double   a[],
		const int &    lda,
		double         b[],
		const int &    ldb)
  {
	dtrsm_ (side, uplo, transa, diag, m, n, alpha, a, lda, b, ldb);
  }

  template<>
  inline void
  trsm (const char &  side,
		const char &  uplo,
		const char &  transa,
		const char &  diag,
		const int &   m,
		const int &   n,
		const float & alpha,
		const float   a[],
		const int &   lda,
		float         b[],
		const int &   ldb)
  {
	strsm_ (side, uplo, transa, diag, m, n, alpha, a, lda, b, ldb);
  }

  template<class T>
  inline void
  trmm (const char & side,
		const char & uplo,
		const char & transa,
		const char & diag,
		const int &  m,
		const int &  n,
		const T &    alpha,
		const T      a[],
		const int &  lda,
		T            b[],
		const int &  ldb)
  {
	throw "Generic trmm() not yet implemented.";
  }

  template<>
  inline void
  trmm (const char &   side,
		const char &   uplo,
		const char &   transa,
		const char &   diag,
		const int &    m,
		const int &    n,
		const double & alpha,
		const double   a[],
		const int &    lda,
		double         b[],
		const int &    ldb)
  {
	dtrmm_ (side, uplo, transa, diag, m, n, alpha, a, lda, b, ldb);
  }

  template<>
  inline void
  trmm (const char &  side,
		const char &  uplo,
		const char &  transa,
		const char &  diag,
		const int &   m,
		const int &   n,
		const float & alpha,
		const float   a[],
		const int &   lda,
		float         b[],
		const int &   ldb)
  {
	strmm_ (side, uplo, transa, diag, m, n, alpha, a, lda, b, ldb);
  }
}

#endif  // HAVE_BLAS

#endif
