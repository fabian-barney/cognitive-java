package media.barney.cognitive.cli;

import java.io.PrintStream;
import java.nio.file.Path;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        try {
            System.exit(run(args, Path.of(".").toAbsolutePath().normalize(), System.out, System.err));
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            System.exit(3);
        }
    }

    static int run(String[] args, Path projectRoot, PrintStream out, PrintStream err) throws Exception {
        return media.barney.cognitive.core.Main.run(args, projectRoot, out, err);
    }
}
