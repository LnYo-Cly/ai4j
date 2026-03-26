package io.github.lnyocly.ai4j.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CustomCommandRegistry {

    private final Path workspaceRoot;

    public CustomCommandRegistry(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public List<CustomCommandTemplate> list() {
        Map<String, CustomCommandTemplate> commands = new LinkedHashMap<String, CustomCommandTemplate>();
        loadDirectory(homeCommandsDirectory(), commands);
        loadDirectory(workspaceCommandsDirectory(), commands);
        List<CustomCommandTemplate> values = new ArrayList<CustomCommandTemplate>(commands.values());
        Collections.sort(values, java.util.Comparator.comparing(CustomCommandTemplate::getName));
        return values;
    }

    public CustomCommandTemplate find(String name) {
        if (isBlank(name)) {
            return null;
        }
        for (CustomCommandTemplate command : list()) {
            if (name.equalsIgnoreCase(command.getName())) {
                return command;
            }
        }
        return null;
    }

    private void loadDirectory(Path directory, Map<String, CustomCommandTemplate> commands) {
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path file : stream) {
                if (!hasSupportedExtension(file)) {
                    continue;
                }
                CustomCommandTemplate command = loadTemplate(file);
                if (command != null) {
                    commands.put(command.getName(), command);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private CustomCommandTemplate loadTemplate(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            String raw = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) {
                return null;
            }
            String fileName = file.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            String name = dot > 0 ? fileName.substring(0, dot) : fileName;
            String description = null;
            String template = raw;
            int newline = raw.indexOf('\n');
            if (raw.startsWith("#") && newline > 0) {
                description = raw.substring(1, newline).trim();
                template = raw.substring(newline + 1).trim();
            }
            return new CustomCommandTemplate(name, description, template, file.toAbsolutePath().normalize().toString());
        } catch (IOException ex) {
            return null;
        }
    }

    private Path workspaceCommandsDirectory() {
        return workspaceRoot == null ? null : workspaceRoot.resolve(".ai4j").resolve("commands");
    }

    private Path homeCommandsDirectory() {
        String home = System.getProperty("user.home");
        if (isBlank(home)) {
            return null;
        }
        return Paths.get(home).resolve(".ai4j").resolve("commands");
    }

    private boolean hasSupportedExtension(Path file) {
        if (file == null || file.getFileName() == null) {
            return false;
        }
        String fileName = file.getFileName().toString().toLowerCase();
        return fileName.endsWith(".md") || fileName.endsWith(".txt") || fileName.endsWith(".prompt");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
