package ecommerce.subsystema;

public final class InventoryManager {
  private final InventoryRepository repository;
  private final OrderValidator validator;

  public InventoryManager(InventoryRepository repository, OrderValidator validator) {
    this.repository = repository;
    this.validator = validator;
  }

  public InventoryReservation reserve(String id, String product, int quantity) {
    validator.validate(id, product, quantity);
    return repository.reserve(id, product, quantity);
  }

  public void release(String id) {
    repository.release(id);
  }
}
