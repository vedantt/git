package org.git;

import io.qameta.allure.Step;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for executing Git CLI commands.
 */
public class GitCliUtil {

    private static final Logger LOGGER = Logger.getLogger(GitCliUtil.class.getName());
    private static final int COMMAND_TIMEOUT_SECONDS = 60; // Timeout for git commands

    /**
     * Represents the result of a process execution.
     */
    public static class ProcessResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        public ProcessResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }

        @Override
        public String toString() {
            return "ProcessResult{" +
                    "exitCode=" + exitCode +
                    ", stdout='" + stdout + '\'' +
                    ", stderr='" + stderr + '\'' +
                    '}';
        }
    }

    /**
     * Executes a command in the specified working directory.
     *
     * @param workingDirectory The directory where the command should be executed.
     * @param command          The command and its arguments.
     * @return ProcessResult containing exit code, stdout, and stderr.
     * @throws IOException          If an I/O error occurs.
     * @throws InterruptedException If the current thread is interrupted while waiting for the command to finish.
     */
    @Step("Executing command: {command} in directory {workingDirectory}")
    public static ProcessResult executeCommand(Path workingDirectory, String... command)
            throws IOException, InterruptedException {
        if (command == null || command.length == 0) {
            throw new IllegalArgumentException("Command cannot be null or empty.");
        }

        LOGGER.log(Level.INFO, "Executing command: {0} in {1}",
                new Object[]{String.join(" ", command), workingDirectory});

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }
        processBuilder.redirectErrorStream(false); // Separate stdout and stderr

        Process process = processBuilder.start();

        StringBuilder stdoutBuilder = new StringBuilder();
        StringBuilder stderrBuilder = new StringBuilder();

        // Consumers to read output streams
        StreamGobbler stdOutGobbler = new StreamGobbler(process.getInputStream(), stdoutBuilder::append);
        StreamGobbler stdErrGobbler = new StreamGobbler(process.getErrorStream(), stderrBuilder::append);

        Thread stdOutThread = new Thread(stdOutGobbler);
        stdOutThread.setDaemon(true); // Set as daemon thread
        stdOutThread.start();

        Thread stdErrThread = new Thread(stdErrGobbler);
        stdErrThread.setDaemon(true); // Set as daemon thread
        stdErrThread.start();

        boolean exited = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!exited) {
            process.destroyForcibly(); // Ensure the process is killed on timeout
            // Wait for gobbler threads to finish after process destruction, with a short timeout
            stdOutThread.join(1000);
            stdErrThread.join(1000);
            LOGGER.log(Level.SEVERE, "Command timed out: {0}", String.join(" ", command));
            return new ProcessResult(-1, stdoutBuilder.toString(),
                    stderrBuilder.toString() + "\nERROR: Command timed out after " + COMMAND_TIMEOUT_SECONDS + " seconds.");
        }

        int exitCode = process.exitValue();

        // Ensure stream gobblers have finished processing all output
        // Even if process.waitFor() returned, streams might need a moment to be fully consumed.
        stdOutThread.join();
        stdErrThread.join();

        String stdout = stdoutBuilder.toString().trim();
        String stderr = stderrBuilder.toString().trim();

        if (exitCode == 0) {
            LOGGER.log(Level.INFO, "Command executed successfully. Stdout: {0}", stdout);
        } else {
            LOGGER.log(Level.WARNING, "Command failed with exit code {0}. Stderr: {1}. Stdout: {2}",
                    new Object[]{exitCode, stderr, stdout});
        }
        return new ProcessResult(exitCode, stdout, stderr);
    }

    /**
     * Helper class to consume an InputStream in a separate thread.
     */
    private static class StreamGobbler implements Runnable {
        private final InputStream inputStream;
        private final Consumer<String> consumer;

        public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    consumer.accept(line + System.lineSeparator());
                }
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Error reading stream (possibly due to process termination or stream closure): " + e.getMessage());
            } catch (Exception e) { // Catch any other unexpected errors during stream reading
                LOGGER.log(Level.SEVERE, "Unexpected error reading stream", e);
            }
        }
    }
}
