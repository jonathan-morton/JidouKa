package dev.jidouka.components

/**
 * Every entity in Home Assistant belongs to a general category known as a domain
 */
public class Domain<S : BaseState>(
    public val id: String,
    public val entityParser: BaseState.Parser<S>? = null
) {
    public companion object {
        public val BinarySensor: Domain<GenericState> = Domain("binary_sensor")
        public val Sensor: Domain<GenericState> = Domain("sensor")
        public val Light: Domain<GenericState> = Domain("light")
        public val Switch: Domain<GenericState> = Domain("switch")
        public val Climate: Domain<GenericState> = Domain("climate")
        public val Cover: Domain<GenericState> = Domain("cover")
        public val Fan: Domain<GenericState> = Domain("fan")
        public val Lock: Domain<GenericState> = Domain("lock")
        public val MediaPlayer: Domain<GenericState> = Domain("media_player")
        public val Camera: Domain<GenericState> = Domain("camera")
        public val Vacuum: Domain<GenericState> = Domain("vacuum")
    }
}