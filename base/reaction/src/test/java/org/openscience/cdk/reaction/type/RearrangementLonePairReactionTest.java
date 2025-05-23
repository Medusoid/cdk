/* Copyright (C) 2004-2007  Miguel Rojas <miguel.rojas@uni-koeln.de>
 *
 * Contact: cdk-devel@lists.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.openscience.cdk.reaction.type;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.LonePair;
import org.openscience.cdk.atomtype.CDKAtomTypeMatcher;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IAtomContainerSet;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IChemObject;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.interfaces.IReactionSet;
import org.openscience.cdk.reaction.IReactionProcess;
import org.openscience.cdk.reaction.ReactionProcessTest;
import org.openscience.cdk.reaction.type.parameters.IParameterReact;
import org.openscience.cdk.reaction.type.parameters.SetReactionCenter;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.tools.LoggingToolFactory;
import org.openscience.cdk.tools.LonePairElectronChecker;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import org.openscience.cdk.tools.manipulator.ReactionManipulator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <p>IReactionProcess which participate in movement resonance.
 * This reaction could be represented as |A-B=C => [A+]=B-[C-]. Due to
 * the negative charge of the atom A, the double bond in position 2 is
 * displaced.</p>
 * <pre>
 *  IAtomContainerSet setOfReactants = DefaultChemObjectBuilder.getInstance().newInstance(IAtomContainerSet.class);
 *  setOfReactants.addAtomContainer(new AtomContainer());
 *  IReactionProcess type = new RearrangementLonePairReaction();
 *  HashMap<String,Object> params = new HashMap<String,Object>();
        params.put("hasActiveCenter",Boolean.FALSE);;
    type.setParameters(params);
 *  IReactionSet setOfReactions = type.initiate(setOfReactants, null);
 *  </pre>
 *
 * <p>We have the possibility to localize the reactive center. Good method if you
 * want to localize the reaction in a fixed point</p>
 * <pre>atoms[0].setFlag(CDKConstants.REACTIVE_CENTER,true);</pre>
 * <p>Moreover you must put the parameter Boolean.TRUE</p>
 * <p>If the reactive center is not localized then the reaction process will
 * try to find automatically the possible reactive center.</p>
 *
 *
 * @author         Miguel Rojas
 *
 * @cdk.created    2006-05-05
 *
 **/
/**
 * TestSuite that runs a test for the RearrangementLonePairReactionTest.
 * Generalized Reaction: [A-]-B=C => A=B-[C-].
 *
 */

public class RearrangementLonePairReactionTest extends ReactionProcessTest {

    private final LonePairElectronChecker lpcheck = new LonePairElectronChecker();
    private final IChemObjectBuilder            builder = SilentChemObjectBuilder.getInstance();

    /**
     *  The JUnit setup method
     */
    RearrangementLonePairReactionTest() throws Exception {
        setReaction(RearrangementLonePairReaction.class);
    }

    /**
     *  The JUnit setup method
     */
    @Test
    void testRearrangementLonePairReaction() throws Exception {
        IReactionProcess type = new RearrangementLonePairReaction();
        Assertions.assertNotNull(type);
    }

    /**
     * A unit test suite for JUnit. Reaction: O-C=C-C => [O+]=C-[C-]-C
     * Automatic search of the center active.
     *
     * @cdk.inchi  InChI=1/C3H6O/c1-2-3-4/h2-4H,1H3
     *
     *
     */
    @Test
    @Override
    public void testInitiate_IAtomContainerSet_IAtomContainerSet() throws Exception {
        IReactionProcess type = new RearrangementLonePairReaction();

        IAtomContainerSet setOfReactants = getExampleReactants();
        IAtomContainer molecule = setOfReactants.getAtomContainer(0);

        /* initiate */

        List<IParameterReact> paramList = new ArrayList<>();
        IParameterReact param = new SetReactionCenter();
        param.setParameter(Boolean.FALSE);
        paramList.add(param);
        type.setParameterList(paramList);
        IReactionSet setOfReactions = type.initiate(setOfReactants, null);

        Assertions.assertEquals(1, setOfReactions.getReactionCount());
        Assertions.assertEquals(1, setOfReactions.getReaction(0).getProductCount());

        IAtomContainer product = setOfReactions.getReaction(0).getProducts().getAtomContainer(0);
        Assertions.assertEquals(-1, product.getAtom(2).getFormalCharge().intValue());
        Assertions.assertEquals(0, product.getConnectedLonePairsCount(product.getAtom(1)));

        IAtomContainer molecule2 = getExpectedProducts().getAtomContainer(0);

        assertEquals(molecule2, product);
    }

