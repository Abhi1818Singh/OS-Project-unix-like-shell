import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        Set<String> builtins = Set.of("echo", "exit", "type", "pwd", "cd");

        String currentDirectory = System.getProperty("user.dir");

        while (true) {
            System.out.print("$ ");

            String input = scanner.nextLine();

            if (input.trim().isEmpty()) {
                continue;
            }

            List<String> parts = parseInput(input);

            if (parts.isEmpty()) {
                continue;
            }

            String command = parts.get(0);

            // exit builtin
            if (command.equals("exit")) {
                System.exit(0);
            }

            // echo builtin
            if (command.equals("echo")) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parts.size(); i++) {
                    if (i > 1)
                        sb.append(" ");
                    sb.append(parts.get(i));
                }
                System.out.println(sb.toString());
                continue;
            }

            // pwd builtin
            if (command.equals("pwd")) {
                System.out.println(currentDirectory);
                continue;
            }

            // cd builtin
            if (command.equals("cd")) {
                if (parts.size() < 2) {
                    continue;
                }

                String targetPath = parts.get(1);

                if (targetPath.equals("~")) {
                    targetPath = System.getenv("HOME");
                }

                File targetDir;
                if (targetPath.startsWith("/")) {
                    targetDir = new File(targetPath);
                } else {
                    targetDir = new File(currentDirectory, targetPath);
                }

                targetDir = targetDir.getCanonicalFile();

                if (targetDir.isDirectory()) {
                    currentDirectory = targetDir.getAbsolutePath();
                } else {
                    System.out.println("cd: " + targetPath + ": No such file or directory");
                }
                continue;
            }

            // type builtin
            if (command.equals("type")) {
                if (parts.size() < 2) {
                    continue;
                }

                String cmdToCheck = parts.get(1);

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

            // Try to run as external program
            File executable = findExecutable(command);
            if (executable != null) {
                runExternalProgram(parts, currentDirectory);
                continue;
            }

            // Unknown command
            System.out.println(command + ": command not found");
        }
    }

    // Parses a raw input line into a list of arguments, honoring quotes and
    // backslash escaping.
    private static List<String> parseInput(String input) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean hasToken = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inSingleQuotes) {
                if (c == '\'') {
                    inSingleQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (inDoubleQuotes) {
                if (c == '"') {
                    inDoubleQuotes = false;
                } else if (c == '\\' && i + 1 < input.length()
                        && isEscapableInDoubleQuotes(input.charAt(i + 1))) {
                    i++;
                    current.append(input.charAt(i));
                } else {
                    current.append(c);
                }
            } else {
                if (c == '\\') {
                    if (i + 1 < input.length()) {
                        i++;
                        current.append(input.charAt(i));
                        hasToken = true;
                    }
                } else if (c == '\'') {
                    inSingleQuotes = true;
                    hasToken = true;
                } else if (c == '"') {
                    inDoubleQuotes = true;
                    hasToken = true;
                } else if (c == '>') {
                    if (hasToken) {
                        result.add(current.toString());
                        current.setLength(0);
                        hasToken = false;
                    }
                    result.add(">");
                } else if (c == ' ' || c == '\t') {
                    if (hasToken) {
                        result.add(current.toString());
                        current.setLength(0);
                        hasToken = false;
                    }
                } else {
                    current.append(c);
                    hasToken = true;
                }
            }
        }

        if (hasToken) {
            result.add(current.toString());
        }

        return result;
    }

    // Inside double quotes, backslash only has special meaning before these
    // characters.
    private static boolean isEscapableInDoubleQuotes(char c) {
        return c == '"' || c == '\\' || c == '$' || c == '`' || c == '\n';
    }

    // Searches PATH for the given command, returns the File if found & executable
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

    // Runs the external program, passing through stdin/stdout/stderr
    private static void runExternalProgram(List<String> parts, String currentDirectory) {
        try {
            ProcessBuilder pb = new ProcessBuilder(parts);
            pb.directory(new File(currentDirectory));
            pb.inheritIO();
            Process process = pb.start();
            process.waitFor();
        } catch (Exception e) {
            System.out.println(parts.get(0) + ": command not found");
        }
    }
}