package org.rogach.ardiff;

import org.apache.commons.compress.archivers.ArchiveEntry;

public class ArchiveEntryWithData<GenArchiveEntry extends ArchiveEntry> {
    GenArchiveEntry entry;
    byte[] data;

    public ArchiveEntryWithData(GenArchiveEntry entry, byte[] data) {
        this.entry = entry;
        this.data = data;
    }
}
