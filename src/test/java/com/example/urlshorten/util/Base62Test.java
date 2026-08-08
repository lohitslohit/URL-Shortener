package com.example.urlshorten.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Base62Test {

    @Test
    void encode_longKnownValues() {
        assertThat(Base62.encode(1)).isEqualTo("1");
        assertThat(Base62.encode(61)).isEqualTo("Z");
        assertThat(Base62.encode(62)).isEqualTo("10");
        assertThat(Base62.encode(1_000_000_000L)).isEqualTo("15FTGg");
    }

    @Test
    void encode_longRejectsNonPositive() {
        assertThatThrownBy(() -> Base62.encode(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base62.encode(-5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encode_bytesIsDeterministic() {
        byte[] input = "hello".getBytes(StandardCharsets.UTF_8);
        assertThat(Base62.encode(input)).isEqualTo(Base62.encode(input));
        assertThat(Base62.encode(input)).matches("[0-9a-zA-Z]+");
    }

    @Test
    void isValidCode() {
        assertThat(Base62.isValidCode("abc123XYZ")).isTrue();
        assertThat(Base62.isValidCode("has-dash")).isFalse();
        assertThat(Base62.isValidCode("")).isFalse();
        assertThat(Base62.isValidCode(null)).isFalse();
    }
}