    /**
     * A unit test suite for JUnit. Reaction: O-C=C-C => [O+]=C-[C-]-C
     * Manually put of the center active.
     *
     * @cdk.inchi  InChI=1/C3H6O/c1-2-3-4/h2-4H,1H3
     *
     *
     */
    @Test
    void testManuallyCentreActive() throws Exception {
        IReactionProcess type = new RearrangementLonePairReaction();

        IAtomContainerSet setOfReactants = getExampleReactants();
        IAtomContainer molecule = setOfReactants.getAtomContainer(0);

        /* manually put the center active */
        molecule.getAtom(0).setFlag(IChemObject.REACTIVE_CENTER, true);
        molecule.getBond(0).setFlag(IChemObject.REACTIVE_CENTER, true);
        molecule.getAtom(1).setFlag(IChemObject.REACTIVE_CENTER, true);
        molecule.getBond(1).setFlag(IChemObject.REACTIVE_CENTER, true);
        molecule.getAtom(2).setFlag(IChemObject.REACTIVE_CENTER, true);
        molecule.getBond(2).setFlag(IChemObject.REACTIVE_CENTER, true);
        molecule.getAtom(3).setFlag(IChemObject.REACTIVE_CENTER, true);

        List<IParameterReact> paramList = new ArrayList<>();
        IParameterReact param = new SetReactionCenter();
        param.setParameter(Boolean.TRUE);
        paramList.add(param);
        type.setParameterList(paramList);

        /* initiate */

        IReactionSet setOfReactions = type.initiate(setOfReactants, null);

        Assertions.assertEquals(1, setOfReactions.getReactionCount());
        Assertions.assertEquals(1, setOfReactions.getReaction(0).getProductCount());

        IAtomContainer product = setOfReactions.getReaction(0).getProducts().getAtomContainer(0);

        /* C=C-[C-]-C */
        IAtomContainer molecule2 = getExpectedProducts().getAtomContainer(0);

        assertEquals(molecule2, product);
    }

    /**
     * A unit test suite for JUnit.
     *
     *
     */
    @Test
    void testCDKConstants_REACTIVE_CENTER() throws Exception {
        IReactionProcess type = new RearrangementLonePairReaction();

        IAtomContainerSet setOfReactants = getExampleReactants();
        IAtomContainer molecule = setOfReactants.getAtomContainer(0);

        /* manually put the reactive center */
        molecule.getAtom(0).setFlag(IChemObject.REACTIVE_CENTER, true);
        molecule.getAtom(1).setFlag(IChemObject.REACTIVE_CENTER, true);
        molecule.getAtom(2).setFlag(IChemObject.REACTIVE_CENTER, true);
        molecule.getBond(0).setFlag(IChemObject.REACTIVE_CENTER, true);
        molecule.getBond(1).setFlag(IChemObject.REACTIVE_CENTER, true);

        List<IParameterReact> paramList = new ArrayList<>();
        IParameterReact param = new SetReactionCenter();
        param.setParameter(Boolean.TRUE);
        paramList.add(param);
        type.setParameterList(paramList);

        /* initiate */
        IReactionSet setOfReactions = type.initiate(setOfReactants, null);

        IAtomContainer reactant = setOfReactions.getReaction(0).getReactants().getAtomContainer(0);
        Assertions.assertTrue(molecule.getAtom(0).getFlag(IChemObject.REACTIVE_CENTER));
        Assertions.assertTrue(reactant.getAtom(0).getFlag(IChemObject.REACTIVE_CENTER));
        Assertions.assertTrue(molecule.getAtom(1).getFlag(IChemObject.REACTIVE_CENTER));
        Assertions.assertTrue(reactant.getAtom(1).getFlag(IChemObject.REACTIVE_CENTER));
        Assertions.assertTrue(molecule.getAtom(2).getFlag(IChemObject.REACTIVE_CENTER));
        Assertions.assertTrue(reactant.getAtom(2).getFlag(IChemObject.REACTIVE_CENTER));
        Assertions.assertTrue(molecule.getBond(0).getFlag(IChemObject.REACTIVE_CENTER));
        Assertions.assertTrue(reactant.getBond(0).getFlag(IChemObject.REACTIVE_CENTER));
        Assertions.assertTrue(molecule.getBond(1).getFlag(IChemObject.REACTIVE_CENTER));
        Assertions.assertTrue(reactant.getBond(1).getFlag(IChemObject.REACTIVE_CENTER));
    }

