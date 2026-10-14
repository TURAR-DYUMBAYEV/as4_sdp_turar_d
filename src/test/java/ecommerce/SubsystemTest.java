package ecommerce;
import ecommerce.persistence.Database;
import ecommerce.subsystema.*;
import ecommerce.subsystemb.*;
import ecommerce.subsystemc.*;
import ecommerce.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SubsystemTest {
    @TempDir Path dir;
    Database db;
    @BeforeEach void setup() { db=new Database(dir.resolve("test.db")); }
    @Test void inventoryWorksIndependentlyAndReloads() {
        var repo=new InventoryRepository(db);
        var manager=new InventoryManager(repo,new OrderValidator());
        assertNull(repo.find("missing"));
        var r=manager.reserve("a","BOOK",3);
        assertEquals(InventoryReservation.Status.RESERVED,r.status());
        assertEquals(3,r.quantity()); assertEquals("BOOK",r.product()); assertEquals("a",r.id());
        manager.reserve("a","BOOK",3);
        assertThrows(StageException.class,()->manager.reserve("a","BOOK",4));
        assertThrows(StageException.class,()->manager.reserve("a","MOUSE",3));
        assertEquals("97",repo.inventory().get(0).get("stock"));
        manager.release("a"); manager.release("a"); manager.release("missing");
        assertEquals("100",repo.inventory().get(0).get("stock"));
        assertEquals(InventoryReservation.Status.RELEASED,new InventoryRepository(new Database(dir.resolve("test.db"))).find("a").status());
        assertThrows(StageException.class,()->manager.reserve("a","BOOK",3));
        assertThrows(ValidationException.class,()->manager.reserve("x","UNKNOWN",1));
        assertThrows(ValidationException.class,()->manager.reserve("x","BOOK",101));
        assertThrows(ValidationException.class,()->manager.reserve("x","BOOK",0));
        for(String id:new String[]{null,""," "}) assertThrows(ValidationException.class,()->manager.reserve(id,"BOOK",1));
        for(String product:new String[]{null,""," "}) assertThrows(ValidationException.class,()->manager.reserve("x",product,1));
    }
    @Test void warehouseWorksIndependently() {
        var repo=new WarehouseRepository(db);
        var station=new PackingStation(repo,new WarehousePicker());
        assertNull(repo.find("missing")); station.cancel("missing");
        assertThrows(StageException.class,()->station.pack("b","BOOK",2,true));
        assertNull(repo.find("b"));
        var job=station.pack("b","BOOK",2,false);
        assertEquals("b",job.id()); assertEquals(PackingJob.Status.PACKED,job.status());
        station.pack("b","BOOK",2,false);
        assertThrows(StageException.class,()->station.pack("b","BOOK",3,false));
        assertThrows(StageException.class,()->station.pack("b","MOUSE",2,false));
        station.cancel("b"); station.cancel("b");
        assertEquals(PackingJob.Status.CANCELLED,repo.find("b").status());
        assertThrows(StageException.class,()->station.pack("b","BOOK",2,false));
        assertThrows(StageException.class,()->station.pack("x","BOOK",0,false));
        for(String s:new String[]{null,""}) {
            assertThrows(StageException.class,()->station.pack(s,"BOOK",1,false));
            assertThrows(StageException.class,()->station.pack("x",s,1,false));
        }
    }
    @Test void paymentAndShippingWorkIndependently() {
        var repo=new ShipmentRepository(db);
        var gateway=new PaymentGateway(repo,new ShippingCarrier());
        assertNull(repo.find("missing")); gateway.cancel("missing");
        assertThrows(StageException.class,()->gateway.finalizeOrder(null,false,false));
        assertThrows(StageException.class,()->gateway.finalizeOrder("",false,false));
        var s=gateway.finalizeOrder("c",false,false);
        assertEquals("c",s.id()); assertEquals("TRACK-c",s.tracking()); assertEquals("CHARGED",s.payment());
        assertEquals(Shipment.Status.CONFIRMED,gateway.finalizeOrder("c",true,true).status());
        gateway.cancel("c"); gateway.cancel("c");
        assertEquals("REFUNDED",repo.find("c").payment());
        assertThrows(StageException.class,()->gateway.finalizeOrder("c",false,false));
        assertThrows(StageException.class,()->gateway.finalizeOrder("declined",true,false));
        gateway.cancel("declined"); assertEquals("UNPAID",repo.find("declined").payment());
        assertThrows(StageException.class,()->gateway.finalizeOrder("shipping",false,true));
        assertEquals("REFUNDED",repo.find("shipping").payment());
        var paid=new Shipment("pending",Shipment.Status.NEW,"UNPAID",""); paid.pay(); repo.save(paid);
        assertEquals(Shipment.Status.CONFIRMED,gateway.finalizeOrder("pending",true,false).status());
    }
    @Test void entitiesEnforceTransitions() {
        var a=new InventoryReservation("a","BOOK",1,InventoryReservation.Status.NEW);
        a.transition(InventoryReservation.Status.NEW);
        assertThrows(StageException.class,()->a.transition(InventoryReservation.Status.RELEASED));
        a.transition(InventoryReservation.Status.RESERVED); a.transition(InventoryReservation.Status.RELEASED);
        assertThrows(StageException.class,()->a.transition(InventoryReservation.Status.RESERVED));
        var b=new PackingJob("b","BOOK",1,PackingJob.Status.NEW);
        b.transition(PackingJob.Status.NEW);
        assertThrows(StageException.class,()->b.transition(PackingJob.Status.CANCELLED));
        b.transition(PackingJob.Status.PACKED); b.transition(PackingJob.Status.CANCELLED);
        assertThrows(StageException.class,()->b.transition(PackingJob.Status.PACKED));
        var s=new Shipment("c",Shipment.Status.NEW,"UNPAID","");
        assertThrows(StageException.class,()->s.confirm("TRACK")); s.pay(); s.pay();
        assertThrows(StageException.class,()->s.confirm("")); assertThrows(StageException.class,()->s.confirm(null));
        s.confirm("TRACK"); s.confirm("TRACK"); s.pay(); s.cancel(); s.cancel();
        assertThrows(StageException.class,s::pay);
        assertThrows(StageException.class,()->s.confirm("TRACK"));
    }
}
