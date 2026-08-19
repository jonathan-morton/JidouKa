package dev.jidouka.components

public interface DomainParserProvider {
    public fun domains(): List<Domain<*>>
}