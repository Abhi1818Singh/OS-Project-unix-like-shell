import java.io.File;
import java.util.Scanner;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        Set<String> builtins = Set.of("echo", "exit", "type", "pwd");

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

                if (builtins.contains(cmdToCheck)) {
                    System.out.println(cmdToCheck + " is a shell builtin");
                    continue;
                }

                File executable = findExecutable(cmdToCheck);
                if (executable != null) {
                    System.out.println(cmdToCheck + " is " + executable.getAbsolutePath());
                } else {
                    System.out.println(cmdToCheck + ": not found");
                }
                continue;
            }
            // pwd builtin
            if (command.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
                continue;
            }

            // Try to run as external program
            File executable = findExecutable(command);
            if (executable != null) {
                runExternalProgram(parts);
                continue;
            }

            // Unknown command
            System.out.println(command + ": command not found");
        }
    }

    private static File findExecutable(String cmdToCheck) {
        String pathEnv = System.getenv("PATH");

        if (pathEnv == null) {
            return null;
        }

        String[] directories = pathEnv.split(File.pathSeparator);

        for (String dir : directories) {
            File file = new File(dir, cmdToCheck);

            if (file.exists() && file.isFile() && file.canExecute()) {
                return file;
            }
        }

        return null;
    }

    private static void runExternalProgram(String[] parts) {
        try {
            ProcessBuilder pb = new ProcessBuilder(parts);
            // Important: pass parts[0] as-is (not the absolute path) so
            // the program sees its name the way the user typed it (argv[0])
            pb.inheritIO();
            Process process = pb.start();
            process.waitFor();
        } catch (Exception e) {
            System.out.println(parts[0] + ": command not found");
        }
    }
}
