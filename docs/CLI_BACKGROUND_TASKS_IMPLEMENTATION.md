# CLI Background Tasks Implementation Analysis

This document describes how Claude CLI 2.1.4 internally implements the `CLAUDE_CODE_DISABLE_BACKGROUND_TASKS` environment variable.

## Overview

The `CLAUDE_CODE_DISABLE_BACKGROUND_TASKS` environment variable controls whether the `run_in_background` parameter is available for Task and Bash tools.

## Internal Implementation

### Boolean Parsing Function: `i1()`

CLI uses an internal function `i1()` to parse boolean environment variables. This function accepts the following values (case-insensitive):

- `"1"`
- `"true"`
- `"yes"`
- `"on"`

Any other value (including empty string, `"0"`, `"false"`, `"no"`, `"off"`) is treated as `false`.

### Key Code Locations (CLI 2.1.4)

#### Line 1400 - Bash Tool Help Text

```javascript
function Go8() {
  if (i1(process.env.CLAUDE_CODE_DISABLE_BACKGROUND_TASKS))
    return "";  // Disable run_in_background help text
  return "\n  - You can use the `run_in_background` parameter to run the command in the background...";
}
```

When background tasks are disabled, the help text for `run_in_background` is removed from the Bash tool description.

#### Line 3046 - Task Tool Schema

```javascript
ifA = i1(process.env.CLAUDE_CODE_DISABLE_BACKGROUND_TASKS)
kw0 = ifA ? R19.omit({run_in_background: !0}) : R19
```

- `ifA`: Global boolean flag indicating if background tasks are disabled
- `R19`: Original Task tool Zod schema
- `kw0`: Modified schema with `run_in_background` parameter omitted when disabled

#### Line 3149 - Bash Tool Schema

```javascript
rF1 = i1(process.env.CLAUDE_CODE_DISABLE_BACKGROUND_TASKS)
sF1 = rF1 ? T09.omit({run_in_background: !0}) : T09
```

- `rF1`: Global boolean flag for Bash tool
- `T09`: Original Bash tool Zod schema
- `sF1`: Modified schema with `run_in_background` parameter omitted when disabled

### Implementation Pattern

1. **Initialization**: At CLI startup, the environment variable is read and parsed using `i1()`
2. **Global Storage**: The boolean result is stored in global variables (`ifA`, `rF1`)
3. **Schema Modification**: Tool input schemas are dynamically modified using Zod's `.omit()` method
4. **Help Text**: Tool descriptions are conditionally modified to remove references to background execution

## SDK Patch Implementation

The `008-get-capabilities.js` patch implements a `get_capabilities` control request that mirrors CLI's internal logic:

```javascript
var __disableBackgroundTasks = (process.env.CLAUDE_CODE_DISABLE_BACKGROUND_TASKS || '').toLowerCase();
var __isDisabled = __disableBackgroundTasks === '1' 
                || __disableBackgroundTasks === 'true' 
                || __disableBackgroundTasks === 'yes' 
                || __disableBackgroundTasks === 'on';
var __backgroundEnabled = !__isDisabled;
```

### Request Format

```json
{
  "type": "control_request",
  "request_id": "xxx",
  "request": {
    "subtype": "get_capabilities"
  }
}
```

### Response Format

```json
{
  "type": "control_response",
  "response": {
    "subtype": "capabilities",
    "request_id": "xxx",
    "response": {
      "capabilities": {
        "background_tasks_enabled": true
      }
    }
  }
}
```

## SDK Usage

```kotlin
// Get capabilities from CLI
val capabilities = session.getCapabilities()
val backgroundEnabled = capabilities.backgroundTasksEnabled

if (backgroundEnabled) {
    // run_in_background parameter is available
} else {
    // run_in_background parameter is omitted from tool schemas
}
```

## Environment Variable Examples

| Value | Parsed Result | Background Tasks |
|-------|--------------|------------------|
| (not set) | `false` | Enabled |
| `""` | `false` | Enabled |
| `"0"` | `false` | Enabled |
| `"false"` | `false` | Enabled |
| `"no"` | `false` | Enabled |
| `"1"` | `true` | **Disabled** |
| `"true"` | `true` | **Disabled** |
| `"TRUE"` | `true` | **Disabled** |
| `"yes"` | `true` | **Disabled** |
| `"on"` | `true` | **Disabled** |

## Version History

- **CLI 2.1.4**: Current implementation using `i1()` function
- **CLI 2.0.73**: Earlier version used `E0()` function (same logic, different name due to minification)
