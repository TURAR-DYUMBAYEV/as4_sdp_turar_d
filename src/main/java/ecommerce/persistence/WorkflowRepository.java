package ecommerce.persistence;

import static ecommerce.persistence.Database.*;

import ecommerce.exception.*;
import java.util.*;

public final class WorkflowRepository {
  private final Database db;

  public WorkflowRepository(Database db) {
    this.db = db;
  }

  public Workflow create(String id, String product, int quantity, Workflow.Mode mode) {
    if (mode == null) throw new ValidationException("Demo mode is required.");
    db.transaction(
        c -> {
          if (!query(c, "SELECT id FROM workflows WHERE id=?", id).isEmpty())
            throw new ValidationException("Order ID exists. Inspect or resume it instead.");
          execute(
              c,
              "INSERT INTO workflows(id,product,quantity,mode,status) VALUES(?,?,?,?,?)",
              id,
              product,
              quantity,
              mode.name(),
              "NEW");
          execute(c, "INSERT INTO history(id,event) VALUES(?,?)", id, "NEW");
          return null;
        });
    return get(id);
  }

  private Workflow map(Map<String, String> r) {
    return new Workflow(
        r.get("id"),
        r.get("product"),
        Integer.parseInt(r.get("quantity")),
        Workflow.Mode.valueOf(r.get("mode")),
        Workflow.Status.valueOf(r.get("status")),
        r.get("error"));
  }

  public Workflow get(String id) {
    return db.transaction(
        c -> {
          var rows = query(c, "SELECT * FROM workflows WHERE id=?", id);
          if (rows.isEmpty()) throw new ValidationException("Unknown order: " + id);
          return map(rows.get(0));
        });
  }

  public List<Workflow> all() {
    return db.transaction(
        c -> query(c, "SELECT * FROM workflows ORDER BY id").stream().map(this::map).toList());
  }

  public void transition(String id, Workflow.Status next) {
    db.transaction(
        c -> {
          var rows = query(c, "SELECT * FROM workflows WHERE id=?", id);
          if (rows.isEmpty()) throw new ValidationException("Unknown order: " + id);
          map(rows.get(0)).validateTransition(next);
          execute(c, "UPDATE workflows SET status=? WHERE id=?", next.name(), id);
          execute(c, "INSERT INTO history(id,event) VALUES(?,?)", id, next.name());
          return null;
        });
  }

  public void event(String id, String event) {
    db.transaction(c -> execute(c, "INSERT INTO history(id,event) VALUES(?,?)", id, event));
  }

  public void error(String id, String error) {
    db.transaction(c -> execute(c, "UPDATE workflows SET error=? WHERE id=?", error, id));
  }

  public List<Map<String, String>> history(String id) {
    get(id);
    return db.transaction(
        c -> query(c, "SELECT seq,event,created FROM history WHERE id=? ORDER BY seq", id));
  }

  public void reset() {
    db.transaction(
        c -> {
          for (String table :
              List.of("history", "workflows", "shipments", "jobs", "reservations", "inventory"))
            execute(c, "DELETE FROM " + table);
          execute(c, "INSERT INTO inventory VALUES('BOOK',100),('LAPTOP',20),('MOUSE',50)");
          return null;
        });
  }
}
