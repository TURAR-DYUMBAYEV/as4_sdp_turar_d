package ecommerce.subsystemc;

import static ecommerce.persistence.Database.*;

import ecommerce.persistence.Database;

public final class ShipmentRepository {
  private final Database db;

  public ShipmentRepository(Database db) {
    this.db = db;
  }

  public Shipment find(String id) {
    return db.transaction(
        c -> {
          var rows = query(c, "SELECT * FROM shipments WHERE id=?", id);
          if (rows.isEmpty()) return null;
          var r = rows.get(0);
          return new Shipment(
              id, Shipment.Status.valueOf(r.get("status")), r.get("payment"), r.get("tracking"));
        });
  }

  public void save(Shipment s) {
    db.transaction(
        c ->
            execute(
                c,
                "INSERT INTO shipments VALUES(?,?,?,?) ON CONFLICT(id) DO UPDATE SET"
                    + " status=excluded.status,payment=excluded.payment,tracking=excluded.tracking",
                s.id(),
                s.status().name(),
                s.payment(),
                s.tracking()));
  }
}
