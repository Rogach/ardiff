package org.rogach.ardiff;

import org.apache.commons.compress.archivers.*;
import org.apache.commons.compress.utils.IOUtils;
import org.rogach.ardiff.exceptions.ArchiveDiffException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public interface ArchiveDiffBase<GenArchiveEntry extends ArchiveEntry> {

    String archiverName();

    default ArchiveOutputStream createArchiveOutputStream(OutputStream output) throws IOException, ArchiveDiffException, ArchiveException {
        return new ArchiveStreamFactory().createArchiveOutputStream(archiverName(), output);
    }

    default ArchiveInputStream createArchiveInputStream(InputStream input) throws IOException, ArchiveDiffException, ArchiveException {
        return new ArchiveStreamFactory().createArchiveInputStream(archiverName(), input);
    }

    boolean attributesEqual(GenArchiveEntry entryBefore, GenArchiveEntry entryAfter);

    /* separated into separate method so that we suppress warnings only for this statement */
    @SuppressWarnings("unchecked")
    default GenArchiveEntry getNextEntry(ArchiveInputStream archiveInputStream) throws IOException {
        return (GenArchiveEntry) archiveInputStream.getNextEntry();
    }

    default List<ArchiveEntryWithData<GenArchiveEntry>> listAllEntries(ArchiveInputStream archiveInputStream) throws IOException {

        List<ArchiveEntryWithData<GenArchiveEntry>> entries = new ArrayList<>();
        GenArchiveEntry entry = getNextEntry(archiveInputStream);
        while (entry != null) {
            entries.add(new ArchiveEntryWithData<>(entry, IOUtils.toByteArray(archiveInputStream)));
            entry = getNextEntry(archiveInputStream);
        }

        Collections.sort(entries, archiveEntryComparator());

        return entries;
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

    default Comparator<ArchiveEntryWithData<GenArchiveEntry>> archiveEntryComparator() {
        return (o1, o2) -> o1.entry.getName().compareTo(o2.entry.getName());
    }

}
