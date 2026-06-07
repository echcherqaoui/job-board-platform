package com.echcherqaoui.jobboard.util;

import lombok.NonNull;

import java.math.BigDecimal;

import static java.math.RoundingMode.HALF_UP;

public final class MoneyConverter {

    private MoneyConverter() {
    }

    /**
     * Converts a BigDecimal amount to the smallest currency unit (cents, fils, centimes…).
     * Example: 1500.75 → 150075
     */
    public static long toCents(@NonNull BigDecimal amount, int decimalPlaces) {
        return amount
              .setScale(decimalPlaces, HALF_UP)
              .movePointRight(decimalPlaces)
              .longValueExact();
    }

    /**
     * Reconstructs a BigDecimal from the smallest currency unit.
     * Example: 150075, 2 → 1500.75
     * 1500, 0 → 1500
     */
    @NonNull
    public static BigDecimal fromCents(long cents, int decimalPlaces) {
        return BigDecimal.valueOf(cents, decimalPlaces);
    }
}