/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.gif;

import com.kismetsoftware.politigif.media.UploadRejectedException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GifHelpersTest {

    // ---- Tags ----

    @Test
    void tagsAreCleanedLowercasedAndDeduplicatedInOrder() {
        assertThat(Tags.parse("#Senate, debate  #2026 senate")).containsExactly("senate", "debate", "2026");
    }

    @Test
    void blankOrJunkTagsGiveNothing() {
        assertThat(Tags.parse(null)).isEmpty();
        assertThat(Tags.parse("   ")).isEmpty();
        assertThat(Tags.parse("### !!! ,,,")).isEmpty();
    }

    @Test
    void atMostTenTagsAreKept() {
        assertThat(Tags.parse("a b c d e f g h i j k l")).hasSize(10).endsWith("j");
    }

    @Test
    void longTagsAreCutToThirtyCharacters() {
        assertThat(Tags.normalize("x".repeat(40))).hasSize(30);
        assertThat(Tags.normalize("vote-2026_now")).isEqualTo("vote-2026_now");
    }

    // ---- titles ----

    @Test
    void titlesAreTrimmedAndWhitespaceCollapsed() {
        assertThat(GifService.cleanTitle("  When the   vote\tpasses ")).isEqualTo("When the vote passes");
    }

    @Test
    void aBlankTitleIsRefused() {
        assertThatThrownBy(() -> GifService.cleanTitle("  ")).isInstanceOf(UploadRejectedException.class);
        assertThatThrownBy(() -> GifService.cleanTitle(null)).isInstanceOf(UploadRejectedException.class);
    }

    @Test
    void titlesOver120CharactersAreRefused() {
        assertThat(GifService.cleanTitle("x".repeat(120))).hasSize(120);
        assertThatThrownBy(() -> GifService.cleanTitle("x".repeat(121))).isInstanceOf(UploadRejectedException.class);
    }

    @Test
    void idsAreTenUnambiguousCharacters() {
        String id = GifService.newId();

        assertThat(id).hasSize(10).matches("[A-Za-z0-9]+").doesNotContain("0", "O", "l", "1", "I");
    }

    // ---- ByteRange ----

    @Test
    void noHeaderMeansTheWholeObject() {
        assertThat(ByteRange.parse(null, 100)).isNull();
    }

    @Test
    void anExplicitRange() {
        assertThat(ByteRange.parse("bytes=10-19", 100)).containsExactly(10, 19);
    }

    @Test
    void anOpenEndedRange() {
        assertThat(ByteRange.parse("bytes=90-", 100)).containsExactly(90, 99);
    }

    @Test
    void aSuffixRange() {
        assertThat(ByteRange.parse("bytes=-10", 100)).containsExactly(90, 99);
        assertThat(ByteRange.parse("bytes=-500", 100)).containsExactly(0, 99);
    }

    @Test
    void anEndPastTheObjectIsClamped() {
        assertThat(ByteRange.parse("bytes=0-1000", 100)).containsExactly(0, 99);
    }

    @Test
    void unsatisfiableOrMalformedRangesAreNull() {
        assertThat(ByteRange.parse("bytes=100-", 100)).isNull();
        assertThat(ByteRange.parse("bytes=20-10", 100)).isNull();
        assertThat(ByteRange.parse("bytes=a-b", 100)).isNull();
        assertThat(ByteRange.parse("bytes=0-1,5-6", 100)).isNull();
        assertThat(ByteRange.parse("items=0-1", 100)).isNull();
    }

    // ---- sitemap ----

    @Test
    void theSitemapListsHomeAndEveryShareOnTheCanonicalHost() {
        Gif gif = new Gif("abcdEFGH23", "t", List.of(), "u", Instant.parse("2026-11-17T23:00:00Z"), 2, 2, 10, false, 1);

        String xml = SitemapController.render("https://www.politigif.com", List.of(gif));

        assertThat(xml).contains("<loc>https://www.politigif.com/</loc>")
                .contains("<loc>https://www.politigif.com/g/abcdEFGH23</loc><lastmod>2026-11-17</lastmod>")
                .startsWith("<?xml");
    }

    @Test
    void mp4DimensionsAreRoundedDownToEven() {
        Gif odd = new Gif("abcdEFGH23", "t", List.of(), "u", Instant.EPOCH, 321, 241, 10, false, 1);

        assertThat(odd.getWidth()).isEqualTo(320);
        assertThat(odd.getHeight()).isEqualTo(240);
        assertThat(odd.storageKey()).isEqualTo("gifs/abcdEFGH23.mp4");
    }
}
