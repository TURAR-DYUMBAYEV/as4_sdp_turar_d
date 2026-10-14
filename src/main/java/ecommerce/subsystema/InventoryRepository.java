package ecommerce.subsystema;

import static ecommerce.persistence.Database.*;

import ecommerce.exception.*;
import ecommerce.persistence.Database;
import java.sql.*;
import java.util.*;

public final class InventoryRepository {
  private final Database db;

  public InventoryRepository(Database db) {
    this.db = db;
  }

  public InventoryReservation find(String id) {
    return db.transaction(c -> find(c, id));
  }

  private InventoryReservation find(Connection c, String id) throws SQLException {
    var rows = query(c, "SELECT * FROM reservations WHERE id=?", id);
    if (rows.isEmpty()) return null;
    var r = rows.get(0);
    return new InventoryReservation(
        id,
        r.get("product"),
        Integer.parseInt(r.get("quantity")),
        InventoryReservation.Status.valueOf(r.get("status")));
  }

  public InventoryReservation reserve(String id, String product, int quantity) {
    return db.transaction(
        c -> {
          var existing = find(c, id);
          if (existing != null) {
            if (!existing.product().equals(product)
                || existing.quantity() != quantity
                || existing.status() != InventoryReservation.Status.RESERVED)
              throw new StageException(
                  "Reservation ID already used with different input or released state.");
            return existing;
          }
          if (query(c, "SELECT product FROM inventory WHERE product=?", product).isEmpty())
            throw new ValidationException("Unknown product: " + product);
          if (execute(
                  c,
                  "UPDATE inventory SET stock=stock-? WHERE product=? AND stock>=?",
                  quantity,
                  product,
                  quantity)
              != 1) throw new ValidationException("Insufficient stock for " + product);
          var reservation =
              new InventoryReservation(id, product, quantity, InventoryReservation.Status.NEW);
          reservation.transition(InventoryReservation.Status.RESERVED);
          execute(
              c,
              "INSERT INTO reservations VALUES(?,?,?,?)",
              id,
              product,
              quantity,
              reservation.status().name());
          return reservation;
        });
  }

  public void release(String id) {
    db.transaction(
        c -> {
          var r = find(c, id);
          if (r != null && r.status() != InventoryReservation.Status.RELEASED) {
            r.transition(InventoryReservation.Status.RELEASED);
            execute(c, "UPDATE reservations SET status=? WHERE id=?", r.status().name(), id);
            execute(
                c, "UPDATE inventory SET stock=stock+? WHERE product=?", r.quantity(), r.product());
          }
          return null;
        });
  }

  public List<Map<String, String>> inventory() {
    return db.transaction(c -> query(c, "SELECT * FROM inventory ORDER BY product"));
  }
}
