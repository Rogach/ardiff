package org.rogach.ardiff;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.rogach.ardiff.exceptions.ArchiveDiffException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;

public interface ArchiveComparator<GenArchiveEntry extends ArchiveEntry> extends ArchiveDiffBase<GenArchiveEntry> {

    default boolean archivesEqual(InputStream streamBefore, InputStream streamAfter) throws IOException, ArchiveException, ArchiveDiffException {
        ArchiveInputStream archiveStreamBefore = createArchiveInputStream(streamBefore);
        ArchiveInputStream archiveStreamAfter = createArchiveInputStream(streamAfter);

        HashMap<String, ArchiveEntryWithData<GenArchiveEntry>> entriesBefore = readAllEntries(archiveStreamBefore);
        HashMap<String, ArchiveEntryWithData<GenArchiveEntry>> entriesAfter = readAllEntries(archiveStreamAfter);

        for (String path : entriesBefore.keySet()) {
            ArchiveEntryWithData<GenArchiveEntry> entryBefore = entriesBefore.get(path);
            ArchiveEntryWithData<GenArchiveEntry> entryAfter = entriesAfter.get(path);
            if (entryAfter != null) {
                if (!attributesEqual(entryBefore.entry, entryAfter.entry)) {
                    System.err.printf("attributes differ for entries at '%s'\n", path);
                    return false;
                }

                if (ArchiveDiff.isSupportedArchive(entryAfter.entry)) {
                    if (!ArchiveDiff.archivesAreEqual(new ByteArrayInputStream(entryBefore.data), new ByteArrayInputStream(entryAfter.data))) {
                        System.err.printf("archive contents differ for entries at '%s'\n", path);
                        return false;
                    }
                } else {
                    if (!Arrays.equals(entryBefore.data, entryAfter.data)) {
                        System.err.printf("data differs for entries at '%s'\n", path);
                        return false;
                    }
                }
            } else {
                System.err.printf("entry at '%s' was removed\n", path);
                return false;
            }
        }

        for (String path : entriesAfter.keySet()) {
            if (!entriesBefore.containsKey(path)) {
                System.err.printf("entry was added at '%s'\n", path);
                return false;
            }
        }

        return true;
    }


}
