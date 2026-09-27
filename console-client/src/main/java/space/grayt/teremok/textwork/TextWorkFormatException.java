package space.grayt.teremok.textwork;

/** Text-work fixture parsing error. The line number starts at 1. */
public class TextWorkFormatException extends RuntimeException {

    private final int lineNumber;

    public TextWorkFormatException(String message, int lineNumber) {
        super(message);
        this.lineNumber = lineNumber;
    }

    public int lineNumber() {
        return lineNumber;
    }
}
