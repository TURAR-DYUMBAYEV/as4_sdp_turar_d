package ecommerce.subsystema;
import ecommerce.exception.StageException;

public final class InventoryReservation {
    public enum Status { NEW, RESERVED, RELEASED }
    private final String id;
    private final String product;
    private final int quantity;
    private Status status;
    public InventoryReservation(String id, String product, int quantity, Status status) {
        this.id=id; this.product=product; this.quantity=quantity; this.status=status;
    }
    public String id() { return id; }
    public String product() { return product; }
    public int quantity() { return quantity; }
    public Status status() { return status; }
    public void transition(Status next) {
        if (next==status) return;
        if ((status==Status.NEW && next==Status.RESERVED) || (status==Status.RESERVED && next==Status.RELEASED)) {
            status=next;
        } else throw new StageException("Illegal InventoryReservation transition: " + status + " -> " + next);
    }
}
