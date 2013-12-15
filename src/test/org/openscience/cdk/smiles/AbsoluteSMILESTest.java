package org.openscience.cdk.smiles;

import com.google.common.base.Joiner;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.openscience.cdk.silent.SilentChemObjectBuilder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;

/**
 * @author John May
 * @cdk.module test-inchi
 */
public class AbsoluteSMILESTest {

    @Test public void myo_inositol() throws Exception{
        test("O[C@H]1[C@H](O)[C@@H](O)[C@H](O)[C@H](O)[C@@H]1O",
             "O[C@H]1[C@H](O)[C@@H](O)[C@H](O)[C@@H](O)[C@H]1O",
             "O[C@@H]1[C@@H](O)[C@H](O)[C@@H](O)[C@H](O)[C@@H]1O",
             "[C@@H]1(O)[C@H](O)[C@@H](O)[C@H](O)[C@@H](O)[C@@H]1O",
             "[C@@H]1([C@@H](O)[C@@H]([C@H]([C@H](O)[C@H]1O)O)O)O",
             "O[C@H]1[C@@H]([C@@H]([C@H](O)[C@H]([C@@H]1O)O)O)O", 
             "O[C@H]1[C@H](O)[C@@H](O)[C@H](O)[C@H]([C@H]1O)O",
             "[C@H]1(O)[C@H](O)[C@@H](O)[C@@H]([C@H](O)[C@@H]1O)O",
             "O[C@@H]1[C@@H](O)[C@H]([C@H]([C@H](O)[C@H]1O)O)O",
             "[C@@H]1(O)[C@H](O)[C@@H](O)[C@@H]([C@H]([C@@H]1O)O)O",
             "[C@@H]1([C@@H]([C@@H](O)[C@@H]([C@@H](O)[C@H]1O)O)O)O",
             "[C@@H]1([C@H](O)[C@@H](O)[C@@H]([C@@H](O)[C@H]1O)O)O",
             "O[C@H]1[C@@H]([C@H](O)[C@H](O)[C@H]([C@@H]1O)O)O",
             "[C@H]1([C@H](O)[C@H](O)[C@H]([C@@H]([C@H]1O)O)O)O",
             "[C@H]1([C@H](O)[C@@H]([C@@H](O)[C@@H]([C@H]1O)O)O)O",
             "[C@@H]1(O)[C@@H](O)[C@@H]([C@@H](O)[C@H](O)[C@@H]1O)O",
             "[C@H]1(O)[C@@H]([C@H]([C@H](O)[C@@H](O)[C@H]1O)O)O", 
             "O[C@H]1[C@H]([C@H](O)[C@@H](O)[C@H](O)[C@H]1O)O",
             "O[C@H]1[C@H](O)[C@@H](O)[C@H](O)[C@H](O)[C@@H]1O",
             "[C@H]1([C@@H]([C@@H]([C@H]([C@H](O)[C@H]1O)O)O)O)O",
             "[C@@H]1([C@H]([C@@H](O)[C@H](O)[C@H]([C@@H]1O)O)O)O");
    }

    @Test public void _1_3_diethylidenecyclobutane() throws Exception {
        test("C/C=C1/CC(=C/C)/C1",
             "C/C=C/1C\\C(=C/C)C1",
             "C/1(C\\C(C1)=C/C)=C\\C",
             "C\\1C(/CC1=C/C)=C\\C",
             "C/C=C1/CC(=C/C)/C1",
             "C1(=C/C)/CC(=C\\C)/C1",
             "C\\C=C1/CC(=C\\C)/C1",
             "C(\\C)=C1/CC(=C/C)/C1", 
             "C1\\C(C\\C1=C\\C)=C/C",
             "C/1(C\\C(=C/C)C1)=C\\C",
             "C(/C)=C1/CC(=C\\C)/C1",
             "C/1(C\\C(C1)=C\\C)=C/C",
             "C\\1C(/CC1=C/C)=C\\C",
             "C\\C=C/1C\\C(=C\\C)C1",
             "C/1(C\\C(=C\\C)C1)=C/C",
             "C1(/CC(=C\\C)/C1)=C/C",
             "C1\\C(=C/C)C\\C1=C\\C",
             "C1(/CC(/C1)=C/C)=C\\C",
             "C/C=C1/CC(=C/C)/C1",
             "C(=C/1C\\C(=C\\C)C1)/C");   
    }

