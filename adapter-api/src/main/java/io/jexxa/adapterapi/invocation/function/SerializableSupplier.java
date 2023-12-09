package io.jexxa.adapterapi.invocation.function;

import java.io.Serializable;
import java.util.function.Supplier;

@FunctionalInterface
public interface SerializableSupplier<T> extends Supplier<T>, Serializable
{
    // Functional interface for a supplier that can be serialized
}
