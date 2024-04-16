package io.github.i1123581321.jiff;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

record FileDescriptor(Path root, Path path, long size, boolean isDirectory)
    implements Comparable<FileDescriptor> {
  FileDescriptor(Path root, Path path) throws IOException {
    this(
        root,
        root.relativize(path),
        Files.isDirectory(path) ? 0L : Files.size(path), // if directory, set size to zero
        Files.isDirectory(path));
  }

  @Override
  public int compareTo(FileDescriptor o) {
    return path.compareTo(o.path);
  }
}
