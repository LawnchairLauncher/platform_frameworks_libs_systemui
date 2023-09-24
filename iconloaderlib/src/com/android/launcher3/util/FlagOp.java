package com.android.launcher3.util;

public interface FlagOp {

    FlagOp NO_OP = i -> i;

    int apply(int flags);

    static FlagOp addFlag(int flag) {
        return i -> i | flag;
    }

    static FlagOp removeFlag(int flag) {
        return i -> i & ~flag;
    }

    /**
     * Returns a new OP which adds or removed the provided flag based on {@code enable} after
     * applying all previous operations
     */
    default FlagOp setFlag(int flag, boolean enable) {
        return enable ? addFlag(flag) : removeFlag(flag);
    }
}