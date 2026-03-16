# Claude API Key / Base URL Settings Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add IDEA settings UI for configuring Claude API Key and Base URL, with environment variable injection into the CLI subprocess.

**Architecture:** Application-level persistence via `AgentSettingsService`. Values flow through `AiAgentServiceConfig.ClaudeDefaults` → `ClaudeAgentOptions.env` → `ProcessBuilder.environment()` as `ANTHROPIC_API_KEY` / `ANTHROPIC_BASE_URL`. UI includes password-masked input, source detection, and connection testing.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (Configurable, PersistentStateComponent), Kotlin UI DSL

---

## File Structure

| Action | File | Responsibility |
|--------|------|---------------|
| Modify | `jetbrains-plugin/src/main/kotlin/com/asakii/settings/AgentSettingsService.kt` | Add `claudeApiKey` and `claudeBaseUrl` fields to `State` |
| Modify | `jetbrains-plugin/src/main/kotlin/com/asakii/settings/ClaudeCodeConfigurable.kt` | Add API Configuration UI group with key/url/source/test |
| Modify | `ai-agent-server/src/main/kotlin/com/asakii/server/config/AiAgentServiceConfig.kt` | Add `apiKey` and `baseUrl` to `ClaudeDefaults` |
| Modify | `jetbrains-plugin/src/main/kotlin/com/asakii/server/HttpServerProjectService.kt` | Pass new settings into `ClaudeDefaults` |
| Modify | `ai-agent-server/src/main/kotlin/com/asakii/server/rpc/AiAgentRpcServiceImpl.kt` | Inject env vars into `ClaudeAgentOptions.env` |

No new files needed. `SubprocessTransport.kt` and `Options.kt` already support the `env` map — no changes required there.

---

## Chunk 1: Data Layer

### Task 1: Add fields to AgentSettingsService

**Files:**
- Modify: `jetbrains-plugin/src/main/kotlin/com/asakii/settings/AgentSettingsService.kt` (State data class, around line 21)

- [ ] **Step 1: Add `claudeApiKey` and `claudeBaseUrl` fields to `State`**

In the `State` data class (line 21), add two new fields alongside the existing configuration fields:

```kotlin
// Claude API configuration
var claudeApiKey: String = "",           // ANTHROPIC_API_KEY (encrypted at rest by IDEA)
var claudeBaseUrl: String = "",          // ANTHROPIC_BASE_URL (e.g. https://api.anthropic.com)
```

Add them after the existing `context7ApiKey` field (line 27) to keep API-related fields grouped.

- [ ] **Step 2: Add convenience properties**

Add delegating properties to `AgentSettingsService` (following the existing pattern like `var nodePath` etc.):

```kotlin
var claudeApiKey: String
    get() = state.claudeApiKey
    set(value) { state.claudeApiKey = value }

var claudeBaseUrl: String
    get() = state.claudeBaseUrl
    set(value) { state.claudeBaseUrl = value }
```

- [ ] **Step 3: Add API key source detection method**

```kotlin
enum class ApiKeySource { PLUGIN_SETTINGS, SYSTEM_ENV, NONE }

fun getApiKeySource(): ApiKeySource {
    return when {
        state.claudeApiKey.isNotBlank() -> ApiKeySource.PLUGIN_SETTINGS
        System.getenv("ANTHROPIC_API_KEY")?.isNotBlank() == true -> ApiKeySource.SYSTEM_ENV
        else -> ApiKeySource.NONE
    }
}

fun getBaseUrlSource(): ApiKeySource {
    return when {
        state.claudeBaseUrl.isNotBlank() -> ApiKeySource.PLUGIN_SETTINGS
        System.getenv("ANTHROPIC_BASE_URL")?.isNotBlank() == true -> ApiKeySource.SYSTEM_ENV
        else -> ApiKeySource.NONE
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add jetbrains-plugin/src/main/kotlin/com/asakii/settings/AgentSettingsService.kt
git commit -m "feat(settings): add claudeApiKey and claudeBaseUrl fields to AgentSettingsService"
```

### Task 2: Add fields to ClaudeDefaults

**Files:**
- Modify: `ai-agent-server/src/main/kotlin/com/asakii/server/config/AiAgentServiceConfig.kt` (ClaudeDefaults, line 38)