    @Test public void bispropenyloctatriene() throws Exception{
        test("C(=C/C)/C(=C(\\C=C/C)/C=C/C)/C=C/C",
             "C(=C/C)/C(/C=C/C)=C(\\C=C/C)/C=C/C",
             "C\\C=C\\C(=C(/C=C/C)\\C=C/C)\\C=C/C",
             "C(=C/C)/C(/C=C/C)=C(/C=C/C)\\C=C/C",
             "C(/C=C/C)(=C(/C=C/C)\\C=C/C)\\C=C/C",
             "C\\C=C\\C(\\C=C/C)=C(/C=C/C)\\C=C/C",
             "C\\C=C\\C(\\C=C/C)=C(/C=C/C)\\C=C/C",
             "C(\\C=C/C)(/C=C/C)=C(/C=C/C)\\C=C/C",
             "C(/C(/C=C/C)=C(\\C=C/C)/C=C/C)=C/C",
             "C(/C(=C(/C=C/C)\\C=C/C)/C=C/C)=C/C",
             "C\\C=C\\C(=C(/C=C/C)\\C=C/C)\\C=C/C", 
             "C(/C(/C=C/C)=C(\\C=C/C)/C=C/C)=C/C",
             "C(\\C(=C(\\C=C/C)/C=C/C)\\C=C/C)=C/C",
             "C(\\C(\\C=C/C)=C(/C=C/C)\\C=C/C)=C/C",
             "C(/C)=C\\C(\\C=C/C)=C(/C=C/C)\\C=C/C",
             "C(/C)=C/C(/C=C/C)=C(/C=C/C)\\C=C/C", 
             "C(\\C=C/C)(=C(/C=C/C)\\C=C/C)/C=C/C",
             "C(=C/C)/C(=C(/C=C/C)\\C=C/C)/C=C/C",
             "C\\C=C/C(=C(/C=C/C)\\C=C/C)/C=C/C",
             "C(=C/C)\\C(\\C=C/C)=C(\\C=C/C)/C=C/C", 
             "C\\C=C/C(/C=C/C)=C(/C=C/C)\\C=C/C");    
    }
    
    // 2,4,6,8-tetramethyl-1,3,5,7-tetraazatricyclo[5.1.0.0³,⁵]octane
    @Test public void tetramethyltetraazatricyclooctane() throws Exception {
        test("C[C@H]1N2N([C@@H](C)N3[C@H](C)N13)[C@H]2C",
             "N12N([C@H](C)N3[C@@H](C)N3[C@@H]1C)[C@@H]2C",
             "C[C@H]1N2[C@@H](N3[C@H](N3[C@H](C)N21)C)C",
             "C[C@@H]1N2[C@@H](C)N3[C@H](C)N3[C@@H](C)N12",
             "N12N([C@H](N3N([C@H]1C)[C@H]3C)C)[C@H]2C", 
             "C[C@@H]1N2N1[C@@H](N3[C@H](N3[C@@H]2C)C)C",
             "[C@H]1(N2[C@@H](C)N3N([C@H](N12)C)[C@H]3C)C",
             "[C@H]1(C)N2[C@@H](N3[C@@H](C)N3[C@H](C)N12)C", 
             "[C@H]1(N2N1[C@H](C)N3N([C@@H]2C)[C@@H]3C)C", 
             "[C@H]1(N2[C@H](N2[C@@H](N3N1[C@@H]3C)C)C)C", 
             "C[C@@H]1N2[C@H](N2[C@@H](N3N1[C@@H]3C)C)C",
             "N12N([C@@H](N3[C@@H](C)N3[C@@H]1C)C)[C@@H]2C",
             "N12N([C@@H]1C)[C@@H](N3[C@@H](C)N3[C@@H]2C)C",
             "N12N([C@@H](C)N3[C@H](C)N3[C@H]1C)[C@H]2C",
             "[C@H]1(C)N2N([C@H]2C)[C@H](N3N1[C@H]3C)C",
             "N12[C@H](C)N1[C@@H](C)N3[C@H](C)N3[C@H]2C",
             "N12N([C@H](C)N3[C@H](N3[C@@H]1C)C)[C@@H]2C",
             "[C@H]1(N2[C@@H](C)N2[C@@H](N3[C@@H](C)N13)C)C", 
             "N12[C@H](C)N3N([C@@H]3C)[C@H](C)N1[C@H]2C",
             "N12N([C@@H](C)N3N([C@H]3C)[C@H]1C)[C@H]2C");
    }

    static void test(String... inputs) throws Exception {

        SmilesParser    sp = new SmilesParser(SilentChemObjectBuilder.getInstance());
        SmilesGenerator sg = SmilesGenerator.absolute();

        Set<String> output = new HashSet<String>();
        
        for (String input : inputs)
            output.add(sg.create(sp.parseSmiles(input)));

        Assert.assertThat(Joiner.on(".").join(inputs) + " were not canonicalised, outputs were " + output,
                          output.size(), is(1));

    }

}
