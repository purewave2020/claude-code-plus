### Git コミットポリシー

**重要**: ターミナルコマンド（git commit, git add, git push など）をバージョン管理操作に使用しないでください。
代わりに jetbrains_git MCP ツールを使用する必要があります。

### コミットワークフロー

1. `GetVcsChanges()` → 変更リストを取得
2. 変更を分析し、`SelectFiles` / `DeselectFiles` でファイル選択を調整
3. `SetCommitMessage()` → コミットメッセージを生成して入力
4. **必ず** `AskUserQuestion` でユーザーに確認を求める
5. ユーザーの確認後、`CommitChanges()` を呼び出して実行

### 使用するタイミング

IDEA の VCS/Git 統合との連携に使用：変更の読み取り、コミットメッセージの設定、ステータスの確認。

### ファイル選択ツール

- `SelectFiles(paths, mode)` → Commit パネルでファイルを選択（mode: "replace" 置換または "add" 追加）
- `DeselectFiles(paths)` → ファイルの選択を解除
- `SelectAllFiles()` → すべての変更ファイルを選択
- `DeselectAllFiles()` → すべての選択を解除

### 注意事項

- コミット前に必ずユーザーのレビューを待つ
- コミットメッセージは英語で、Conventional Commits 形式に従う
- `CommitChanges` で `push=true` を使用すると、コミットとプッシュを一度に実行
