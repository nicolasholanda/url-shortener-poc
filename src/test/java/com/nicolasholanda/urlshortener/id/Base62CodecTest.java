package com.nicolasholanda.urlshortener.id;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62CodecTest {

    @Test
    @DisplayName("encodes zero to the first alphabet character")
    void encodesZero() {
        assertThat(Base62Codec.encode(0L)).isEqualTo("0");
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 61L, 62L, 3843L, 238328L, 1_234_567_890L, Long.MAX_VALUE})
    @DisplayName("round trips every encoded value")
    void roundTrips(long value) {
        assertThat(Base62Codec.decode(Base62Codec.encode(value))).isEqualTo(value);
    }

    @Test
    @DisplayName("produces at most 11 characters for the largest long")
    void staysWithinKeyLength() {
        assertThat(Base62Codec.encode(Long.MAX_VALUE)).hasSizeLessThanOrEqualTo(11);
    }

    @Test
    @DisplayName("rejects negative values")
    void rejectsNegative() {
        assertThatThrownBy(() -> Base62Codec.encode(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects characters outside the alphabet")
    void rejectsInvalidCharacters() {
        assertThatThrownBy(() -> Base62Codec.decode("abc-def"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validates candidate keys")
    void validatesCandidates() {
        assertThat(Base62Codec.isValid("aZ09")).isTrue();
        assertThat(Base62Codec.isValid("")).isFalse();
        assertThat(Base62Codec.isValid(null)).isFalse();
        assertThat(Base62Codec.isValid("has space")).isFalse();
        assertThat(Base62Codec.isValid("0123456789abcdefg")).isFalse();
    }
}
