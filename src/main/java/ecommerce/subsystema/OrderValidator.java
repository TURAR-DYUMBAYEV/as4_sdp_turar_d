package ecommerce.subsystema;

import ecommerce.exception.ValidationException;

public final class OrderValidator {
  public void validate(String id, String product, int quantity) {
    if (id == null || id.isBlank() || product == null || product.isBlank())
      throw new ValidationException("Order ID and product ID are required.");
    if (quantity <= 0) throw new ValidationException("Quantity must be positive.");
  }
}
