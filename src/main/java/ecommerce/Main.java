package ecommerce;

import ecommerce.cli.ConsoleApp;
import java.nio.file.Path;
import java.util.Scanner;

public final class Main {
  public static void main(String[] args) {
    Path path = Path.of(args.length == 0 ? "fulfillment.db" : args[0]).toAbsolutePath();
    System.out.println("SQLite database: " + path);
    try {
      var app = new Application(path);
      new ConsoleApp(app.fulfillment(), app.admin(), new Scanner(System.in), System.out).run();
    } catch (ecommerce.exception.FulfillmentException e) {
      System.err.println("Cannot open application: " + e.getMessage());
      System.exit(1);
    }
  }
}