    /**
     * A unit test suite for JUnit.
     *
     *
     */
    @Test
    void testMapping() throws Exception {
        IReactionProcess type = new RearrangementLonePairReaction();

        IAtomContainerSet setOfReactants = getExampleReactants();
        IAtomContainer molecule = setOfReactants.getAtomContainer(0);
        molecule.addLonePair(new LonePair(molecule.getAtom(0)));

        /* automatic search of the center active */
        List<IParameterReact> paramList = new ArrayList<>();
        IParameterReact param = new SetReactionCenter();
        param.setParameter(Boolean.FALSE);
        paramList.add(param);
        type.setParameterList(paramList);
        /* initiate */

        IReactionSet setOfReactions = type.initiate(setOfReactants, null);

        IAtomContainer product = setOfReactions.getReaction(0).getProducts().getAtomContainer(0);

        Assertions.assertEquals(10, setOfReactions.getReaction(0).getMappingCount());
        IAtom mappedProductA1 = (IAtom) ReactionManipulator.getMappedChemObject(setOfReactions.getReaction(0),
                molecule.getAtom(0));
        Assertions.assertEquals(mappedProductA1, product.getAtom(0));
        mappedProductA1 = (IAtom) ReactionManipulator.getMappedChemObject(setOfReactions.getReaction(0),
                molecule.getAtom(1));
        Assertions.assertEquals(mappedProductA1, product.getAtom(1));
        mappedProductA1 = (IAtom) ReactionManipulator.getMappedChemObject(setOfReactions.getReaction(0),
                molecule.getAtom(2));
        Assertions.assertEquals(mappedProductA1, product.getAtom(2));

    }

    /**
     * Test to recognize if this IAtomContainer_1 matches correctly into the CDKAtomTypes.
     */
    @Test
    void testAtomTypesAtomContainer1() throws Exception {
        IAtomContainer moleculeTest = getExampleReactants().getAtomContainer(0);
        makeSureAtomTypesAreRecognized(moleculeTest);

    }

    /**
     * Test to recognize if this IAtomContainer_2 matches correctly into the CDKAtomTypes.
     */
    @Test
    void testAtomTypesAtomContainer2() throws Exception {
        IAtomContainer moleculeTest = getExpectedProducts().getAtomContainer(0);
        makeSureAtomTypesAreRecognized(moleculeTest);

    }

    /**
     * get the molecule 1: O-C=C-C
     *
     * @cdk.inchi  InChI=1/C3H6O/c1-2-3-4/h2-4H,1H3
     *
     * @return The IAtomContainerSet
     */
    private IAtomContainerSet getExampleReactants() {
        IAtomContainerSet setOfReactants = DefaultChemObjectBuilder.getInstance().newInstance(IAtomContainerSet.class);

        IAtomContainer molecule = builder.newInstance(IAtomContainer.class);
        molecule.addAtom(builder.newInstance(IAtom.class, "O"));
        molecule.addLonePair(new LonePair(molecule.getAtom(0)));
        molecule.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule.addBond(0, 1, IBond.Order.SINGLE);
        molecule.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule.addBond(1, 2, IBond.Order.DOUBLE);
        molecule.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule.addBond(2, 3, IBond.Order.SINGLE);

        try {
            addExplicitHydrogens(molecule);
            AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(molecule);

            lpcheck.saturate(molecule);
        } catch (Exception e) {
            LoggingToolFactory.createLoggingTool(getClass())
                              .error("Unexpected Error:", e);
        }

        setOfReactants.addAtomContainer(molecule);
        return setOfReactants;
    }

