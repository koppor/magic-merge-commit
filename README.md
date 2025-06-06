# Create Magic Merge Commit

Ever build on a branch which meanwhile was squashed-merged into `main` and then you wanted to merge a pull request that was based on that branch? You know, the one that has a commit that conflicts with the squashed commit in `main`?

This tool creates a "magic" merge commit that resolves the conflict by connecting the two branches together, allowing you to merge without conflicts.

Execute is with following command:

```terminal
jbang do@koppor/magic-merge-commit <pr-number>
```

For this, you need to have [jbang](https://www.jbang.dev/) installed.

If you don't want to install jbang, place [`gg.cmd`](https://github.com/eirikb/gg#ggcmd) into the root of your git repository and execute as follows:

- Linux/macOS: `sh ./gg.cmd jbang do@koppor/magic-merge-commit <pr-number>`
- Windows: `.\gg.cmd jbang do@koppor/magic-merge-commit <pr-number>`

## Visualization of the process

### 1. First pull request is created

The pull request starts with one commit.

![First PR](img/01%20-%20first%20pr.png)

### 2. Second pull request is created

Another improvement is needed.
It should be reviewed separate pull request, but needs the first pull reuqest.

Therefore, a new branch is created and a commit is added to it.
Finally, a second pull request is created, based on the first one, which is still opened.

![Second Commit](img/02%20-%20add%20second%20commit.png)

### 3. First pull request is merged in to `main`

![First PR Merged](img/03%20-%20first%20pr%20merged.png)

### 4. `main` branch has its own commit

The conflicting change in `main`.

![Main has first commit](img/04%20-%20main%20branch%20has%20first%20commit.png)

### 5. Conflict Arises with the `main` branch

The second commit in the pull request conflicts with the `main` branch, which has its own commit.

![Conapsflict with main](img/05%20-%20conflict%20with%20main%20branch.png)

### 6. Conflicting state visualized with gitk

The first pull request was added as commit to the `main` branch.
There is no git merge commit and there is no git connect with the pull request.
The only indicator is `#1` in the commit message.

![Conflicting state](img/06%20-%20conflicting%20state.png)

### 7. The magic commit is created

This tool creates a new commit that has both `main` and `pr-last-commit` as parents.
This commit "magically" resolves the conflict by wiring the two branches together.

![Magic Commit](img/07%20-%20magic%20commit.png)

### 8. Merge without conflicts

This "magic" merge commit now merges cleanly.

![No Conflicts](img/08%20-%20no%20conflicts.png)

## More information

- `gitk` was used to visualize the commits. Learn more about it at [lostechies](https://lostechies.com/joshuaflanagan/2010/09/03/use-gitk-to-understand-git/).
- The scenario is available at [squash-merge-demo](https://github.com/koppor/squash-merge-demo).
- Background is available at [a blog post](https://blog.flupp.de/posts/git-and-squashed-prs/).
