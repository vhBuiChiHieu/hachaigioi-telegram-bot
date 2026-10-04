package com.acme.moviebot.catalog.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.moviebot.catalog.CatalogCommands.AttachLinkCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ExternalLinkTest {

    @ParameterizedTest
    @ValueSource(strings = {"https://t.me/c/123/456?single", "http://example.com/watch?id=2&lang=vi#player",
            "HTTPS://example.com/path", "https://example.com/phim-tiếng-việt", "https://example.com/a%20b"})
    void acceptsHttpLinksAndPreservesTheirContents(String url) {
        assertThat(new AttachLinkCommand(91L, "  " + url + "  ").externalUrl()).isEqualTo(url);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "example.com/watch", "ftp://example.com/video", "javascript:alert(1)",
            "https://", "https:///watch", "https://example.com/a b", "https://example.com/a\nhttps://example.com/b",
            "https://user:password@example.com/watch"})
    void rejectsInvalidLinks(String url) {
        assertThatThrownBy(() -> new AttachLinkCommand(91L, url)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void permitsTheMaximumLengthAndRejectsOversizedLinks() {
        String prefix = "https://example.com/";
        String url = prefix + "a".repeat(2048 - prefix.length());
        assertThat(new AttachLinkCommand(91L, url).externalUrl()).isEqualTo(url);
        assertThatThrownBy(() -> new AttachLinkCommand(91L, url + "a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2048");
    }
}
