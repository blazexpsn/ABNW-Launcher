package org.teamzetaverse.cosmetics.api;

public final class InvalidRegistryException extends RuntimeException {
    public InvalidRegistryException(final String message) {
        super(message);
    }

    public InvalidRegistryException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
