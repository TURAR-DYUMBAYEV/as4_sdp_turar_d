package ecommerce;
import ecommerce.persistence.*;
import ecommerce.subsystema.*;
import ecommerce.subsystemb.*;
import ecommerce.subsystemc.*;
import ecommerce.facade.*;
import java.nio.file.Path;
/** Composition root: constructs dependencies once; the CLI only receives facades. */
public final class Application {
    private final OrderFulfillmentFacade fulfillment;
    private final FulfillmentAdminFacade admin;
    public Application(Path path) {
        var db=new Database(path);
        var a=new InventoryRepository(db); var b=new WarehouseRepository(db); var c=new ShipmentRepository(db);
        var workflows=new WorkflowRepository(db);
        fulfillment=new OrderFulfillmentFacade(workflows,new InventoryManager(a,new OrderValidator()),a,
            new PackingStation(b,new WarehousePicker()),b,new PaymentGateway(c,new ShippingCarrier()),c);
        admin=new FulfillmentAdminFacade(workflows,fulfillment,a);
    }
    public OrderFulfillmentFacade fulfillment() { return fulfillment; }
    public FulfillmentAdminFacade admin() { return admin; }
}
