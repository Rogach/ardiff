ArchiveDiff
===========

Utility to compute, store and apply diffs to archives.

Motivation: simply using binary diffs on compressed archives results in huge diffs
even for smallest archive content changes. Making diff utility aware of format
specifics should result in big diff size savings.

Diff file format
----------------

I didn't find any common-sense binary format description language,
so I invented something that vaguely resembles BNF with C extensions.

```
<file> ::=
  "_ardiff_" # magic bytes
  <diffEntry>[]
  0

<diffEntry> ::=
  command: int8 # add (1), replace (2), remove (3), patch (4), archive patch (5), update attributes (6)
  <path>
  resultLength: int32 # omitted for remove, update attributes
  resultChecksum: int32? # optional, used only by zip format
  <attributes> # omitted for remove command
  dataLength: int32 # omitted for remove and update attributes commands
  data: int8[dataLength] # omitted for remove and update attributes commands
  checksum: int64 # crc32

<path> ::=
  length: int16
  name: int8[length]

<attributes> ::=
  attrs: <attribute>[]
  0

<attribute> ::=
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