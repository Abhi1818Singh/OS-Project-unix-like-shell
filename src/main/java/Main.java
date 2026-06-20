import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.nio.file.StandardOpenOption;

public class Main {

    private static String[] parseCommand(String input) {
        java.util.ArrayList<String> args = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        // boolean escape = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            //BackSlash Handling
            if (c == '\\' && !inSingle) {
            // INSIDE DOUBLE QUOTES
                if (inDouble) {
                    if (i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (next == '"' || next == '\\') {
                            current.append(next);
                            i++; // consume next char
                        } else {
                            current.append(next);
                            i++;
                        }
                    }
                    continue;
                }
                // OUTSIDE QUOTES
                if (i + 1 < input.length()) {
                    current.append(input.charAt(i + 1));
                    i++; // consume next char
                }
                continue;
            }
            // toggle Single if not in double
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            //toggle double if not in single
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            //remove space outside the quotes
            if (Character.isWhitespace(c) && !inSingle && !inDouble) {
                if (current.length() > 0) {
                    args.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) {
            args.add(current.toString());
        }
        return args.toArray(new String[0]);
    }

    private static void ensureParentDirs(String filePath) throws Exception {
        Path p = Path.of(filePath);
        if (p.getParent() != null) {
            Files.createDirectories(p.getParent());
        }
    }

    static class Job {
        int jobId;
        long pid;
        String command;
        Process process;

        Job(int jobId, long pid, String command, Process process) {
            this.jobId = jobId;
            this.pid = pid;
            this.command = command;
            this.process = process;
        }
    }

    // Shared reaping logic: print Done for completed jobs, remove them from table
    private static void reapJobs(java.util.ArrayList<Job> jobs) {
        java.util.ArrayList<Job> completedJobs = new java.util.ArrayList<>();
        int total = jobs.size();

        for (int i = 0; i < total; i++) {
            Job job = jobs.get(i);
            // + for last, - for second-to-last, space for all others
            String marker;
            if (i == total - 1) {
                marker = "+";
            } else if (i == total - 2) {
                marker = "-";
            } else {
                marker = " ";
            }
            if (!job.process.isAlive()) {
                System.out.printf(
                    "[%d]%s  %-23s %s\n",
                    job.jobId,
                    marker,
                    "Done",
                    job.command
                );
                completedJobs.add(job);
            }
        }
        // Remove completed jobs after printing
        jobs.removeAll(completedJobs);
    }

    // Find the smallest available job number not currently in use
    private static int nextAvailableJobId(java.util.ArrayList<Job> jobs) {
        java.util.HashSet<Integer> usedIds = new java.util.HashSet<>();
        for (Job job : jobs) {
            usedIds.add(job.jobId);
        }
        int id = 1;
        while (usedIds.contains(id)) {
            id++;
        }
        return id;
    }

    // Split a list of tokens into segments separated by "|"
    private static java.util.ArrayList<String[]> splitByPipe(String[] parts) {
        java.util.ArrayList<String[]> segments = new java.util.ArrayList<>();
        java.util.ArrayList<String> current = new java.util.ArrayList<>();
        for (String part : parts) {
            if (part.equals("|")) {
                if (!current.isEmpty()) {
                    segments.add(current.toArray(new String[0]));
                    current.clear();
                }
            } else {
                current.add(part);
            }
        }
        if (!current.isEmpty()) {
            segments.add(current.toArray(new String[0]));
        }
        return segments;
    }

    private static final java.util.Set<String> BUILTINS = new java.util.HashSet<>(
        java.util.Arrays.asList("echo", "type", "pwd", "cd", "jobs", "exit")
    );

    private static boolean isBuiltin(String cmd) {
        return BUILTINS.contains(cmd);
    }

    /**
     * Execute a built-in command, reading from `stdin` and writing to `stdout`.
     * Returns the exit code (0 = success).
     */
    private static int executeBuiltin(
            String[] parts,
            InputStream stdin,
            OutputStream stdout,
            File currentDirectory,
            java.util.ArrayList<Job> jobs
    ) throws Exception {
        PrintStream out = new PrintStream(stdout, true);
        String cmd = parts[0];

        if (cmd.equals("echo")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                if (i > 1) sb.append(" ");
                sb.append(parts[i]);
            }
            out.println(sb.toString());
            return 0;
        }

        if (cmd.equals("pwd")) {
            out.println(currentDirectory.getAbsolutePath());
            return 0;
        }

        if (cmd.equals("type")) {
            if (parts.length < 2) return 1;
            String t = parts[1];
            if (isBuiltin(t)) {
                out.println(t + " is a shell builtin");
            } else {
                String path = System.getenv("PATH");
                boolean found = false;
                for (String dir : path.split(File.pathSeparator)) {
                    File file = new File(dir, t);
                    if (file.exists() && file.canExecute()) {
                        out.println(t + " is " + file.getAbsolutePath());
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    out.println(t + ": not found");
                    return 1;
                }
            }
            return 0;
        }

        if (cmd.equals("jobs")) {
            int total = jobs.size();
            java.util.ArrayList<Job> completedJobs = new java.util.ArrayList<>();
            for (int i = 0; i < total; i++) {
                Job job = jobs.get(i);
                // + for last, - for second-to-last, space for all others
                String marker;
                if (i == total - 1) {
                    marker = "+";
                } else if (i == total - 2) {
                    marker = "-";
                } else {
                    marker = " ";
                }
                if (job.process.isAlive()) {
                    out.printf("[%d]%s  %-23s %s &\n", job.jobId, marker, "Running", job.command);
                } else {
                    out.printf("[%d]%s  %-23s %s\n", job.jobId, marker, "Done", job.command);
                    // Mark for removal after display
                    completedJobs.add(job);
                }
            }
            // Remove completed jobs after printing
            jobs.removeAll(completedJobs);
            return 0;
        }

        // cd and exit don't make much sense in pipelines, but handle gracefully
        if (cmd.equals("cd")) {
            return 0;
        }
        if (cmd.equals("exit")) {
            return 0;
        }

        return 1;
    }

    // Execute a pipeline of two or more commands (external or built-in)
    private static void executePipeline(
            java.util.ArrayList<String[]> segments,
            File currentDirectory,
            String stdoutFile,
            boolean appendStdout,
            String stderrFile,
            boolean appendStderr,
            java.util.ArrayList<Job> jobs
    ) throws Exception {
        int n = segments.size();

        // Check if any segment is a built-in
        boolean anyBuiltin = false;
        for (String[] seg : segments) {
            if (isBuiltin(seg[0])) {
                anyBuiltin = true;
                break;
            }
        }

        // If no built-ins, use the fast path with ProcessBuilder.startPipeline
        if (!anyBuiltin) {
            // Build all ProcessBuilders
            java.util.ArrayList<ProcessBuilder> builders = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                ProcessBuilder pb = new ProcessBuilder(segments.get(i));
                pb.directory(currentDirectory);
                // stderr for all processes goes to terminal or file
                if (stderrFile != null) {
                    if (appendStderr) {
                        pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(stderrFile)));
                    } else {
                        pb.redirectError(new File(stderrFile));
                    }
                } else {
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                }
                builders.add(pb);
            }

