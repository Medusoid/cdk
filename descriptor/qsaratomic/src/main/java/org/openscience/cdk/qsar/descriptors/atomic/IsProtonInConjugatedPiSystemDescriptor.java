/* Copyright (C) 2004-2007  The Chemistry Development Kit (CDK) project
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
package org.openscience.cdk.qsar.descriptors.atomic;

import java.util.Iterator;
import java.util.List;

import org.openscience.cdk.aromaticity.Aromaticity;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.graph.invariant.ConjugatedPiSystemsDetector;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IAtomContainerSet;
import org.openscience.cdk.interfaces.IElement;
import org.openscience.cdk.qsar.AbstractAtomicDescriptor;
import org.openscience.cdk.qsar.DescriptorSpecification;
import org.openscience.cdk.qsar.DescriptorValue;
import org.openscience.cdk.qsar.IAtomicDescriptor;
import org.openscience.cdk.qsar.result.BooleanResult;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;

/**
 *  This class evaluates if a proton is joined to a conjugated system.
 *
 * <table border="1"><caption>Parameters for this descriptor:</caption>
 *   <tr>
 *     <td>Name</td>
 *     <td>Default</td>
 *     <td>Description</td>
 *   </tr>
 *   <tr>
 *     <td>checkAromaticity</td>
 *     <td>false</td>
 *     <td>True is the aromaticity has to be checked</td>
 *   </tr>
 * </table>
 *
 * @author      mfe4
 * @cdk.created 2004-11-03
 * @cdk.dictref qsar-descriptors:isProtonInConjugatedPiSystem
 */
public class IsProtonInConjugatedPiSystemDescriptor extends AbstractAtomicDescriptor implements IAtomicDescriptor {

    private static final String[] NAMES            = {"protonInConjSystem"};
    private boolean               checkAromaticity = false;
    private IAtomContainer        acold            = null;
    private IAtomContainerSet     acSet            = null;

    /**
     *  Constructor for the IsProtonInConjugatedPiSystemDescriptor object
     */
    public IsProtonInConjugatedPiSystemDescriptor() {}

    /**
     *  Gets the specification attribute of the
     *  IsProtonInConjugatedPiSystemDescriptor object
     *
     *@return    The specification value
     */
    @Override
    public DescriptorSpecification getSpecification() {
        return new DescriptorSpecification(
                "http://www.blueobelisk.org/ontologies/chemoinformatics-algorithms/#isProtonInConjugatedPiSystem", this
                        .getClass().getName(), "The Chemistry Development Kit");
    }

    /**
     *  Sets the parameters attribute of the IsProtonInConjugatedPiSystemDescriptor
     *  object
     *
     *@param  params            Parameters are an integer (heavy atom position) and a boolean (true if is needed a checkAromaticity)
     *@exception  CDKException  Description of the Exception
     */
    @Override
    public void setParameters(Object[] params) throws CDKException {
        if (params.length > 1) {
            throw new CDKException("IsProtonInConjugatedPiSystemDescriptor only expects one parameters");
        }
        if (!(params[0] instanceof Boolean)) {
            throw new CDKException("The parameter must be of type Boolean");
        }
        checkAromaticity = (Boolean) params[0];
    }

    /**
     *  Gets the parameters attribute of the IsProtonInConjugatedPiSystemDescriptor
     *  object
     *
     *@return    The parameters value
     */
    @Override
    public Object[] getParameters() {
        // return the parameters as used for the descriptor calculation
        Object[] params = new Object[1];
        params[0] = checkAromaticity;
        return params;
    }

    @Override
    public String[] getDescriptorNames() {
        return NAMES;
    }

    /**
     *  The method is a proton descriptor that evaluates if a proton is joined to a conjugated system.
     *
     *@param  atom              The IAtom for which the DescriptorValue is requested
     *@param  atomContainer              AtomContainer
     *@return                   true if the proton is bonded to a conjugated system
     */
    @Override
    public DescriptorValue calculate(IAtom atom, IAtomContainer atomContainer) {
        IAtomContainer clonedAtomContainer;
        try {
            clonedAtomContainer = atomContainer.clone();
        } catch (CloneNotSupportedException e) {
            return new DescriptorValue(getSpecification(), getParameterNames(), getParameters(), new BooleanResult(
                    false), NAMES, e);
        }
        IAtom clonedAtom = clonedAtomContainer.getAtom(atomContainer.indexOf(atom));

        boolean isProtonInPiSystem = false;
        IAtomContainer mol = clonedAtom.getBuilder().newInstance(IAtomContainer.class, clonedAtomContainer);
        if (checkAromaticity) {
            try {
                AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(mol);
                Aromaticity.cdkLegacy().apply(mol);
            } catch (CDKException e) {
                return new DescriptorValue(getSpecification(), getParameterNames(), getParameters(), new BooleanResult(
                        false), NAMES, e);
            }
        }
        if (atom.getAtomicNumber() == IElement.H) {
            if (acold != clonedAtomContainer) {
                acold = clonedAtomContainer;
                acSet = ConjugatedPiSystemsDetector.detect(mol);
            }
            Iterator<IAtomContainer> detected = acSet.atomContainers().iterator();
            List<IAtom> neighboors = mol.getConnectedAtomsList(clonedAtom);
            for (IAtom neighboor : neighboors) {
                while (detected.hasNext()) {
                    IAtomContainer detectedAC = detected.next();
                    if ((detectedAC != null) && (detectedAC.contains(neighboor))) {
                        isProtonInPiSystem = true;
                        break;
                    }
                }
            }
        }
        return new DescriptorValue(getSpecification(), getParameterNames(), getParameters(), new BooleanResult(
                isProtonInPiSystem), NAMES);
    }

    /**
     *  Gets the parameterNames attribute of the
     *  IsProtonInConjugatedPiSystemDescriptor object
     *
     *@return    The parameterNames value
     */
    @Override
    public String[] getParameterNames() {
        String[] params = new String[1];
        params[0] = "checkAromaticity";
        return params;
    }

    /**
     *  Gets the parameterType attribute of the
     *  IsProtonInConjugatedPiSystemDescriptor object
     *
     *@param  name  Description of the Parameter
     *@return       The parameterType value
     */
    @Override
    public Object getParameterType(String name) {
        return true;
    }
}
