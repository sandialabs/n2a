/*
Author: Fred Rothganger
Copyright (c) 2001-2004 Dept. of Computer Science and Beckman Institute,
                        Univ. of Illinois.  All rights reserved.
Distributed under the UIUC/NCSA Open Source License.  See the file LICENSE
for details.


Copyright 2005 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the GNU Lesser General Public License.  See the file LICENSE
for details.
*/


#ifndef fl_string_h
#define fl_string_h


#include <ctype.h>
#include <string>
#include <string.h>

#ifdef _MSC_VER

# define strtoll     _strtoi64
# define strcasecmp  _stricmp
# define strncasecmp _strnicmp
# define wcscasecmp  _wcsicmp
# define wcsncasecmp _wcsnicmp

#else

# include <strings.h>

#endif


namespace fl
{
  inline void
  split (const std::string & source, const char * delimiter, std::string & first, std::string & second)
  {
	int index = source.find (delimiter);
	if (index == std::string::npos)
	{
	  first = source;
	  second.erase ();
	}
	else
	{
	  std::string temp = source;
	  first = temp.substr (0, index);
	  second = temp.substr (index + strlen (delimiter));
	}
  }

  inline void
  split (const std::wstring & source, const wchar_t * delimiter, std::wstring & first, std::wstring & second)
  {
	int index = source.find (delimiter);
	if (index == std::wstring::npos)
	{
	  first = source;
	  second.erase ();
	}
	else
	{
	  std::wstring temp = source;
	  first = temp.substr (0, index);
	  second = temp.substr (index + wcslen (delimiter));
	}
  }

  inline void
  trim (std::string & target)
  {
	int begin = target.find_first_not_of (" \t\r\n");
	int end   = target.find_last_not_of  (" \t\r\n");
	if (begin == std::string::npos)
	{
	  // all spaces
	  target.erase ();
	  return;
	}
	target = target.substr (begin, end - begin + 1);
  }

  inline void
  lowercase (std::string & target)
  {
	std::string::iterator i;
	for (i = target.begin (); i < target.end (); i++)
	{
	  *i = tolower (*i);
	}
  }

  inline void
  uppercase (std::string & target)
  {
	std::string::iterator i;
	for (i = target.begin (); i < target.end (); i++)
	{
	  *i = toupper (*i);
	}
  }
}


#endif
