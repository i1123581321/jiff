package io.github.i1123581321.jiff;

import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

@CommandLine.Command(name = "Jiff", mixinStandardHelpOptions = true, version = "0.1.0", description = "Java diff")
public class Jiff implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "Source directory (newer)")
    Path src;

    @CommandLine.Parameters(index = "1", description = "Destination directory (older)")
    Path dst;

    @CommandLine.Option(names = {"-s", "--strict"}, description = "Strict mode. Files of the same size will be tested for equality by calculating a checksum")
    boolean strict = false;

    @CommandLine.Option(names = {"-c", "--chunk-size"}, description = "Checksum chunk size (KiB)")
    int chunk_size = 16;

    @Override
    public Integer call() {
        long start = System.currentTimeMillis();
        var srcSet = getDescriptors(src);
        var dstSet = getDescriptors(dst);
        var comparator = new FileComparator(src, dst, chunk_size);

        Map<FileDescriptor, FileDescriptor> modified = new HashMap<>();

        if (strict) {
            srcSet.parallelStream().filter(dstSet::contains).forEach(e -> {
                if (!e.isDirectory() && !comparator.strictEq(e, e)) {
                    modified.put(e, e);
                }
            });
        }

        var added = srcSet.parallelStream().filter(e -> !dstSet.contains(e)).collect(Collectors.toSet());
        var deleted = dstSet.parallelStream().filter(e -> !srcSet.contains(e)).collect(Collectors.toSet());

        modified.putAll(filterSet(added, deleted, comparator::modified));
        var temp = filterSet(added, deleted, comparator::moved);

        Map<FileDescriptor, FileDescriptor> moved;

        if (strict) {
            moved = new HashMap<>();
            temp.keySet().parallelStream().forEach(k -> {
                if (comparator.strictEq(k, temp.get(k))) {
                    moved.put(k, temp.get(k));
                } else {
                    added.add(k);
                    deleted.add(temp.get(k));
                }
            });
        } else {
            moved = temp;
        }

        added.stream().sorted().forEach(e -> System.out.println(STR."Added: \{e.path()}"));
        modified.keySet().stream().sorted().forEach(k -> System.out.println(STR."Modified: \{k.path()} \{k.size()} -> \{modified.get(k).size()}"));
        moved.keySet().stream().sorted().forEach(k -> System.out.println(STR."Moved: \{k.path()} -> \{moved.get(k).path()}"));
        deleted.stream().sorted().forEach(e -> System.out.println(STR."deleted: \{e.path()}"));
        long end = System.currentTimeMillis();

        System.out.println(STR."Time elapsed: \{(end - start) / 1000}s");
        return 0;

    }

    public static void main(String @NotNull [] args) {
        System.exit(new CommandLine(new Jiff()).execute(args));
    }

    static @NotNull Set<FileDescriptor> getDescriptors(Path root) {
        Set<FileDescriptor> result = new HashSet<>();
        try (var stream = Files.walk(root)) {
            stream.parallel().forEach(path -> {
                if (path != root) {
                    try {
                        result.add(new FileDescriptor(root, path));
                    } catch (IOException exception) {
                        System.err.println(exception.getMessage());
                    }
                }
            });
        } catch (IOException exception) {
            System.err.println(exception.getMessage());
        }
        return result;
    }

    static @NotNull Map<FileDescriptor, FileDescriptor> filterSet(@NotNull Set<FileDescriptor> src, @NotNull Set<FileDescriptor> dst, BiPredicate<FileDescriptor, FileDescriptor> predicate) {
        Map<FileDescriptor, FileDescriptor> result = new HashMap<>();
        src.parallelStream().forEach(e1 -> dst.parallelStream().forEach(e2 -> {
            if (predicate.test(e1, e2)) {
                result.put(e1, e2);
            }
        }));
        src.removeAll(result.keySet());
        dst.removeAll(result.values());
        return result;
    }
}
