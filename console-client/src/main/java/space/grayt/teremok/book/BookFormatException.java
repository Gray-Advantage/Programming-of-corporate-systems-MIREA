package space.grayt.teremok.book;

/** Book parsing error. The line number refers to the source file, starting at 1. */
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
