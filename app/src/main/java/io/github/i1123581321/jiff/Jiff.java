package io.github.i1123581321.jiff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import com.sun.jna.platform.FileUtils;
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
      names = {"-c", "--chunk-size"},
      description = "Checksum chunk size (KiB)")
  int chunk_size = 16;

  @Override
  public Integer call() {
    System.out.println("(拒绝访问。)");
    System.err.println("(拒绝访问。)");
    FileUtils fileUtils = FileUtils.getInstance();
    var comparator = new FileComparator(src, dst, chunk_size);

    long start = System.currentTimeMillis();

    var srcMap = getDescriptors(src);
    var dstMap = getDescriptors(dst);

    // 路径未变更，内容变更
    Map<FileDescriptor, FileDescriptor> modified = new ConcurrentHashMap<>();
    // 内容未变更，路径变更
    Map<FileDescriptor, FileDescriptor> moved;
    // src 中新增的文件
    Set<FileDescriptor> added;
    // src 中删除的文件
    Set<FileDescriptor> deleted;

    var unchanged = new HashSet<>(srcMap.keySet());
    unchanged.retainAll(dstMap.keySet());
//
   unchanged.parallelStream()
       .forEach(
           p -> {
             var src = srcMap.get(p);
             var dst = dstMap.get(p);
             if (src.size() != dst.size()) {
               modified.put(src, dst);
             } else if (strict) {
               if (!comparator.strictEq(src, dst)) {
                 modified.put(src, dst);
               }
             }
           });

//    modified.keySet().stream()
//        .sorted()
//        .forEach(
//            s ->
//                System.out.printf(
//                    "Modified: %s %d -> %d\n", s.path(), s.size(), modified.get(s).size()));

//    return 0;

//    added = srcSet.parallelStream().filter(e -> !dstSet.contains(e)).collect(Collectors.toSet());
//    deleted = dstSet.parallelStream().filter(e -> !srcSet.contains(e)).collect(Collectors.toSet());
//
//    if (strict) {
//      srcSet.parallelStream()
//          .filter(dstSet::contains)
//          .forEach(
//              e -> {
//                if (!e.isDirectory() && !comparator.strictEq(e, e)) {
//                  modified.put(e, e);
//                }
//              });
//    }

//    modified.putAll(filterSet(added, deleted, comparator::modified));
//
//    var temp = filterSet(added, deleted, comparator::moved);
//
//    if (strict) {
//      moved = new HashMap<>();
//      temp.keySet().parallelStream()
//          .forEach(
//              k -> {
//                if (comparator.strictEq(k, temp.get(k))) {
//                  moved.put(k, temp.get(k));
//                } else {
//                  added.add(k);
//                  deleted.add(temp.get(k));
//                }
//              });
//    } else {
//      moved = temp;
//    }
//
//    added.stream().sorted().forEach(e -> System.out.printf("Added: %s\n", e.path()));
//    modified.keySet().stream()
//        .sorted()
//        .forEach(
//            k ->
//                System.out.printf(
//                    "Modified: %s %d -> %d\n", k.path(), k.size(), modified.get(k).size()));
//    moved.keySet().stream()
//        .sorted()
//        .forEach(k -> System.out.printf("Moved: %s -> %s\n", moved.get(k).path(), k.path()));
//    deleted.stream().sorted().forEach(e -> System.out.printf("Deleted: %s\n", e.path()));
//    long end = System.currentTimeMillis();
//
//    System.out.printf("Time elapsed: %fs\n", (end - start) / 1000.0);
//
//    System.out.println("merge?(y/N)");
//    Scanner scanner = new Scanner(System.in);
//
//    String reply = scanner.nextLine();
//    if (!reply.equals("y")) {
//      return 0;
//    }
//
//    // move added to dst
//    added.forEach(
//        p -> {
//          var source = src.resolve(p.path());
//          var target = dst.resolve(p.path());
//          try {
//            if (p.isDirectory()) {
//              Files.createDirectories(target);
//            } else {
//              Files.copy(source, target);
//            }
//          } catch (IOException e) {
//            System.err.println(e.getMessage());
//          }
//        });
//    modified.forEach((s, t) -> {});
//
//    moved.forEach(
//        (s, t) -> {
//          var before = dst.resolve(t.path());
//          var after = dst.resolve(s.path());
//          try {
//            Files.move(before, after, StandardCopyOption.REPLACE_EXISTING);
//          } catch (IOException e) {
//            System.err.println(e.getMessage());
//          }
//        });
//    deleted.forEach(
//        p -> {
//          var target = dst.resolve(p.path());
//          try {
//            if (fileUtils.hasTrash()) {
//              fileUtils.moveToTrash(target.toFile());
//            } else {
//              Files.delete(target);
//            }
//          } catch (IOException e) {
//            System.err.println(e.getMessage());
//          }
//        });
    return 0;
  }

  public static void main(String[] args) {
    System.exit(new CommandLine(new Jiff()).execute(args));
  }

  static Map<Path, FileDescriptor> getDescriptors(Path root) {
    Map<Path, FileDescriptor> result = new HashMap<>();
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
