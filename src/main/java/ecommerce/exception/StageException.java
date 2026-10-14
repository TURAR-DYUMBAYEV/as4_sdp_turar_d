package ecommerce.exception;
public class StageException extends FulfillmentException {
    public StageException(String message) { super(message); }
    public StageException(String message, Throwable cause) { super(message, cause); }
}
