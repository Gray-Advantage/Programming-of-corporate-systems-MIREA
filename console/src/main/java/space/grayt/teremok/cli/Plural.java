package space.grayt.teremok.cli;

/**
 * Число с существительным в нужной форме: 1 реплика, 2 реплики, 5 реплик.
 * В JDK нет публичного API правил множественного числа, а ICU4J нарушил бы требование
 * «ноль зависимостей в рантайме», поэтому здесь записано правило CLDR для русских целых чисел.
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
