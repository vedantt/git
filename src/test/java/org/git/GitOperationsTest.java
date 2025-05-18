package org.git;



import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

@Epic("Git Operations")
@Feature("Local Git Repository Management")
public class GitOperationsTest {

    private static final Logger LOGGER = Logger.getLogger(GitOperationsTest.class.getName());

    // REPO_URL is read from properties file, with a fallback default.
    //  set 'git.repo.url' in your git_test.properties file.
    private static final String REPO_URL = ConfigLoader.getProperty("git.repo.url", "https://github.com/git-fixtures/basic.git");

    private GitRepoActions gitRepoActions;
    private Path localClonedRepoPath;
    private Path tempDirBase; // For managing temporary directories with TestNG

    @BeforeMethod
    void setUp() throws IOException {
        // Manually create a temporary directory for TestNG
        tempDirBase = Files.createTempDirectory("testng-git-tests-" + UUID.randomUUID().toString().substring(0,8) + "-");
        // Create a unique subdirectory within tempDirBase for each test's clone
        localClonedRepoPath = tempDirBase.resolve("repo-" + UUID.randomUUID().toString());
        Files.createDirectories(localClonedRepoPath.getParent()); // Ensure parent of clone target exists

        gitRepoActions = new GitRepoActions(localClonedRepoPath);
        LOGGER.info("Test setup: Local repo path set to " + localClonedRepoPath);
        LOGGER.info("Using Git repository URL: " + REPO_URL);
    }

    @AfterMethod
    void tearDown() throws IOException {
        if (gitRepoActions != null) {
            LOGGER.info("Cleaning up repository in tearDown: " + localClonedRepoPath);
            gitRepoActions.cleanupRepository(); // This should delete localClonedRepoPath
        }
        // Clean up the base temporary directory created for this test method
        if (tempDirBase != null && Files.exists(tempDirBase)) {
            try {
                Files.walk(tempDirBase)
                        .sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
                LOGGER.info("Cleaned up base temporary directory: " + tempDirBase);
            } catch (IOException e) {
                LOGGER.severe("Could not clean up temporary directory " + tempDirBase + ": " + e.getMessage());
            }
        }
    }

    @Test(description = "Clone repository and add a new file using properties")
    @Story("Add New File to Repo")
    @Severity(SeverityLevel.CRITICAL)
    public void testCloneAndAddNewFile() throws IOException, InterruptedException {
        // Read file name and content from properties file
        final String newFileName = ConfigLoader.getProperty("git.new.filename", "default_new_file.txt");
        final String fileContent = ConfigLoader.getProperty("git.new.filecontent", "Default content if property is missing.");
        // Construct commit message, potentially using a base from properties
        final String commitMessageBase = ConfigLoader.getProperty("git.commit.message.newfile", "Automated: Add new file");
        final String commitMessage = commitMessageBase + ": " + newFileName;


        Allure.parameter("Repository URL", REPO_URL);
        Allure.parameter("New File Name (from props)", newFileName);
        // Log only a snippet of potentially long file content to Allure report
        Allure.parameter("File Content (from props)", fileContent.substring(0, Math.min(fileContent.length(), 100)) + (fileContent.length() > 100 ? "..." : ""));

        stepLog("Cloning repository: " + REPO_URL);
        Assert.assertTrue(gitRepoActions.cloneRepository(REPO_URL), "Repository cloning should be successful.");
        Assert.assertTrue(Files.exists(localClonedRepoPath.resolve(".git")), "Cloned repository should have a .git directory.");

        stepLog("Creating and adding new file: " + newFileName);
        Assert.assertTrue(gitRepoActions.createAndAddFile(newFileName, fileContent, commitMessage),
                "Creating and adding new file should be successful.");

        Path newFilePath = localClonedRepoPath.resolve(newFileName);
        Assert.assertTrue(Files.exists(newFilePath), "New file should exist in the local repository.");

        // Normalize line endings for comparison as Git might change them (core.autocrlf)
        // or properties file might have different line endings than expected by Files.writeString.
        String expectedContentNormalized = fileContent.replace("\r\n", "\n");
        String actualContentNormalized = Files.readString(newFilePath).replace("\r\n", "\n");
        Assert.assertEquals(actualContentNormalized, expectedContentNormalized, "File content should match the expected content (normalized line endings).");

        stepLog("Verifying commit via git log (last commit)");
        GitCliUtil.ProcessResult logResult = GitCliUtil.executeCommand(localClonedRepoPath, "git", "log", "-1", "--pretty=%B"); // %B gets full commit message body
        Assert.assertTrue(logResult.isSuccess(), "Git log command should succeed. Stderr: " + logResult.getStderr());
        Assert.assertTrue(logResult.getStdout().trim().contains(commitMessage.trim()),
                "Commit message should be present in git log. Expected: '" + commitMessage.trim() + "', Got: '" + logResult.getStdout().trim() + "'");
    }

