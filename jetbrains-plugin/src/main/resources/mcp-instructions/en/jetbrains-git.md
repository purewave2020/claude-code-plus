### Git Commit Policy

**IMPORTANT**: Do NOT use terminal commands (git commit, git add, git push, etc.) for version control operations.
You MUST use jetbrains_git MCP tools instead.

### Commit Workflow

1. `GetVcsChanges()` → Get list of changes
2. Analyze changes, use `SelectFiles` / `DeselectFiles` to adjust file selection
3. `SetCommitMessage()` → Generate and fill commit message
4. **MUST** use `AskUserQuestion` to ask user for confirmation
5. After user confirms, call `CommitChanges()` to execute

### When to Use

Use for interacting with IDEA's VCS/Git integration: reading changes, setting commit messages, checking status.

### File Selection Tools

- `SelectFiles(paths, mode)` → Select files in Commit panel (mode: "replace" or "add")
- `DeselectFiles(paths)` → Deselect files from Commit panel
- `SelectAllFiles()` → Select all changed files
- `DeselectAllFiles()` → Deselect all files

### Notes

- Always wait for user review before committing
- Commit messages must be in English, following Conventional Commits format
- Use `push=true` in `CommitChanges` to commit and push in one step
