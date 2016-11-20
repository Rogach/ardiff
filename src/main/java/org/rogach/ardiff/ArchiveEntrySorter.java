package org.rogach.ardiff;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.rogach.ardiff.exceptions.ArchiveDiffException;
import org.rogach.ardiff.formats.ArArchiveDiff;

import java.io.*;
import java.util.List;

public interface ArchiveEntrySorter<GenArchiveEntry extends ArchiveEntry> extends ArchiveDiffBase<GenArchiveEntry> {

    default void sortArchiveEntriesImpl(InputStream input, OutputStream output) throws IOException, ArchiveException, ArchiveDiffException {
        ArchiveInputStream archiveInputStream = createArchiveInputStream(input);

        // we must not sort AR, because dpkg explicitly specifies archive entry order
        List<ArchiveEntryWithData<GenArchiveEntry>> entries = listAllEntries(archiveInputStream, !(this instanceof ArArchiveDiff));

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

        finishArchiveOutputStream(archiveOutputStream);
    }

    GenArchiveEntry getEntryForData(GenArchiveEntry entry, byte[] data);

}
