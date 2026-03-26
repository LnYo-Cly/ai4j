package io.github.lnyocly.ai4j.cli;

public final class Ai4jCliMain {

    private Ai4jCliMain() {
    }

    public static void main(String[] args) {
        int exitCode = new Ai4jCli().run(args, System.in, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