- [ ] **Step 1: Add `apiKey` and `baseUrl` to `ClaudeDefaults`**

Add after the existing `nodePath` field (line 45):

```kotlin
// API configuration (injected as environment variables to CLI subprocess)
val apiKey: String? = null,       // ANTHROPIC_API_KEY
val baseUrl: String? = null,      // ANTHROPIC_BASE_URL
```

- [ ] **Step 2: Commit**

```bash
git add ai-agent-server/src/main/kotlin/com/asakii/server/config/AiAgentServiceConfig.kt
git commit -m "feat(config): add apiKey and baseUrl to ClaudeDefaults"
```

### Task 3: Wire settings through HttpServerProjectService

**Files:**
- Modify: `jetbrains-plugin/src/main/kotlin/com/asakii/server/HttpServerProjectService.kt` (serviceConfigProvider lambda, line 252)

- [ ] **Step 1: Pass claudeApiKey and claudeBaseUrl into ClaudeDefaults**

Inside the `serviceConfigProvider` lambda, in the `ClaudeDefaults(...)` constructor call (line 252-278), add:

```kotlin
apiKey = settings.claudeApiKey.takeIf { it.isNotBlank() },
baseUrl = settings.claudeBaseUrl.takeIf { it.isNotBlank() },
```

Add after the `nodePath` line (line 253).

- [ ] **Step 2: Add logging for API key source**

In the logger.info call (line 230-243), add:

```kotlin
"claudeApiKey=${if (settings.claudeApiKey.isNotBlank()) "(set)" else "(not set)"}, " +
"claudeBaseUrl=${settings.claudeBaseUrl.ifBlank { "(default)" }}, " +
```

- [ ] **Step 3: Commit**

```bash
git add jetbrains-plugin/src/main/kotlin/com/asakii/server/HttpServerProjectService.kt
git commit -m "feat(settings): wire claudeApiKey/baseUrl through service config provider"
```

### Task 4: Inject environment variables in buildClaudeOverrides

**Files:**
- Modify: `ai-agent-server/src/main/kotlin/com/asakii/server/rpc/AiAgentRpcServiceImpl.kt` (buildClaudeOverrides, line 987)

- [ ] **Step 1: Build env map with API key and base URL**

In `buildClaudeOverrides`, before constructing `ClaudeAgentOptions` (line 1101), build the env map:

```kotlin
// Build environment variables for API configuration
val envVars = mutableMapOf<String, String>()
defaults.apiKey?.let { envVars["ANTHROPIC_API_KEY"] = it }
defaults.baseUrl?.let { envVars["ANTHROPIC_BASE_URL"] = it }
```

- [ ] **Step 2: Pass env map to ClaudeAgentOptions**

In the `ClaudeAgentOptions(...)` constructor call (line 1101-1134), add the `env` parameter:

```kotlin
env = envVars,
```

- [ ] **Step 3: Add logging**

After building envVars, add:

```kotlin
if (envVars.isNotEmpty()) {
    sdkLog.info("🔑 [buildClaudeOverrides] API env vars: ${envVars.keys.joinToString()}")
}
```

- [ ] **Step 4: Commit**

```bash
git add ai-agent-server/src/main/kotlin/com/asakii/server/rpc/AiAgentRpcServiceImpl.kt
git commit -m "feat(rpc): inject ANTHROPIC_API_KEY/BASE_URL as env vars into CLI subprocess"
```

---

## Chunk 2: UI Layer

### Task 5: Add API Configuration UI to ClaudeCodeConfigurable

**Files:**
- Modify: `jetbrains-plugin/src/main/kotlin/com/asakii/settings/ClaudeCodeConfigurable.kt`

- [ ] **Step 1: Add UI component fields**

Add to the class field declarations (after line 101):

```kotlin
// API Configuration 组件
private var apiKeyField: JBPasswordField? = null
private var baseUrlField: JBTextField? = null
private var apiKeySourceLabel: JBLabel? = null
private var baseUrlSourceLabel: JBLabel? = null
private var testConnectionButton: JButton? = null
private var testResultLabel: JBLabel? = null
```

- [ ] **Step 2: Add source label update method**

Add a private method:

