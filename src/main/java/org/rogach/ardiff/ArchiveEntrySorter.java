package org.rogach.ardiff;

import org.apache.commons.compress.archivers.*;
import org.apache.commons.compress.utils.IOUtils;
import org.rogach.ardiff.exceptions.ArchiveDiffException;

import java.io.*;
import java.util.List;

public interface ArchiveEntrySorter<GenArchiveEntry extends ArchiveEntry> extends ArchiveDiffBase<GenArchiveEntry> {

    default void sortArchiveEntriesImpl(InputStream input, OutputStream output) throws IOException, ArchiveException, ArchiveDiffException {
        ArchiveInputStream archiveInputStream = createArchiveInputStream(input);

        List<ArchiveEntryWithData<GenArchiveEntry>> entries = listAllEntries(archiveInputStream);

        ArchiveOutputStream archiveOutputStream = createArchiveOutputStream(output);

        for (ArchiveEntryWithData<GenArchiveEntry> archiveEntry : entries) {
            if (ArchiveDiff.isSupportedArchive(archiveEntry.entry)) {

                ByteArrayOutputStream sortedArchiveOutputStream = new ByteArrayOutputStream();
                ArchiveDiff.sortArchiveEntries(
                        new ByteArrayInputStream(archiveEntry.data),
                        sortedArchiveOutputStream
                );
                sortedArchiveOutputStream.close();

                byte[] sortedArchive = sortedArchiveOutputStream.toByteArray();

                archiveOutputStream.putArchiveEntry(getEntryForData(archiveEntry.entry, sortedArchive));
                IOUtils.copy(new ByteArrayInputStream(sortedArchive), archiveOutputStream);
                archiveOutputStream.closeArchiveEntry();

            } else {

                archiveOutputStream.putArchiveEntry(archiveEntry.entry);
                IOUtils.copy(new ByteArrayInputStream(archiveEntry.data), archiveOutputStream);
                archiveOutputStream.closeArchiveEntry();

            }
        }

        archiveOutputStream.finish();
    }

    GenArchiveEntry getEntryForData(GenArchiveEntry entry, byte[] data);

}
