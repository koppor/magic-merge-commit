///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//DEPS org.eclipse.jgit:org.eclipse.jgit:7.5.0.202512021534-r
//DEPS org.eclipse.jgit:org.eclipse.jgit.pgm:7.5.0.202512021534-r
//DEPS org.kohsuke:github-api:2.0-rc.5
//DEPS info.picocli:picocli:4.7.7
//DEPS org.tinylog:tinylog-api:2.7.0
//DEPS org.tinylog:tinylog-impl:2.7.0

//FILES tinylog.properties

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.pgm.Main;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.tinylog.Logger;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/// Its aim is to create a merge-commit having the files directly after a squash-merge of a pr into main and the main as well as the pr at that point in time as parent.
///
/// Libraries to be used:
///
/// - jbang
/// - Java 21+
/// - eclipse jgit
/// - eclipse jgit's pgm: org.eclipse.jgit.pgm.Main.main(args) -> //DEPS org.eclipse.jgit:org.eclipse.jgit.pgm:7.5.0.202512021534-r
/// - hub4j -> //DEPS org.kohsuke:github-api:2.0-rc.5
///
/// 1. (with jgit) store current branch in variable: {{current branch}}
/// 1. (with jgit) merge {{pr branch}}
/// 1. (with hub4j) gets <prnum> information --> A) gets PR last commit id {{pr-last-commit}}, B) stores branch name in {{pr branch}}
/// 1. (with pgm): "git" "fetch" "origin"
/// 1. (with pgm) checkout {{pr branch}}
/// 1. (with jgit) merge origin/{{pr branch}} ((accept if that branch does not exist any more))
/// 1. (with jgit) git checkout main
/// 1. (with jgit) git merge origin/main
/// 1. (with jgit) search from here commit ending with "(#<prnum>)" --> store in {{pr squash-merge commit}}
/// 1. (with jgit) checkout {{pr-last-commit}} (create new branch "create-merge-commit-support" for this)
/// 1. (with jgit) merge {{pr squash-merge commit}} // now, the current working directory has the same files as main when pr is merged and when main is merged with {{pr-last-commit}}
/// 1. (with jgit) git cat-file -p create-merge-commit-support --> store tree id {{tree-id}}
/// 1. (with jgit) git commit-tree {{tree-id}} -p {{pr-last-commit}} -p {{pr squash-merge commit}} --> results in a commit id {{magic-commit-id}} // no create-merge-commit-support here, to have a "clean" git history; the "tree-id" is the "secret" here to have the right information
/// 1. (with jgit) git checkout {{current branch}}
/// 1. (with jgit) git merge {{magic-commit-id}}
/// 1. (with jgit) delete branch "create-merge-commit-support"

@CommandLine.Command(name = "create-pr-merge-commit",
        version = "2025-06-17",
        mixinStandardHelpOptions = true,
        sortSynopsis = false)
public class CreatePRMergeCommit implements Callable<Integer> {

    private static final String branchNameCreateMergeCommitSupport = "create-merge-commit-support";

    @CommandLine.Parameters(index = "0", paramLabel = "pr-number", description = "The number of the pull request to process")
    int prNum;

