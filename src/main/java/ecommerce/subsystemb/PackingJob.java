package ecommerce.subsystemb;

import ecommerce.exception.StageException;

public final class PackingJob {
  public enum Status {
    NEW,
    PACKED,
    CANCELLED
  }

  private final String id;
  private final String product;
  private final int quantity;
  private Status status;

  public PackingJob(String id, String product, int quantity, Status status) {
    this.id = id;
    this.product = product;
    this.quantity = quantity;
    this.status = status;
  }

  public String id() {
    return id;
  }

  public String product() {
    return product;
  }

  public int quantity() {
    return quantity;
  }

  public Status status() {
    return status;
  }

  public void transition(Status next) {
    if (next == status) return;
    if ((status == Status.NEW && next == Status.PACKED)
        || (status == Status.PACKED && next == Status.CANCELLED)) {
      status = next;
    } else throw new StageException("Illegal PackingJob transition: " + status + " -> " + next);
  }
}
