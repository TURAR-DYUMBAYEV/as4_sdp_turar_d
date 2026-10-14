package ecommerce.exception;
public class PersistenceException extends FulfillmentException {
    public PersistenceException(String message) { super(message); }
    public PersistenceException(String message, Throwable cause) { super(message, cause); }
}
