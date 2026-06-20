import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class Main {

    // Tracks a background job for later use by the `jobs` builtin
    static class Job {
        int jobNumber;
        long pid;
        String commandLine;
        Process process;

        Job(int jobNumber, long pid, String commandLine, Process process) {
            this.jobNumber = jobNumber;
            this.pid = pid;
            this.commandLine = commandLine;
            this.process = process;
        }
    }

    static List<Job> backgroundJobs = new ArrayList<>();
    static int nextJobNumber = 1;

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        Set<String> builtins = Set.of("echo", "exit", "type", "pwd", "cd", "jobs");

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

            // Check for trailing & (background execution)
            boolean runInBackground = false;
            if (parts.get(parts.size() - 1).equals("&")) {
                runInBackground = true;
                parts = new ArrayList<>(parts.subList(0, parts.size() - 1));
            }

            if (parts.isEmpty()) {
                continue;
            }

            // Extract output/error redirection (>, >>, 1>, 1>>, 2>, 2>>) before dispatching
            String stdoutRedirectFile = null;
            String stderrRedirectFile = null;
            boolean stdoutAppend = false;
            boolean stderrAppend = false;
            List<String> cleanParts = new ArrayList<>();

            for (int i = 0; i < parts.size(); i++) {
                String token = parts.get(i);

                boolean isStdoutTrunc = token.equals(">");
                boolean isStdoutAppendOp = token.equals(">>");
                boolean isStdoutFdTrunc = token.equals("1") && i + 1 < parts.size() && parts.get(i + 1).equals(">");
                boolean isStdoutFdAppend = token.equals("1") && i + 1 < parts.size() && parts.get(i + 1).equals(">>");
                boolean isStderrFdTrunc = token.equals("2") && i + 1 < parts.size() && parts.get(i + 1).equals(">");
                boolean isStderrFdAppend = token.equals("2") && i + 1 < parts.size() && parts.get(i + 1).equals(">>");

                if (isStdoutTrunc) {
                    if (i + 1 < parts.size()) {
                        stdoutRedirectFile = parts.get(i + 1);
                        stdoutAppend = false;
                        i++;
                    }
                } else if (isStdoutAppendOp) {
                    if (i + 1 < parts.size()) {
                        stdoutRedirectFile = parts.get(i + 1);
                        stdoutAppend = true;
                        i++;
                    }
                } else if (isStdoutFdTrunc) {
                    i++;
                    if (i + 1 < parts.size()) {
                        stdoutRedirectFile = parts.get(i + 1);
                        stdoutAppend = false;
                        i++;
                    }
                } else if (isStdoutFdAppend) {
                    i++;
                    if (i + 1 < parts.size()) {
                        stdoutRedirectFile = parts.get(i + 1);
                        stdoutAppend = true;
                        i++;
                    }
                } else if (isStderrFdTrunc) {
                    i++;
                    if (i + 1 < parts.size()) {
                        stderrRedirectFile = parts.get(i + 1);
                        stderrAppend = false;
                        i++;
                    }
                } else if (isStderrFdAppend) {
                    i++;
                    if (i + 1 < parts.size()) {
                        stderrRedirectFile = parts.get(i + 1);
                        stderrAppend = true;
                        i++;
                    }
                } else {
                    cleanParts.add(token);
                }
            }

            parts = cleanParts;

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

                if (stderrRedirectFile != null) {
                    try {
                        new FileOutputStream(stderrRedirectFile, stderrAppend).close();
                    } catch (Exception e) {
                        System.out.println("echo: " + stderrRedirectFile + ": No such file or directory");
                    }
                }

                if (stdoutRedirectFile != null) {
                    try (PrintStream out = new PrintStream(new FileOutputStream(stdoutRedirectFile, stdoutAppend))) {
                        out.println(sb.toString());
                    } catch (Exception e) {
                        System.out.println("echo: " + stdoutRedirectFile + ": No such file or directory");
                    }
                } else {
                    System.out.println(sb.toString());
                }
                continue;
            }

            // pwd builtin
            if (command.equals("pwd")) {
                System.out.println(currentDirectory);
                continue;
            }

            // jobs builtin
            if (command.equals("jobs")) {
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
                if (runInBackground) {
                    runExternalProgramBackground(parts, currentDirectory,
                            stdoutRedirectFile, stdoutAppend, stderrRedirectFile, stderrAppend, input);
                } else {
                    runExternalProgram(parts, currentDirectory,
                            stdoutRedirectFile, stdoutAppend, stderrRedirectFile, stderrAppend);
                }
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
                    if (i + 1 < input.length() && input.charAt(i + 1) == '>') {
                        result.add(">>");
                        i++;
                    } else {
                        result.add(">");
                    }
                } else if (c == '&') {
                    if (hasToken) {
                        result.add(current.toString());
                        current.setLength(0);
                        hasToken = false;
                    }
                    result.add("&");
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

    private static boolean isEscapableInDoubleQuotes(char c) {
        return c == '"' || c == '\\' || c == '$' || c == '`' || c == '\n';
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

    // Runs the external program in the foreground, waiting for completion.
    private static void runExternalProgram(List<String> parts, String currentDirectory,
            String stdoutRedirectFile, boolean stdoutAppend,
            String stderrRedirectFile, boolean stderrAppend) {
        try {
            ProcessBuilder pb = buildProcessBuilder(parts, currentDirectory,
                    stdoutRedirectFile, stdoutAppend, stderrRedirectFile, stderrAppend);
            Process process = pb.start();
            process.waitFor();
        } catch (Exception e) {
            System.out.println(parts.get(0) + ": command not found");
        }
    }

    // Runs the external program in the background, without waiting.
    private static void runExternalProgramBackground(List<String> parts, String currentDirectory,
            String stdoutRedirectFile, boolean stdoutAppend,
            String stderrRedirectFile, boolean stderrAppend, String commandLine) {
        try {
            ProcessBuilder pb = buildProcessBuilder(parts, currentDirectory,
                    stdoutRedirectFile, stdoutAppend, stderrRedirectFile, stderrAppend);
            Process process = pb.start();

            int jobNumber = nextJobNumber++;
            long pid = process.pid();
            backgroundJobs.add(new Job(jobNumber, pid, commandLine, process));

            System.out.println("[" + jobNumber + "] " + pid);
        } catch (Exception e) {
            System.out.println(parts.get(0) + ": command not found");
        }
    }

    private static ProcessBuilder buildProcessBuilder(List<String> parts, String currentDirectory,
            String stdoutRedirectFile, boolean stdoutAppend,
            String stderrRedirectFile, boolean stderrAppend) {
        ProcessBuilder pb = new ProcessBuilder(parts);
        pb.directory(new File(currentDirectory));

        if (stdoutRedirectFile != null) {
            pb.redirectOutput(stdoutAppend
                    ? ProcessBuilder.Redirect.appendTo(new File(stdoutRedirectFile))
                    : ProcessBuilder.Redirect.to(new File(stdoutRedirectFile)));
        } else {
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        }

        if (stderrRedirectFile != null) {
            pb.redirectError(stderrAppend
                    ? ProcessBuilder.Redirect.appendTo(new File(stderrRedirectFile))
                    : ProcessBuilder.Redirect.to(new File(stderrRedirectFile)));
        } else {
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        }

        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);

        return pb;
    }
}