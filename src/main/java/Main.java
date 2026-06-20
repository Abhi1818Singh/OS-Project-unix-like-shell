import java.io.File;
import java.util.Scanner;
import java.util.Set;

public class Main {


    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");

        if (pathEnv == null) {
            return null;
        }

        String[] directories = pathEnv.split(File.pathSeparator);

        for (String dir : directories) {
            File file = new File(dir, command);

            if (file.exists() && file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }

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

            // exit
            if (command.equals("exit")) {
                System.exit(0);
            }

            // echo
            if (command.equals("echo")) {
                if (input.length() > 5) {
                    System.out.println(input.substring(5));
                } else {
                    System.out.println();
                }
                continue;
            }

            // type
            if (command.equals("type")) {
                if (parts.length < 2) {
                    continue;
                }

                String cmdToCheck = parts[1];

                if (builtins.contains(cmdToCheck)) {
                    System.out.println(cmdToCheck + " is a shell builtin");
                    continue;
                }

                String executablePath = findExecutable(cmdToCheck);

                if (executablePath != null) {
                    System.out.println(cmdToCheck + " is " + executablePath);
                } else {
                    System.out.println(cmdToCheck + ": not found");
                }

                continue;
            }

            // external command
            String executablePath = findExecutable(command);

            if (executablePath != null) {
                parts[0] = executablePath;

                ProcessBuilder pb = new ProcessBuilder(parts);
                pb.inheritIO();

                Process process = pb.start();
                process.waitFor();
            } else {
                System.out.println(command + ": command not found");
            }
        }
    }
}
