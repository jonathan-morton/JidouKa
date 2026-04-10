package dev.jidouka.components.event

public object HaEvents {
    //region Lifecycle events
    public val Start: EventType<GenericEvent> = EventType("homeassistant_start", GenericEvent.parser)
    public val Stop: EventType<GenericEvent> = EventType("homeassistant_stop", GenericEvent.parser)
    public val FinalWrite: EventType<GenericEvent> = EventType("homeassistant_final_write", GenericEvent.parser)
    public val Close: EventType<GenericEvent> = EventType("homeassistant_close", GenericEvent.parser)
    //endregion

    //region State events
    public val StateChanged: EventType<GenericEvent> = EventType("state_changed", GenericEvent.parser)
    //endregion

    //region Service events
    public val ServiceRegistered: EventType<GenericEvent> = EventType("service_registered", GenericEvent.parser)
    public val ServiceRemoved: EventType<GenericEvent> = EventType("service_removed", GenericEvent.parser)
    public val CallService: EventType<GenericEvent> = EventType("call_service", GenericEvent.parser)
    //endregion

    //region Component events
    public val ComponentLoaded: EventType<GenericEvent> = EventType("component_loaded", GenericEvent.parser)
    public val PlatformDiscovered: EventType<GenericEvent> = EventType("platform_discovered", GenericEvent.parser)
    //endregion

    //region Automation events
    public val AutomationTriggered: EventType<GenericEvent> = EventType("automation_triggered", GenericEvent.parser)
    public val ScriptStarted: EventType<GenericEvent> = EventType("script_started", GenericEvent.parser)
    //endregion

    //region Timer events
    public val TimerStarted: EventType<GenericEvent> = EventType("timer.started", GenericEvent.parser)
    public val TimerPaused: EventType<GenericEvent> = EventType("timer.paused", GenericEvent.parser)
    public val TimerRestarted: EventType<GenericEvent> = EventType("timer.restarted", GenericEvent.parser)
    public val TimerCancelled: EventType<GenericEvent> = EventType("timer.cancelled", GenericEvent.parser)
    public val TimerFinished: EventType<GenericEvent> = EventType("timer.finished", GenericEvent.parser)
    //endregion

    //region Time event
    public val TimeChanged: EventType<GenericEvent> = EventType("time_changed", GenericEvent.parser)
    //endregion

    //region Registry events
    public val DeviceRegistryUpdated: EventType<GenericEvent> =
        EventType("device_registry_updated", GenericEvent.parser)
    public val EntityRegistryUpdated: EventType<GenericEvent> =
        EventType("entity_registry_updated", GenericEvent.parser)
    public val AreaRegistryUpdated: EventType<GenericEvent> = EventType("area_registry_updated", GenericEvent.parser)
    //endregion

    //region User events
    public val UserAdded: EventType<GenericEvent> = EventType("user_added", GenericEvent.parser)
    public val UserRemoved: EventType<GenericEvent> = EventType("user_removed", GenericEvent.parser)
    //endregion

    //region Mobile app
    public val MobileAppNotificationAction: EventType<GenericEvent> =
        EventType("mobile_app_notification_action", GenericEvent.parser)
    //endregion
}