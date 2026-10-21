package su.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Writes the sources of one target into {@link #getOutputDirectory()}. */
public abstract class PreprocessTask extends DefaultTask {
    /** Source roots, in order. A file may exist in only one of them. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceRoots();

    /** Optional {@code condition | from => to} lines. */
    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getSwapFile();

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Input
    public abstract SetProperty<String> getFlags();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void run() throws IOException {
        Preprocessor preprocessor = new Preprocessor(getMinecraftVersion().get(), getFlags().get(), readSwaps());
        Path output = getOutputDirectory().get().getAsFile().toPath();
        deleteContents(output);
        Map<String, Path> seen = new HashMap<>();
        List<String> errors = new ArrayList<>();
        for (File rootFile : getSourceRoots().getFiles()) {
            Path root = rootFile.toPath();
            if (!Files.isDirectory(root)) {
                continue;
            }
            List<Path> files;
            try (Stream<Path> walk = Files.walk(root)) {
                files = walk.filter(Files::isRegularFile).sorted().toList();
            }
            for (Path file : files) {
                String relative = root.relativize(file).toString().replace(File.separatorChar, '/');
                Path previous = seen.put(relative, file);
                if (previous != null) {
                    errors.add(relative + " exists in both " + previous + " and " + file);
                    continue;
                }
                Path target = output.resolve(relative);
                Files.createDirectories(target.getParent());
                if (relative.endsWith(".java")) {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    try {
                        Files.writeString(target, preprocessor.process(file.toString(), text), StandardCharsets.UTF_8);
                    } catch (IllegalArgumentException e) {
                        errors.add(e.getMessage());
                    }
                } else {
                    Files.copy(file, target);
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new GradleException("Preprocessing failed:\n  " + String.join("\n  ", errors));
        }
    }

    private List<Preprocessor.Swap> readSwaps() throws IOException {
        List<Preprocessor.Swap> swaps = new ArrayList<>();
        if (!getSwapFile().isPresent()) {
            return swaps;
        }
        File file = getSwapFile().get().getAsFile();
        int number = 0;
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            number++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int bar = trimmed.indexOf('|');
            int arrow = trimmed.indexOf("=>");
            if (bar < 0 || arrow < bar) {
                throw new GradleException(file + ":" + number + ": expected 'condition | from => to'");
            }
            swaps.add(new Preprocessor.Swap(trimmed.substring(0, bar).trim(), trimmed.substring(bar + 1, arrow).trim(),
                trimmed.substring(arrow + 2).trim()));
        }
        return swaps;
    }

    private static void deleteContents(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path path : walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                if (!path.equals(dir)) {
                    Files.delete(path);
                }
            }
        }
    }
}
