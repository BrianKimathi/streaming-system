package com.streamx.media.delivery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ByteRangeParserTest {

    @Test
    void noHeaderMeansFullBody() throws Exception {
        assertNull(ByteRangeParser.parse(null, 100));
        assertNull(ByteRangeParser.parse("  ", 100));
    }

    @Test
    void closedRange() throws Exception {
        assertEquals(new ByteRange(0, 3), ByteRangeParser.parse("bytes=0-3", 100));
        assertEquals(new ByteRange(10, 10), ByteRangeParser.parse("bytes=10-10", 100));
        assertEquals(new ByteRange(5, 9), ByteRangeParser.parse("BYTES = 5-9".replace(" ", ""), 100));
    }

    @Test
    void endIsClampedToTheLastByte() throws Exception {
        assertEquals(new ByteRange(90, 99), ByteRangeParser.parse("bytes=90-5000", 100));
    }

    @Test
    void openEndedRange() throws Exception {
        ByteRange range = ByteRangeParser.parse("bytes=40-", 100);
        assertEquals(new ByteRange(40, 99), range);
        assertEquals(60, range.length());
        assertEquals("bytes 40-99/100", range.contentRange(100));
    }

    @Test
    void suffixRange() throws Exception {
        assertEquals(new ByteRange(90, 99), ByteRangeParser.parse("bytes=-10", 100));
        assertEquals(new ByteRange(0, 99), ByteRangeParser.parse("bytes=-500", 100));
    }

    @Test
    void multipleRangesFallBackToFullBody() throws Exception {
        assertNull(ByteRangeParser.parse("bytes=0-1,5-6", 100));
    }

    @Test
    void unsatisfiableRangesAreRejected() {
        for (String header : new String[]{"bytes=100-", "bytes=100-200", "bytes=-0", "bytes=500-600"}) {
            assertThrows(ByteRangeParser.InvalidRangeException.class, () -> ByteRangeParser.parse(header, 100), header);
        }
        assertThrows(ByteRangeParser.InvalidRangeException.class, () -> ByteRangeParser.parse("bytes=-5", 0));
    }

    @Test
    void malformedRangesAreRejected() {
        for (String header : new String[]{"bytes=abc", "bytes=5", "bytes=9-3", "items=0-1", "bytes=-", "bytes=1-x",
                "bytes=-1-2", "bytes=99999999999999999999-"}) {
            assertThrows(ByteRangeParser.InvalidRangeException.class, () -> ByteRangeParser.parse(header, 100), header);
        }
    }
}
