package space.grayt.teremok.exception;

/** The operation breaks a business rule, for example deleting a published voicing. */
public class BusinessRuleException extends TeremokException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
