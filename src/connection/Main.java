package connection;

import logic.Controller;

import java.util.List;
import java.util.Scanner;

class Main {
    static final String VERSION = "1.0";
    static final String ENGINE_NAME = "JECDAR";

    public static void main(String[] args) {
        Scanner console = new Scanner(System.in);
        while (console.hasNextLine()) {
            System.out.println(chooseCommand(console.nextLine()));
        }
    }

    static String chooseCommand(String query) {
        String indicator = query;
        if (query.contains(" ")) {
            indicator = query.substring(0, query.indexOf(' '));
        }

        switch (indicator.toLowerCase()) {
            case "-version":
                return ENGINE_NAME + " Version: " + VERSION;
            case "-rq":
                try {
                    List<String> temp = Controller.handleRequest(query.substring(query.indexOf(' ') + 1), false);
                    if (temp.size() == 1) return temp.get(0);
                    else {
                        StringBuilder str = new StringBuilder();
                        for (int i = 0; i < temp.size(); i++)
                            str.append(temp.get(i));
                        return str.toString();
                    }
                } catch (Exception e) {
                    return "Error: " + e.getMessage();//e.printStackTrace();
                }
            case "-rqrrr":
                try {
                    List<String> temp = Controller.handleRequest(query.substring(query.indexOf(' ') + 1), true);
                    if (temp.size() == 1) return temp.get(0);
                    else {
                        StringBuilder str = new StringBuilder();
                        for (int i = 0; i < temp.size(); i++)
                            str.append(temp.get(i));
                        return str.toString();
                    }
                } catch (Exception e) {
                    return "Error: " + e.getMessage();//e.printStackTrace();
                }
            case "-vq":
                try {
                    Controller.isQueryValid(query.substring(query.indexOf(' ') + 1));
                    return String.valueOf(true);
                } catch (Exception e) {
                    return "Error: " + e.getMessage();//e.printStackTrace();
                }
            case "-help":
                return "In order to check version type:-version\n"
                        + "In order to run query type:-rq -json/-xml folderPath query query...\n"
                        + "In order to check the validity of a query type:-vq query";
            default:
                return "Unknown command: \"" + query + "\"\nwrite -help to get list of commands";
        }
    }
}
