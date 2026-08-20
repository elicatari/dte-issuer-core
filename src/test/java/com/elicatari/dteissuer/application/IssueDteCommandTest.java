package com.elicatari.dteissuer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elicatari.dteissuer.application.port.in.IdempotencyKey;
import com.elicatari.dteissuer.application.port.in.IssueDteCommand;
import com.elicatari.dteissuer.domain.Money;
import com.elicatari.dteissuer.domain.Rut;
import com.elicatari.dteissuer.domain.TenantId;
import org.junit.jupiter.api.Test;

class CanonicalRequestHashTest {

    private static final TenantId ALPHA = new TenantId("alpha");
    private static final IdempotencyKey KEY = new IdempotencyKey("k");

    @Test
    void sameCanonicalBodyYieldsSameHashRegardlessOfRutFormatting() {
        IssueDteCommand dotted =
                new IssueDteCommand(ALPHA, KEY, Rut.parse("12.345.678-5"), Money.pesos(1000));
        IssueDteCommand compact =
                new IssueDteCommand(ALPHA, KEY, Rut.parse("12345678-5"), Money.pesos(1000));

        assertThat(CanonicalRequestHash.of(dotted)).isEqualTo(CanonicalRequestHash.of(compact));
        assertThat(CanonicalRequestHash.of(dotted)).hasSize(64);
    }

    @Test
    void differentNetoYieldsDifferentHash() {
        IssueDteCommand a = new IssueDteCommand(ALPHA, KEY, Rut.parse("12.345.678-5"), Money.pesos(1000));
        IssueDteCommand b = new IssueDteCommand(ALPHA, KEY, Rut.parse("12.345.678-5"), Money.pesos(1001));

        assertThat(CanonicalRequestHash.of(a)).isNotEqualTo(CanonicalRequestHash.of(b));
    }

    @Test
    void hashIgnoresIdempotencyKeyAndTenant() {
        IssueDteCommand alpha = new IssueDteCommand(
                ALPHA, new IdempotencyKey("one"), Rut.parse("12.345.678-5"), Money.pesos(1000));
        IssueDteCommand beta = new IssueDteCommand(
                new TenantId("beta"),
                new IdempotencyKey("two"),
                Rut.parse("12.345.678-5"),
                Money.pesos(1000));

        assertThat(CanonicalRequestHash.of(alpha)).isEqualTo(CanonicalRequestHash.of(beta));
    }
}

class IssueDteCommandTest {

    private static final TenantId ALPHA = new TenantId("alpha");
    private static final IdempotencyKey KEY = new IdempotencyKey("k");
    private static final Rut RUT = Rut.parse("12.345.678-5");

    @Test
    void rejectsZeroNeto() {
        assertThatThrownBy(() -> new IssueDteCommand(ALPHA, KEY, RUT, Money.zero()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("neto");
    }

    @Test
    void rejectsNulls() {
        assertThatThrownBy(() -> new IssueDteCommand(null, KEY, RUT, Money.pesos(1)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IssueDteCommand(ALPHA, null, RUT, Money.pesos(1)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IssueDteCommand(ALPHA, KEY, null, Money.pesos(1)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IssueDteCommand(ALPHA, KEY, RUT, null))
                .isInstanceOf(NullPointerException.class);
    }
}

class IdempotencyKeyTest {

    @Test
    void trimsValue() {
        assertThat(new IdempotencyKey("  abc  ").value()).isEqualTo("abc");
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new IdempotencyKey("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyKey(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTooLong() {
        String tooLong = "k".repeat(IdempotencyKey.MAX_LENGTH + 1);
        assertThatThrownBy(() -> new IdempotencyKey(tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("255");
    }

    @Test
    void toStringIsTheValue() {
        assertThat(new IdempotencyKey("abc")).hasToString("abc");
    }
}