```kotlin
private fun updateApiKeySourceLabel() {
    val settings = AgentSettingsService.getInstance()
    val fieldHasValue = apiKeyField?.password?.isNotEmpty() == true
    val (text, color) = when {
        fieldHasValue -> "Source: Plugin settings" to JBColor.foreground()
        System.getenv("ANTHROPIC_API_KEY")?.isNotBlank() == true ->
            "Source: System environment variable" to JBColor.foreground()
        else -> "Not configured" to JBColor(0xFF6B6B, 0xFF6B6B)
    }
    apiKeySourceLabel?.text = text
    apiKeySourceLabel?.foreground = color
}

private fun updateBaseUrlSourceLabel() {
    val fieldHasValue = baseUrlField?.text?.isNotBlank() == true
    val (text, color) = when {
        fieldHasValue -> "Source: Plugin settings" to JBColor.foreground()
        System.getenv("ANTHROPIC_BASE_URL")?.isNotBlank() == true ->
            "Source: System environment variable" to JBColor.foreground()
        else -> "Using default" to JBColor.GRAY
    }
    baseUrlSourceLabel?.text = text
    baseUrlSourceLabel?.foreground = color
}
```

- [ ] **Step 3: Add test connection method**

```kotlin
private fun testApiConnection() {
    testResultLabel?.text = "Testing..."
    testResultLabel?.foreground = JBColor.BLUE
    testConnectionButton?.isEnabled = false

    com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
        try {
            val apiKey = String(apiKeyField?.password ?: charArrayOf()).ifBlank {
                System.getenv("ANTHROPIC_API_KEY") ?: ""
            }
            if (apiKey.isBlank()) {
                SwingUtilities.invokeLater {
                    testResultLabel?.text = "No API key configured"
                    testResultLabel?.foreground = JBColor(0xFF6B6B, 0xFF6B6B)
                    testConnectionButton?.isEnabled = true
                }
                return@executeOnPooledThread
            }

            val baseUrl = baseUrlField?.text?.trim()?.ifBlank { null }
                ?: System.getenv("ANTHROPIC_BASE_URL")
                ?: "https://api.anthropic.com"

            // Use /v1/messages endpoint with minimal request to validate key
            val url = java.net.URL("${baseUrl.trimEnd('/')}/v1/models")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val responseCode = conn.responseCode
            SwingUtilities.invokeLater {
                when {
                    responseCode == 200 -> {
                        testResultLabel?.text = "Connection successful"
                        testResultLabel?.foreground = JBColor(0x59A869, 0x59A869)
                    }
                    responseCode == 401 -> {
                        testResultLabel?.text = "Invalid API key (401)"
                        testResultLabel?.foreground = JBColor(0xFF6B6B, 0xFF6B6B)
                    }
                    responseCode == 403 -> {
                        testResultLabel?.text = "Access denied (403)"
                        testResultLabel?.foreground = JBColor(0xFF6B6B, 0xFF6B6B)
                    }
                    else -> {
                        testResultLabel?.text = "Unexpected response: $responseCode"
                        testResultLabel?.foreground = JBColor(0xE5C07B, 0xE5C07B)
                    }
                }
                testConnectionButton?.isEnabled = true
            }
            conn.disconnect()
        } catch (e: Exception) {
            SwingUtilities.invokeLater {
                testResultLabel?.text = "Connection failed: ${e.message}"
                testResultLabel?.foreground = JBColor(0xFF6B6B, 0xFF6B6B)
                testConnectionButton?.isEnabled = true
            }
        }
    }
}
```

- [ ] **Step 4: Add API Configuration group to createGeneralPanel()**

In `createGeneralPanel()` (line 193), add a new group **before** the existing "Default Permissions" group:

