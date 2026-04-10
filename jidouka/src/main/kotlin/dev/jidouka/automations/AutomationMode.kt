package dev.jidouka.automations

public sealed class AutomationMode {
    /**
     * Only one single automation will run at a time, others are warned
     */
    public data object Single : AutomationMode()

    /**
     * Runs an automation. Will cancel the previously running automation before running.
     */
    public data object Restart : AutomationMode()

    /**
     * Automation will execute with a maximum number of queued actions
     * The currently executing action is NOT part of the queue
     */
    public data class Queued(val max: Int = DEFAULT_MAX_QUEUED) : AutomationMode()

    /**
     * Multiple automations will run in parallel, up to the defined maximum number
     * @param maximum The maximum number of automations that can run in parallel at once
     */
    public data class Parallel(val maximum: Int = DEFAULT_MAX_PARALLEL) : AutomationMode()

    private companion object {
        const val DEFAULT_MAX_QUEUED = 10
        const val DEFAULT_MAX_PARALLEL = 10
    }
}