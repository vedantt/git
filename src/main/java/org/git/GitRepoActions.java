package org.git;



import io.qameta.allure.Step;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

/**
 * Handles Git repository operations like clone, add, commit.
 */
public class GitRepoActions {
    private static final Logger LOGGER = Logger.getLogger(GitRepoActions.class.getName());
    private final Path localRepoPath;

    /**
     * Constructor for GitRepoActions.
     * @param localRepoPath The local path where the repository will be cloned or exists.
     */
    public GitRepoActions(Path localRepoPath) {
        this.localRepoPath = localRepoPath;
    }

    /**
     * Clones a Git repository.
     * @param repoUrl The URL of the Git repository.
     * @return True if clone was successful, false otherwise.
     * @throws IOException If an I/O error occurs.
     * @throws InterruptedException If the thread is interrupted.
     */
    @Step("Cloning repository from URL: {repoUrl} into {this.localRepoPath}")
    public boolean cloneRepository(String repoUrl) throws IOException, InterruptedException {
        if (Files.exists(localRepoPath) && Files.isDirectory(localRepoPath) && localRepoPath.toFile().list().length > 0) {
            LOGGER.info("Directory " + localRepoPath + " already exists and is not empty. Skipping clone or assuming it's already cloned.");

        } else {
            Files.createDirectories(localRepoPath.getParent()); // Ensure parent directory exists
        }

        // We clone into a specific directory, so the command should be "git clone <url> <directory_name>"
        // and the working directory for this command should be the PARENT of localRepoPath.
        GitCliUtil.ProcessResult result = GitCliUtil.executeCommand(
                localRepoPath.getParent(), // Execute clone in the parent directory
                "git", "clone", repoUrl, localRepoPath.getFileName().toString()
        );

        if (!result.isSuccess()) {
            LOGGER.severe("Failed to clone repository: " + result.getStderr());
            throw new IOException("Git clone failed: " + result.getStderr());
        }
        LOGGER.info("Repository cloned successfully to " + localRepoPath);
        return true;
    }

    /**
     * Creates a new file, adds it to staging, and commits.
     * @param fileName The name of the file to create.
     * @param content The content of the file.
     * @param commitMessage The commit message.
     * @return True if successful, false otherwise.
     * @throws IOException If an I/O error occurs.
     * @throws InterruptedException If the thread is interrupted.
     */
    @Step("Creating and adding new file: {fileName} with content and committing with message: {commitMessage}")
    public boolean createAndAddFile(String fileName, String content, String commitMessage)
            throws IOException, InterruptedException {
        Path filePath = localRepoPath.resolve(fileName);
        Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        LOGGER.info("Created file: " + filePath);

        return addAndCommit(fileName, commitMessage);
    }

    /**
     * Appends content to an existing file, adds it to staging, and commits.
     * @param fileName The name of the existing file to update.
     * @param contentToAppend The content to append to the file.
     * @param commitMessage The commit message.
     * @return True if successful, false otherwise.
     * @throws IOException If an I/O error occurs.
     * @throws InterruptedException If the thread is interrupted.
     */
    @Step("Updating existing file: {fileName} by appending content and committing with message: {commitMessage}")
    public boolean updateAndAddFile(String fileName, String contentToAppend, String commitMessage)
            throws IOException, InterruptedException {
        Path filePath = localRepoPath.resolve(fileName);
        if (!Files.exists(filePath)) {
            LOGGER.warning("File to update does not exist: " + filePath + ". Creating it first.");
            Files.writeString(filePath, contentToAppend + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            Files.writeString(filePath, contentToAppend + System.lineSeparator(), StandardOpenOption.APPEND);
        }
        LOGGER.info("Updated file: " + filePath);

        return addAndCommit(fileName, commitMessage);
    }

    /**
     * Helper method to stage a file and commit changes.
     * @param fileName The name of the file to add and commit.
     * @param commitMessage The commit message.
     * @return True if successful, false otherwise.
     * @throws IOException If an I/O error occurs.
     * @throws InterruptedException If the thread is interrupted.
     */
    @Step("Staging file {fileName} and committing with message: {commitMessage}")
    private boolean addAndCommit(String fileName, String commitMessage) throws IOException, InterruptedException {
        // Git Add
        GitCliUtil.ProcessResult addResult = GitCliUtil.executeCommand(localRepoPath, "git", "add", fileName);
        if (!addResult.isSuccess()) {
            LOGGER.severe("Git add failed for " + fileName + ": " + addResult.getStderr());
            throw new IOException("Git add failed: " + addResult.getStderr());
        }
        LOGGER.info("Git add successful for: " + fileName);

        // Git Commit
        // Configure user for commit if not set globally (important for CI environments)
        GitCliUtil.executeCommand(localRepoPath, "git", "config", "user.email", "test@example.com");
        GitCliUtil.executeCommand(localRepoPath, "git", "config", "user.name", "Test User");

        GitCliUtil.ProcessResult commitResult = GitCliUtil.executeCommand(localRepoPath, "git", "commit", "-m", commitMessage);
        if (!commitResult.isSuccess()) {
            // It's possible the commit fails because there are no changes (e.g., if the file content was identical)
            // A more robust check would inspect the stderr for "nothing to commit"
            if (commitResult.getStdout().contains("nothing to commit") || commitResult.getStderr().contains("nothing to commit")) {
                LOGGER.warning("Git commit - nothing to commit for message: " + commitMessage);
                return true; // Consider this a success in this context
            }
            LOGGER.severe("Git commit failed: " + commitResult.getStderr());
            throw new IOException("Git commit failed: " + commitResult.getStderr());
        }
        LOGGER.info("Git commit successful with message: " + commitMessage);
        return true;
    }

    /**
     * Deletes the local repository directory.
     * @throws IOException If an I/O error occurs during deletion.
     */
    @Step("Cleaning up local repository directory: {this.localRepoPath}")
    public void cleanupRepository() throws IOException {
        if (Files.exists(localRepoPath)) {
            // Simple recursive delete.
            Files.walk(localRepoPath)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(java.io.File::delete);
            LOGGER.info("Cleaned up directory: " + localRepoPath);
        }
    }
}
