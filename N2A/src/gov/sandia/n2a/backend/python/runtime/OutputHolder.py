"""
    A utility class for writing output files in the same format as N2A simulators.

    Copyright 2021-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
    Under the terms of Contract DE-NA0003525 with NTESS,
    the U.S. Government retains certain rights in this software.
"""

import sys
import math
import re

class OutputHolder:
    """ Full implementation of the N2A output file format.
        Compare with the Java version in gov.sandia.n2a.langauge.function.Output.Holder
    """

    def __init__(self, fileName):
        if fileName:
            self.out = open(fileName, 'w')
        else:
            self.out = sys.stdout
            fileName = 'out'  # Assumes we are wrapped in a shell script that redirects stdout to 'out'.
        self.columnFileName  = fileName + '.columns'
        self.columnMap       = {}     # key is column name; value is column position
        self.columnMode      = []     # for each column position, a dictionary of attributes
        self.columnValues    = []     # current value in each column
        self.columnsPrevious = 0      # how many column headers have been written out so far
        self.t               = 0      # current time
        self.traceReceived   = False  # Indicates that at least one data item has arrived in the current time step.
        self.raw             = False  # Indicates that column name is an exact index.

    def close(self):
        self.writeTrace()
        self.out.close()
        self.writeModes()

    def trace(self, now, column, value, mode=''):
        # This assumes that time increases monotonically.
        # If time regresses, value simply gets included in current row.
        if now > self.t:
            self.writeTrace()
            self.t = now

        if not self.traceReceived:  # First trace for this cycle
            self.traceReceived = True
            if len(self.columnValues) == 0:  # slip $t into first column
                self.columnMap['$t'] = 0
                self.columnValues.append(self.t)
                self.columnMode.append({})
            else:
                self.columnValues[0] = self.t

        if column in self.columnMap:  # Existing column
            index = self.columnMap[column];
            self.columnValues[index] = value
        else:  # Add new column
            if self.raw:
                i = int(column) + 1  # 1 is offset for time in first column
                while len(self.columnValues) < i:
                    self.columnValues.append(float('nan'))
                    self.columnMode.append({})
                index = i;
            else:
                index = len(self.columnValues);
            self.columnMap[column] = index
            self.columnValues.append(value)

            self.columnMode.append({})
            for h in mode.split(','):
                h = h.strip()
                if  not h  or  h == 'raw': continue
                pieces = h.split('=', 2)
                key = pieces[0].strip()
                val = ''
                if len(pieces) > 1: val = pieces[1].strip()
                if key == 'timeScale':
                    self.columnMode[0]['scale'] = val  # Set on time column.
                elif key in ['xmax', 'xmin', 'ymax', 'ymin']:
                    self.columnMode[0][key] = val  # All chart-wide parameters go on time column.
                else:
                    self.columnMode[index][key] = val

    def writeTrace(self):
        if not self.traceReceived: return  # Don't output anything unless at least one value was set.

        count = len(self.columnValues)
        last  = count - 1

        # Write headers if new columns have been added.
        if count > self.columnsPrevious:
            if not self.raw:
                headers = [''] * count
                for k, v in self.columnMap.items():
                    headers[v] = k
                self.out.write(headers[0])  # Should be $t
                i = 1
                while i < self.columnsPrevious:
                    self.out.write('\t')
                    i = i + 1
                while i < count:
                    self.out.write('\t')
                    if re.search(r'[ \t,"]', headers[i]):  # Check for reserved characters
                        self.out.write('"')
                        self.out.write(headers[i].replace('"', '""'))
                        self.out.write('"')
                    else:
                        self.out.write(headers[i])
                    i = i + 1
                self.out.write('\n')
            self.columnsPrevious = count
            self.writeModes()

        # Write values
        for i in range(count):
            c = self.columnValues[i]
            if not math.isnan(c): self.out.write(str(c))
            if i < last: self.out.write('\t')
            self.columnValues[i] = float('nan')
        self.out.write('\n')

        self.traceReceived = False

    def writeModes(self):
        with open(self.columnFileName, 'w') as mo:
            mo.write('N2A.schema=3\n')
            for columnName, columnIndex in self.columnMap.items():
                mo.write('{}:{}\n'.format (columnIndex, columnName))
                for attributeName, attributeValue in self.columnMode[columnIndex].items():
                    mo.write(' {}:{}\n'.format (attributeName, attributeValue))
