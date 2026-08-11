package vn.covie.shared.money;

import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

public record Money(long amount, String currency) {
    public Money {
        Objects.requireNonNull(currency, "currency");
        currency = currency.toUpperCase(Locale.ROOT);
        Currency.getInstance(currency);
    }
}
