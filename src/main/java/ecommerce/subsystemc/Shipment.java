package ecommerce.subsystemc;

import ecommerce.exception.StageException;

public final class Shipment {
  public enum Status {
    NEW,
    PAID,
    CONFIRMED,
    CANCELLED
  }

  private final String id;
  private Status status;
  private String payment;
  private String tracking;

  public Shipment(String id, Status status, String payment, String tracking) {
    this.id = id;
    this.status = status;
    this.payment = payment;
    this.tracking = tracking;
  }

  public String id() {
    return id;
  }

  public Status status() {
    return status;
  }

  public String payment() {
    return payment;
  }

  public String tracking() {
    return tracking;
  }

  public void pay() {
    if (status == Status.PAID || status == Status.CONFIRMED) return;
    require(Status.NEW);
    status = Status.PAID;
    payment = "CHARGED";
  }

  public void confirm(String tracking) {
    if (status == Status.CONFIRMED) return;
    require(Status.PAID);
    if (tracking == null || tracking.isBlank())
      throw new StageException("Tracking number is required.");
    this.tracking = tracking;
    status = Status.CONFIRMED;
  }

  public void cancel() {
    if (status == Status.CANCELLED) return;
    if (payment.equals("CHARGED")) payment = "REFUNDED";
    status = Status.CANCELLED;
    tracking = "";
  }

  private void require(Status expected) {
    if (status != expected) throw new StageException("Illegal shipment transition from " + status);
  }
}
