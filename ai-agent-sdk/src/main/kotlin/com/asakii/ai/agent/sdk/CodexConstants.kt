package com.asakii.ai.agent.sdk

/**
 * Codex feature keys for configuration.
 * These keys are used in `[features]` section of Codex config.
 *
 * @see <a href="https://developers.openai.com/codex/config-reference/">Codex Configuration Reference</a>
 */
object CodexFeatures {
    /**
     * Enable the default shell tool for running commands.
     * Stage: Stable, Default: true
     */
    const val SHELL_TOOL = "shell_tool"

    /**
     * Include the freeform apply_patch tool.
     * Stage: Beta, Default: false
     */
    const val APPLY_PATCH_FREEFORM = "apply_patch_freeform"

    /**
     * Use the unified PTY-backed exec tool.
     * Stage: Experimental, Default: false
     */
    const val UNIFIED_EXEC = "unified_exec"

    /**
     * Include the view_image tool.
     * Stage: Stable, Default: true
     */
    const val VIEW_IMAGE_TOOL = "view_image_tool"

    /**
     * Allow the model to issue web searches.
     * Stage: Stable, Default: false
     */
    const val WEB_SEARCH_REQUEST = "web_search_request"

    /**
     * Enable discovery and injection of skills.
     * Stage: Experimental, Default: false
     */
    const val SKILLS = "skills"
}
