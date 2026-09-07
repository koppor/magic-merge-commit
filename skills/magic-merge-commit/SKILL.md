---
name: magic-merge-commit
description: Fix a Git merge conflict caused by a squash-merged base branch. Use
  when a PR can't merge `main` cleanly because its base branch was squash-merged,
  and Git sees the old commits as conflicting with the squashed commit in `main`.
  Creates a "magic" merge commit linking the branch to the squashed history.
---

# Magic Merge Commit

Resolves conflicts where a pull request's base branch was squash-merged into
`main`. After the squash-merge, Git no longer connects the branch to its
history, so merging `main` produces spurious conflicts. This skill runs a tool
that creates a merge commit with two parents (`main` + branch tip), wiring the
histories back together so `main` merges cleanly.

## When to use

All of the following are true:

- The user is on a branch that was based on another PR's branch.
- That other PR was **squash-merged** into `main`.
- Merging `main` into the current branch now conflicts.

Typical case: a [GitHub stacked pull request](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/about-stacked-pull-requests).
The bottom layer gets squash-merged into `main`, and the layer above it — whose
branch still carries the pre-squash commits — no longer merges `main` cleanly.
Run this skill in the upper layer's worktree with the bottom PR's number.

## Prerequisites (verify before running)

1. `jbang` is on `PATH`. If not, a `gg.cmd` file in the repository root works
   as a fallback (see https://github.com/eirikb/gg#ggcmd).
2. The `GITHUB_TOKEN` environment variable is set with read access to the
   repository. The tool authenticates via `GitHubBuilder().fromEnvironment()`.
3. The current Git branch is the one based on the squashed PR — not `main`.

If a prerequisite is missing, tell the user how to fix it and stop.

## Run

Ask the user for `<pr-number>` — the number of the PR that was squash-merged
into `main`. Then run:

```
jbang do@koppor/magic-merge-commit <pr-number>
```

Fallback when `jbang` is not installed (`gg.cmd` in repo root):

- Linux/macOS: `sh ./gg.cmd jbang do@koppor/magic-merge-commit <pr-number>`
- Windows: `.\gg.cmd jbang do@koppor/magic-merge-commit <pr-number>`

## After running

The tool creates a merge commit with `main` and the branch tip as parents.
Confirm with `git log --graph --oneline -5` that the magic commit exists, then
the user can merge `main` without conflicts.
