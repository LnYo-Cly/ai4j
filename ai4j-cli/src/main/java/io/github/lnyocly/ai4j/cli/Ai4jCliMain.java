package io.github.lnyocly.ai4j.cli;

public final class Ai4jCliMain {

    private Ai4jCliMain() {
    }

    public static void main(String[] args) {
        configureLogging(args);
        int exitCode = new Ai4jCli().run(args, System.in, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static void configureLogging(String[] args) {
        boolean verbose = hasVerboseFlag(args);
        setIfAbsent("org.slf4j.simpleLogger.defaultLogLevel", verbose ? "debug" : "warn");
        setIfAbsent("org.slf4j.simpleLogger.showThreadName", verbose ? "true" : "false");
        setIfAbsent("org.slf4j.simpleLogger.showDateTime", "false");
        setIfAbsent("org.slf4j.simpleLogger.showLogName", verbose ? "true" : "false");
        setIfAbsent("org.slf4j.simpleLogger.showShortLogName", verbose ? "true" : "false");
    }

    private static boolean hasVerboseFlag(String[] args) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if ("--verbose".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void setIfAbsent(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}
