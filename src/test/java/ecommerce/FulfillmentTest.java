package ecommerce;

import static ecommerce.persistence.Workflow.Mode.*;
import static ecommerce.persistence.Workflow.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import ecommerce.exception.*;
import ecommerce.persistence.*;
import ecommerce.subsystema.*;
import ecommerce.subsystemb.*;
import ecommerce.subsystemc.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class FulfillmentTest {
  @TempDir Path dir;
  Path file;
  Application app;
  Database db;

  @BeforeEach
  void setup() {
    file = dir.resolve("orders.db");
    db = new Database(file);
    app = new Application(file);
  }

  void givenProductWithStock(int stock) {
    db.transaction(
        c -> Database.execute(c, "UPDATE inventory SET stock=? WHERE product='BOOK'", stock));
  }

  Workflow whenOrderIsFulfilled(int quantity) {
    return app.fulfillment().fulfillOrder("order", "BOOK", quantity);
  }

  void thenInventoryShouldBe(int quantity) {
    assertEquals(String.valueOf(quantity), app.admin().inventory().get(0).get("stock"));
  }

  List<String> events(String id) {
    return app.admin().history(id).stream().map(r -> r.get("event")).toList();
  }

  @Test
  void successfulChainPersistsEveryStage() {
    givenProductWithStock(10);
    assertEquals(COMPLETED, whenOrderIsFulfilled(3).status());
    thenInventoryShouldBe(7);
    assertEquals(List.of("NEW", "PENDING_B", "PENDING_C", "COMPLETED"), events("order"));
    assertTrue(
        app.admin().inspect("order").contains("A=RESERVED, B=PACKED, C=CONFIRMED payment=CHARGED"));
    assertEquals(1, app.admin().orders().size());
    assertEquals(COMPLETED, app.admin().resume("order").status());
    assertThrows(ValidationException.class, () -> whenOrderIsFulfilled(3));
    assertThrows(ValidationException.class, () -> app.admin().inspect("missing"));
  }

  @Test
  void invalidInputNeverReservesStock() {
    assertThrows(ValidationException.class, () -> whenOrderIsFulfilled(0));
    assertThrows(ValidationException.class, () -> app.fulfillment().fulfillOrder("", "BOOK", 1));
    assertThrows(
        ValidationException.class, () -> app.fulfillment().fulfillOrder("o", "BOOK", 1, null));
    assertTrue(app.admin().orders().isEmpty());
    thenInventoryShouldBe(100);
  }

  @Test
  void insufficientStockAndUnknownProductsBecomeFailed() {
    assertEquals(FAILED, whenOrderIsFulfilled(101).status());
    assertEquals(FAILED, app.admin().resume("order").status());
    assertEquals(FAILED, app.fulfillment().fulfillOrder("unknown", "NOT-SOLD", 1).status());
    thenInventoryShouldBe(100);
    assertTrue(app.admin().inspect("order").contains("Insufficient stock"));
    assertEquals(COMPENSATED, app.admin().compensate("order").status());
  }

  @Test
  void bFailureAfterACommitOnlyReleasesA() {
    assertEquals(COMPENSATED, app.fulfillment().fulfillOrder("b", "BOOK", 3, FAIL_B).status());
    assertTrue(
        events("b").stream()
            .anyMatch(e -> e.contains("FAILURE before compensation: A=RESERVED, B=ABSENT")));
    assertFalse(events("b").contains("B_CANCELLED"));
    assertTrue(events("b").contains("A_RELEASED"));
    assertEquals(
        InventoryReservation.Status.RELEASED, new InventoryRepository(db).find("b").status());
    assertNull(new WarehouseRepository(db).find("b"));
    thenInventoryShouldBe(100);
  }

  @Test
  void cFailureAfterABCommitCancelsBThenReleasesA() {
    assertEquals(COMPENSATED, app.fulfillment().fulfillOrder("c", "BOOK", 3, FAIL_C).status());
    var history = events("c");
    assertTrue(history.stream().anyMatch(e -> e.contains("A=RESERVED, B=PACKED, C=NEW")));
    assertTrue(history.indexOf("B_CANCELLED") < history.indexOf("A_RELEASED"));
    assertEquals(PackingJob.Status.CANCELLED, new WarehouseRepository(db).find("c").status());
    assertEquals(
        InventoryReservation.Status.RELEASED, new InventoryRepository(db).find("c").status());
    assertEquals("UNPAID", new ShipmentRepository(db).find("c").payment());
    thenInventoryShouldBe(100);
  }

  @Test
  void shippingFailureRefundsChargeAndRestoresInventory() {
    assertEquals(
        COMPENSATED, app.fulfillment().fulfillOrder("s", "BOOK", 3, FAIL_SHIPPING).status());
    var shipment = new ShipmentRepository(db).find("s");
    assertEquals("REFUNDED", shipment.payment());
    assertEquals(Shipment.Status.CANCELLED, shipment.status());
    thenInventoryShouldBe(100);
  }

  @Test
  void paymentGatewayTimeoutAfterWarehouseCommitCompensatesWithoutCharging() {
    assertEquals(
        COMPENSATED,
        app.fulfillment().fulfillOrder("timeout", "BOOK", 3, FAIL_PAYMENT_TIMEOUT).status());
    var history = events("timeout");
    assertTrue(history.stream().anyMatch(e -> e.contains("Payment gateway timed out")));
    assertTrue(history.indexOf("B_CANCELLED") < history.indexOf("A_RELEASED"));
    assertEquals("UNPAID", new ShipmentRepository(db).find("timeout").payment());
    assertEquals(Shipment.Status.CANCELLED, new ShipmentRepository(db).find("timeout").status());
    assertEquals(PackingJob.Status.CANCELLED, new WarehouseRepository(db).find("timeout").status());
    thenInventoryShouldBe(100);
  }

  @Test
  void pauseAfterAAndRestartResumesFromDisk() {
    assertEquals(PENDING_B, app.fulfillment().fulfillOrder("a", "BOOK", 3, PAUSE_AFTER_A).status());
    thenInventoryShouldBe(97);
    app = new Application(file);
    assertTrue(app.admin().inspect("a").contains("B=ABSENT"));
    assertEquals(COMPLETED, app.admin().resume("a").status());
    thenInventoryShouldBe(97);
    app = new Application(file);
    assertTrue(app.admin().inspect("a").contains("C=CONFIRMED"));
  }

  @Test
  void pauseAfterBAndRestartCanCompensateTwice() {
    assertEquals(PENDING_C, app.fulfillment().fulfillOrder("b", "BOOK", 3, PAUSE_AFTER_B).status());
    app = new Application(file);
    assertEquals(COMPENSATED, app.admin().compensate("b").status());
    int size = events("b").size();
    assertEquals(COMPENSATED, app.admin().compensate("b").status());
    assertEquals(COMPENSATED, app.admin().resume("b").status());
    assertEquals(size, events("b").size());
    thenInventoryShouldBe(100);
  }

  @Test
  void pauseAfterBResumesAndCompletedOrderCanBeCancelledInSimulator() {
    app.fulfillment().fulfillOrder("b", "BOOK", 3, PAUSE_AFTER_B);
    assertEquals(COMPLETED, new Application(file).admin().resume("b").status());
    assertEquals(COMPENSATED, app.admin().compensate("b").status());
    assertEquals("REFUNDED", new ShipmentRepository(db).find("b").payment());
    thenInventoryShouldBe(100);
  }

  @Test
  void restartBetweenACommitAndWorkflowUpdateDoesNotReserveTwice() {
    new WorkflowRepository(db).create("gap", "BOOK", 3, NORMAL);
    new InventoryManager(new InventoryRepository(db), new OrderValidator())
        .reserve("gap", "BOOK", 3);
    assertEquals(COMPLETED, new Application(file).admin().resume("gap").status());
    thenInventoryShouldBe(97);
  }

  @Test
  void restartBetweenBCommitAndWorkflowUpdateDoesNotPackTwice() {
    app.fulfillment().fulfillOrder("gap", "BOOK", 3, PAUSE_AFTER_A);
    new PackingStation(new WarehouseRepository(db), new WarehousePicker())
        .pack("gap", "BOOK", 3, false);
    assertEquals(COMPLETED, new Application(file).admin().resume("gap").status());
    thenInventoryShouldBe(97);
  }

  @Test
  void restartAfterPaymentCommitResumesShipping() {
    app.fulfillment().fulfillOrder("gap", "BOOK", 3, PAUSE_AFTER_B);
    var s = new Shipment("gap", Shipment.Status.NEW, "UNPAID", "");
    s.pay();
    new ShipmentRepository(db).save(s);
    assertEquals(COMPLETED, new Application(file).admin().resume("gap").status());
    assertEquals("CHARGED", new ShipmentRepository(db).find("gap").payment());
    thenInventoryShouldBe(97);
  }

  @Test
  void restartAfterCCommitBeforeWorkflowUpdateIsIdempotent() {
    app.fulfillment().fulfillOrder("gap", "BOOK", 3, PAUSE_AFTER_B);
    new PaymentGateway(new ShipmentRepository(db), new ShippingCarrier())
        .finalizeOrder("gap", false, false);
    assertEquals(COMPLETED, new Application(file).admin().resume("gap").status());
    thenInventoryShouldBe(97);
  }

  @Test
  void restartInCompensationReplaysAlreadyCancelledB() {
    app.fulfillment().fulfillOrder("gap", "BOOK", 3, PAUSE_AFTER_B);
    new WorkflowRepository(db).transition("gap", COMPENSATING);
    new WarehouseRepository(db).cancel("gap");
    assertEquals(COMPENSATED, new Application(file).admin().resume("gap").status());
    thenInventoryShouldBe(100);
  }

  @Test
  void restartAfterReleaseDoesNotRestoreStockTwice() {
    app.fulfillment().fulfillOrder("gap", "BOOK", 3, PAUSE_AFTER_A);
    new WorkflowRepository(db).transition("gap", COMPENSATING);
    new InventoryRepository(db).release("gap");
    assertEquals(COMPENSATED, new Application(file).admin().resume("gap").status());
    thenInventoryShouldBe(100);
  }

  @Test
  void resetRequiresExplicitConfirmation() {
    whenOrderIsFulfilled(3);
    app.admin().reset(false);
    assertEquals(1, app.admin().orders().size());
    app.admin().reset(true);
    assertTrue(app.admin().orders().isEmpty());
    thenInventoryShouldBe(100);
    assertNull(new InventoryRepository(db).find("order"));
    assertNull(new WarehouseRepository(db).find("order"));
    assertNull(new ShipmentRepository(db).find("order"));
  }

  @Test
  void illegalWorkflowTransitionsAreRejected() {
    var repo = new WorkflowRepository(db);
    repo.create("x", "BOOK", 1, NORMAL);
    assertThrows(StageException.class, () -> repo.transition("x", COMPLETED));
    repo.transition("x", NEW);
    repo.transition("x", PENDING_B);
    assertThrows(StageException.class, () -> repo.transition("x", NEW));
    repo.transition("x", PENDING_C);
    assertThrows(StageException.class, () -> repo.transition("x", NEW));
    repo.transition("x", COMPLETED);
    repo.transition("x", COMPENSATING);
    repo.transition("x", COMPENSATED);
    assertThrows(StageException.class, () -> repo.transition("x", PENDING_B));
    assertThrows(ValidationException.class, () -> repo.transition("missing", NEW));
  }

  @Test
  void failedCompensationWriteRemainsRecoverableWithoutReleasingEarly() {
    app.fulfillment().fulfillOrder("retry", "BOOK", 3, PAUSE_AFTER_B);
    db.transaction(
        c ->
            Database.execute(
                c,
                "CREATE TRIGGER fail_cancel BEFORE UPDATE ON jobs BEGIN SELECT"
                    + " RAISE(ABORT,'warehouse disk write failed'); END"));
    assertThrows(PersistenceException.class, () -> app.admin().compensate("retry"));
    assertEquals(COMPENSATING, new WorkflowRepository(db).get("retry").status());
    assertEquals(PackingJob.Status.PACKED, new WarehouseRepository(db).find("retry").status());
    thenInventoryShouldBe(97);
    db.transaction(c -> Database.execute(c, "DROP TRIGGER fail_cancel"));
    assertEquals(COMPENSATED, new Application(file).admin().resume("retry").status());
    thenInventoryShouldBe(100);
  }

  @Test
  void failedRefundWriteLeavesDurableChargeForRecovery() {
    app.fulfillment().fulfillOrder("refund", "BOOK", 3, PAUSE_AFTER_B);
    db.transaction(
        c ->
            Database.execute(
                c,
                "CREATE TRIGGER fail_refund BEFORE UPDATE ON shipments WHEN NEW.payment='REFUNDED'"
                    + " BEGIN SELECT RAISE(ABORT,'refund store unavailable'); END"));
    var gateway = new PaymentGateway(new ShipmentRepository(db), new ShippingCarrier());
    assertThrows(PersistenceException.class, () -> gateway.finalizeOrder("refund", false, true));
    assertEquals("CHARGED", new ShipmentRepository(db).find("refund").payment());
    assertEquals(Shipment.Status.PAID, new ShipmentRepository(db).find("refund").status());
    assertThrows(PersistenceException.class, () -> app.admin().compensate("refund"));
    thenInventoryShouldBe(97);
    db.transaction(c -> Database.execute(c, "DROP TRIGGER fail_refund"));
    assertEquals(COMPENSATED, new Application(file).admin().resume("refund").status());
    assertEquals("REFUNDED", new ShipmentRepository(db).find("refund").payment());
    thenInventoryShouldBe(100);
  }
}
