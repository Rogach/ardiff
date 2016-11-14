package org.rogach.ardiff;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.IOException;
import java.util.HashMap;

public interface ArchiveDiffBase<GenArchiveEntry extends ArchiveEntry> {

    String archiverName();

    boolean attributesEqual(GenArchiveEntry entryBefore, GenArchiveEntry entryAfter);

    /* separated into separate method so that we suppress warnings only for this statement */
    @SuppressWarnings("unchecked")
    default GenArchiveEntry getNextEntry(ArchiveInputStream archiveInputStream) throws IOException {
        return (GenArchiveEntry) archiveInputStream.getNextEntry();
    }

    default HashMap<String, ArchiveEntryWithData<GenArchiveEntry>> readAllEntries(ArchiveInputStream archiveInputStream) throws IOException {
        HashMap<String, ArchiveEntryWithData<GenArchiveEntry>> entries = new HashMap<>();
        GenArchiveEntry entry = getNextEntry(archiveInputStream);
        while (entry != null) {
            entries.put(entry.getName(), new ArchiveEntryWithData<>(entry, IOUtils.toByteArray(archiveInputStream)));
            entry = getNextEntry(archiveInputStream);
        }
        return entries;
    }

}