    @Test(description = "Clone repository and update an existing file using properties")
    @Story("Update Existing File in Repo")
    @Severity(SeverityLevel.CRITICAL)
    public void testCloneAndUpdateExistingFile() throws IOException, InterruptedException {
        // Read configuration from properties file
        final String existingFileName = ConfigLoader.getProperty("git.update.filename", "default_update_me.txt");
        final String initialContent = ConfigLoader.getProperty("git.update.initialcontent", "Default initial content for update test.");
        final String contentToAppend = ConfigLoader.getProperty("git.update.appendcontent", "Default appended content for update test.");

        final String commitMessageInitialBase = ConfigLoader.getProperty("git.commit.message.newfile", "Automated: Add initial file for update test");
        final String commitMessageInitial = commitMessageInitialBase + ": " + existingFileName;

        final String commitMessageUpdateBase = ConfigLoader.getProperty("git.commit.message.updatefile", "Automated: Update file");
        final String commitMessageUpdate = commitMessageUpdateBase + ": " + existingFileName;

        Allure.parameter("Repository URL", REPO_URL);
        Allure.parameter("Existing File Name (from props)", existingFileName);
        Allure.parameter("Content to Append (from props)", contentToAppend.substring(0, Math.min(contentToAppend.length(),100)) + (contentToAppend.length() > 100 ? "..." : ""));

        stepLog("Cloning repository: " + REPO_URL);
        Assert.assertTrue(gitRepoActions.cloneRepository(REPO_URL), "Repository cloning should be successful.");

        Path filePath = localClonedRepoPath.resolve(existingFileName);
        String contentBeforeUpdateNormalized = ""; // Will store normalized content

        // Ensure the file exists before updating, or create it if it doesn't.
        if (!Files.exists(filePath)) {
            stepLog("File " + existingFileName + " does not exist. Creating it first using properties content.");
            Assert.assertTrue(gitRepoActions.createAndAddFile(existingFileName, initialContent, commitMessageInitial),
                    "Creating initial file should be successful.");
            Assert.assertTrue(Files.exists(filePath), "Initial file should exist after creation.");

            String expectedInitialContentNormalized = initialContent.replace("\r\n", "\n");
            String actualInitialContentNormalized = Files.readString(filePath).replace("\r\n", "\n");
            Assert.assertEquals(actualInitialContentNormalized, expectedInitialContentNormalized, "Initial file content should match (normalized).");
            contentBeforeUpdateNormalized = actualInitialContentNormalized; // Store normalized content
        } else {
            stepLog("File " + existingFileName + " already exists. Proceeding to update.");
            contentBeforeUpdateNormalized = Files.readString(filePath).replace("\r\n", "\n"); // Read and normalize existing content
        }

        stepLog("Updating file: " + existingFileName + " by appending content from properties.");
        // The updateAndAddFile method in GitRepoActions already appends a newline.
        Assert.assertTrue(gitRepoActions.updateAndAddFile(existingFileName, contentToAppend, commitMessageUpdate),
                "Updating file should be successful.");

        Assert.assertTrue(Files.exists(filePath), "Updated file should exist in the local repository.");
        String finalContentNormalized = Files.readString(filePath).replace("\r\n", "\n");

        // Check if the original content is at the start
        Assert.assertTrue(finalContentNormalized.startsWith(contentBeforeUpdateNormalized),
                "Final content should start with original content (normalized).\nOriginal:\n'" + contentBeforeUpdateNormalized + "'\nFinal:\n'" + finalContentNormalized + "'");

        // Check if the appended content is present (normalize appended content for comparison)
        String appendedContentNormalized = contentToAppend.replace("\r\n", "\n");
        Assert.assertTrue(finalContentNormalized.contains(appendedContentNormalized),
                "Appended content should be present in the file (normalized).\nExpected to contain:\n'" + appendedContentNormalized + "'\nActual Final:\n'" + finalContentNormalized + "'");

        stepLog("Verifying update commit via git log (last commit)");
        GitCliUtil.ProcessResult logResult = GitCliUtil.executeCommand(localClonedRepoPath, "git", "log", "-1", "--pretty=%B");
        Assert.assertTrue(logResult.isSuccess(), "Git log command should succeed. Stderr: " + logResult.getStderr());

        // The last commit message should be the update message.
        // If the file was created and then updated in the same test flow, the update commit is the last one.
        Assert.assertTrue(logResult.getStdout().trim().contains(commitMessageUpdate.trim()),
                "The update commit message should be in the log. Expected: '" + commitMessageUpdate.trim() + "', Got: '" + logResult.getStdout().trim() + "'");
    }

    // Helper to add logs that also appear as Allure steps
    @Step("{logMessage}")
    private void stepLog(String logMessage) {
        LOGGER.info(logMessage); // Also log to console/file
    }
}
