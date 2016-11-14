ArchiveDiff
===========

Utility to compute, store and apply diffs to archives.

Motivation: simply using binary diffs on compressed archives results in huge diffs
even for smallest archive content changes. Making diff utility aware of format
specifics should result in huge diff size savings.

Diff file format
----------------

I didn't find any common-sense binary format description language,
so I invented something that vaguely resembles BNF with C extensions.

```
<file> ::=
  "_ardiff_" # magic bytes
  <diffEntry>[] # read until EOF or explicit zero command

<diffEntry> ::=
  command: int8 # replace (1), remove (2), patch (3), archive patch (4), update attributes (5)
  <path>
  <attributes> # omitted for remove command
  dataLength: int32 # omitted for remove and update attributes commands
  data: int8[dataLength] # omitted for remove and update attributes commands
  checksum: int64 # crc32

<path> ::=
  length: int16
  name: int8[length]

<attributes> ::=
  attrs: <attribute>[] # read until zero command is found

<attributeEntry> ::=
  command: int8 # replace (1), remove (2)
  code: int8
  *attribute-specific data*
```

Limitations
-----------

* full path inside archive can't be longer than 65536 bytes;
* single archive entry can't be over 2GB in size;
* limitations of individual archive formats apply as well.

All aforementioned limitations (except for archive formats) are mostly arbitrary
and can be lifted without much work - just submit an issue!