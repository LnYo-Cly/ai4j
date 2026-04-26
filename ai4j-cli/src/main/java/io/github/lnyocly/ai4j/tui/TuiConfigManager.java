package io.github.lnyocly.ai4j.tui;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TuiConfigManager {

    private static final List<String> BUILT_IN_THEMES = Arrays.asList(
            "default",
            "amber",
            "ocean",
            "matrix",
            "github-dark",
            "github-light"
    );

    private final Path workspaceRoot;

    public TuiConfigManager(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot == null ? Paths.get(".").toAbsolutePath().normalize() : workspaceRoot.toAbsolutePath().normalize();
    }

    public TuiConfig load(String overrideTheme) {
        TuiConfig config = merge(loadConfig(homeConfigPath()), loadConfig(workspaceConfigPath()));
        if (config == null) {
            config = new TuiConfig();
        }
        normalize(config);
        if (!isBlank(overrideTheme)) {
            config.setTheme(overrideTheme.trim());
        }
        return config;
    }

    public TuiConfig save(TuiConfig config) throws IOException {
        TuiConfig normalized = config == null ? new TuiConfig() : config;
        normalize(normalized);
        Files.createDirectories(workspaceConfigPath().getParent());
        String json = JSON.toJSONString(normalized, JSONWriter.Feature.PrettyFormat);
        Files.write(workspaceConfigPath(), json.getBytes(StandardCharsets.UTF_8));
        return normalized;
    }

    public TuiConfig switchTheme(String themeName) throws IOException {
        if (isBlank(themeName)) {
            throw new IllegalArgumentException("theme name is required");
        }
        TuiTheme theme = resolveTheme(themeName);
        if (theme == null) {
            throw new IllegalArgumentException("Unknown TUI theme: " + themeName);
        }
        TuiConfig config = load(null);
        config.setTheme(theme.getName());
        return save(config);
    }

    public TuiTheme resolveTheme(String name) {
        String target = isBlank(name) ? "default" : name.trim();
        TuiTheme theme = loadTheme(workspaceThemePath(target));
        if (theme != null) {
            normalize(theme, target);
            return theme;
        }
        theme = loadTheme(homeThemePath(target));
        if (theme != null) {
            normalize(theme, target);
            return theme;
        }
        theme = loadBuiltInTheme(target);
        if (theme != null) {
            normalize(theme, target);
            return theme;
        }
        if (!"default".equals(target)) {
            return resolveTheme("default");
        }
        return defaultTheme();
    }

    public List<String> listThemeNames() {
        Set<String> names = new LinkedHashSet<String>();
        names.addAll(BUILT_IN_THEMES);
        names.addAll(scanThemeNames(homeThemesDir()));
        names.addAll(scanThemeNames(workspaceThemesDir()));
        return new ArrayList<String>(names);
    }

    private TuiConfig merge(TuiConfig base, TuiConfig override) {
        return override != null ? override : base;
    }

    private TuiConfig loadConfig(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try {
            return JSON.parseObject(Files.readAllBytes(path), TuiConfig.class);
        } catch (IOException ex) {
            return null;
        }
    }

    private TuiTheme loadTheme(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try {
            return JSON.parseObject(Files.readAllBytes(path), TuiTheme.class);
        } catch (IOException ex) {
            return null;
        }
    }

    private TuiTheme loadBuiltInTheme(String name) {
        String resource = "/io/github/lnyocly/ai4j/tui/themes/" + name + ".json";
        try (InputStream inputStream = TuiConfigManager.class.getResourceAsStream(resource)) {
            if (inputStream == null) {
                return null;
            }
            byte[] bytes = readAllBytes(inputStream);
            return JSON.parseObject(bytes, TuiTheme.class);
        } catch (IOException ex) {
            return null;
        }
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[4096];
        int read;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        while ((read = inputStream.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private List<String> scanThemeNames(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return Collections.emptyList();
        }
        try {
            List<String> names = new ArrayList<String>();
            java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json");
            try {
                for (Path path : stream) {
                    String fileName = path.getFileName().toString();
                    names.add(fileName.substring(0, fileName.length() - 5));
                }
            } finally {
                stream.close();
            }
            Collections.sort(names);
            return names;
        } catch (IOException ex) {
            return Collections.emptyList();
        }
    }

    private void normalize(TuiConfig config) {
        if (config == null) {
            return;
        }
        if (isBlank(config.getTheme())) {
            config.setTheme("default");
        }
        if (config.getMaxEvents() <= 0) {
            config.setMaxEvents(10);
        }
    }

    private void normalize(TuiTheme theme, String fallbackName) {
        if (theme == null) {
            return;
        }
        if (isBlank(theme.getName())) {
            theme.setName(fallbackName);
        }
        if (isBlank(theme.getBrand())) {
            theme.setBrand("#7cc6fe");
        }
        if (isBlank(theme.getAccent())) {
            theme.setAccent("#f5b14c");
        }
        if (isBlank(theme.getSuccess())) {
            theme.setSuccess("#8fd694");
        }
        if (isBlank(theme.getWarning())) {
            theme.setWarning("#f4d35e");
        }
        if (isBlank(theme.getDanger())) {
            theme.setDanger("#ef6f6c");
        }
        if (isBlank(theme.getText())) {
            theme.setText("#f3f4f6");
        }
        if (isBlank(theme.getMuted())) {
            theme.setMuted("#9ca3af");
        }
        if (isBlank(theme.getPanelBorder())) {
            theme.setPanelBorder("#4b5563");
        }
        if (isBlank(theme.getPanelTitle())) {
            theme.setPanelTitle(theme.getAccent());
        }
        if (isBlank(theme.getBadgeForeground())) {
            theme.setBadgeForeground("#111827");
        }
        if (isBlank(theme.getCodeBackground())) {
            theme.setCodeBackground("#161b22");
        }
        if (isBlank(theme.getCodeBorder())) {
            theme.setCodeBorder("#30363d");
        }
        if (isBlank(theme.getCodeText())) {
            theme.setCodeText("#c9d1d9");
        }
        if (isBlank(theme.getCodeKeyword())) {
            theme.setCodeKeyword("#ff7b72");
        }
        if (isBlank(theme.getCodeString())) {
            theme.setCodeString("#a5d6ff");
        }
        if (isBlank(theme.getCodeComment())) {
            theme.setCodeComment("#8b949e");
        }
        if (isBlank(theme.getCodeNumber())) {
            theme.setCodeNumber("#79c0ff");
        }
    }

    private TuiTheme defaultTheme() {
        TuiTheme theme = new TuiTheme();
        normalize(theme, "default");
        return theme;
    }

    private Path workspaceConfigPath() {
        return workspaceRoot.resolve(".ai4j").resolve("tui.json");
    }

    private Path workspaceThemesDir() {
        return workspaceRoot.resolve(".ai4j").resolve("themes");
    }

    private Path workspaceThemePath(String name) {
        return workspaceThemesDir().resolve(name + ".json");
    }

    private Path homeConfigPath() {
        String userHome = System.getProperty("user.home");
        return isBlank(userHome) ? null : Paths.get(userHome).resolve(".ai4j").resolve("tui.json");
    }

    private Path homeThemesDir() {
        String userHome = System.getProperty("user.home");
        return isBlank(userHome) ? null : Paths.get(userHome).resolve(".ai4j").resolve("themes");
    }

    private Path homeThemePath(String name) {
        Path homeThemes = homeThemesDir();
        return homeThemes == null ? null : homeThemes.resolve(name + ".json");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