    public static void main(String... args) {
        CommandLine commandLine = new CommandLine(new CreatePRMergeCommit());
        commandLine.parseArgs(args);
        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        try (Git git = Git.open(new File("."))) {
            Repository repository = git.getRepository();

            // 1. Store current branch
            String currentBranch = repository.getBranch();
            Logger.info("Current branch: {}", currentBranch);

            if (currentBranch.equals(branchNameCreateMergeCommitSupport)) {
                Logger.warn("You are on the helper branch '{}'. Please switch to the target branch before running this command.", branchNameCreateMergeCommitSupport);
                return 1;
            }

            if (repository.findRef(branchNameCreateMergeCommitSupport) != null) {
                deleteSupportBranch(git);
            }

            // 3. Get PR information using hub4j
            GitHub github = new GitHubBuilder().fromEnvironment().build();

            // Extract repo info from remote URL
            String remoteUrl = git.remoteList().call().stream()
                .filter(config -> config.getName().equals("origin"))
                .findFirst()
                .map(config -> config.getURIs())
                .map(uris -> uris.getFirst().toASCIIString())
                .orElseThrow(() -> new Exception("No remote origin found"));
            String[] urlParts = remoteUrl.split("[:/]");
            String repoOwner = urlParts[urlParts.length - 2];
            String repoName = urlParts[urlParts.length - 1];
            if (repoName.endsWith(".git")) {
                repoName = repoName.substring(0, repoName.length() - 4);
            }

            GHPullRequest pr = github.getRepository(repoOwner + "/" + repoName).getPullRequest(prNum);
            String prBranch = pr.getHead().getRef();
            String prLastCommit = pr.getHead().getSha();

            Logger.info("PR branch: {}", prBranch);
            Logger.info("PR last commit: {}", prLastCommit);

            // 4. Fetch from origin using pgm
            String runResult = runJGitPgmCommand("fetch", "origin");
            Logger.info(runResult);

            // 5. Checkout PR branch
            try {
                git.checkout().setName(prBranch).call();
                Logger.info("Checked out PR branch: {}", prBranch);
            } catch (Exception e) {
                Logger.info("Could not checkout PR branch directly: {}", e.getMessage());
                // Create and checkout the branch if it doesn't exist
                git.checkout().setCreateBranch(true).setName(prBranch).call();
                Logger.info("Created and checked out PR branch: {}", prBranch);
            }

            // 6. Merge origin/prBranch (if it exists)
            try {
                Ref originPrBranch = repository.findRef("refs/remotes/origin/" + prBranch);
                if (originPrBranch == null) {
                    Logger.info("origin/{} does not exist. Not updating", prBranch);
                } else {
                    MergeResult mergeResult = git.merge()
                            .include(originPrBranch)
                            .setFastForward(MergeCommand.FastForwardMode.NO_FF)
                            .call();
                    Logger.info("Merged origin/{}: {}", prBranch, mergeResult.getMergeStatus());
                }
            } catch (Exception e) {
                Logger.info("Could not merge origin/{}: {}", prBranch, e.getMessage());
                // Continue if the branch doesn't exist remotely
            }

            // 7. Checkout main
            git.checkout().setName("main").call();
            Logger.info("Checked out main branch");

            // 8. Merge origin/main
            MergeResult mainMergeResult = git.merge()
                    .include(repository.findRef("refs/remotes/origin/main"))
                    .setFastForward(MergeCommand.FastForwardMode.NO_FF)
                    .call();
            Logger.info("Merged origin/main: {}", mainMergeResult.getMergeStatus());

            // 9. Find PR squash-merge commit
            RevCommit prSquashMergeCommit = null;
            Pattern prPattern = Pattern.compile(" \\(#" + prNum + "\\)");

            Iterable<RevCommit> commits = git.log().call();
            for (RevCommit commit : commits) {
                Logger.debug("Checking commit: {}", commit.getId());
                Logger.trace("Commit message: {}", commit.getFullMessage().trim());
                if (prPattern.matcher(commit.getFullMessage()).find()) {
                    prSquashMergeCommit = commit;
                    break;
                }
            }

            if (prSquashMergeCommit == null) {
                throw new IllegalStateException("Could not find PR squash-merge commit for PR #" + prNum);
            }

            Logger.info("PR squash-merge commit: {}", prSquashMergeCommit.getName());

            // 10. Checkout PR last commit (create new branch)
            git.checkout()
                    .setCreateBranch(true)
                    .setName(branchNameCreateMergeCommitSupport)
                    .setStartPoint(prLastCommit)
                    .call();
            Logger.info("Checked out PR last commit to branch create-merge-commit-support");

            // 11. Merge PR squash-merge commit
            MergeResult mergeResult = git.merge()
                    .include(prSquashMergeCommit)
                    .setMessage("Merge PR squash-merge commit for PR #" + prNum)
                    .setFastForward(MergeCommand.FastForwardMode.NO_FF)
                    .call();
            Logger.info("Merged PR squash-merge commit: {}", mergeResult.getMergeStatus());

            // 12. Get tree ID using jgit
            ObjectId commitId = repository.resolve(branchNameCreateMergeCommitSupport);
            ObjectId treeId;
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitId);
                treeId = commit.getTree().getId();
                Logger.info("Tree ID: {}", treeId.getName());
            }

            // 13. Create commit-tree with both parents using pgm
            String commitMessageMagicMergeCommit = "Magic merge commit for PR #" + prNum;
            ObjectId magicCommitId;
            try (ObjectInserter inserter = repository.newObjectInserter()) {
                CommitBuilder commitBuilder = new CommitBuilder();
                commitBuilder.setTreeId(treeId);
                commitBuilder.setParentIds(ObjectId.fromString(prLastCommit), prSquashMergeCommit.getId());
                commitBuilder.setMessage(commitMessageMagicMergeCommit);
                PersonIdent personIdent = new PersonIdent(repository);
                commitBuilder.setAuthor(personIdent);
                commitBuilder.setCommitter(personIdent);
                magicCommitId = inserter.insert(commitBuilder);
                Logger.info("Created commit: {}", magicCommitId.getName());
            } catch (IOException e) {
                throw new Exception("Failed to create commit tree", e);
            }

            // 14. Checkout original branch
            git.checkout().setName(currentBranch).call();
            Logger.info("Checked out original branch: {}", currentBranch);

            // 15. Merge magic commit
            mergeResult = git.merge()
                    .include(magicCommitId)
                    .setMessage("Merge into " + currentBranch)
                    .setFastForward(MergeCommand.FastForwardMode.NO_FF)
                    .call();
            Logger.info("Merged magic commit: {}", mergeResult.getMergeStatus());

            if (mergeResult.getMergeStatus() != MergeStatus.MERGED) {
                Logger.error("Not merged");
                return 1;
            }

            // 16. Delete temporary branch
            deleteSupportBranch(git);

            Logger.info("Successfully created merge commit for PR #" + prNum);
        }
        return 0;
    }

    private void deleteSupportBranch(Git git) throws GitAPIException {
        git.branchDelete().setBranchNames(branchNameCreateMergeCommitSupport).setForce(true).call();
        Logger.info("Deleted temporary branch create-merge-commit-support");
    }

    private String runJGitPgmCommand(String... args) throws Exception {
        // Save the original System.out
        PrintStream originalOut = System.out;

        // Create a stream to capture the output
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream captureOut = new PrintStream(outputStream);

        try {
            // Redirect System.out to our capture stream
            System.setOut(captureOut);

            // Run the JGit command
            Main.main(args);

            // Flush to make sure all output is captured
            captureOut.flush();

            // Return the captured output as a string
            return outputStream.toString();
        } catch (Exception e) {
            throw new IOException("JGit command failed: " + String.join(" ", args), e);
        } finally {
            // Restore the original System.out
            System.setOut(originalOut);
        }
    }
}
