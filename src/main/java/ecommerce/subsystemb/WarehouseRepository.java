package ecommerce.subsystemb;

import static ecommerce.persistence.Database.*;

import ecommerce.exception.StageException;
import ecommerce.persistence.Database;
import java.sql.*;

public final class WarehouseRepository {
  private final Database db;

  public WarehouseRepository(Database db) {
    this.db = db;
  }

  public PackingJob find(String id) {
    return db.transaction(c -> find(c, id));
  }

  private PackingJob find(Connection c, String id) throws SQLException {
    var rows = query(c, "SELECT * FROM jobs WHERE id=?", id);
    if (rows.isEmpty()) return null;
    var r = rows.get(0);
    return new PackingJob(
        id,
        r.get("product"),
        Integer.parseInt(r.get("quantity")),
        PackingJob.Status.valueOf(r.get("status")));
  }

  public PackingJob pack(String id, String product, int quantity) {
    return db.transaction(
        c -> {
          var job = find(c, id);
          if (job != null) {
            if (!job.product().equals(product)
                || job.quantity() != quantity
                || job.status() != PackingJob.Status.PACKED)
              throw new StageException(
                  "Packing ID already used with different input or cancelled state.");
            return job;
          }
          job = new PackingJob(id, product, quantity, PackingJob.Status.NEW);
          job.transition(PackingJob.Status.PACKED);
          execute(
              c, "INSERT INTO jobs VALUES(?,?,?,?)", id, product, quantity, job.status().name());
          return job;
        });
  }

  public void cancel(String id) {
    db.transaction(
        c -> {
          var job = find(c, id);
          if (job != null && job.status() != PackingJob.Status.CANCELLED) {
            job.transition(PackingJob.Status.CANCELLED);
            execute(c, "UPDATE jobs SET status=? WHERE id=?", job.status().name(), id);
          }
          return null;
        });
  }
}
