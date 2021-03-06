package parser;

import logic.GraphEdge;
import logic.GraphNode;
import models.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class JSONParser {

    private static ArrayList<JSONObject> objectList = new ArrayList<>();
    private static final ArrayList<Channel> globalChannels = new ArrayList<>();
    private static final List<Clock> componentClocks = new ArrayList<>();

    public static Automaton[] parse(String folderPath, boolean makeInpEnabled) {
        File dir = new File(folderPath + "/Components");
        File[] files = dir.listFiles((dir1, name) -> name.endsWith(".json"));

        ArrayList<String> locations = new ArrayList<>(Collections.singletonList(folderPath + "/GlobalDeclarations.json"));
        locations.addAll(Arrays.stream(files).map(File::toString).collect(Collectors.toList()));

        objectList = parseFiles(locations);
        return distrubuteObjects(objectList, makeInpEnabled);
    }

    public static Automaton[] parse(String base, String[] components, boolean makeInpEnabled) {
        ArrayList<String> locations = Arrays.stream(components).map(c -> base + c).collect(Collectors.toCollection(ArrayList::new));

        objectList = parseFiles(locations);
        return distrubuteObjects(objectList, makeInpEnabled);
    }

    public static String writeRefinement(GraphNode refTree) {
        JSONObject obj = new JSONObject();
        List<GraphEdge> children = refTree.getSuccessors();
        obj.put("initial sp id", "" + refTree.getNodeId());
        obj.put("left", "" + refTree.getStatePair().getLeft().getLocation());
        obj.put("right", "" + refTree.getStatePair().getRight().getLocation());
        obj.put("zone", "" + refTree.getStatePair().getLeft().getInvZone());
        obj.put("transitions", helper(children));

        System.out.println(obj.toJSONString());
        return obj.toJSONString();
    }

    private static JSONArray helper(List<GraphEdge> children) {
        JSONArray transitions = new JSONArray();
        for (GraphEdge child : children) {
            JSONObject transition = new JSONObject();

            if (child.getSubsetZone() == null) {
                JSONObject statePair = new JSONObject();
                statePair.put("state pair id", "" + child.getTarget().getNodeId());
                statePair.put("left", "" + child.getTarget().getStatePair().getLeft().getLocation());
                statePair.put("right", "" + child.getTarget().getStatePair().getRight().getLocation());
                statePair.put("zone", "" + child.getTarget().getStatePair().getLeft().getInvZone());
                transition.put("source sp id", "" + child.getSource().getNodeId());
                transition.put("target sp id", "" + child.getTarget().getNodeId());
                transition.put("target sp", statePair);
                transitions.add(transition);
                if (child.getTarget().getSuccessors().size() != 0)
                    statePair.put("transitions", helper(child.getTarget().getSuccessors()));
            } else {
                transition.put("source sp id", "" + child.getSource().getNodeId());
                transition.put("target sp id", "" + child.getTarget().getNodeId());
                transitions.add(transition);
            }
        }


        return transitions;
    }
    //---------------------------Testing-----------------

    private static ArrayList<JSONObject> parseFiles(ArrayList<String> locations) {
        org.json.simple.parser.JSONParser parser = new org.json.simple.parser.JSONParser();
        ArrayList<JSONObject> returnList = new ArrayList<>();

        try {
            for (String location : locations) {
                Object obj = parser.parse(new FileReader(location));
                returnList.add((JSONObject) obj);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return returnList;
    }

    private static Automaton[] distrubuteObjects(ArrayList<JSONObject> objList, boolean makeInpEnabled) {
        ArrayList<Automaton> automata = new ArrayList<>();

        try {
            for (JSONObject obj : objList) {
                if (!obj.get("name").toString().equals("Global Declarations")) {
                    addDeclarations((String) obj.get("declarations"));
                    JSONArray locationList = (JSONArray) obj.get("locations");
                    List<Location> locations = addLocations(locationList);
                    JSONArray edgeList = (JSONArray) obj.get("edges");
                    List<Edge> edges = addEdges(edgeList, locations);
                    Automaton automaton = new Automaton((String) obj.get("name"), locations, edges, new ArrayList<>(componentClocks), makeInpEnabled);
                    automata.add(automaton);
                    componentClocks.clear();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return automata.toArray(new Automaton[0]);
    }

    private static void addDeclarations(String declarations) {//add for typedefs
        String[] firstList = declarations.split(";");

        for (String value : firstList) {
            boolean isClock = value.contains("clock");

            if (isClock) {
                String clocks = value;

                clocks = clocks.replaceAll("//.*\n", "")
                        .replaceFirst("clock", "");

                clocks = clocks.replaceAll("clock", ",")
                        .replaceAll(";", "")
                        .replaceAll(" ", "")
                        .replaceAll("\n", "");

                String[] clockArr = clocks.split(",");

                for (String s : clockArr) {
                    componentClocks.add(new Clock(s));
                }
            }
        }
    }

    private static List<Location> addLocations(JSONArray locationList) {
        ArrayList<Location> returnLocList = new ArrayList<>();

        for (Object obj : locationList) {
            JSONObject jsonObject = (JSONObject) obj;

            boolean isInitial, isUniversal, isInconsistent;
            isInitial = isUniversal = isInconsistent = false;

            switch (jsonObject.get("type").toString()) {
                case "INITIAL":
                    isInitial = true;
                    break;
                case "UNIVERSAL":
                    isUniversal = true;
                    break;
                case "INCONSISTENT":
                    isInconsistent = true;
                    break;
            }

            boolean isNotUrgent = "NORMAL".equals(jsonObject.get("urgency").toString());

            List<Guard> invariant = ("".equals(jsonObject.get("invariant").toString()) ? new ArrayList<>() :
                    addGuards(jsonObject.get("invariant").toString()));
            Location loc = new Location(jsonObject.get("id").toString(), invariant, isInitial, !isNotUrgent,
                    isUniversal, isInconsistent);

            returnLocList.add(loc);
        }

        return returnLocList;
    }

    private static List<Guard> addGuards(String invariant) {
        ArrayList<Guard> guards = new ArrayList<>();
        String[] listOfInv = invariant.split("&&");

        for (String str : listOfInv) {
            String symbol = "";
            boolean strict, greater, isEq;
            strict = false;
            greater = false;
            isEq = false;

            if (str.contains("==")) {
                symbol = "==";
                greater = false;
                isEq = true;
            } else if (str.contains("<=")) {
                symbol = "<=";
            } else if (str.contains(">=")) {
                symbol = ">=";
                greater = true;
            } else if (str.contains("<") && !str.contains("=")) {
                symbol = "<";
                strict = true;
            } else if (str.contains(">") && !str.contains("=")) {
                symbol = ">";
                greater = true;
                strict = true;
            }

            String[] s = str.split(symbol);
            for (int x = 0; x < s.length; x++) {
                s[x] = s[x].replaceAll(" ", "");
            }

            if (isEq)
                guards.add(new Guard(findClock(s[0]), Integer.parseInt(s[1])));
            else
                guards.add(new Guard(findClock(s[0]), Integer.parseInt(s[1]), greater, strict));
        }

        return guards;
    }

    private static Clock findClock(String clockName) {
        for (Clock clock : componentClocks)
            if (clock.getName().equals(clockName)) return clock;

        return null;
    }

    private static List<Edge> addEdges(JSONArray edgeList, List<Location> locations) {
        ArrayList<Edge> edges = new ArrayList<>();

        for (Object obj : edgeList) {
            JSONObject jsonObject = (JSONObject) obj;

            List<Guard> guards;
            Update[] updates;

            if (!jsonObject.get("guard").toString().equals(""))
                guards = addGuards((String) jsonObject.get("guard"));
            else
                guards = new ArrayList<>();

            if (!jsonObject.get("update").toString().equals(""))
                updates = addUpdates((String) jsonObject.get("update"));
            else
                updates = new Update[]{};

            Location sourceLocation = findLoc(locations, (String) jsonObject.get("sourceLocation"));
            Location targetLocation = findLoc(locations, (String) jsonObject.get("targetLocation"));

            boolean isInput = "INPUT".equals(jsonObject.get("status").toString());

            Channel c = addChannel(jsonObject.get("sync").toString());
            if (c != null) {
                Edge edge = new Edge(sourceLocation, targetLocation, c, isInput, guards, updates);
                edges.add(edge);
            }
        }
        return edges;
    }

    private static Channel addChannel(String name) {
        if (name.equals("*")) return null;

        for (Channel channel : globalChannels)
            if (channel.getName().equals(name))
                return channel;

        Channel chan = new Channel(name);
        globalChannels.add(chan);
        return chan;
    }

    private static Update[] addUpdates(String update) {
        ArrayList<Update> updates = new ArrayList<>();
        String[] listOfInv = update.split(",");

        for (String str : listOfInv) {
            String[] s = str.split("=");
            for (int i = 0; i < s.length; i++)
                s[i] = s[i].replaceAll(" ", "");
            Update upd = new Update(findClock(s[0]), Integer.parseInt(s[1]));
            updates.add(upd);
        }

        return updates.toArray(new Update[0]);
    }

    //Helper method for addEdge in order to find which Location is source and which one is target
    private static Location findLoc(List<Location> locations, String name) {
        List<Location> locs = locations.stream().filter(location -> location.getName().equals(name)).collect(Collectors.toList());

        if (locs.isEmpty()) return null;

        return locs.get(0);
    }
}