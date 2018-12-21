package features;

import logic.Composition;
import logic.Refinement;
import logic.SimpleTransitionSystem;
import models.Component;
import org.junit.BeforeClass;
import org.junit.Test;
import parser.Parser;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;

public class UnspecTest {
    private static Component a, aa, b;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        String fileName = "src/" + System.mapLibraryName("DBM");
        File lib = new File(fileName);
        System.load(lib.getAbsolutePath());

        String base = "./samples/Unspec/";
        List<String> components = new ArrayList<>(Arrays.asList("GlobalDeclarations.json",
                "Components/A.json",
                "Components/AA.json",
                "Components/B.json"));
        List<Component> machines = Parser.parse(base, components);

        a = machines.get(0);
        aa = machines.get(1);
        b = machines.get(2);
    }

    @Test
    public void compNotRefinesB() {
        Refinement ref = new Refinement(
                new Composition(new ArrayList<>(Arrays.asList(new SimpleTransitionSystem(a), new SimpleTransitionSystem(aa)))),
                new SimpleTransitionSystem(b));
        assertFalse(ref.check());
    }
}
