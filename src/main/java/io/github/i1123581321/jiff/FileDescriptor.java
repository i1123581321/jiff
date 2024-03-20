package io.github.i1123581321.jiff;


import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


record FileDescriptor(
        Path path,
        long size,
        boolean isDirectory
) implements Comparable<FileDescriptor> {
    FileDescriptor(Path root, Path path) throws IOException {
        this(
                root.relativize(path),
                Files.size(path),
                Files.isDirectory(path)
        );
    }

    @Override
    public int compareTo(@NotNull FileDescriptor o) {
        return path.compareTo(o.path);
    }
}
