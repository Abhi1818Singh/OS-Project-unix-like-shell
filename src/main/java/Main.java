import java.io.File;
import java.util.Scanner;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        Set<String> builtins = Set.of("echo", "exit", "type");

        while (true) {
            System.out.print("$ ");

            String input = scanner.nextLine();

            if (input.trim().isEmpty()) {
                continue;
            }

            String[] parts = input.split(" ");
            String command = parts[0];

            // exit builtin
            if (command.equals("exit")) {
                System.exit(0);
            }

            // echo builtin
            if (command.equals("echo")) {
                if (input.length() > 5) {
                    System.out.println(input.substring(5));
                } else {
                    System.out.println();
                }
                continue;
            }

            // type builtin
            if (command.equals("type")) {
                if (parts.length < 2) {
                    continue;
                }

                String cmdToCheck = parts[1];

                // Check builtins first
                if (builtins.contains(cmdToCheck)) {
                    System.out.println(cmdToCheck + " is a shell builtin");
                    continue;
                }

                // Search PATH
                String pathEnv = System.getenv("PATH");

                if (pathEnv != null) {
                    String[] directories = pathEnv.split(File.pathSeparator);

                    boolean found = false;

                    for (String dir : directories) {
                        File file = new File(dir, cmdToCheck);

                        if (file.exists() && file.isFile() && file.canExecute()) {
                            System.out.println(cmdToCheck + " is " + file.getAbsolutePath());
                            found = true;
                            break;
                        }
                    }

                    if (found) {
                        continue;
                    }
                }

                System.out.println(cmdToCheck + ": not found");
                continue;
            }

            // Unknown command
            System.out.println(command + ": command not found");
        
        }
    }
}
