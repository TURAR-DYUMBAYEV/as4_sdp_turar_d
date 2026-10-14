package ecommerce.cli;
import ecommerce.facade.*;
import ecommerce.persistence.Workflow;
import ecommerce.exception.FulfillmentException;
import java.util.Scanner;
import java.io.PrintStream;

public final class ConsoleApp {
    private final OrderFulfillmentFacade fulfillment;
    private final FulfillmentAdminFacade admin;
    private final Scanner input;
    private final PrintStream out;
    public ConsoleApp(OrderFulfillmentFacade fulfillment,FulfillmentAdminFacade admin,Scanner input,PrintStream out) {
        this.fulfillment=fulfillment; this.admin=admin; this.input=input; this.out=out;
    }
    public void run() {
        while(true) {
            out.println("\n===== E-COMMERCE FULFILLMENT =====\n1. Create and fulfill order\n2. Show all orders\n3. Inspect order and history\n4. Fail B: station unavailable\n5. Fail C: payment declined\n6. Resume interrupted workflow\n7. Manually compensate order\n8. Show inventory\n9. Show independent subsystem test instructions\n10. Reset demo data\n11. Pause after A\n12. Pause after B\n13. Fail shipping after payment\n0. Exit");
            if(!input.hasNextLine()) return;
            String choice=input.nextLine().trim();
            try {
                switch(choice) {
                    case "0" -> { return; }
                    case "1" -> create(Workflow.Mode.NORMAL);
                    case "2" -> admin.orders().forEach(out::println);
                    case "3" -> details(ask("Order ID: "));
                    case "4" -> create(Workflow.Mode.FAIL_B);
                    case "5" -> create(Workflow.Mode.FAIL_C);
                    case "6" -> { String id=ask("Order ID: "); out.println(admin.resume(id)); details(id); }
                    case "7" -> { String id=ask("Order ID: "); out.println(admin.compensate(id)); details(id); }
                    case "8" -> admin.inventory().forEach(out::println);
                    case "9" -> out.println("Run: mvn -Dtest=SubsystemTest test. Three standalone tests call A, B and C without either facade, each with its own temporary SQLite database.");
                    case "10" -> { boolean yes=ask("Delete ALL demo records? Type RESET: ").equals("RESET"); admin.reset(yes); out.println(yes?"Demo data reset.":"Reset cancelled."); }
                    case "11" -> create(Workflow.Mode.PAUSE_AFTER_A);
                    case "12" -> create(Workflow.Mode.PAUSE_AFTER_B);
                    case "13" -> create(Workflow.Mode.FAIL_SHIPPING);
                    default -> out.println("Choose a menu number from 0 to 13.");
                }
            } catch(NumberFormatException e) { out.println("Quantity must be a whole number.");
            } catch(FulfillmentException e) { out.println("Error: " + e.getMessage() + " Inspect persisted state before retrying.");
            } catch(java.util.NoSuchElementException e) { return; }
        }
    }
    private String ask(String prompt) { out.print(prompt); return input.nextLine().trim(); }
    private void create(Workflow.Mode mode) {
        String id=ask("Unique order ID: ");
        String product=ask("Product (BOOK, LAPTOP, MOUSE): ").toUpperCase(java.util.Locale.ROOT);
        int quantity=Integer.parseInt(ask("Quantity: "));
        out.println(fulfillment.fulfillOrder(id,product,quantity,mode));
        details(id);
    }
    private void details(String id) {
        out.println(admin.inspect(id));
        admin.history(id).forEach(out::println);
        out.println("Current inventory: " + admin.inventory());
    }
}
