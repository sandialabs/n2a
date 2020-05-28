import numpy
import re
import sys

class Column:

    def __init__(self, header):
        self.header    = header
        self.index     = 0
        self.values    = []
        self.value     = 0.0  # For most recent row
        self.startRow  = 0
        self.textWidth = 0
        self.minimum   =  numpy.inf
        self.maximum   = -numpy.inf
        self.range     = 0.0
        self.color     = ''
        self.scale     = ''

    def computeStats(self):
        for f in self.values:
            if isinf(f) or isnan(f): continue
            self.minimum = min(self.minimum, f)
            self.maximum = max(self.maximum, f)
        if isinf(maximum):  # There was no good data. If max is infinite, then so is min.
            # Set defensive values.
            self.range   = 0.0
            self.minimum = 0.0
            self.maximum = 0.0
        else:
            self.range = self.maximum - self.minimum

    def get(self, row = -1, defaultValue = 0.0):
        if row < 0: return self.value
        row -= self.startRow
        if row < 0 or row >= len(self.values): return defaultValue
        return self.values[row]

class OutputParser:

    def __init__(self):
        self.columns      = []
        self.inFile       = None
        self.raw          = False  # Indicates that all column names are empty, likely the result of output() in raw mode.
        self.isXycePRN    = False
        self.time         = None
        self.timeFound    = False  # Indicates that time is a properly-labeled column, rather than a fallback.
        self.rows         = 0      # Total number of rows successfully read by nextRow()
        self.defaultValue = 0.0

    def open(self, fileName):
        """ Use this function in conjunction with nextRow() to read file line-by-line
            without filling memory with more than one row.
        """
        self.close()
        self.inFile    = open(fileName)
        self.raw       = True  # Will be negated if any non-empty column name is found.
        self.isXycePRN = False
        self.time      = None
        self.timeFound = False
        self.rows      = 0

    def close(self):
        if self.inFile: self.inFile.close()
        self.inFile = None
        self.columns = []

    def nextRow(self):
        """ Returns number of columns found in current row. If zero, then end-of-file
            has been reached or there is an error.
        """
        if self.inFile is None: return 0
        whitespace = re.compile("[ \t]")
        for line in self.inFile:
            if line == "\n": continue
            if line[:6] == "End of": return 0  # Don't mistake Xyce final output line as a column header.
            if line[-1] == "\n": line = line[0:-1]  # strip trailing newline

            c = 0  # Column index
            start = 0  # Current position for column scan.
            l = line[0]
            isHeader = (l < "0"  or  l > "9")  and  l != "+"  and  l != "-"  # any character other than the start of a float
            if isHeader: self.raw = False
            while True:
                pos = whitespace.search(line, start)
                if pos:
                    pos = pos.start()
                    value = line[start:pos]
                    next = pos + 1
                else:
                    value = line[start:]
                    next = -1

                # Notice that c can never be greater than column count, because we always fill in columns as we go.
                if isHeader:
                    if c == len(self.columns):
                        self.columns.append(Column(value))
                else:
                    if c == len(self.columns): self.columns.append(Column(""))
                    column = self.columns[c]
                    if value == "":
                        column.value = self.defaultValue
                    else:
                        column.textWidth = max(column.textWidth, len(value))
                        column.value = float(value)

                c += 1
                if next == -1: break
                start = next

            if isHeader:
                self.isXycePRN =  self.columns[0].header == "Index"
            else:
                self.rows += 1
                return c
        return 0

    def parse(self, fileName, defaultValue = 0.0):
        """ Use this function to read the entire file into memory.
        """
        self.defaultValue = defaultValue
        self.open(fileName)
        while True:
            count = self.nextRow()
            if count == 0: break
            c = 0
            for column in self.columns:
                if len(column.values) == 0: column.startRow = self.rows - 1
                if c < count: column.values.append(column.value)
                else:         column.values.append(defaultValue)  # Because the structure is not sparse, we must fill out every row.
                c += 1
        if len(self.columns) == 0: return  # failed to read any input, not even a header row

        # If there is a separate columns file, open and parse it.
        columnFileName = fileName + ".columns"
        try:
            columnFile = open(columnFileName)
            line = columnFile.readline()
            if line[0:10] == "N2A.schema":
                c = None
                for line in columnFile:
                    if line[-1] == "\n": line = line[0:-1]
                    pos = line.find(":")
                    if pos < 0: pos = len(line)
                    key = line[0:pos]
                    value = line[pos+1:]
                    if key[0] == ' ':
                        if c is None: continue
                        setattr(c, key[1:], value)
                    else:
                        i = int(key)
                        if i < 0 or i >= len(self.columns):
                            c = None
                            continue
                        c = self.columns[i]
                        if column.header == "": column.header = value
            columnFile.close()
        except OSError: pass

        # Determine time column
        self.time = self.columns[0]  # fallback, in case we don't find it by name
        timeMatch = 0
        for column in self.columns:
            potentialMatch = 0
            if   column.header == "t"   : potentialMatch = 1
            elif column.header == "TIME": potentialMatch = 2
            elif column.header == "$t"  : potentialMatch = 3
            if potentialMatch > timeMatch:
                timeMatch = potentialMatch
                self.time = column
                self.timeFound = True

        # Get rid of Xyce "Index" column, as it is redundant with row number.
        if self.isXycePRN: self.columns = self.columns[1:]

    def getColumn(self, columnName):
        for column in self.columns:
            if column.header == columnName: return column
        return None

    def get(self, columnName, row = -1):
        column = self.getColumn(columnName)
        if column is None: return defaultValue
        return column.get(row)

    def hasData(self):
        for column in self.columns:
            if len(column.values) > 0: return True
        return False

    def hasHeaders(self):
        for column in self.columns:
            if column.header != "": return True
        return False

    def dump(self, out=sys.stdout):
        """ Dumps parsed data in tabular form. This can be used directly by most software.
        """
        if len(self.columns) == 0: return
        last = self.columns[-1]

        if self.hasHeaders():
            for column in self.columns:
                if column is last: e = "\n"
                else:              e = "\t"
                print(column.header, end=e, file=out)

        if self.hasData():
            for r in range(self.rows):
                for column in self.columns:
                    if column is last: e = "\n"
                    else:              e = "\t"
                    print(column.get(r), end=e, file=out)

    def dumpMode(self, out=sys.stdout):
        """ Dumps column metadata (from output mode field).
        """
        if self.hasHeaders():
            for column in self.columns:
                print(column.header, file=out)
                print("color=" + column.color, file=out)
                print("scale=" + column.scale, file=out)

if __name__ == "__main__":
    o = OutputParser()
    o.parse("C:/Users/frothga/n2a/jobs/2020-05-27-205826-0/out")
    o.dump()
    print ('done')