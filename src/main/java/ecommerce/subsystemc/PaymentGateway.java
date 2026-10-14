package ecommerce.subsystemc;
import ecommerce.exception.StageException;
public final class PaymentGateway {
    private final ShipmentRepository repository;
    private final ShippingCarrier carrier;
    public PaymentGateway(ShipmentRepository repository,ShippingCarrier carrier) { this.repository=repository; this.carrier=carrier; }
    public Shipment finalizeOrder(String id,boolean declined,boolean shippingFailure) {
        if(id==null || id.isBlank()) throw new StageException("Payment order ID is required.");
        var s=repository.find(id);
        if(s==null) { s=new Shipment(id,Shipment.Status.NEW,"UNPAID",""); repository.save(s); }
        if(s.status()==Shipment.Status.CONFIRMED) return s;
        if(s.status()==Shipment.Status.CANCELLED) throw new StageException("Shipment was cancelled.");
        if(s.status()==Shipment.Status.NEW) {
            if(declined) throw new StageException("Payment was declined.");
            s.pay();
            repository.save(s); // Durable charge record before contacting the simulated carrier.
        }
        try {
            s.confirm(carrier.register(id,shippingFailure));
            repository.save(s);
            return s;
        } catch(StageException e) {
            s.cancel();
            repository.save(s); // If this write fails, PAID is still recoverable on disk.
            throw e;
        }
    }
    public void cancel(String id) {
        var s=repository.find(id);
        if(s!=null) { s.cancel(); repository.save(s); }
    }
}
