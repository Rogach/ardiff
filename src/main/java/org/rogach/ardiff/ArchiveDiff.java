package org.rogach.ardiff;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.rogach.ardiff.exceptions.ArchiveDiffException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

public abstract class ArchiveDiff<GenArchiveEntry extends ArchiveEntry>
        implements ArchiveDiffWriter<GenArchiveEntry>, ArchiveDiffReader<GenArchiveEntry>, ArchiveComparator<GenArchiveEntry>, ArchiveEntrySorter<GenArchiveEntry> {

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
            OutputStream diff
    ) throws ArchiveDiffException, ArchiveException, IOException {
        computeDiff(before, after, diff, false);
    }

    public static void computeDiff(
            InputStream before,
            InputStream after,
            OutputStream diff,
            boolean assumeOrdering
    ) throws ArchiveDiffException, ArchiveException, IOException {
        String beforeArchiveType = detectArchiveType(before);
        String afterArchiveType = detectArchiveType(after);

        if (!Objects.equals(beforeArchiveType, afterArchiveType)) {
            throw new ArchiveDiffException(String.format("Unable to compute diff for different archive types: before=%s, after=%s", beforeArchiveType, afterArchiveType));
        }

        computeDiff(before, after, beforeArchiveType, assumeOrdering, diff);
    }

    public static void computeDiff(
            InputStream before,
            InputStream after,
            String archiveType,
            boolean assumeOrdering,
            OutputStream diff
    ) throws ArchiveException, ArchiveDiffException, IOException {
        if ("zip".equals(archiveType)) {
            new ZipArchiveDiff().computeDiff(before, after, assumeOrdering, diff);
        } else {
            throw new RuntimeException("Unsupported archive type: " + archiveType);
        }
    }

    public static void applyDiff(
            InputStream before,
            InputStream diff,
            OutputStream after
    ) throws ArchiveException, IOException, ArchiveDiffException {
        applyDiff(before, diff, after, false);
    }

    public static void applyDiff(
            InputStream before,
            InputStream diff,
            OutputStream after,
            boolean assumeOrdering
    ) throws ArchiveException, IOException, ArchiveDiffException {
        String archiveType = detectArchiveType(before);
        applyDiff(before, diff, archiveType, assumeOrdering, after);
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

    public static boolean archivesAreEqual(InputStream before, InputStream after) throws ArchiveDiffException, ArchiveException, IOException {
        String beforeArchiveType = detectArchiveType(before);
        String afterArchiveType = detectArchiveType(after);

        if (!Objects.equals(beforeArchiveType, afterArchiveType)) {
            throw new ArchiveDiffException(String.format("Unable to compute diff for different archive types: before=%s, after=%s", beforeArchiveType, afterArchiveType));
        }
        return comparatorForArchiveType(beforeArchiveType).archivesEqual(before, after);
    }

    public static ArchiveComparator comparatorForArchiveType(String archiveType) {
        if ("zip".equals(archiveType)) {
            return new ZipArchiveDiff();
        } else {
            throw new RuntimeException("Unsupported archive type: " + archiveType);
        }
    }

    public static void sortArchiveEntries(InputStream input, OutputStream output) throws IOException, ArchiveDiffException, ArchiveException {
        String archiveType = detectArchiveType(input);
        if ("zip".equals(archiveType)) {
            new ZipArchiveDiff().sortArchiveEntriesImpl(input, output);
        } else {
            throw new RuntimeException("Unsupported archive type: " + archiveType);
        }
    }

    public static String detectArchiveType(InputStream in) throws ArchiveDiffException, IOException {
        try {
            final byte[] signature = new byte[12];
            in.mark(signature.length);
            int signatureLength = IOUtils.readFully(in, signature);
            in.reset();
            if (ZipArchiveInputStream.matches(signature, signatureLength)) {
                return "zip";
            }
            throw new ArchiveDiffException("Unable to detect archive type - no supported archive type found for signature");
        } catch (IOException ex) {
            throw new ArchiveDiffException("Could not use reset and mark operations on input stream", ex);
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


}
