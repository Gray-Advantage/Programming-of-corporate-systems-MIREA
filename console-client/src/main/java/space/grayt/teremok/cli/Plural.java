package space.grayt.teremok.cli;

/**
 * A count with a noun in the correct Russian plural form: one, few or many, as for 1, 2 and 5.
 * The JDK has no public plural rules API and ICU4J would break the zero runtime dependencies
 * requirement, so the CLDR rule for Russian integers is written out here.
 */
final class Plural {

    private Plural() {
    }

    static String lines(int count) {
        return of(count, "реплика", "реплики", "реплик");
    }

    static String of(int count, String one, String few, String many) {
        int lastTwo = Math.abs(count) % 100;
        int last = lastTwo % 10;
        String form;
        if (last == 1 && lastTwo != 11) {
            form = one;
        } else if (last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14)) {
            form = few;
        } else {
            form = many;
        }
        return count + " " + form;
    }
}
