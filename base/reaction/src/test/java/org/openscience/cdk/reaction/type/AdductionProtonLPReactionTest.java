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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IAtomContainerSet;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IChemObject;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.interfaces.IReactionSet;
import org.openscience.cdk.isomorphism.UniversalIsomorphismTester;
import org.openscience.cdk.isomorphism.matchers.IQueryAtomContainer;
import org.openscience.cdk.isomorphism.matchers.QueryAtomContainerCreator;
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
import java.util.List;

/**
 * TestSuite that runs a test for the AdductionProtonLPReactionTest.
 * Generalized Reaction: [X-] + [H+] => X -H.
 *
 */
public class AdductionProtonLPReactionTest extends ReactionProcessTest {

    private final LonePairElectronChecker lpcheck = new LonePairElectronChecker();
    private final IChemObjectBuilder            builder = SilentChemObjectBuilder.getInstance();
    private UniversalIsomorphismTester    uiTester;

    @BeforeEach
    void setUpUITester() {
        uiTester = new UniversalIsomorphismTester();
    }

    /**
     *  The JUnit setup method
     */
    AdductionProtonLPReactionTest() throws Exception {
        setReaction(AdductionProtonLPReaction.class);
    }

    /**
     *  The JUnit setup method
     */
    @Test
    void testAdductionProtonLPReaction() throws Exception {
        IReactionProcess type = new AdductionProtonLPReaction();
        Assertions.assertNotNull(type);
    }

    /**
     * A unit test suite for JUnit for acetaldehyde.
     * Reaction: O=C-C-H => O(H)-C=C.
     * Automatically looks for the active centre.
     *
     * @cdk.inchi InChI=1/C2H4O/c1-2-3/h2H,1H3
     *
     *
     */
    @Test
    @Override
    public void testInitiate_IAtomContainerSet_IAtomContainerSet() throws Exception {

        IReactionProcess type = new AdductionProtonLPReaction();

        /* initiate */
        List<IParameterReact> paramList = new ArrayList<>();
        IParameterReact param = new SetReactionCenter();
        param.setParameter(Boolean.FALSE);
        paramList.add(param);
        type.setParameterList(paramList);
        IReactionSet setOfReactions = type.initiate(getExampleReactants(), null);

        Assertions.assertEquals(1, setOfReactions.getReactionCount());
        Assertions.assertEquals(1, setOfReactions.getReaction(0).getProductCount());

        IAtomContainer product = setOfReactions.getReaction(0).getProducts().getAtomContainer(0);

        IAtomContainer molecule2 = getExpectedProducts().getAtomContainer(0);

        IQueryAtomContainer queryAtom = QueryAtomContainerCreator.createSymbolAndChargeQueryContainer(product);
        Assertions.assertTrue(uiTester.isIsomorph(molecule2, queryAtom));

    }

    /**
     * A unit test suite for JUnit for acetaldehyde.
     * Reaction: O=C-C-H => O(H)-C=C.
     * Manually tests for active centre.
     *
     * @cdk.inchi InChI=1/C2H4O/c1-2-3/h2H,1H3
     *
     *
     */
    @Test
    void testManuallyCentreActive() throws Exception {
        IReactionProcess type = new AdductionProtonLPReaction();
        IAtomContainerSet setOfReactants = getExampleReactants();

        /* manually putting the active center */
        setOfReactants.getAtomContainer(0).getAtom(0).setFlag(IChemObject.REACTIVE_CENTER, true);

        /* initiate */
        List<IParameterReact> paramList = new ArrayList<>();
        IParameterReact param = new SetReactionCenter();
        param.setParameter(Boolean.TRUE);
        paramList.add(param);
        type.setParameterList(paramList);
        IReactionSet setOfReactions = type.initiate(setOfReactants, null);

        Assertions.assertEquals(1, setOfReactions.getReactionCount());
        Assertions.assertEquals(1, setOfReactions.getReaction(0).getProductCount());

        IAtomContainer product = setOfReactions.getReaction(0).getProducts().getAtomContainer(0);

        IAtomContainer molecule2 = getExpectedProducts().getAtomContainer(0);

        IQueryAtomContainer queryAtom = QueryAtomContainerCreator.createSymbolAndChargeQueryContainer(product);
        Assertions.assertTrue(uiTester.isIsomorph(molecule2, queryAtom));

    }

