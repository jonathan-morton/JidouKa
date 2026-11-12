package dev.jidouka.codegen

import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

public abstract class EntitiesHandler {
    public abstract val enabled: Property<Boolean>
    public abstract val excludedDomains: SetProperty<String>
    public abstract val entityNameOverrides: MapProperty<String, String>
}