    /**
     * Get the expected set of molecules.
     *
     * @return The IAtomContainerSet
     */
    private IAtomContainerSet getExpectedProducts() {
        IAtomContainerSet setOfProducts = builder.newInstance(IAtomContainerSet.class);
        //[O+]=C-[C-]-C

        IAtomContainer molecule = builder.newInstance(IAtomContainer.class);
        molecule.addAtom(builder.newInstance(IAtom.class, "O"));
        molecule.getAtom(0).setFormalCharge(+1);
        molecule.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule.addBond(0, 1, IBond.Order.DOUBLE);
        molecule.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule.getAtom(2).setFormalCharge(-1);
        molecule.addLonePair(new LonePair(molecule.getAtom(2)));
        molecule.addBond(1, 2, IBond.Order.SINGLE);
        molecule.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule.addBond(2, 3, IBond.Order.SINGLE);

        try {
            addExplicitHydrogens(molecule);
            AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(molecule);

            lpcheck.saturate(molecule);

        } catch (Exception e) {
            LoggingToolFactory.createLoggingTool(getClass())
                              .error("Unexpected Error:", e);
        }
        setOfProducts.addAtomContainer(molecule);
        return setOfProducts;
    }

    /**
     * Test to recognize if a IAtomContainer matcher correctly identifies the CDKAtomTypes.
     *
     * @param molecule          The IAtomContainer to analyze
     * @throws CDKException
     */
    private void makeSureAtomTypesAreRecognized(IAtomContainer molecule) throws Exception {

        Iterator<IAtom> atoms = molecule.atoms().iterator();
        CDKAtomTypeMatcher matcher = CDKAtomTypeMatcher.getInstance(molecule.getBuilder());
        while (atoms.hasNext()) {
            IAtom nextAtom = atoms.next();
            Assertions.assertNotNull(matcher.findMatchingAtomType(molecule, nextAtom), "Missing atom type for: " + nextAtom);
        }
    }

    /**
     * A unit test suite for JUnit: Resonance Fluorobenzene  Fc1ccccc1 <=> ...
     *
     * InChI=1/C6H5F/c7-6-4-2-1-3-5-6/h1-5H
     *
     *
     */
    @Test
    void testFluorobenzene() throws Exception {

        IAtomContainer molecule = builder.newInstance(IAtomContainer.class);
        molecule.addAtom(builder.newInstance(IAtom.class, "F"));
        molecule.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule.addBond(0, 1, IBond.Order.SINGLE);
        molecule.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule.addBond(1, 2, IBond.Order.DOUBLE);
        molecule.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule.addBond(2, 3, IBond.Order.SINGLE);
        molecule.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule.addBond(3, 4, IBond.Order.DOUBLE);
        molecule.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule.addBond(4, 5, IBond.Order.SINGLE);
        molecule.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule.addBond(5, 6, IBond.Order.DOUBLE);
        molecule.addBond(6, 1, IBond.Order.SINGLE);

        addExplicitHydrogens(molecule);
        AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(molecule);

        lpcheck.saturate(molecule);

        IReactionProcess type = new RearrangementLonePairReaction();

        IAtomContainerSet setOfReactants = DefaultChemObjectBuilder.getInstance().newInstance(IAtomContainerSet.class);
        setOfReactants.addAtomContainer(molecule);
        /* automatic search of the center active */
        List<IParameterReact> paramList = new ArrayList<>();
        IParameterReact param = new SetReactionCenter();
        param.setParameter(Boolean.FALSE);
        paramList.add(param);
        type.setParameterList(paramList);
        /* initiate */

        IReactionSet setOfReactions = type.initiate(setOfReactants, null);
        Assertions.assertEquals(1, setOfReactions.getReactionCount());
        Assertions.assertEquals(1, setOfReactions.getReaction(0).getProductCount());
        IAtomContainer product1 = setOfReactions.getReaction(0).getProducts().getAtomContainer(0);

        IAtomContainer molecule1 = builder.newInstance(IAtomContainer.class);
        molecule1.addAtom(builder.newInstance(IAtom.class, "F"));
        molecule1.getAtom(0).setFormalCharge(1);
        molecule1.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule1.addBond(0, 1, IBond.Order.DOUBLE);
        molecule1.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule1.getAtom(2).setFormalCharge(-1);
        molecule1.addBond(1, 2, IBond.Order.SINGLE);
        molecule1.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule1.addBond(2, 3, IBond.Order.SINGLE);
        molecule1.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule1.addBond(3, 4, IBond.Order.DOUBLE);
        molecule1.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule1.addBond(4, 5, IBond.Order.SINGLE);
        molecule1.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule1.addBond(5, 6, IBond.Order.DOUBLE);
        molecule1.addBond(6, 1, IBond.Order.SINGLE);
        addExplicitHydrogens(molecule1);
        AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(molecule1);
        lpcheck.saturate(molecule1);

        assertEquals(molecule1, product1);
    }
}