    /**
     * A unit test suite for JUnit.
     *
     * @cdk.inchi InChI=1/C2H4O/c1-2-3/h2H,1H3
     *
     *
     */
    @Test
    void testCDKConstants_REACTIVE_CENTER() throws Exception {
        IReactionProcess type = new AdductionProtonLPReaction();
        IAtomContainerSet setOfReactants = getExampleReactants();

        /* manually putting the active center */
        IAtomContainer molecule = setOfReactants.getAtomContainer(0);
        molecule.getAtom(0).setFlag(IChemObject.REACTIVE_CENTER, true);
        molecule.getAtom(1).setFlag(IChemObject.REACTIVE_CENTER, true);
        molecule.getAtom(2).setFlag(IChemObject.REACTIVE_CENTER, true);
        molecule.getAtom(4).setFlag(IChemObject.REACTIVE_CENTER, true);
        molecule.getBond(0).setFlag(IChemObject.REACTIVE_CENTER, true);
        molecule.getBond(1).setFlag(IChemObject.REACTIVE_CENTER, true);
        molecule.getBond(3).setFlag(IChemObject.REACTIVE_CENTER, true);

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
        Assertions.assertTrue(molecule.getAtom(4).getFlag(IChemObject.REACTIVE_CENTER));
        Assertions.assertTrue(reactant.getAtom(4).getFlag(IChemObject.REACTIVE_CENTER));
        Assertions.assertTrue(molecule.getBond(0).getFlag(IChemObject.REACTIVE_CENTER));
        Assertions.assertTrue(reactant.getBond(0).getFlag(IChemObject.REACTIVE_CENTER));
        Assertions.assertTrue(molecule.getBond(1).getFlag(IChemObject.REACTIVE_CENTER));
        Assertions.assertTrue(reactant.getBond(1).getFlag(IChemObject.REACTIVE_CENTER));
        Assertions.assertTrue(molecule.getBond(3).getFlag(IChemObject.REACTIVE_CENTER));
        Assertions.assertTrue(reactant.getBond(3).getFlag(IChemObject.REACTIVE_CENTER));
    }

    /**
     * A unit test suite for JUnit.
     *
     * @cdk.inchi InChI=1/C2H4O/c1-2-3/h2H,1H3
     *
     *
     */
    @Test
    void testMapping() throws Exception {
        IReactionProcess type = new AdductionProtonLPReaction();

        /* automatic looking for active center */
        List<IParameterReact> paramList = new ArrayList<>();
        IParameterReact param = new SetReactionCenter();
        param.setParameter(Boolean.FALSE);
        paramList.add(param);
        type.setParameterList(paramList);

        /* initiate */
        IAtomContainerSet setOfReactants = getExampleReactants();
        IAtomContainer molecule = setOfReactants.getAtomContainer(0);
        IReactionSet setOfReactions = type.initiate(setOfReactants, null);

        IAtomContainer product = setOfReactions.getReaction(0).getProducts().getAtomContainer(0);

        Assertions.assertEquals(8, setOfReactions.getReaction(0).getMappingCount());

        IAtom mappedProductA0 = (IAtom) ReactionManipulator.getMappedChemObject(setOfReactions.getReaction(0),
                molecule.getAtom(0));
        Assertions.assertEquals(mappedProductA0, product.getAtom(0));

    }

    /**
     * Get the Acetaldehyde structure.
     *
     * @cdk.inchi InChI=1/C2H4O/c1-2-3/h2H,1H3
     *
     * @return The IAtomContainerSet
     */
    private IAtomContainerSet getExampleReactants() {
        IAtomContainerSet setOfReactants = DefaultChemObjectBuilder.getInstance().newInstance(IAtomContainerSet.class);
        IAtomContainer molecule = builder.newInstance(IAtomContainer.class);
        molecule.addAtom(builder.newInstance(IAtom.class, "O"));
        molecule.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule.addBond(0, 1, IBond.Order.DOUBLE);
        molecule.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule.addBond(1, 2, IBond.Order.SINGLE);
        molecule.addAtom(builder.newInstance(IAtom.class, "H"));
        molecule.addAtom(builder.newInstance(IAtom.class, "H"));
        molecule.addAtom(builder.newInstance(IAtom.class, "H"));
        molecule.addAtom(builder.newInstance(IAtom.class, "H"));
        molecule.addBond(1, 3, IBond.Order.SINGLE);
        molecule.addBond(2, 4, IBond.Order.SINGLE);
        molecule.addBond(2, 5, IBond.Order.SINGLE);
        molecule.addBond(2, 6, IBond.Order.SINGLE);

        try {
            AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(molecule);

            lpcheck.saturate(molecule);
        } catch (CDKException e) {
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
        IAtomContainer molecule = builder.newInstance(IAtomContainer.class);
        molecule.addAtom(builder.newInstance(IAtom.class, "O"));
        molecule.getAtom(0).setFormalCharge(1);
        molecule.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule.addBond(0, 1, IBond.Order.DOUBLE);
        molecule.addAtom(builder.newInstance(IAtom.class, "C"));
        molecule.addBond(1, 2, IBond.Order.SINGLE);
        molecule.addAtom(builder.newInstance(IAtom.class, "H"));
        molecule.addAtom(builder.newInstance(IAtom.class, "H"));
        molecule.addAtom(builder.newInstance(IAtom.class, "H"));
        molecule.addAtom(builder.newInstance(IAtom.class, "H"));
        molecule.addAtom(builder.newInstance(IAtom.class, "H"));
        molecule.addBond(1, 3, IBond.Order.SINGLE);
        molecule.addBond(2, 4, IBond.Order.SINGLE);
        molecule.addBond(2, 5, IBond.Order.SINGLE);
        molecule.addBond(2, 6, IBond.Order.SINGLE);
        molecule.addBond(0, 7, IBond.Order.SINGLE);
        try {
            AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(molecule);

            lpcheck.saturate(molecule);
        } catch (CDKException e) {
            LoggingToolFactory.createLoggingTool(getClass())
                              .error("Unexpected Error:", e);
        }

        setOfProducts.addAtomContainer(molecule);
        return setOfProducts;
    }
}
