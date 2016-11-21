package org.rogach.ardiff;

import org.apache.commons.compress.archivers.ArchiveEntry;

public class ArchiveEntryWithData<GenArchiveEntry extends ArchiveEntry> implements Comparable<ArchiveEntryWithData<GenArchiveEntry>> {

    GenArchiveEntry entry;
    byte[] data;

    public ArchiveEntryWithData(GenArchiveEntry entry, byte[] data) {
        this.entry = entry;
        this.data = data;
    }

    @Override
    public int compareTo(ArchiveEntryWithData<GenArchiveEntry> o) {
        return this.entry.getName().compareTo(o.entry.getName());
    }
}
