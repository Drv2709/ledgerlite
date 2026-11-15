package com.ledgerlite.common.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @Test
    void decimalAdditionIsExact() {
        BigDecimal sum = new BigDecimal("0.1").add(new BigDecimal("0.2"));

        assertThat(sum).isEqualByComparingTo("0.3");
        assertThat(Money.format(sum)).isEqualTo("0.3000");
    }

    @Test
    void tiesRoundToEvenNotUp() {
        // HALF_EVEN: ...0|5 -> down to 0 (even), ...1|5 -> up to 2 (even), ...2|5 -> down to 2 (even).
        // HALF_UP would give 1.0001 / 1.0002 / 1.0003, so this fails if the rounding mode is changed.
        assertThat(Money.normalize(new BigDecimal("1.00005"))).isEqualTo(new BigDecimal("1.0000"));
        assertThat(Money.normalize(new BigDecimal("1.00015"))).isEqualTo(new BigDecimal("1.0002"));
        assertThat(Money.normalize(new BigDecimal("1.00025"))).isEqualTo(new BigDecimal("1.0002"));
    }

    @Test
    void alwaysUsesScaleFour() {
        assertThat(Money.normalize(new BigDecimal("10")).scale()).isEqualTo(4);
        assertThat(Money.normalize(new BigDecimal("10.5")).toPlainString()).isEqualTo("10.5000");
    }

    @Test
    void formatNeverUsesScientificNotation() {
        assertThat(Money.format(new BigDecimal("1E+3"))).isEqualTo("1000.0000");
        assertThat(Money.format(new BigDecimal("1234567.1"))).isEqualTo("1234567.1000");
    }
}
