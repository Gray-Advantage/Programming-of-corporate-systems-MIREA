package space.grayt.teremok.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PluralTest {

    @Test
    void oneAndNumbersEndingInOne() {
        assertEquals("1 фрагмент", Plural.fragments(1));
        assertEquals("21 фрагмент", Plural.fragments(21));
        assertEquals("101 фрагмент", Plural.fragments(101));
    }

    @Test
    void numbersEndingInTwoThreeFour() {
        assertEquals("2 фрагмента", Plural.fragments(2));
        assertEquals("4 фрагмента", Plural.fragments(4));
        assertEquals("22 фрагмента", Plural.fragments(22));
    }

    @Test
    void zeroAndNumbersFromFiveUp() {
        assertEquals("0 фрагментов", Plural.fragments(0));
        assertEquals("5 фрагментов", Plural.fragments(5));
        assertEquals("18 фрагментов", Plural.fragments(18));
        assertEquals("9999 фрагментов", Plural.fragments(9999));
    }

    @Test
    void elevenToFourteenUseManyForm() {
        assertEquals("11 фрагментов", Plural.fragments(11));
        assertEquals("12 фрагментов", Plural.fragments(12));
        assertEquals("14 фрагментов", Plural.fragments(14));
        assertEquals("111 фрагментов", Plural.fragments(111));
        assertEquals("112 фрагментов", Plural.fragments(112));
    }

    @Test
    void otherWordsPassTheirFormsExplicitly() {
        assertEquals("1 лайк", Plural.of(1, "лайк", "лайка", "лайков"));
        assertEquals("3 дизлайка", Plural.of(3, "дизлайк", "дизлайка", "дизлайков"));
        assertEquals("0 лайков", Plural.of(0, "лайк", "лайка", "лайков"));
    }
}
