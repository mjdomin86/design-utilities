package com.flowable.designbackup.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllowedDirectoryGuardTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsNormalizedAbsolutePathWhenNoAllowedBaseDirConfigured() {
        Path resolved = AllowedDirectoryGuard.resolve(tempDir.resolve("sub").toString(), null, "some.property");

        assertThat(resolved).isEqualTo(tempDir.resolve("sub").toAbsolutePath().normalize());
    }

    @Test
    void acceptsPathInsideAllowedBaseDir() throws Exception {
        Path sub = Files.createDirectories(tempDir.resolve("sub"));

        Path resolved = AllowedDirectoryGuard.resolve(sub.toString(), tempDir.toString(), "some.property");

        assertThat(resolved).startsWith(tempDir);
    }

    @Test
    void rejectsPathOutsideAllowedBaseDir() {
        Path allowed = tempDir.resolve("allowed");
        Path outside = tempDir.resolve("outside");

        assertThatThrownBy(() -> AllowedDirectoryGuard.resolve(outside.toString(), allowed.toString(), "some.property"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowed base directory")
                .hasMessageContaining("some.property");
    }

    @Test
    void rejectsSymlinkEscapingAllowedBaseDir() throws Exception {
        Path allowed = Files.createDirectories(tempDir.resolve("allowed"));
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        Path link = Files.createSymbolicLink(allowed.resolve("link"), outside);

        assertThatThrownBy(() -> AllowedDirectoryGuard.resolve(link.toString(), allowed.toString(), "some.property"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowed base directory");
    }
}
