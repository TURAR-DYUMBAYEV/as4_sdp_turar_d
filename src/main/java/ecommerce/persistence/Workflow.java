package ecommerce.persistence;

import ecommerce.exception.StageException;

public record Workflow(
    String id, String product, int quantity, Mode mode, Status status, String error) {
  public enum Mode {
    NORMAL,
    FAIL_B,
    FAIL_C,
    FAIL_SHIPPING,
    PAUSE_AFTER_A,
    PAUSE_AFTER_B
  }

  public enum Status {
    NEW,
    PENDING_B,
    PENDING_C,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    FAILED
  }

  public void validateTransition(Status next) {
    if (status == next) return;
    boolean legal =
        switch (status) {
          case NEW ->
              next == Status.PENDING_B || next == Status.FAILED || next == Status.COMPENSATING;
          case PENDING_B -> next == Status.PENDING_C || next == Status.COMPENSATING;
          case PENDING_C -> next == Status.COMPLETED || next == Status.COMPENSATING;
          case COMPLETED, FAILED -> next == Status.COMPENSATING;
          case COMPENSATING -> next == Status.COMPENSATED;
          case COMPENSATED -> false;
        };
    if (!legal) throw new StageException("Illegal workflow transition: " + status + " -> " + next);
  }
}
