package io.casehub.ops.claudony;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.ops.api.claudony.ClaudonyGoalEntry;
import io.casehub.ops.api.claudony.ClaudonyAppNodeSpec;
import io.casehub.ops.api.claudony.ClaudonyGoals;
import io.casehub.ops.api.claudony.ClaudonyPoolNodeSpec;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@ApplicationScoped
public class ClaudonyGoalLoader {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
        .registerModule(new JavaTimeModule());

    public ClaudonyGoals load(String path) {
        try (var stream = resolveStream(path)) {
            if (stream == null) return new ClaudonyGoals(List.of(), List.of());
            return yamlMapper.readValue(stream, ClaudonyGoals.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ClaudonyGoals loadDirectory(String directoryPath) {
        var dir = Path.of(directoryPath);
        if (!Files.isDirectory(dir)) return new ClaudonyGoals(List.of(), List.of());

        try (Stream<Path> files = Files.list(dir)) {
            var yamlFiles = files
                .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                .sorted()
                .toList();

            var allApps = new ArrayList<ClaudonyGoalEntry<ClaudonyAppNodeSpec>>();
            var allPools = new ArrayList<ClaudonyGoalEntry<ClaudonyPoolNodeSpec>>();

            for (var file : yamlFiles) {
                var goals = yamlMapper.readValue(file.toFile(), ClaudonyGoals.class);
                allApps.addAll(goals.apps());
                allPools.addAll(goals.pools());
            }
            return new ClaudonyGoals(allApps, allPools);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private InputStream resolveStream(String path) {
        var classpath = getClass().getClassLoader().getResourceAsStream(path);
        if (classpath != null) return classpath;
        var file = Path.of(path);
        if (Files.exists(file)) {
            try { return Files.newInputStream(file); }
            catch (IOException e) { throw new UncheckedIOException(e); }
        }
        return null;
    }
}
