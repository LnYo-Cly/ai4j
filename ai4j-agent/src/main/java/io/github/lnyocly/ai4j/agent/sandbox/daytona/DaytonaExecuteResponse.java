package io.github.lnyocly.ai4j.agent.sandbox.daytona;

/**
 * Daytona toolbox command execution response.
 */
public final class DaytonaExecuteResponse {

    private Integer exitCode;
    private String result;
    private String stdout;
    private String stderr;
    private String output;

    public DaytonaExecuteResponse() {
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String stdoutOrResult() {
        if (stdout != null) {
            return stdout;
        }
        if (output != null) {
            return output;
        }
        return result;
    }
}
