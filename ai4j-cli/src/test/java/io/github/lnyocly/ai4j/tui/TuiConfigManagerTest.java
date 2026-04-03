package io.github.lnyocly.ai4j.tui;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TuiConfigManagerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldLoadBuiltInThemesAndPersistWorkspaceConfig() throws Exception {
        Path workspace = temporaryFolder.newFolder("workspace").toPath();
        TuiConfigManager manager = new TuiConfigManager(workspace);

        List<String> themeNames = manager.listThemeNames();
        TuiTheme ocean = manager.resolveTheme("ocean");
        TuiTheme defaults = manager.resolveTheme("default");
        TuiTheme githubDark = manager.resolveTheme("github-dark");
        TuiConfig saved = manager.switchTheme("matrix");
        TuiConfig loaded = manager.load(null);

        assertTrue(themeNames.contains("default"));
        assertTrue(themeNames.contains("ocean"));
        assertTrue(themeNames.contains("github-dark"));
        assertTrue(themeNames.contains("github-light"));
        assertNotNull(ocean);
        assertEquals("ocean", ocean.getName());
        assertEquals("#161b22", defaults.getCodeBackground());
        assertEquals("#30363d", defaults.getCodeBorder());
        assertEquals("#c9d1d9", defaults.getCodeText());
        assertEquals("#ff7b72", defaults.getCodeKeyword());
        assertEquals("#a5d6ff", defaults.getCodeString());
        assertEquals("#8b949e", defaults.getCodeComment());
        assertEquals("#79c0ff", defaults.getCodeNumber());
        assertEquals("github-dark", githubDark.getName());
        assertEquals("#161b22", githubDark.getCodeBackground());
        assertEquals("matrix", saved.getTheme());
        assertEquals("matrix", loaded.getTheme());
        assertTrue(Files.exists(workspace.resolve(".ai4j").resolve("tui.json")));
    }

    @Test
    public void shouldPreferWorkspaceThemeOverrides() throws Exception {
        Path workspace = temporaryFolder.newFolder("workspace-override").toPath();
        Path themesDir = workspace.resolve(".ai4j").resolve("themes");
        Files.createDirectories(themesDir);
        Files.write(themesDir.resolve("custom.json"),
                ("{\n"
                        + "  \"name\": \"custom\",\n"
                        + "  \"brand\": \"#112233\",\n"
                        + "  \"accent\": \"#445566\"\n"
                        + "}").getBytes(StandardCharsets.UTF_8));

        TuiConfigManager manager = new TuiConfigManager(workspace);
        TuiTheme custom = manager.resolveTheme("custom");

        assertEquals("custom", custom.getName());
        assertEquals("#112233", custom.getBrand());
        assertEquals("#161b22", custom.getCodeBackground());
        assertEquals("#ff7b72", custom.getCodeKeyword());
        assertEquals("#79c0ff", custom.getCodeNumber());
        assertTrue(manager.listThemeNames().contains("custom"));
    }
}

