package dev.mauricioandrade.kakeizu.application.logging;

import java.util.UUID;

/**
 * An id that ties together the log lines of one upload: "[3f2a9c1e] Model read the receipt in 9120 ms".
 * A scoped value instead of a ThreadLocal: it is bound for a block of code and gone after it, can't be
 * changed half way, and threads forked inside a structured task scope see it too.
 */
public final class Correlation {

    private static final ScopedValue<String> ID = ScopedValue.newInstance();

    private Correlation() {
    }

    public static String newId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public static <T, X extends Throwable> T with(String id, ScopedValue.CallableOp<T, X> op) throws X {
        return ScopedValue.where(ID, id).call(op);
    }

    /**
     * The id bound to the current code, or "-" outside an upload.
     */
    public static String id() {
        return ID.orElse("-");
    }
}