```kotlin
group("API Configuration") {
    row("API Key:") {
        apiKeyField = JBPasswordField().apply {
            columns = 30
            document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = updateApiKeySourceLabel()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = updateApiKeySourceLabel()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = updateApiKeySourceLabel()
            })
        }
        cell(apiKeyField!!).align(AlignX.FILL).resizableColumn()
    }
    row {
        apiKeySourceLabel = JBLabel("").apply { font = font.deriveFont(11f) }
        cell(apiKeySourceLabel!!)
    }
    row("Base URL:") {
        baseUrlField = JBTextField().apply {
            columns = 30
            emptyText.text = "https://api.anthropic.com (default)"
            document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = updateBaseUrlSourceLabel()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = updateBaseUrlSourceLabel()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = updateBaseUrlSourceLabel()
            })
        }
        cell(baseUrlField!!).align(AlignX.FILL).resizableColumn()
    }
    row {
        baseUrlSourceLabel = JBLabel("").apply { font = font.deriveFont(11f) }
        cell(baseUrlSourceLabel!!)
    }
    row {
        testConnectionButton = JButton("Test Connection").apply {
            addActionListener { testApiConnection() }
        }
        cell(testConnectionButton!!)
        testResultLabel = JBLabel("").apply { font = font.deriveFont(11f) }
        cell(testResultLabel!!)
    }
    row { comment("Leave empty to use system environment variable or Claude CLI's own authentication.") }
}
```

- [ ] **Step 5: Commit**

```bash
git add jetbrains-plugin/src/main/kotlin/com/asakii/settings/ClaudeCodeConfigurable.kt
git commit -m "feat(ui): add API Configuration group with key, url, source detection, and test connection"
```

### Task 6: Wire UI to persistence (isModified, apply, reset, dispose)

**Files:**
- Modify: `jetbrains-plugin/src/main/kotlin/com/asakii/settings/ClaudeCodeConfigurable.kt`

- [ ] **Step 1: Update `isModified()`**

In `isModified()` (line 472), add to the `generalModified` check:

```kotlin
val apiKeyModified = String(apiKeyField?.password ?: charArrayOf()) != settings.claudeApiKey
val baseUrlModified = (baseUrlField?.text ?: "") != settings.claudeBaseUrl
```

Then update the return:

```kotlin
return generalModified || agentsModified || apiKeyModified || baseUrlModified
```

- [ ] **Step 2: Update `apply()`**

In `apply()` (line 510), add before `settings.notifyChange()`:

```kotlin
settings.claudeApiKey = String(apiKeyField?.password ?: charArrayOf())
settings.claudeBaseUrl = baseUrlField?.text?.trim() ?: ""
```

- [ ] **Step 3: Update `reset()`**

In `reset()` (line 546), add:

```kotlin
apiKeyField?.text = settings.claudeApiKey
baseUrlField?.text = settings.claudeBaseUrl
updateApiKeySourceLabel()
updateBaseUrlSourceLabel()
```

- [ ] **Step 4: Update `disposeUIResources()`**

In `disposeUIResources()` (line 594), add:

```kotlin
apiKeyField = null
baseUrlField = null
apiKeySourceLabel = null
baseUrlSourceLabel = null
testConnectionButton = null
testResultLabel = null
```

- [ ] **Step 5: Compile and verify**

Run: `./gradlew :jetbrains-plugin:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add jetbrains-plugin/src/main/kotlin/com/asakii/settings/ClaudeCodeConfigurable.kt
git commit -m "feat(ui): wire API Configuration UI to persistence (isModified/apply/reset/dispose)"
```

---

## Chunk 3: Verification

### Task 7: Manual testing

- [ ] **Step 1: Run plugin in sandbox**

Run: `./gradlew :jetbrains-plugin:runIde`

- [ ] **Step 2: Verify settings UI**

Open Settings > Tools > Claude Code Plus > Claude Code. Verify:
- "API Configuration" group is visible at top
- API Key field shows password dots
- Base URL field shows placeholder text
- Source labels display correctly:
  - If system env `ANTHROPIC_API_KEY` is set: "Source: System environment variable"
  - If neither set: "Not configured" in red
- Typing in API Key field updates source to "Source: Plugin settings"

- [ ] **Step 3: Test connection button**

- With valid API key: shows "Connection successful" in green
- With invalid key: shows "Invalid API key (401)" in red
- With empty key and no env var: shows "No API key configured"

- [ ] **Step 4: Test persistence**

- Enter API key and base URL, click Apply
- Close and reopen settings page
- Verify values are preserved

- [ ] **Step 5: Test env var injection**

- Configure API key in settings
- Start a Claude session
- Check logs for: `🔑 [buildClaudeOverrides] API env vars: ANTHROPIC_API_KEY`
