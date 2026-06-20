import java.util.Scanner;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        Set<String> builtins = Set.of("echo", "exit", "type");

        while (true) {
            System.out.print("$ ");

            String input = sc.nextLine();

            if (input.equals("exit 0")) {
                System.exit(0);
            }

            String[] parts = input.split(" ");

            if (parts[0].equals("type")) {
                String command = parts[1];

                if (builtins.contains(command)) {
                    System.out.println(command + " is a shell builtin");
                } else {
                    System.out.println(command + ": not found");
                }

                continue;
            }

            System.out.println(parts[0] + ": command not found");
        }
    }
}
