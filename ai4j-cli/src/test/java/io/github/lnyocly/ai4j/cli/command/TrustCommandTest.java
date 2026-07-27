package io.github.lnyocly.ai4j.cli.command;

import io.github.lnyocly.ai4j.cli.hook.TrustedDirsStore;
import io.github.lnyocly.ai4j.tui.StreamsTerminalIO;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TrustCommandTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private StreamsTerminalIO terminal(ByteArrayOutputStream out) {
        return new StreamsTerminalIO(new ByteArrayInputStream(new byte[0]), out, new ByteArrayOutputStream());
    }

    @Test
    public void trust_dir_should_persist_directory() throws Exception {
        TrustedDirsStore store = new TrustedDirsStore(tmp.newFile("store.txt").toPath());
        TrustCommand cmd = new TrustCommand(store);
        Path workspace = tmp.newFolder("workspace").toPath();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = cmd.run(Arrays.asList("--dir", workspace.toString()), terminal(out));

        Assert.assertEquals(0, exit);
        Assert.assertTrue(store.isTrusted(workspace));
        Assert.assertTrue(new String(out.toByteArray()).contains("Trusted:"));
    }

    @Test
    public void trust_dir_inline_equals_should_persist_directory() throws Exception {
        TrustedDirsStore store = new TrustedDirsStore(tmp.newFile("store.txt").toPath());
        TrustCommand cmd = new TrustCommand(store);
        Path workspace = tmp.newFolder("workspace").toPath();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = cmd.run(Arrays.asList("--dir=" + workspace.toString()), terminal(out));

        Assert.assertEquals(0, exit);
        Assert.assertTrue(store.isTrusted(workspace));
    }

    @Test
    public void revoke_dir_should_remove_directory() throws Exception {
        TrustedDirsStore store = new TrustedDirsStore(tmp.newFile("store.txt").toPath());
        Path workspace = tmp.newFolder("workspace").toPath();
        store.trust(workspace);

        TrustCommand cmd = new TrustCommand(store);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = cmd.run(Arrays.asList("--revoke", workspace.toString()), terminal(out));

        Assert.assertEquals(0, exit);
        Assert.assertFalse(store.isTrusted(workspace));
        Assert.assertTrue(new String(out.toByteArray()).contains("Revoked:"));
    }

    @Test
    public void revoke_non_trusted_should_report_not_trusted() throws Exception {
        TrustedDirsStore store = new TrustedDirsStore(tmp.newFile("store.txt").toPath());
        TrustCommand cmd = new TrustCommand(store);
        Path workspace = tmp.newFolder("workspace").toPath();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = cmd.run(Arrays.asList("--revoke", workspace.toString()), terminal(out));

        Assert.assertEquals(0, exit);
        Assert.assertTrue(new String(out.toByteArray()).contains("not trusted"));
    }

    @Test
    public void list_should_show_trusted_directories() throws Exception {
        TrustedDirsStore store = new TrustedDirsStore(tmp.newFile("store.txt").toPath());
        Path workspace = tmp.newFolder("workspace").toPath();
        store.trust(workspace);

        TrustCommand cmd = new TrustCommand(store);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = cmd.run(Collections.singletonList("--list"), terminal(out));

        Assert.assertEquals(0, exit);
        String output = new String(out.toByteArray());
        Assert.assertTrue(output.contains("Trusted workspace directories"));
        Assert.assertTrue(output.contains(workspace.toAbsolutePath().normalize().toString()));
    }

    @Test
    public void list_empty_should_report_no_trusted_dirs() throws Exception {
        TrustedDirsStore store = new TrustedDirsStore(tmp.newFile("store.txt").toPath());
        TrustCommand cmd = new TrustCommand(store);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = cmd.run(Collections.singletonList("--list"), terminal(out));

        Assert.assertEquals(0, exit);
        Assert.assertTrue(new String(out.toByteArray()).contains("No trusted"));
    }

    @Test
    public void no_args_should_print_help() throws Exception {
        TrustedDirsStore store = new TrustedDirsStore(tmp.newFile("store.txt").toPath());
        TrustCommand cmd = new TrustCommand(store);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<String> empty = Collections.emptyList();
        int exit = cmd.run(empty, terminal(out));

        Assert.assertEquals(0, exit);
        String output = new String(out.toByteArray());
        Assert.assertTrue(output.contains("ai4j-cli trust"));
        Assert.assertTrue(output.contains("--dir"));
        Assert.assertTrue(output.contains("--revoke"));
        Assert.assertTrue(output.contains("--list"));
    }
}
