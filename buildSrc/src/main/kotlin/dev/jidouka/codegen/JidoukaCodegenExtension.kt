package dev.jidouka.codegen

import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Nested

public abstract class JidoukaCodegenExtension {
    public abstract val outputPackage: Property<String>
    public abstract val excludedDomains: SetProperty<String>

    public abstract val haHost: Property<String>
    public abstract val haPort: Property<Int>
    public abstract val haAccessToken: Property<String>

    @get:Nested
    public abstract val entities: EntitiesHandler

    public fun entities(action: Action<EntitiesHandler>) {
        action.execute(entities)
    }
}
