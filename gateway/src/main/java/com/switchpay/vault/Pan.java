package com.switchpay.vault;

import java.io.Serializable;
import java.util.Objects;

/**
 * A wrapper type for the raw PAN string.
 * This class is intentionally not Serializable.
 * Overrides toString() to prevent accidental logging of the PAN.
 */
public final class Pan {
    private final String value;

    public Pan(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PAN cannot be null or empty");
        }
        // Remove spaces or dashes if present
        this.value = value.replaceAll("[\\s-]", "");
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "Pan[****]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pan pan = (Pan) o;
        return Objects.equals(value, pan.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    // Explicitly prevent serialization by throwing if someone tries to serialize through reflection hacks
    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        throw new java.io.NotSerializableException("Pan must not be serialized");
    }

    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        throw new java.io.NotSerializableException("Pan must not be serialized");
    }
}
