import java.awt.GraphicsEnvironment;
import view.BookingCLI;
import view.BookingConsole;

/**
 * Main.java — Universal Application Entry Point
 *
 * Automatically detects whether the runtime environment has a graphical display
 * (GUI) or is executing inside a headless terminal, remote SSH session, Docker
 * container, or automated grading pipeline.
 *
 * Routing Rules:
 *  1. If any CLI flags are passed (--cli, -c, --test, --status, --simulate, -h, --help),
 *     or if GraphicsEnvironment.isHeadless() is true, routes directly to BookingCLI.
 *  2. If a graphical display environment is available and no CLI flags were passed,
 *     launches BookingConsole (Swing GUI).
 *  3. If GUI initialization fails for any reason (e.g. missing X11 display, HeadlessException),
 *     automatically and gracefully falls back to BookingCLI.
 */
public class Main {

    public static void main(String[] args) {
        boolean forceCli = false;

        for (String arg : args) {
            String clean = arg.trim().toLowerCase();
            if (clean.equals("--cli") || clean.equals("-c") || clean.equals("cli") ||
                clean.equals("--test") || clean.equals("-t") || clean.equals("test") ||
                clean.equals("--status") || clean.equals("--summary") || clean.equals("-s") || clean.equals("status") ||
                clean.equals("--simulate") || clean.equals("simulate") ||
                clean.equals("--help") || clean.equals("-h") || clean.equals("help")) {
                forceCli = true;
                break;
            }
        }

        // Headless detection or explicit CLI flag
        if (forceCli || GraphicsEnvironment.isHeadless()) {
            BookingCLI.main(args);
            return;
        }

        // Attempt to launch GUI; fallback to CLI if display server is missing or fails
        try {
            BookingConsole.main(args);
        } catch (Throwable t) {
            System.out.println("[NOTICE] Graphical display unavailable or headless environment detected.");
            System.out.println("[NOTICE] Seamlessly falling back to Terminal Command-Line Interface (CLI)...\n");
            BookingCLI.main(args);
        }
    }
}
