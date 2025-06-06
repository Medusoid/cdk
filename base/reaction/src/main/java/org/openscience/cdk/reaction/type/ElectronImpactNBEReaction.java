/* Copyright (C) 2008 Miguel Rojas <miguelrojasch@users.sf.net>
 *
 *  Contact: cdk-devel@lists.sourceforge.net
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2.1
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.openscience.cdk.reaction.type;

import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IAtomContainerSet;
import org.openscience.cdk.interfaces.IChemObject;
import org.openscience.cdk.interfaces.IReaction;
import org.openscience.cdk.interfaces.IReactionSet;
import org.openscience.cdk.reaction.IReactionProcess;
import org.openscience.cdk.reaction.ReactionEngine;
import org.openscience.cdk.reaction.ReactionSpecification;
import org.openscience.cdk.reaction.mechanism.RemovingSEofNBMechanism;
import org.openscience.cdk.reaction.type.parameters.IParameterReact;
import org.openscience.cdk.reaction.type.parameters.SetReactionCenter;
import org.openscience.cdk.tools.ILoggingTool;
import org.openscience.cdk.tools.LoggingToolFactory;

import java.util.ArrayList;

/**
 * <p>IReactionProcess which make an electron impact for for Non-Bonding Electron Lost.
 * This reaction type is a representation of the processes which occurs in the mass spectrometer.</p>
 * <p>It is processed by the RemovingSEofNBMechanism class</p>
 *
 *<pre>
 *  IAtomContainerSet setOfReactants = DefaultChemObjectBuilder.getInstance().newAtomContainerSet();
 *  setOfReactants.addAtomContainer(new AtomContainer());
 *  IReactionProcess type = new ElectronImpactNBEReaction();
 *  Object[] params = {Boolean.FALSE};
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
 * @cdk.created    2006-04-01
 * @cdk.dictref    reaction-types:electronImpact
 *
 * @see RemovingSEofNBMechanism
 *
 **/
public class ElectronImpactNBEReaction extends ReactionEngine implements IReactionProcess {

    private static final ILoggingTool logger = LoggingToolFactory.createLoggingTool(ElectronImpactNBEReaction.class);

    /**
     * Constructor of the ElectronImpactNBEReaction object.
     *
     */
    public ElectronImpactNBEReaction() {
        super();
    }

    /**
     * Gets the specification attribute of the ElectronImpactNBEReaction object.
     *
     * @return    The specification value
     */
    @Override
    public ReactionSpecification getSpecification() {
        return new ReactionSpecification(
                "http://almost.cubic.uni-koeln.de/jrg/Members/mrc/reactionDict/reactionDict#ElectronImpactNBE", this
                        .getClass().getName(), "$Id$", "The Chemistry Development Kit");
    }

    /**
     *  Initiate process.
     *  It is needed to call the addExplicitHydrogensToSatisfyValency
     *  from the class tools.HydrogenAdder.
     *
     *
     * @param  reactants         Reactants of the reaction
     * @param  agents            Agents of the reaction (Must be in this case null)
     *
     * @exception  CDKException  Description of the Exception
     */
    @Override
    public IReactionSet initiate(IAtomContainerSet reactants, IAtomContainerSet agents) throws CDKException {

        logger.debug("initiate reaction: ElectronImpactNBEReaction");

        if (reactants.getAtomContainerCount() != 1) {
            throw new CDKException("ElectronImpactNBEReaction only expects one reactant");
        }
        if (agents != null) {
            throw new CDKException("ElectronImpactNBEReaction don't expects agents");
        }

        IReactionSet setOfReactions = reactants.getBuilder().newInstance(IReactionSet.class);
        IAtomContainer reactant = reactants.getAtomContainer(0);

        /*
         * if the parameter hasActiveCenter is not fixed yet, set the active
         * centers
         */
        IParameterReact ipr = super.getParameterClass(SetReactionCenter.class);
        if (ipr != null && !ipr.isSetParameter()) setActiveCenters(reactant);

        for (IAtom atom : reactant.atoms()) {
            if (atom.getFlag(IChemObject.REACTIVE_CENTER) && reactant.getConnectedLonePairsCount(atom) > 0
                    && reactant.getConnectedSingleElectronsCount(atom) == 0) {

                ArrayList<IAtom> atomList = new ArrayList<>();
                atomList.add(atom);
                IAtomContainerSet moleculeSet = reactant.getBuilder().newInstance(IAtomContainerSet.class);
                moleculeSet.addAtomContainer(reactant);
                IReaction reaction = mechanism.initiate(moleculeSet, atomList, null);
                if (reaction == null)
                    continue;
                else
                    setOfReactions.addReaction(reaction);
            }
        }
        return setOfReactions;

    }

    /**
     * set the active center for this molecule. The active center
     * will be heteroatoms which contain at least one group of
     * lone pair electrons.
     *
     * @param reactant The molecule to set the activity
     * @throws CDKException
     */
    private void setActiveCenters(IAtomContainer reactant) throws CDKException {
        for (IAtom atom : reactant.atoms()) {
            if (reactant.getConnectedLonePairsCount(atom) > 0 && reactant.getConnectedSingleElectronsCount(atom) == 0)
                atom.setFlag(IChemObject.REACTIVE_CENTER, true);

        }
    }
}
