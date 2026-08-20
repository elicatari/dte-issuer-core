package com.elicatari.dteissuer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

class DomainPropertyTest {

    @Property(tries = 80)
    void validCheckDigitAlwaysParses(@ForAll("rutBodies") String body) {
        char digit = Rut.checkDigitOf(body);
        Rut rut = Rut.parse(body + "-" + digit);
        assertThat(rut.body()).isEqualTo(body);
        assertThat(rut.checkDigit()).isEqualTo(digit);
    }

    @Property(tries = 80)
    void wrongCheckDigitIsRejected(@ForAll("rutBodies") String body) {
        char real = Rut.checkDigitOf(body);
        char wrong = real == '0' ? '1' : '0';
        assertThatThrownBy(() -> Rut.parse(body + "-" + wrong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Property(tries = 80)
    void iva19IsHalfUpToWholePesos(@ForAll @LongRange(min = 1, max = 10_000_000) long neto) {
        Money money = Money.pesos(neto);
        Money iva = money.iva19();
        BigDecimal expected = BigDecimal.valueOf(neto)
                .multiply(Money.IVA_RATE)
                .setScale(0, RoundingMode.HALF_UP);
        assertThat(iva.amount()).isEqualByComparingTo(expected);
        assertThat(money.plus(iva).amount()).isEqualByComparingTo(BigDecimal.valueOf(neto).add(expected));
    }

    @Property(tries = 40)
    void reservingNFoliosOnRangeOfNLeavesNoGaps(
            @ForAll @IntRange(min = 1, max = 20) int size, @ForAll @LongRange(min = 1, max = 1_000) long start) {
        FolioRange range = FolioRange.open(new TenantId("alpha"), new Folio(start), new Folio(start + size - 1));
        Set<Long> seen = new HashSet<>();
        FolioRange current = range;
        for (int i = 0; i < size; i++) {
            FolioReservation reservation = current.reserveNext();
            seen.add(reservation.folio().value());
            current = reservation.range();
        }
        assertThat(seen).hasSize(size);
        assertThat(seen).contains(start, start + size - 1);
        assertThat(current.exhausted()).isTrue();
        assertThatThrownBy(current::reserveNext).isInstanceOf(NoFolioAvailableException.class);
    }

    @Provide
    Arbitrary<String> rutBodies() {
        return Arbitraries.longs().between(1, 99_999_999L).map(String::valueOf);
    }
}