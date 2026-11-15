package com.ledgerlite.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Single place that defines how money is represented: BigDecimal, scale 4, HALF_EVEN. Never double/float. */
public final class Money {

    public static final int SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private Money() {}

    public static BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(SCALE, ROUNDING);
    }

    /** Plain decimal string for JSON (never scientific notation), e.g. "100.0000". */
    public static String format(BigDecimal amount) {
        return normalize(amount).toPlainString();
    }
}
