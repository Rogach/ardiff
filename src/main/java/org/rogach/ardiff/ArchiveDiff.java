package org.rogach.ardiff;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.rogach.ardiff.exceptions.ArchiveDiffException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;

public abstract class ArchiveDiff<GenArchiveEntry extends ArchiveEntry>
        implements ArchiveDiffWriter<GenArchiveEntry>, ArchiveDiffReader<GenArchiveEntry> {

    static final String HEADER = "_ardiff_";

    static final byte COMMAND_ADD = 1;
    static final byte COMMAND_REPLACE = 2;
    static final byte COMMAND_REMOVE = 3;
    static final byte COMMAND_PATCH = 4;
    static final byte COMMAND_ARCHIVE_PATCH = 5;
    static final byte COMMAND_UPDATE_ATTRIBUTES = 6;


    public static void computeDiff(
            InputStream before,
            InputStream after,
            String archiveType,
            boolean assumeOrdering,
            OutputStream diff
    ) throws ArchiveException, IOException {
        if ("zip".equals(archiveType)) {
            new ZipArchiveDiff().computeDiff(before, after, assumeOrdering, diff);
        } else {
            throw new RuntimeException("Unsupported archive type: " + archiveType);
        }
    }

    public static void applyDiff(
            InputStream before,
            InputStream diff,
            String archiveType,
            boolean assumeOrdering,
            OutputStream after
    ) throws ArchiveException, IOException, ArchiveDiffException {
        if ("zip".equals(archiveType)) {
            new ZipArchiveDiff().applyDiff(before, diff, assumeOrdering, after);
        } else {
            throw new RuntimeException("Unsupported archive type: " + archiveType);
        }
    }

    public static ArchiveDiff comparatorForArchiveType(String archiveType) {
        if ("zip".equals(archiveType)) {
            return new ZipArchiveDiff();
        } else {
            throw new RuntimeException("Unsupported archive type: " + archiveType);
        }
    }

    static boolean isSupportedArchive(ArchiveEntry entry) {
        return getArchiverType(entry) != null;
    }

    static String getArchiverType(ArchiveEntry entry) {
        if (entry.getName().endsWith(".zip")) {
            return "zip";
        } else {
            return null;
        }
    }

    public boolean archivesEqual(InputStream streamBefore, InputStream streamAfter) throws IOException, ArchiveException {
        ArchiveInputStream archiveStreamBefore = new ArchiveStreamFactory().createArchiveInputStream(archiverName(), streamBefore);
        ArchiveInputStream archiveStreamAfter = new ArchiveStreamFactory().createArchiveInputStream(archiverName(), streamAfter);

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
                if (!Arrays.equals(entryBefore.data, entryAfter.data)) {
                    System.err.printf("data differs for entries at '%s'\n", path);
                    return false;
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
