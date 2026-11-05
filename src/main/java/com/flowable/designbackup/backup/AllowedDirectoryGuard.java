package com.flowable.designbackup.backup;

import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Resolves a user-supplied directory to an absolute, normalized path and
 * confirms it falls under the configured allowed base directory, used by
 * both backup (write) and restore (read) so neither can be pointed outside
 * the directory tree an operator has sanctioned.
 */
public final class AllowedDirectoryGuard {

    private AllowedDirectoryGuard() {
    }

    public static Path resolve(String rawPath, String allowedBaseDir, String propertyName) {
        Path target = Path.of(rawPath).toAbsolutePath().normalize();
        if (!StringUtils.hasText(allowedBaseDir)) {
            return target;
        }
        Path allowed = resolveSymlinks(Path.of(allowedBaseDir).toAbsolutePath());
        if (!resolveSymlinks(target).startsWith(allowed)) {
            throw new IllegalArgumentException(
                    "Directory " + target + " is outside the allowed base directory " + allowed
                            + " (" + propertyName + ")");
        }
        return target;
    }

    /**
     * Resolves symlinks on the deepest existing ancestor so a link inside the
     * allowed base directory cannot redirect the operation outside of it.
     */
    private static Path resolveSymlinks(Path path) {
        Path existing = path.normalize();
        Deque<Path> missingSegments = new ArrayDeque<>();
        while (existing != null && !Files.exists(existing)) {
            missingSegments.push(existing.getFileName());
            existing = existing.getParent();
        }
        if (existing == null) {
            return path.normalize();
        }
        try {
            Path resolved = existing.toRealPath();
            for (Path segment : missingSegments) {
                resolved = resolved.resolve(segment);
            }
            return resolved;
        } catch (IOException e) {
            return path.normalize();
        }
    }
}
