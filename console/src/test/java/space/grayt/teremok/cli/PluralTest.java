package space.grayt.teremok.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PluralTest {

    @Test
    void oneAndNumbersEndingInOne() {
        assertEquals("1 реплика", Plural.lines(1));
        assertEquals("21 реплика", Plural.lines(21));
        assertEquals("101 реплика", Plural.lines(101));
    }

    @Test
    void numbersEndingInTwoThreeFour() {
        assertEquals("2 реплики", Plural.lines(2));
        assertEquals("4 реплики", Plural.lines(4));
        assertEquals("22 реплики", Plural.lines(22));
    }

    @Test
    void zeroAndNumbersFromFiveUp() {
        assertEquals("0 реплик", Plural.lines(0));
        assertEquals("5 реплик", Plural.lines(5));
        assertEquals("18 реплик", Plural.lines(18));
        assertEquals("9999 реплик", Plural.lines(9999));
    }

    @Test
    void elevenToFourteenUseManyForm() {
        assertEquals("11 реплик", Plural.lines(11));
        assertEquals("12 реплик", Plural.lines(12));
        assertEquals("14 реплик", Plural.lines(14));
        assertEquals("111 реплик", Plural.lines(111));
        assertEquals("112 реплик", Plural.lines(112));
    }

    @Test
    void otherWordsPassTheirFormsExplicitly() {
        assertEquals("1 лайк", Plural.of(1, "лайк", "лайка", "лайков"));
        assertEquals("3 дизлайка", Plural.of(3, "дизлайк", "дизлайка", "дизлайков"));
        assertEquals("0 лайков", Plural.of(0, "лайк", "лайка", "лайков"));
    }
}
