package ecommerce;
import ecommerce.cli.ConsoleApp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.io.*;
import java.util.Scanner;
import static org.junit.jupiter.api.Assertions.*;
class ConsoleTest {
    @TempDir Path dir;
    @Test void interactiveInputExercisesDemoAndRestart() {
        Path db=dir.resolve("cli.db"); var app=new Application(db); var bytes=new ByteArrayOutputStream();
        String script="bad\n1\no1\nBOOK\n2\n4\no2\nBOOK\n3\n5\no3\nBOOK\n4\n13\no4\nBOOK\n1\n11\no5\nBOOK\n2\n2\n3\no1\n8\n9\n0\n";
        new ConsoleApp(app.fulfillment(),app.admin(),new Scanner(script),new PrintStream(bytes)).run();
        String out=bytes.toString(); assertTrue(out.contains("Choose a menu number")); assertTrue(out.contains("B_CANCELLED"));
        assertTrue(out.contains("A_RELEASED")); assertTrue(out.contains("REFUNDED"));
        app=new Application(db); bytes.reset();
        new ConsoleApp(app.fulfillment(),app.admin(),new Scanner("6\no5\n7\no5\n10\nNO\n3\nmissing\n1\nx\nBOOK\nwrong\n0\n"),new PrintStream(bytes)).run();
        out=bytes.toString(); assertTrue(out.contains("COMPLETED")); assertTrue(out.contains("COMPENSATED"));
        assertTrue(out.contains("Reset cancelled")); assertTrue(out.contains("Unknown order")); assertTrue(out.contains("whole number"));
    }
    @Test void eofAndResetAreSafe() {
        var app=new Application(dir.resolve("db"));
        var out=new PrintStream(new ByteArrayOutputStream());
        for(String script:new String[]{"","1\n","12\nx\nBOOK\n1\n10\nRESET\n0\n"})
            new ConsoleApp(app.fulfillment(),app.admin(),new Scanner(script),out).run();
        assertTrue(app.admin().orders().isEmpty());
    }
}
