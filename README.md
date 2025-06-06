# Create Magic Merge Commit

Ever build on a branch which was squashed-merged into `main` and then you wanted to merge a pull request that was based on that branch? You know, the one that has a commit that conflicts with the squashed commit in `main`?

This tool creates a "magic" merge commit that resolves the conflict by connecting the two branches together, allowing you to merge without conflicts.



## Visualization of the process

### 1. First PR is created

The pull request starts with one commit.

![First PR](img/01%20-%20first%20pr.png)

### 2. Add a second commit to the PR

A second PR is created, based on the first one, which is still opened.

![Second Commit](img/02%20-%20add%20second%20commit.png)

### 3. First PR is merged in to `main`

![First PR Merged](img/03%20-%20first%20PR%20merged.png)

### 4. `main` branch has its own commit

The conflicting change in `main`.

![Main has first commit](img/04%20-%20main%20branch%20has%20first%20commit.png)

### 5. Conflict Arises with the `main` branch

The second commit in the PR conflicts with the `main` branch, which has its own commit.

![Conapsflict with main](img/05%20-%20conflict%20with%20main%20branch.png)

### 6. Conflicting state visualized with gitk

The first PR was added as commit to the `main` branch.
There is no git merge commit and there is no git connect with the pull request.
The only indicator is `#1` in the commit message.

![Conflicting state](img/06%20-%20conflicting%20state.png)

### 7. The magic commit is created

This tool creates a new commit that has both `main` and `pr-last-commit` as parents.
This commit "magically" resolves the conflict by wiring the two branches together.

![Magic Commit](img/07%20-%20magic%20commit.png)

### 8. Merge Without Conflicts

This "magic" merge commit now merges cleanly.

![No Conflicts](img/08%20-%20no%20conflicts.png)
