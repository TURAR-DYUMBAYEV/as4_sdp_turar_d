package ecommerce.facade;
import ecommerce.persistence.*;
import ecommerce.subsystema.InventoryRepository;
import java.util.*;
public final class FulfillmentAdminFacade {
    private final WorkflowRepository workflows;
    private final OrderFulfillmentFacade fulfillment;
    private final InventoryRepository inventory;
    public FulfillmentAdminFacade(WorkflowRepository workflows,OrderFulfillmentFacade fulfillment,InventoryRepository inventory) {
        this.workflows=workflows; this.fulfillment=fulfillment; this.inventory=inventory;
    }
    public List<Workflow> orders() { return workflows.all(); }
    public String inspect(String id) { return workflows.get(id)+"\n"+fulfillment.snapshot(id); }
    public List<Map<String,String>> history(String id) { return workflows.history(id); }
    public List<Map<String,String>> inventory() { return inventory.inventory(); }
    public Workflow compensate(String id) { return fulfillment.compensate(id); }
    public Workflow resume(String id) { return fulfillment.resume(id); }
    public void reset(boolean confirmed) { if(confirmed) workflows.reset(); }
}
