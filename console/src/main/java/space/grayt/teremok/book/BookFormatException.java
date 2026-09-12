package space.grayt.teremok.book;

/** Ошибка разбора книги. Номер строки — как в исходном файле, начиная с 1. */
public class BookFormatException extends RuntimeException {

    private final int lineNumber;

    public BookFormatException(String message, int lineNumber) {
        super(message);
        this.lineNumber = lineNumber;
    }

    public int lineNumber() {
        return lineNumber;
    }
}
