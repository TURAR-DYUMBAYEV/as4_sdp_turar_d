package ecommerce.facade;

import static ecommerce.persistence.Workflow.Mode.*;
import static ecommerce.persistence.Workflow.Status.*;

import ecommerce.exception.*;
import ecommerce.persistence.*;
import ecommerce.subsystema.*;
import ecommerce.subsystemb.*;
import ecommerce.subsystemc.*;

/** The only layer that knows all three subsystems and their execution order. */
public final class OrderFulfillmentFacade {
  private final WorkflowRepository workflows;
  private final InventoryManager inventory;
  private final InventoryRepository reservations;
  private final PackingStation packing;
  private final WarehouseRepository jobs;
  private final PaymentGateway payments;
  private final ShipmentRepository shipments;

  public OrderFulfillmentFacade(
      WorkflowRepository workflows,
      InventoryManager inventory,
      InventoryRepository reservations,
      PackingStation packing,
      WarehouseRepository jobs,
      PaymentGateway payments,
      ShipmentRepository shipments) {
    this.workflows = workflows;
    this.inventory = inventory;
    this.reservations = reservations;
    this.packing = packing;
    this.jobs = jobs;
    this.payments = payments;
    this.shipments = shipments;
  }

  public Workflow fulfillOrder(String id, String product, int quantity) {
    return fulfillOrder(id, product, quantity, NORMAL);
  }

  public Workflow fulfillOrder(String id, String product, int quantity, Workflow.Mode mode) {
    new OrderValidator().validate(id, product, quantity);
    workflows.create(id, product, quantity, mode);
    return execute(id, true);
  }

  public Workflow resume(String id) {
    return execute(id, false);
  }

  private Workflow execute(String id, boolean allowPause) {
    var w = workflows.get(id);
    if (w.status() == COMPENSATING) return compensate(id);
    if (w.status() == COMPENSATED || w.status() == COMPLETED || w.status() == FAILED) return w;
    try {
      if (w.status() == NEW) {
        inventory.reserve(id, w.product(), w.quantity());
        workflows.transition(id, PENDING_B);
        if (allowPause && w.mode() == PAUSE_AFTER_A) return workflows.get(id);
      }
      w = workflows.get(id);
      if (w.status() == PENDING_B) {
        packing.pack(id, w.product(), w.quantity(), w.mode() == FAIL_B);
        workflows.transition(id, PENDING_C);
        if (allowPause && w.mode() == PAUSE_AFTER_B) return workflows.get(id);
      }
      w = workflows.get(id);
      if (w.status() == PENDING_C) {
        payments.finalizeOrder(id, w.mode() == FAIL_C, w.mode() == FAIL_SHIPPING);
        workflows.transition(id, COMPLETED);
      }
      return workflows.get(id);
    } catch (ValidationException | StageException e) {
      workflows.error(id, e.getMessage());
      workflows.event(id, "FAILURE before compensation: " + snapshot(id) + "; " + e.getMessage());
      if (reservations.find(id) == null && jobs.find(id) == null && shipments.find(id) == null) {
        workflows.transition(id, FAILED);
        return workflows.get(id);
      }
      return compensate(id);
    }
    // Database failures propagate. Durable progress remains available for a retry.
  }

  public Workflow compensate(String id) {
    var w = workflows.get(id);
    if (w.status() == COMPENSATED) return w;
    workflows.transition(id, COMPENSATING);
    workflows.event(id, "COMPENSATION starting: " + snapshot(id));
    if (shipments.find(id) != null) {
      payments.cancel(id);
      workflows.event(id, "C_CANCELLED");
    }
    if (jobs.find(id) != null) {
      packing.cancel(id);
      workflows.event(id, "B_CANCELLED");
    }
    if (reservations.find(id) != null) {
      inventory.release(id);
      workflows.event(id, "A_RELEASED");
    }
    workflows.transition(id, COMPENSATED);
    return workflows.get(id);
  }

  public String snapshot(String id) {
    var a = reservations.find(id);
    var b = jobs.find(id);
    var c = shipments.find(id);
    return "A="
        + (a == null ? "ABSENT" : a.status())
        + ", B="
        + (b == null ? "ABSENT" : b.status())
        + ", C="
        + (c == null
            ? "ABSENT"
            : c.status() + " payment=" + c.payment() + " tracking=" + c.tracking());
  }
}
