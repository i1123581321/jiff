package sputnik.jiff;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import picocli.CommandLine;

@CommandLine.Command(
    name = "Jiff",
    mixinStandardHelpOptions = true,
    version = "0.1.0",
    description = "Java diff")
public class Jiff implements Callable<Integer> {

  @CommandLine.Parameters(index = "0", description = "Source directory (newer)")
  Path src;

  @CommandLine.Parameters(index = "1", description = "Destination directory (older)")
  Path dst;

  @CommandLine.Option(
      names = {"-s", "--strict"},
      description =
          "Strict mode. Files of the same size will be tested for equality by calculating a checksum")
  boolean strict = false;

  @CommandLine.Option(
      names = {"-m", "--merge"},
      description = "Merge src to dst")
  boolean merge = false;

  @CommandLine.Option(
      names = {"-c", "--chunk-size"},
      description = "Checksum chunk size (KiB)")
  int chunk_size = 16;

  @Override
  public Integer call() {
    var comparator = new FileComparator(src, dst, chunk_size);

    long start = System.currentTimeMillis();

    var srcMap = getDescriptors(src);
    var dstMap = getDescriptors(dst);

    // 路径未变更，内容变更
    Map<FileDescriptor, FileDescriptor> modified = new ConcurrentHashMap<>();
    // 内容未变更，路径变更
    Map<FileDescriptor, FileDescriptor> moved = new ConcurrentHashMap<>();
    // src 中新增的文件
    Set<FileDescriptor> added = ConcurrentHashMap.newKeySet();
    // src 中删除的文件
    Set<FileDescriptor> deleted = ConcurrentHashMap.newKeySet();

    var unchanged = Sets.intersection(srcMap.keySet(), dstMap.keySet());

    unchanged.parallelStream()
        .forEach(
            p -> {
              var src = srcMap.get(p);
              var dst = dstMap.get(p);
              if (src.isDirectory() != dst.isDirectory()) {
                added.add(src);
                deleted.add(dst);
              } else if (src.size() != dst.size()) {
                modified.put(src, dst);
              } else if (strict && !comparator.strictEq(src, dst)) {
                modified.put(src, dst);
              }
            });

    added.addAll(
        Sets.difference(srcMap.keySet(), dstMap.keySet()).stream().map(srcMap::get).toList());
    deleted.addAll(
        Sets.difference(dstMap.keySet(), srcMap.keySet()).stream().map(dstMap::get).toList());

    filterSet(added, deleted, comparator::moved).entrySet().parallelStream()
        .forEach(
            e -> {
              if (strict) {
                if (comparator.strictEq(e.getKey(), e.getValue())) {
                  moved.put(e.getKey(), e.getValue());
                } else {
                  added.add(e.getKey());
                  deleted.add(e.getValue());
                }
              } else {
                moved.put(e.getKey(), e.getValue());
              }
            });

    deleted.stream().sorted().forEach(s -> System.out.println(formatDeleted(s)));
    added.stream().sorted().forEach(s -> System.out.println(formatAdded(s)));
    modified.keySet().stream()
        .sorted()
        .forEach(s -> System.out.println(formatModified(s, modified.get(s))));
    moved.keySet().stream().sorted().forEach(s -> System.out.println(formatMoved(s, moved.get(s))));

    long end = System.currentTimeMillis();
    System.out.printf("Time elapsed: %fs\n", (end - start) / 1000.0);

    if (!merge) {
      return 0;
    }

    deleted.forEach(
        p -> {
          var target = dst.resolve(p.path());
          try {
            Files.delete(target);
          } catch (IOException e) {
            System.err.printf("Delete %s failed: %s\n", target, e.getMessage());
          }
        });
    added.forEach(
        p -> {
          var source = src.resolve(p.path());
          var target = dst.resolve(p.path());
          try {
            if (p.isDirectory()) {
              Files.createDirectories(target);
            } else {
              Files.copy(source, target);
            }
          } catch (IOException e) {
            if (p.isDirectory()) {
              System.err.printf("Create directory %s failed: %s\n", target, e.getMessage());
            } else {
              System.err.printf("Copy %s to %s failed: %s\n", source, target, e.getMessage());
            }
          }
        });
    modified.forEach(
        (s, t) -> {
          var source = src.resolve(s.path());
          var target = dst.resolve(t.path());
          try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
          } catch (IOException e) {
            System.err.printf("Copy %s to %s failed: %s\n", source, target, e.getMessage());
          }
        });
    moved.forEach(
        (s, t) -> {
          var before = dst.resolve(t.path());
          var after = dst.resolve(s.path());
          try {
            Files.move(before, after, StandardCopyOption.REPLACE_EXISTING);
          } catch (IOException e) {
            System.err.println(e.getMessage());
          }
        });

    return 0;
  }

  public static void main(String[] args) {
    System.exit(new CommandLine(new Jiff()).execute(args));
  }

  static String formatModified(FileDescriptor srcFile, FileDescriptor dstFile) {
    StringBuilder builder = new StringBuilder();
    builder.append("Modified: ").append(srcFile.path());
    if (srcFile.size() == dstFile.size()) {
      builder.append(" (content modified)");
    } else {
      builder.append(String.format(" (%d -> %d)", dstFile.size(), srcFile.size()));
    }
    return builder.toString();
  }

  static String formatMoved(FileDescriptor srcFile, FileDescriptor dstFile) {
    return String.format("Moved: %s -> %s", dstFile.path(), srcFile.path());
  }

  static String formatAdded(FileDescriptor file) {
    return String.format("Added: %s (%s)", file.path(), file.isDirectory() ? "D" : "F");
  }

  static String formatDeleted(FileDescriptor file) {
    return String.format("Deleted: %s (%s)", file.path(), file.isDirectory() ? "D" : "F");
  }

  static Map<Path, FileDescriptor> getDescriptors(Path root) {
    Map<Path, FileDescriptor> result = new ConcurrentHashMap<>();
    try (var stream = Files.walk(root)) {
      stream.forEach(
          path -> {
            if (path != root) {
              try {
                FileDescriptor descriptor = new FileDescriptor(root, path);
                result.put(descriptor.path(), descriptor);
              } catch (IOException e) {
                System.err.printf("Create file descriptor %s failed: %s\n", path, e.getMessage());
              }
            }
          });
    } catch (IOException e) {
      System.err.printf("Walk %s failed: %s\n", root, e.getMessage());
    }
    return result;
  }

  static <T> Map<T, T> filterSet(Set<T> src, Set<T> dst, BiPredicate<T, T> predicate) {
    Map<T, T> result = new HashMap<>();
    src.forEach(
        e1 ->
            dst.forEach(
                e2 -> {
                  if (predicate.test(e1, e2)) {
                    result.put(e1, e2);
                  }
                }));
    src.removeAll(result.keySet());
    dst.removeAll(result.values());
    return result;
  }
}
