package io.github.i1123581321.jiff;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

class FileComparator {
  int CHUNK_SIZE;
  Path src;
  Path dst;

  FileComparator(Path src, Path dst, int chunk_size) {
    this.src = src;
    this.dst = dst;
    this.CHUNK_SIZE = chunk_size * 1024;
  }

  boolean modified(FileDescriptor srcFile, FileDescriptor dstFile) {
    if (srcFile.isDirectory() != dstFile.isDirectory()) {
      return false;
    }
    return (srcFile.path().equals(dstFile.path()) && srcFile.size() != dstFile.size());
  }

  boolean moved(FileDescriptor srcFile, FileDescriptor dstFile) {
    if (srcFile.isDirectory() || dstFile.isDirectory()) {
      return false;
    }
    return srcFile.size() == dstFile.size();
  }

  boolean strictEq(FileDescriptor srcFile, FileDescriptor dstFile) {
    try (var srcFis = new FileInputStream(src.resolve(srcFile.path()).toFile());
        var dstFis = new FileInputStream(dst.resolve(dstFile.path()).toFile())) {
      byte[] srcBuffer = new byte[CHUNK_SIZE];
      byte[] dstBuffer = new byte[CHUNK_SIZE];

      Checksum srcChecksum = new Adler32();
      Checksum dstChecksum = new Adler32();

      int size;

      while ((size = srcFis.read(srcBuffer)) != -1) {
        if (dstFis.read(dstBuffer, 0, size) == -1) {
          return false;
        }

        srcChecksum.update(srcBuffer, 0, size);
        dstChecksum.update(dstBuffer, 0, size);

        if (srcChecksum.getValue() != dstChecksum.getValue()) {
          return false;
        }
      }

    } catch (IOException exception) {
      System.err.println(exception.getMessage());
      return false;
    }
    return true;
  }
}