            // Use Java's built-in pipeline support (Java 9+)
            // This correctly wires stdout of each process to stdin of the next
            java.util.List<Process> procs = ProcessBuilder.startPipeline(builders);

            // Handle stdout of last process
            Process last = procs.get(procs.size() - 1);
            if (stdoutFile != null) {
                // drain last process stdout to file
                byte[] buf = last.getInputStream().readAllBytes();
                if (appendStdout) {
                    Files.write(Path.of(stdoutFile), buf,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } else {
                    Files.write(Path.of(stdoutFile), buf,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                }
            } else {
                // stream last process stdout to terminal
                last.getInputStream().transferTo(System.out);
                System.out.flush();
            }

            // Wait for all processes
            for (Process p : procs) {
                p.waitFor();
            }
            return;
        }

        // Mixed pipeline: wire segments manually using PipedInputStream/PipedOutputStream
        // pipes[i] connects output of segment i to input of segment i+1
        PipedOutputStream[] pipeOuts = new PipedOutputStream[n - 1];
        PipedInputStream[]  pipeIns  = new PipedInputStream[n - 1];
        for (int i = 0; i < n - 1; i++) {
            pipeOuts[i] = new PipedOutputStream();
            pipeIns[i]  = new PipedInputStream(pipeOuts[i], 65536);
        }

        // Collect threads/processes so we can join/wait
        java.util.ArrayList<Thread>  threads   = new java.util.ArrayList<>();
        java.util.ArrayList<Process> processes = new java.util.ArrayList<>();

        // Determine final stdout destination
        final OutputStream finalOut;
        if (stdoutFile != null) {
            if (appendStdout)
                finalOut = Files.newOutputStream(Path.of(stdoutFile), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            else
                finalOut = Files.newOutputStream(Path.of(stdoutFile), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            finalOut = System.out;
        }

        for (int i = 0; i < n; i++) {
            final String[] seg = segments.get(i);
            final InputStream  segIn  = (i == 0)     ? InputStream.nullInputStream() : pipeIns[i - 1];
            final OutputStream segOut = (i == n - 1) ? finalOut                      : pipeOuts[i];
            final int idx = i;

            if (isBuiltin(seg[0])) {
                final File dir = currentDirectory;
                final java.util.ArrayList<Job> jobsRef = jobs;
                Thread t = new Thread(() -> {
                    try {
                        executeBuiltin(seg, segIn, segOut, dir, jobsRef);
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        // Close our output so the next stage sees EOF
                        if (idx < n - 1) {
                            try { pipeOuts[idx].close(); } catch (Exception ignored) {}
                        } else {
                            // flush final output (but don't close System.out)
                            try { segOut.flush(); } catch (Exception ignored) {}
                            if (stdoutFile != null) {
                                try { segOut.close(); } catch (Exception ignored) {}
                            }
                        }
                    }
                });
                t.start();
                threads.add(t);
            } else {
                // External command
                ProcessBuilder pb = new ProcessBuilder(seg);
                pb.directory(currentDirectory);
                // stderr
                if (stderrFile != null) {
                    if (appendStderr)
                        pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(stderrFile)));
                    else
                        pb.redirectError(new File(stderrFile));
                } else {
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                }

                Process proc = pb.start();
                processes.add(proc);

                // Thread to feed stdin from pipe
                final InputStream procFeed = segIn;
                Thread feedThread = new Thread(() -> {
                    try {
                        procFeed.transferTo(proc.getOutputStream());
                    } catch (Exception ignored) {
                    } finally {
                        try { proc.getOutputStream().close(); } catch (Exception ignored) {}
                    }
                });
                feedThread.start();
                threads.add(feedThread);

                // Thread to drain stdout to next pipe or final output
                final OutputStream procDrain = segOut;
                Thread drainThread = new Thread(() -> {
                    try {
                        proc.getInputStream().transferTo(procDrain);
                    } catch (Exception ignored) {
                    } finally {
                        if (idx < n - 1) {
                            try { pipeOuts[idx].close(); } catch (Exception ignored) {}
                        } else {
                            try { procDrain.flush(); } catch (Exception ignored) {}
                            if (stdoutFile != null) {
                                try { procDrain.close(); } catch (Exception ignored) {}
                            }
                        }
                    }
                });
                drainThread.start();
                threads.add(drainThread);
            }
        }

        // Wait for all threads
        for (Thread t : threads) t.join();
        // Wait for all processes
        for (Process p : processes) p.waitFor();
    }

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        File currentDirectory =
                new File(System.getProperty("user.dir"));
        java.util.ArrayList<Job> jobs = new java.util.ArrayList<>();
        while (true) {
            // Brief wait to let background processes register their exit with the OS
            Thread.sleep(100);
            // Reap completed jobs before printing the prompt
            reapJobs(jobs);

            System.out.print("$ ");
            String input = sc.nextLine();
            String[] parts = parseCommand(input);
            String stdoutFile = null;
            String stderrFile = null;
            boolean appendStdout = false;
            boolean appendStderr = false;
            java.util.ArrayList<String> commandParts = new java.util.ArrayList<>();

            for (int i = 0; i < parts.length; i++) {
               if (parts[i].equals(">") || parts[i].equals("1>")) {
                    if (i + 1 < parts.length) {
                        stdoutFile = parts[i + 1];
                        appendStdout = false;
                    }
                    i++;
                    continue;
                }

                if (parts[i].equals(">>") || parts[i].equals("1>>")) {
                    if (i + 1 < parts.length) {
                        stdoutFile = parts[i + 1];
                        appendStdout = true;
                    }
                    i++;
                    continue;
                }
                if (parts[i].equals("2>")) {
                    if (i + 1 < parts.length) {
                        stderrFile = parts[i + 1];
                        appendStderr = false;
                    }
                    i++;
                    continue;
                }
                if (parts[i].equals("2>>")) {
                    if (i + 1 < parts.length) {
                        stderrFile = parts[i + 1];
                        appendStderr = true;
                    }
                    i++;
                    continue;
                }
                commandParts.add(parts[i]);
            }
            parts = commandParts.toArray(new String[0]);
            boolean background = false;
            if (parts.length > 0 && parts[parts.length - 1].equals("&")) {
                background = true;

                String[] temp = new String[parts.length - 1];
                System.arraycopy(parts, 0, temp, 0, parts.length - 1);
                parts = temp;
            }
            // if (stderrFile != null) {
            //     Files.writeString(Path.of(stderrFile), "");
            // }

            if (stdoutFile != null) {
                ensureParentDirs(stdoutFile);
                if (!appendStdout) {
                    Files.writeString(Path.of(stdoutFile), "",
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } else {
                    Files.writeString(Path.of(stdoutFile), "",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
            }
            if (stderrFile != null) {
                ensureParentDirs(stderrFile);
                if (!appendStderr) {
                    Files.writeString(Path.of(stderrFile), "",
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } else {
                    Files.writeString(Path.of(stderrFile), "",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
            }

            if (parts.length == 0) {
                continue;
            }

            // Check if this is a pipeline command
            boolean hasPipe = false;
            for (String part : parts) {
                if (part.equals("|")) {
                    hasPipe = true;
                    break;
                }
            }

            // pipeline handling
            if (hasPipe) {
                java.util.ArrayList<String[]> segments = splitByPipe(parts);
                executePipeline(segments, currentDirectory,
                    stdoutFile, appendStdout, stderrFile, appendStderr, jobs);
                continue;
            }

            String cmd = parts[0];
            // exit
            if (cmd.equals("exit")) {
                break;
            }
            // pwd
            else if (cmd.equals("pwd")) {
                String output = currentDirectory.getAbsolutePath()
                        + System.lineSeparator();
                if (stdoutFile != null) {
                    if (appendStdout) {
                        Files.writeString(
                            Path.of(stdoutFile),
                            output,
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.APPEND
                        );
                    } else {
                        Files.writeString(Path.of(stdoutFile), output);
                    }
                } 
                else {
                    System.out.print(output);
                }
            }
            // cd
            else if (cmd.equals("cd")) {
                if (parts.length < 2) continue;
                String path = parts[1];
                File newDir;
                if (path.equals("~")) {
                    newDir = new File(System.getenv("HOME"));
                }
                else if (path.startsWith("/")) {
                    newDir = new File(path);
                }
                else {
                    newDir = new File(currentDirectory, path);
                }
                if (newDir.exists() && newDir.isDirectory()) {
                    currentDirectory = newDir.getCanonicalFile();
                } 
                else {
                    String error = "cd: " + path + ": No such file or directory" + System.lineSeparator();
                    if (stderrFile != null) {
                        if (appendStderr) {
                            Files.writeString(
                                Path.of(stderrFile),
                                error,
                                java.nio.file.StandardOpenOption.CREATE,
                                java.nio.file.StandardOpenOption.APPEND
                            );
                        } 
                        else {
                            Files.writeString(
                                Path.of(stderrFile),
                                error
                            );
                        }
                    } 
                    else {
                        System.err.print(error);
                    }
                }
            }
            // jobs
            else if (cmd.equals("jobs")) {
                int total = jobs.size();
                java.util.ArrayList<Job> completedJobs = new java.util.ArrayList<>();

                // Print all jobs with markers based on full current list
                for (int i = 0; i < total; i++) {
                    Job job = jobs.get(i);
                    // + for last, - for second-to-last, space for all others
                    String marker;
                    if (i == total - 1) {
                        marker = "+";
                    } else if (i == total - 2) {
                        marker = "-";
                    } else {
                        marker = " ";
                    }
                    if (job.process.isAlive()) {
                        System.out.printf(
                            "[%d]%s  %-23s %s &\n",
                            job.jobId,
                            marker,
                            "Running",
                            job.command
                        );
                    } else {
                        System.out.printf(
                            "[%d]%s  %-23s %s\n",
                            job.jobId,
                            marker,
                            "Done",
                            job.command
                        );
                        // Mark for removal after display
                        completedJobs.add(job);
                    }
                }
                // Remove completed jobs after printing
                jobs.removeAll(completedJobs);
            }
            // echo
            else if (cmd.equals("echo")) {
                StringBuilder output = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    if (i > 1) output.append(" ");
                    output.append(parts[i]);
                }
                if (stdoutFile != null) {
                    if (appendStdout) {
                        Files.writeString(
                            Path.of(stdoutFile),
                            output.toString() + System.lineSeparator(),
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.APPEND
                        );
                    } 
                    else {
                        Files.writeString(
                            Path.of(stdoutFile),
                            output.toString() + System.lineSeparator()
                        );
                    }
                } 
                else {
                    System.out.println(output);
                }
            }
            // type
            else if (cmd.equals("type")) {
                if (parts.length < 2) continue;
                String t = parts[1];
                String output = null;
                String error = null;
                if (t.equals("echo") || t.equals("exit") ||
                    t.equals("type") || t.equals("pwd") ||
                    t.equals("cd") || t.equals("jobs")) {
                    output = t + " is a shell builtin" + System.lineSeparator();
                }
                else {
                    String path = System.getenv("PATH");
                    boolean found = false;
                    for (String dir : path.split(File.pathSeparator)) {
                        File file = new File(dir, t);
                        if (file.exists() && file.canExecute()) {
                            output = t + " is " + file.getAbsolutePath()
                                    + System.lineSeparator();
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        error = t + ": not found" + System.lineSeparator();
                    }
                }
                // handle stdout
                if (output != null) {
                    if (stdoutFile != null) {
                        if (appendStdout) {
                            Files.writeString(
                                Path.of(stdoutFile),
                                output,
                                java.nio.file.StandardOpenOption.CREATE,
                                java.nio.file.StandardOpenOption.APPEND
                            );
                        } else {
                            Files.writeString(Path.of(stdoutFile), output);
                        }
                    } else {
                        System.out.print(output);
                    }
                }
                // handle stderr
                if (error != null) {
                    if (stderrFile != null) {
                        if (appendStderr) {
                            Files.writeString(
                                Path.of(stderrFile),
                                error,
                                java.nio.file.StandardOpenOption.CREATE,
                                java.nio.file.StandardOpenOption.APPEND
                            );
                        } else {
                            Files.writeString(
                                Path.of(stderrFile),
                                error
                            );
                        }
                    } 
                    else {
                        System.err.print(error);
                    }
                }
            }
            // external commands
            else {
                boolean found = false;
                String path = System.getenv("PATH");
                for (String dir : path.split(File.pathSeparator)) {
                    File file = new File(dir, cmd);
                    if (file.exists() && file.canExecute()) {
                        ProcessBuilder pb = new ProcessBuilder(parts);
                        pb.directory(currentDirectory);
                        if (stdoutFile != null) {
                            if (appendStdout) {
                                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(
                                        new File(stdoutFile)));
                            } else {
                                pb.redirectOutput(new File(stdoutFile));
                            }
                        }
                        if (stderrFile != null) {
                            if (appendStderr) {
                                pb.redirectError(
                                    ProcessBuilder.Redirect.appendTo(
                                        new File(stderrFile)
                                    )
                                );
                            } else {
                                pb.redirectError(new File(stderrFile));
                            }
                        }
                        // Process process = pb.start();
                        if (background) {
                            if (stdoutFile == null) {
                                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                            }
                            if (stderrFile == null) {
                                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                            }
                            Process process = pb.start();
                            // Use smallest available job number instead of incrementing
                            int jobId = nextAvailableJobId(jobs);
                            System.out.println("[" + jobId + "] " + process.pid());
                            String jobCommand = input.substring(0, input.lastIndexOf("&")).trim();
                            // System.err.println("DEBUG: [" + jobCommand + "]");
                            jobs.add(
                                new Job(
                                    jobId,
                                    process.pid(),
                                    jobCommand,
                                    process
                                )
                            );
                        } 
                        else {
                            Process process = pb.start();
                            if (stdoutFile == null) {
                                process.getInputStream().transferTo(System.out);
                            }
                            if (stderrFile == null) {
                                process.getErrorStream().transferTo(System.err);
                            }
                            process.waitFor();
                        }
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    String error = cmd + ": command not found" + System.lineSeparator();
                    if (stderrFile != null) {
                        if (appendStderr) {
                            Files.writeString(
                                Path.of(stderrFile),
                                error,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.APPEND
                            );
                        } else {
                            Files.writeString(
                                Path.of(stderrFile),
                                error
                            );
                        }
                    } else {
                        System.err.print(error);
                    }
                }
            }
        }
    }
}