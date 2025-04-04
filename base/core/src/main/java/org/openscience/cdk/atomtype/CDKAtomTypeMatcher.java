/* Copyright (C) 2007-2015  Egon Willighagen <egonw@users.sf.net>
 *                    2011  Nimish Gopal <nimishg@ebi.ac.uk>
 *                    2011  Syed Asad Rahman <asad@ebi.ac.uk>
 *                    2011  Gilleain Torrance <gilleain.torrance@gmail.com>
 *
 * Contact: cdk-devel@lists.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, version 2.1.
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
package org.openscience.cdk.atomtype;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.config.AtomTypeFactory;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IAtomType;
import org.openscience.cdk.interfaces.IAtomType.Hybridization;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IBond.Order;
import org.openscience.cdk.interfaces.IChemObject;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.interfaces.IElement;
import org.openscience.cdk.interfaces.IPseudoAtom;
import org.openscience.cdk.interfaces.ISingleElectron;
import org.openscience.cdk.isomorphism.matchers.IQueryAtom;
import org.openscience.cdk.isomorphism.matchers.IQueryAtomContainer;
import org.openscience.cdk.ringsearch.RingSearch;
import org.openscience.cdk.tools.ILoggingTool;
import org.openscience.cdk.tools.LoggingToolFactory;
import org.openscience.cdk.tools.manipulator.BondManipulator;

/**
 * Atom Type matcher that perceives atom types as defined in the CDK atom type list
 * <code>org/openscience/cdk/dict/data/cdk-atom-types.owl</code>.
 * If there is not an atom type defined for the tested atom, then NULL
 * is returned.
 *
 * @author         egonw
 * @cdk.created    2007-07-20
 */
public class CDKAtomTypeMatcher implements IAtomTypeMatcher {

    public final static int                                                  REQUIRE_NOTHING            = 1;
    public final static int                                                  REQUIRE_EXPLICIT_HYDROGENS = 2;

    private final AtomTypeFactory                                                  factory;
    private final int                                                              mode;
    
    private final static Object                                              LOCK                       = new Object();

    private static final Map<Integer, Map<IChemObjectBuilder, CDKAtomTypeMatcher>> factories                  = new ConcurrentHashMap<>(5);
    private ILoggingTool logger = LoggingToolFactory.createLoggingTool(CDKAtomTypeMatcher.class);

    private CDKAtomTypeMatcher(IChemObjectBuilder builder, int mode) {
        factory = AtomTypeFactory.getInstance("org/openscience/cdk/dict/data/cdk-atom-types.owl", builder);
        this.mode = mode;
    }

    public static CDKAtomTypeMatcher getInstance(IChemObjectBuilder builder) {
        return getInstance(builder, REQUIRE_NOTHING);
    }

    public static CDKAtomTypeMatcher getInstance(IChemObjectBuilder builder, int mode) {
        synchronized (LOCK) {
            if (!factories.containsKey(mode))
                factories.put(mode, new Hashtable<>(1));
            if (!factories.get(mode).containsKey(builder))
                factories.get(mode).put(builder, new CDKAtomTypeMatcher(builder, mode));
            return factories.get(mode).get(builder);
        }
    }

    /** {@inheritDoc} */
    @Override
    public IAtomType[] findMatchingAtomTypes(IAtomContainer atomContainer) throws CDKException {
        return findMatchingAtomTypes(atomContainer, null);
    }

    private IAtomType[] findMatchingAtomTypes(IAtomContainer atomContainer, RingSearch searcher) throws CDKException {
    	// cache the ring information
    	if (searcher == null) searcher = new RingSearch(atomContainer);
    	// cache atom bonds
    	Map<IAtom, List<IBond>> connectedBonds = new HashMap<>(atomContainer.getAtomCount());
    	for (IBond bond : atomContainer.bonds()) {
    		for (IAtom atom : bond.atoms()) {
    			List<IBond> atomBonds = connectedBonds.get(atom);
    			if (atomBonds == null) {
    				atomBonds = new ArrayList<>(4);
    				connectedBonds.put(atom, atomBonds);
    			}
    			atomBonds.add(bond);
    		}
    	}
    	
        IAtomType[] types = new IAtomType[atomContainer.getAtomCount()];
        int typeCounter = 0;
        for (IAtom atom : atomContainer.atoms()) {
            types[typeCounter] = findMatchingAtomType(atomContainer, atom, searcher, connectedBonds.get(atom));
            typeCounter++;
        }
        return types;
    }

    /** {@inheritDoc} */
    @Override
    public IAtomType findMatchingAtomType(IAtomContainer atomContainer, IAtom atom) throws CDKException {
    	return findMatchingAtomType(atomContainer, atom, null, null);
    }

    private IAtomType findMatchingAtomType(IAtomContainer atomContainer, IAtom atom, RingSearch searcher, List<IBond> connectedBonds) throws CDKException {
        IAtomType type;
        if (atomContainer instanceof IQueryAtomContainer || atom instanceof IQueryAtom)
            logger.warn("A query molecule/atom was provided to the atom type matcher");
        if (atom instanceof IPseudoAtom || atom.getAtomicNumber() == null)
            return factory.getAtomType("X");
        switch (atom.getAtomicNumber()) {
            case IElement.C:
                type = perceiveCarbons(atomContainer, atom, searcher, connectedBonds);
                break;
            case IElement.H:
                type = perceiveHydrogens(atomContainer, atom, connectedBonds);
                break;
            case IElement.O:
                type = perceiveOxygens(atomContainer, atom, searcher, connectedBonds);
                break;
            case IElement.N:
                type = perceiveNitrogens(atomContainer, atom, searcher, connectedBonds);
                break;
            case IElement.S:
                type = perceiveSulphurs(atomContainer, atom, searcher, connectedBonds);
                break;
            case IElement.P:
                type = perceivePhosphors(atomContainer, atom, connectedBonds);
                break;
            case IElement.Si:
                type = perceiveSilicon(atomContainer, atom);
                break;
            case IElement.Li:
                type = perceiveLithium(atomContainer, atom);
                break;
            case IElement.B:
                type = perceiveBorons(atomContainer, atom);
                break;
            case IElement.Be:
                type = perceiveBeryllium(atomContainer, atom);
                break;
            case IElement.Cr:
                type = perceiveChromium(atomContainer, atom);
                break;
            case IElement.Se:
                type = perceiveSelenium(atomContainer, atom, connectedBonds);
                break;
            case IElement.Mo:
                type = perceiveMolybdenum(atomContainer, atom);
                break;
            case IElement.Rb:
                type = perceiveRubidium(atomContainer, atom);
                break;
            case IElement.Te:
                type = perceiveTellurium(atomContainer, atom);
                break;
            case IElement.Cu:
                type = perceiveCopper(atomContainer, atom);
                break;
            case IElement.Ba:
                type = perceiveBarium(atomContainer, atom);
                break;
            case IElement.Ga:
                type = perceiveGallium(atomContainer, atom);
                break;
            case IElement.Ru:
                type = perceiveRuthenium(atomContainer, atom);
                break;
            case IElement.Zn:
                type = perceiveZinc(atomContainer, atom);
                break;
            case IElement.Al:
                type = perceiveAluminium(atomContainer, atom);
                break;
            case IElement.Ni:
                type = perceiveNickel(atomContainer, atom);
                break;
            case IElement.Gd:
                type = perceiveGadolinum(atomContainer, atom);
                break;
            case IElement.Ge:
                type = perceiveGermanium(atomContainer, atom);
                break;
            case IElement.Co:
                type = perceiveCobalt(atomContainer, atom);
                break;
            case IElement.Br:
                type = perceiveBromine(atomContainer, atom);
                break;
            case IElement.V:
                type = perceiveVanadium(atomContainer, atom);
                break;
            case IElement.Ti:
                type = perceiveTitanium(atomContainer, atom);
                break;
            case IElement.Sr:
                type = perceiveStrontium(atomContainer, atom);
                break;
            case IElement.Pb:
                type = perceiveLead(atomContainer, atom);
                break;
            case IElement.Tl:
                type = perceiveThallium(atomContainer, atom);
                break;
            case IElement.Sb:
                type = perceiveAntimony(atomContainer, atom);
                break;
            case IElement.Pt:
                type = perceivePlatinum(atomContainer, atom);
                break;
            case IElement.Hg:
                type = perceiveMercury(atomContainer, atom);
                break;
            case IElement.Fe:
                type = perceiveIron(atomContainer, atom);
                break;
            case IElement.Ra:
                type = perceiveRadium(atomContainer, atom);
                break;
            case IElement.Au:
                type = perceiveGold(atomContainer, atom);
                break;
            case IElement.Ag:
                type = perceiveSilver(atomContainer, atom);
                break;
            case IElement.Cl:
                type = perceiveChlorine(atomContainer, atom, connectedBonds);
                break;
            case IElement.In:
                type = perceiveIndium(atomContainer, atom);
                break;
            case IElement.Pu:
                type = perceivePlutonium(atomContainer, atom);
                break;
            case IElement.Th:
                type = perceiveThorium(atomContainer, atom);
                break;
            case IElement.K:
                type = perceivePotassium(atomContainer, atom);
                break;
            case IElement.Mn:
                type = perceiveManganese(atomContainer, atom);
                break;
            case IElement.Mg:
                type = perceiveMagnesium(atomContainer, atom);
                break;
            case IElement.Na:
                type = perceiveSodium(atomContainer, atom);
                break;
            case IElement.As:
                type = perceiveArsenic(atomContainer, atom);
                break;
            case IElement.Cd:
                type = perceiveCadmium(atomContainer, atom);
                break;
            case IElement.Ca:
                type = perceiveCalcium(atomContainer, atom);
                break;
            default:
                type = perceiveHalogens(atomContainer, atom, connectedBonds);
                if (type == null) type = perceiveCommonSalts(atomContainer, atom);
                if (type == null) type = perceiveOrganometallicCenters(atomContainer, atom);
                if (type == null) type = perceiveNobelGases(atomContainer, atom);
                break;
        }

        // if no atom type can be assigned we set the atom type to 'X', this flags
        // to other methods that atom typing was performed but did not yield a match
        if (type == null) {
            type = getAtomType("X");
        }

        return type;
    }

    private IAtomType perceiveGallium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        IBond.Order maxBondOrder = atomContainer.getMaximumBondOrder(atom);
        if (!isCharged(atom) && maxBondOrder == IBond.Order.SINGLE && atomContainer.getConnectedBondsCount(atom) <= 3) {
            IAtomType type = getAtomType("Ga");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (isCharge(atom, 3)) {
            IAtomType type = getAtomType("Ga.3plus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveGermanium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        IBond.Order maxBondOrder = atomContainer.getMaximumBondOrder(atom);
        if (!isCharged(atom) && maxBondOrder == IBond.Order.SINGLE && atomContainer.getConnectedBondsCount(atom) <= 4) {
            IAtomType type = getAtomType("Ge");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        if (isCharge(atom, 0) && atomContainer.getConnectedBondsCount(atom) == 3) {
            IAtomType type = getAtomType("Ge.3");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveSelenium(IAtomContainer atomContainer, IAtom atom, List<IBond> connectedBonds) throws CDKException {
        if (atom.getAtomicNumber() == IElement.Se) {
        	if (connectedBonds == null) connectedBonds = atomContainer.getConnectedBondsList(atom);
        	int doublebondcount = countAttachedDoubleBonds(connectedBonds, atom);
            if (isCharge(atom, 0)) {
                if (atomContainer.getConnectedBondsCount(atom) == 0) {
                    if (atom.getImplicitHydrogenCount() != null && atom.getImplicitHydrogenCount() == 0) {
                        IAtomType type = getAtomType("Se.2");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    } else {
                        IAtomType type = getAtomType("Se.3");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    }
                } else if (atomContainer.getConnectedBondsCount(atom) == 1) {

                    if (doublebondcount == 1) {
                        IAtomType type = getAtomType("Se.1");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    } else if (doublebondcount == 0) {
                        IAtomType type = getAtomType("Se.3");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    }
                } else if (atomContainer.getConnectedBondsCount(atom) == 2) {
                    if (doublebondcount == 0) {
                        IAtomType type = getAtomType("Se.3");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    } else if (doublebondcount == 2) {
                        IAtomType type = getAtomType("Se.sp2.2");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    }
                } else if (atomContainer.getConnectedBondsCount(atom) == 3) {
                    IAtomType type = getAtomType("Se.sp3.3");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else if (atomContainer.getConnectedBondsCount(atom) == 4) {
                    if (doublebondcount == 2) {
                        IAtomType type = getAtomType("Se.sp3.4");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    } else if (doublebondcount == 0) {
                        IAtomType type = getAtomType("Se.sp3d1.4");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    }
                } else if (atomContainer.getConnectedBondsCount(atom) == 5) {
                    IAtomType type = getAtomType("Se.5");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            } else if ((isCharge(atom, 4))
                    && atomContainer.getConnectedBondsCount(atom) == 0) {
                IAtomType type = getAtomType("Se.4plus");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if ((isCharge(atom, 1))
                    && atomContainer.getConnectedBondsCount(atom) == 3) {
                IAtomType type = getAtomType("Se.plus.3");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if ((isCharge(atom, -2))
                    && atomContainer.getConnectedBondsCount(atom) == 0) {
                IAtomType type = getAtomType("Se.2minus");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        }
        return null;
    }

    private IAtomType perceiveTellurium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        IBond.Order maxBondOrder = atomContainer.getMaximumBondOrder(atom);
        if (!isCharged(atom) && maxBondOrder == IBond.Order.SINGLE && atomContainer.getConnectedBondsCount(atom) <= 2) {
            IAtomType type = getAtomType("Te.3");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (isCharge(atom, 4)) {
            if (atomContainer.getConnectedBondsCount(atom) == 0) {
                IAtomType type = getAtomType("Te.4plus");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        }
        return null;
    }

    private IAtomType perceiveBorons(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        IBond.Order maxBondOrder = atomContainer.getMaximumBondOrder(atom);
        if (isCharge(atom, -1) && maxBondOrder == IBond.Order.SINGLE
                && atomContainer.getConnectedBondsCount(atom) <= 4) {
            IAtomType type = getAtomType("B.minus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (isCharge(atom, +3) && atomContainer.getConnectedBondsCount(atom) == 4) {
            IAtomType type = getAtomType("B.3plus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (atomContainer.getConnectedBondsCount(atom) <= 3) {
            IAtomType type = getAtomType("B");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveBeryllium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (isCharge(atom, -2) && atomContainer.getMaximumBondOrder(atom) == IBond.Order.SINGLE
                && atomContainer.getConnectedBondsCount(atom) <= 4) {
            IAtomType type = getAtomType("Be.2minus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (isCharge(atom, 0) && atomContainer.getConnectedBondsCount(atom) == 0) {
            IAtomType type = getAtomType("Be.neutral");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveCarbonRadicals(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (atomContainer.getConnectedBondsCount(atom) == 0) {
            IAtomType type = getAtomType("C.radical.planar");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (atomContainer.getConnectedBondsCount(atom) <= 3) {
            IBond.Order maxBondOrder = atomContainer.getMaximumBondOrder(atom);
            if (maxBondOrder == IBond.Order.SINGLE) {
                IAtomType type = getAtomType("C.radical.planar");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if (maxBondOrder == IBond.Order.DOUBLE) {
                IAtomType type = getAtomType("C.radical.sp2");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if (maxBondOrder == IBond.Order.TRIPLE) {
                IAtomType type = getAtomType("C.radical.sp1");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        }
        return null;
    }

    private IAtomType perceiveCarbons(IAtomContainer atomContainer, IAtom atom,
    		                          RingSearch searcher, List<IBond> connectedBonds) throws CDKException {
    	if (hasOneSingleElectron(atomContainer, atom)) {
            return perceiveCarbonRadicals(atomContainer, atom);
        }
    	if (connectedBonds == null) connectedBonds = atomContainer.getConnectedBondsList(atom);
        // if hybridization is given, use that
        if (hasHybridization(atom) && !isCharged(atom)) {
            if (atom.getHybridization() == Hybridization.SP2) {
                IAtomType type = getAtomType("C.sp2");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (atom.getHybridization() == Hybridization.SP3) {
                IAtomType type = getAtomType("C.sp3");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (atom.getHybridization() == Hybridization.SP1) {
                IBond.Order maxBondOrder = getMaximumBondOrder(connectedBonds);
                if (maxBondOrder == Order.TRIPLE) {
                    IAtomType type = getAtomType("C.sp");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else {
                    IAtomType type = getAtomType("C.allene");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            }
        } else if (isCharged(atom)) {
            if (isCharge(atom, 1)) {
                if (connectedBonds.isEmpty()) {
                    IAtomType type = getAtomType("C.plus.planar");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else {
                	IBond.Order maxBondOrder = getMaximumBondOrder(connectedBonds);
                    if (maxBondOrder == Order.TRIPLE) {
                        IAtomType type = getAtomType("C.plus.sp1");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    } else if (maxBondOrder == Order.DOUBLE) {
                        IAtomType type = getAtomType("C.plus.sp2");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    } else if (maxBondOrder == Order.SINGLE) {
                        IAtomType type = getAtomType("C.plus.planar");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    }
                }
            } else if (isCharge(atom, -1)) {
                IBond.Order maxBondOrder = getMaximumBondOrder(connectedBonds);
                if (maxBondOrder == Order.SINGLE && connectedBonds.size() <= 3) {
                    if (bothNeighborsAreSp2(atom, atomContainer, connectedBonds) && isRingAtom(atom, atomContainer, searcher)) {
                        IAtomType type = getAtomType("C.minus.planar");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    }
                    IAtomType type = getAtomType("C.minus.sp3");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else if (maxBondOrder == Order.DOUBLE
                        && connectedBonds.size() <= 3) {
                    IAtomType type = getAtomType("C.minus.sp2");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else if (maxBondOrder == Order.TRIPLE
                        && connectedBonds.size() <= 1) {
                    IAtomType type = getAtomType("C.minus.sp1");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            }
            return null;
        } else if (atom.getFlag(IChemObject.AROMATIC)) {
            IAtomType type = getAtomType("C.sp2");
            if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
        } else if (hasOneOrMoreSingleOrDoubleBonds(connectedBonds)) {
            IAtomType type = getAtomType("C.sp2");
            if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
        } else if (connectedBonds.size() > 4) {
            // FIXME: I don't perceive carbons with more than 4 connections yet
            return null;
        } else { // OK, use bond order info
            Order maxBondOrder = getMaximumBondOrder(connectedBonds);
            if (maxBondOrder == Order.QUADRUPLE) {
                // WTF??
                return null;
            } else if (maxBondOrder == Order.TRIPLE) {
                IAtomType type = getAtomType("C.sp");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (maxBondOrder == Order.DOUBLE) {
                // OK, one or two double bonds?
                int doubleBondCount = countAttachedDoubleBonds(connectedBonds, atom);
                if (doubleBondCount == 2) {
                    IAtomType type = getAtomType("C.allene");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else if (doubleBondCount == 1) {
                    IAtomType type = getAtomType("C.sp2");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            } else {
                if (hasAromaticBond(connectedBonds)) {
                    IAtomType type = getAtomType("C.sp2");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
                IAtomType type = getAtomType("C.sp3");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        }
        return null;
    }

    private Order getMaximumBondOrder(List<IBond> connectedBonds) {
    	IBond.Order max = IBond.Order.SINGLE;
    	for (IBond bond : connectedBonds) {
            if (bond.getOrder() != null && bond.getOrder().numeric() > max.numeric())
            	max = bond.getOrder();
        }
        return max;
	}

	private boolean hasOneOrMoreSingleOrDoubleBonds(List<IBond> bonds) {
        for (IBond bond : bonds) {
            if (bond.getFlag(IChemObject.SINGLE_OR_DOUBLE)) return true;
        }
        return false;
    }

    private boolean hasOneSingleElectron(IAtomContainer atomContainer, IAtom atom) {
    	if (atomContainer.getSingleElectronCount() == 0) return false;
        for (ISingleElectron iSingleElectron : atomContainer.singleElectrons()) {
            if (iSingleElectron.contains(atom)) return true;
        }
        return false;
    }

    private int countSingleElectrons(IAtomContainer atomContainer, IAtom atom) {
    	// if there are no single electrons at all, then certainly not for any atom
    	if (atomContainer.getSingleElectronCount() == 0) return 0;
        Iterator<ISingleElectron> singleElectrons = atomContainer.singleElectrons().iterator();
        int count = 0;
        while (singleElectrons.hasNext()) {
            if (singleElectrons.next().contains(atom)) count++;
        }
        return count;
    }

    private IAtomType perceiveOxygenRadicals(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (isCharge(atom, 0)) {
            if (atomContainer.getConnectedBondsCount(atom) <= 1) {
                IAtomType type = getAtomType("O.sp3.radical");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        } else if (isCharge(atom, +1)) {
            if (atomContainer.getConnectedBondsCount(atom) == 0) {
                IAtomType type = getAtomType("O.plus.radical");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if (atomContainer.getConnectedBondsCount(atom) <= 2) {
                IBond.Order maxBondOrder = atomContainer.getMaximumBondOrder(atom);
                if (maxBondOrder == IBond.Order.SINGLE) {
                    IAtomType type = getAtomType("O.plus.radical");
                    if (isAcceptable(atom, atomContainer, type)) return type;
                } else if (maxBondOrder == IBond.Order.DOUBLE) {
                    IAtomType type = getAtomType("O.plus.sp2.radical");
                    if (isAcceptable(atom, atomContainer, type)) return type;
                }
            }
        }
        return null;
    }

    private boolean isCharged(IAtom atom) {
        return atom.getFormalCharge() != null && atom.getFormalCharge() != 0;
    }

    private boolean isCharge(IAtom atom, int q) {
        return atom.getFormalCharge() != null ? atom.getFormalCharge() == q : q == 0;
    }

    private boolean hasHybridization(IAtom atom) {
        return atom.getHybridization() != CDKConstants.UNSET;
    }

    private IAtomType perceiveOxygens(IAtomContainer atomContainer, IAtom atom,
    		                          RingSearch searcher, List<IBond> connectedBonds) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            return perceiveOxygenRadicals(atomContainer, atom);
        }

        // if hybridization is given, use that
        if (connectedBonds == null) connectedBonds = atomContainer.getConnectedBondsList(atom);
        if (hasHybridization(atom) && !isCharged(atom)) {
            if (atom.getHybridization() == Hybridization.SP2) {
                int connectedAtomsCount = connectedBonds.size();
                if (connectedAtomsCount == 1) {
                    if (isCarboxylate(atomContainer, atom, connectedBonds)) {
                        IAtomType type = getAtomType("O.sp2.co2");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    } else {
                        IAtomType type = getAtomType("O.sp2");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    }
                } else if (connectedAtomsCount == 2) {
                    IAtomType type = getAtomType("O.planar3");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            } else if (atom.getHybridization() == Hybridization.SP3) {
                IAtomType type = getAtomType("O.sp3");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (atom.getHybridization() == Hybridization.PLANAR3) {
                IAtomType type = getAtomType("O.planar3");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if (isCharged(atom)) {
            if (isCharge(atom, -1) && connectedBonds.size() <= 1) {
                if (isCarboxylate(atomContainer, atom, connectedBonds)) {
                    IAtomType type = getAtomType("O.minus.co2");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else {
                    IAtomType type = getAtomType("O.minus");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            } else if (isCharge(atom, -2) && connectedBonds.size() == 0) {
                IAtomType type = getAtomType("O.minus2");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (isCharge(atom, +1)) {
                if (connectedBonds.size() == 0) {
                    IAtomType type = getAtomType("O.plus");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
                IBond.Order maxBondOrder = getMaximumBondOrder(connectedBonds);
                if (maxBondOrder == Order.DOUBLE) {
                    IAtomType type = getAtomType("O.plus.sp2");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else if (maxBondOrder == Order.TRIPLE) {
                    IAtomType type = getAtomType("O.plus.sp1");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else {
                    IAtomType type = getAtomType("O.plus");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            }
            return null;
        } else if (connectedBonds.size() > 2) {
            // FIXME: I don't perceive carbons with more than 4 connections yet
            return null;
        } else if (connectedBonds.size() == 0) {
            IAtomType type = getAtomType("O.sp3");
            if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
        } else { // OK, use bond order info
            IBond.Order maxBondOrder = getMaximumBondOrder(connectedBonds);
            if (maxBondOrder == Order.DOUBLE) {
                if (isCarboxylate(atomContainer, atom, connectedBonds)) {
                    IAtomType type = getAtomType("O.sp2.co2");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else {
                    IAtomType type = getAtomType("O.sp2");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            } else if (maxBondOrder == Order.SINGLE) {
                int explicitHydrogens = countExplicitHydrogens(atom, connectedBonds);
                int connectedHeavyAtoms = connectedBonds.size() - explicitHydrogens;
                if (connectedHeavyAtoms == 2) {
                    // a O.sp3 which is expected to take part in an aromatic system
                    if (bothNeighborsAreSp2(atom, atomContainer, connectedBonds) && isRingAtom(atom, atomContainer, searcher)) {
                        IAtomType type = getAtomType("O.planar3");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    }
                    IAtomType type = getAtomType("O.sp3");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else {
                    IAtomType type = getAtomType("O.sp3");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            }
        }
        return null;
    }

    private boolean isCarboxylate(IAtomContainer container, IAtom atom, List<IBond> connectedBonds) {
        // assumes that the oxygen only has one neighbor (C=O, or C-[O-])
        if (connectedBonds.size() != 1) return false;
        IAtom carbon = connectedBonds.get(0).getOther(atom);
        if (carbon.getAtomicNumber() != IElement.C) return false;

        List<IBond> carbonBonds = container.getConnectedBondsList(carbon);
        if (carbonBonds.size() < 2) return false;
        int oxygenCount = 0;
        int singleBondedNegativeOxygenCount = 0;
        int doubleBondedOxygenCount = 0;
        for (IBond cBond : carbonBonds) {
            IAtom neighbor = cBond.getOther(carbon);
            if (neighbor.getAtomicNumber() == IElement.O) {
                oxygenCount++;
                IBond.Order order = cBond.getOrder();
                Integer charge = neighbor.getFormalCharge();
                if (order == IBond.Order.SINGLE && charge != null && charge == -1) {
                    singleBondedNegativeOxygenCount++;
                } else if (order == IBond.Order.DOUBLE) {
                    doubleBondedOxygenCount++;
                }
            }
        }
        return (oxygenCount == 2) && (singleBondedNegativeOxygenCount == 1) && (doubleBondedOxygenCount == 1);
    }

    private boolean atLeastTwoNeighborsAreSp2(IAtom atom, IAtomContainer atomContainer, List<IBond> connectedBonds) {
    	int count = 0;
    	for (IBond bond : connectedBonds) {
    		if (bond.getOrder() == Order.DOUBLE || bond.isAromatic()) {
    			count++;
    		} else {
    			IAtom nextAtom = bond.getOther(atom);
    			if (nextAtom.getHybridization() != CDKConstants.UNSET &&
    					nextAtom.getHybridization() == Hybridization.SP2) {
    				// OK, it's SP2
    				count++;
    			} else {
    				List<IBond> nextConnectBonds = atomContainer.getConnectedBondsList(nextAtom);
    				if (countAttachedDoubleBonds(nextConnectBonds, nextAtom) > 0) {
    					// OK, it's SP2
    					count++;
    				}
    			}
    		}
    		if (count >= 2) return true;
    	}
    	return false;
    }

    private boolean bothNeighborsAreSp2(IAtom atom, IAtomContainer atomContainer, List<IBond> connectedBonds) {
        return atLeastTwoNeighborsAreSp2(atom, atomContainer, connectedBonds);
    }

    private IAtomType perceiveNitrogenRadicals(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (atomContainer.getConnectedBondsCount(atom) >= 1 && atomContainer.getConnectedBondsCount(atom) <= 2) {
            IBond.Order maxBondOrder = atomContainer.getMaximumBondOrder(atom);
            if (isCharge(atom, +1)) {
                if (maxBondOrder == IBond.Order.DOUBLE) {
                    IAtomType type = getAtomType("N.plus.sp2.radical");
                    if (isAcceptable(atom, atomContainer, type)) return type;
                } else if (maxBondOrder == IBond.Order.SINGLE) {
                    IAtomType type = getAtomType("N.plus.sp3.radical");
                    if (isAcceptable(atom, atomContainer, type)) return type;
                }
            } else if (isCharge(atom, 0)) {
                if (maxBondOrder == IBond.Order.SINGLE) {
                    IAtomType type = getAtomType("N.sp3.radical");
                    if (isAcceptable(atom, atomContainer, type)) return type;
                } else if (maxBondOrder == IBond.Order.DOUBLE) {
                    IAtomType type = getAtomType("N.sp2.radical");
                    if (isAcceptable(atom, atomContainer, type)) return type;
                }
            }
        } else {
            IBond.Order maxBondOrder = atomContainer.getMaximumBondOrder(atom);
            if (isCharge(atom, +1)
                    && maxBondOrder == IBond.Order.SINGLE) {
                IAtomType type = getAtomType("N.plus.sp3.radical");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        }
        return null;
    }

    private IAtomType perceiveMolybdenum(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (isCharge(atom, 0)) {
            int neighbors = atomContainer.getConnectedBondsCount(atom);
            if (neighbors == 4) {
                IAtomType type = getAtomType("Mo.4");
                if (isAcceptable(atom, atomContainer, type)) {
                    return type;
                }
            }
            IAtomType type1 = getAtomType("Mo.metallic");
            if (isAcceptable(atom, atomContainer, type1)) {
                return type1;
            }
        }
        return null;
    }

    private IAtomType perceiveNitrogens(IAtomContainer atomContainer, IAtom atom,
    		                            RingSearch searcher, List<IBond> connectedBonds) throws CDKException {
        // if hybridization is given, use that
        if (hasOneSingleElectron(atomContainer, atom)) {
            return perceiveNitrogenRadicals(atomContainer, atom);
        }

        if (connectedBonds == null) connectedBonds = atomContainer.getConnectedBondsList(atom);
        if (hasHybridization(atom) && !isCharged(atom)) {
            if (atom.getHybridization() == Hybridization.SP1) {
                int neighborCount = connectedBonds.size();
                if (neighborCount > 1) {
                    IAtomType type = getAtomType("N.sp1.2");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else {
                    IAtomType type = getAtomType("N.sp1");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            } else if (atom.getHybridization() == Hybridization.SP2) {
                if (isAmide(atom, atomContainer, connectedBonds)) {
                    IAtomType type = getAtomType("N.amide");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else if (isThioAmide(atom, atomContainer, connectedBonds)) {
                    IAtomType type = getAtomType("N.thioamide");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
                // but an sp2 hyb N might N.sp2 or N.planar3 (pyrrole), so check for the latter
                int neighborCount = connectedBonds.size();
                if (neighborCount == 4 && IBond.Order.DOUBLE == getMaximumBondOrder(connectedBonds)) {
                    IAtomType type = getAtomType("N.oxide");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else if (neighborCount > 1 && bothNeighborsAreSp2(atom, atomContainer, connectedBonds)) {
                    if (isRingAtom(atom, atomContainer, searcher)) {
                        if (neighborCount == 3) {
                            IBond.Order maxOrder = getMaximumBondOrder(connectedBonds);
                            if (maxOrder == IBond.Order.DOUBLE) {
                                IAtomType type = getAtomType("N.sp2.3");
                                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                            } else if (maxOrder == IBond.Order.SINGLE) {
                                IAtomType type = getAtomType("N.planar3");
                                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                            }
                        } else if (neighborCount == 2) {
                            IBond.Order maxOrder = getMaximumBondOrder(connectedBonds);
                            if (maxOrder == IBond.Order.SINGLE) {
                                if (atom.getImplicitHydrogenCount() != CDKConstants.UNSET
                                        && atom.getImplicitHydrogenCount() == 1) {
                                    IAtomType type = getAtomType("N.planar3");
                                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                                } else {
                                    IAtomType type = getAtomType("N.sp2");
                                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                                }
                            } else if (maxOrder == IBond.Order.DOUBLE) {
                                IAtomType type = getAtomType("N.sp2");
                                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                            }
                        }
                    }
                }
                IAtomType type = getAtomType("N.sp2");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (atom.getHybridization() == Hybridization.SP3) {
                IAtomType type = getAtomType("N.sp3");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (atom.getHybridization() == Hybridization.PLANAR3) {
                IBond.Order maxBondOrder = getMaximumBondOrder(connectedBonds);
                if (connectedBonds.size() == 3 && maxBondOrder == Order.DOUBLE
                        && countAttachedDoubleBonds(connectedBonds, atom, "O") == 2) {
                    IAtomType type = getAtomType("N.nitro");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
                IAtomType type = getAtomType("N.planar3");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if (isCharged(atom)) {
            if (isCharge(atom, 1)) {
                IBond.Order maxBondOrder = getMaximumBondOrder(connectedBonds);
                if (maxBondOrder == Order.SINGLE || connectedBonds.size() == 0) {
                    if (atom.getHybridization() == IAtomType.Hybridization.SP2) {
                        IAtomType type = getAtomType("N.plus.sp2");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    }
                    IAtomType type = getAtomType("N.plus");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else if (maxBondOrder == Order.DOUBLE) {
                    int doubleBonds = countAttachedDoubleBonds(connectedBonds, atom);
                    if (doubleBonds == 1) {
                        IAtomType type = getAtomType("N.plus.sp2");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    } else if (doubleBonds == 2) {
                        IAtomType type = getAtomType("N.plus.sp1");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    }
                } else if (maxBondOrder == Order.TRIPLE) {
                    if (connectedBonds.size() == 2) {
                        IAtomType type = getAtomType("N.plus.sp1");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    }
                }
            } else if (isCharge(atom, -1)) {
                IBond.Order maxBondOrder = getMaximumBondOrder(connectedBonds);
                if (maxBondOrder == Order.SINGLE) {
                    if (connectedBonds.size() >= 2 && bothNeighborsAreSp2(atom, atomContainer, connectedBonds)
                            && isRingAtom(atom, atomContainer, searcher)) {
                        IAtomType type = getAtomType("N.minus.planar3");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    } else if (connectedBonds.size() <= 2) {
                        IAtomType type = getAtomType("N.minus.sp3");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    }
                } else if (maxBondOrder == Order.DOUBLE) {
                    if (connectedBonds.size() <= 1) {
                        IAtomType type = getAtomType("N.minus.sp2");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    }
                }
            }
        } else if (connectedBonds.size() > 3) {
            if (connectedBonds.size() == 4 && countAttachedDoubleBonds(connectedBonds, atom) == 1) {
                IAtomType type = getAtomType("N.oxide");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
            return null;
        } else if (connectedBonds.size() == 0) {
            IAtomType type = getAtomType("N.sp3");
            if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
        } else if (hasOneOrMoreSingleOrDoubleBonds(connectedBonds)) {
            int connectedAtoms = connectedBonds.size()
                    + (atom.getImplicitHydrogenCount() == CDKConstants.UNSET ? 0 : atom.getImplicitHydrogenCount());
            if (connectedAtoms == 3) {
                IAtomType type = getAtomType("N.planar3");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
            IAtomType type = getAtomType("N.sp2");
            if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
        } else { // OK, use bond order info
            IBond.Order maxBondOrder = getMaximumBondOrder(connectedBonds);
            if (maxBondOrder == Order.SINGLE) {
                if (isAmide(atom, atomContainer, connectedBonds)) {
                    IAtomType type = getAtomType("N.amide");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else if (isThioAmide(atom, atomContainer, connectedBonds)) {
                    IAtomType type = getAtomType("N.thioamide");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }

                List<IBond> heavy = heavyBonds(connectedBonds);

                int expHCount = heavy.size() - connectedBonds.size();

                if (heavy.size() == 2) {

                    if (heavy.get(0).getFlag(IChemObject.AROMATIC) && heavy.get(1).getFlag(IChemObject.AROMATIC)) {

                        int hCount = atom.getImplicitHydrogenCount() != null ? atom.getImplicitHydrogenCount()
                                + expHCount : expHCount;
                        if (hCount == 0) {
                            if (maxBondOrder == Order.SINGLE
                                    && isSingleHeteroAtom(atom, atomContainer)) {
                                IAtomType type = getAtomType("N.planar3");
                                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                            } else {
                                IAtomType type = getAtomType("N.sp2");
                                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                            }
                        } else if (hCount == 1) {
                            IAtomType type = getAtomType("N.planar3");
                            if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                        }
                    } else if (bothNeighborsAreSp2(atom, atomContainer, connectedBonds) && isRingAtom(atom, atomContainer, searcher)) {
                        // a N.sp3 which is expected to take part in an aromatic system
                        IAtomType type = getAtomType("N.planar3");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    } else {
                        IAtomType type = getAtomType("N.sp3");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    }
                } else if (heavy.size() == 3) {
                    if (bothNeighborsAreSp2(atom, atomContainer, connectedBonds) && isRingAtom(atom, atomContainer, searcher)) {
                        IAtomType type = getAtomType("N.planar3");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    }
                    IAtomType type = getAtomType("N.sp3");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else if (heavy.size() == 1) {
                    IAtomType type = getAtomType("N.sp3");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else if (heavy.size() == 0) {
                    IAtomType type = getAtomType("N.sp3");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            } else if (maxBondOrder == Order.DOUBLE) {
                if (connectedBonds.size() == 3
                        && countAttachedDoubleBonds(connectedBonds, atom, "O") == 2) {
                    IAtomType type = getAtomType("N.nitro");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else if (connectedBonds.size() == 3
                        && countAttachedDoubleBonds(connectedBonds, atom) > 0) {
                    IAtomType type = getAtomType("N.sp2.3");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
                IAtomType type = getAtomType("N.sp2");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (maxBondOrder == Order.TRIPLE) {
                int neighborCount = connectedBonds.size();
                if (neighborCount > 1) {
                    IAtomType type = getAtomType("N.sp1.2");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else {
                    IAtomType type = getAtomType("N.sp1");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            }
        }
        return null;
    }

    /**
     * Determines whether the bonds (up to two spheres away) are only to non
     * hetroatoms. Currently used in N.planar3 perception of (e.g. pyrrole).
     *
     * @param atom an atom to test
     * @param container container of the atom
     *
     * @return whether the atom's only bonds are to heteroatoms
     * @see #perceiveNitrogens(IAtomContainer, IAtom, RingSearch, List)
     */
    private boolean isSingleHeteroAtom(IAtom atom, IAtomContainer container) {

        List<IAtom> connected = container.getConnectedAtomsList(atom);

        for (IAtom atom1 : connected) {

            boolean aromatic = container.getBond(atom, atom1).isAromatic();

            // ignoring non-aromatic bonds
            if (!aromatic) continue;

            // found a hetroatom - we're not a single hetroatom
            if (atom1.getAtomicNumber() != IElement.C) return false;

            // check the second sphere
            for (IAtom atom2 : container.getConnectedAtomsList(atom1)) {

                if (!atom2.equals(atom) && container.getBond(atom1, atom2).isAromatic()
                        && atom2.getAtomicNumber() != IElement.C) {
                    return false;
                }

            }

        }

        return true;

    }

    private boolean isRingAtom(IAtom atom, IAtomContainer atomContainer, RingSearch searcher) {
        if (searcher == null) searcher = new RingSearch(atomContainer);
        return searcher.cyclic(atom);
    }

    private boolean isAmide(IAtom atom, IAtomContainer atomContainer, List<IBond> connectedBonds) {
    	if (connectedBonds.size() < 1) return false;
        for (IBond bond : connectedBonds) {
        	IAtom neighbor = bond.getOther(atom);
            if (neighbor.getAtomicNumber() == IElement.C) {
                if (countAttachedDoubleBonds(atomContainer.getConnectedBondsList(neighbor), neighbor, "O") == 1) return true;
            }
        }
        return false;
    }

    private boolean isThioAmide(IAtom atom, IAtomContainer atomContainer, List<IBond> connectedBonds) {
    	if (connectedBonds.size() < 1) return false;
        for (IBond bond : connectedBonds) {
        	IAtom neighbor = bond.getOther(atom);
            if (neighbor.getAtomicNumber() == IElement.C) {
                if (countAttachedDoubleBonds(atomContainer.getConnectedBondsList(neighbor), neighbor, "S") == 1) return true;
            }
        }
        return false;
    }

    private int countExplicitHydrogens(IAtom atom, List<IBond> connectedBonds) {
        int count = 0;
        for (IBond bond : connectedBonds) {
        	IAtom aAtom = bond.getOther(atom);
            if (aAtom.getAtomicNumber() == IElement.H) {
                count++;
            }
        }
        return count;
    }

    /**
     * Filter a bond list keeping only bonds between heavy atoms.
     *
     * @param bonds a list of bond
     * @return the bond list only with heavy bonds
     */
    private List<IBond> heavyBonds(final List<IBond> bonds) {
        final List<IBond> heavy = new ArrayList<>(bonds.size());
        for (final IBond bond : bonds) {
            if (!(bond.getBegin().getAtomicNumber() == IElement.H && bond.getEnd().getAtomicNumber() == IElement.H)) {
                heavy.add(bond);
            }
        }
        return heavy;
    }

    private IAtomType perceiveIron(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (atom.getAtomicNumber() == IElement.Fe) {
            if (hasOneSingleElectron(atomContainer, atom)) {
                // no idea how to deal with this yet
                return null;
            } else if ((isCharge(atom, 0))) {
                int neighbors = atomContainer.getConnectedBondsCount(atom);
                if (neighbors == 0) {
                	IAtomType type = getAtomType("Fe.metallic");
                	if (isAcceptable(atom, atomContainer, type)) {
                		return type;
                	}
                } else if (neighbors == 2) {
                    IAtomType type5 = getAtomType("Fe.2");
                    if (isAcceptable(atom, atomContainer, type5)) {
                        return type5;
                    }
                } else if (neighbors == 3) {
                    IAtomType type6 = getAtomType("Fe.3");
                    if (isAcceptable(atom, atomContainer, type6)) {
                        return type6;
                    }
                } else if (neighbors == 4) {
                    IAtomType type7 = getAtomType("Fe.4");
                    if (isAcceptable(atom, atomContainer, type7)) {
                        return type7;
                    }
                } else if (neighbors == 5) {
                    IAtomType type8 = getAtomType("Fe.5");
                    if (isAcceptable(atom, atomContainer, type8)) {
                        return type8;
                    }
                } else if (neighbors == 6) {
                    IAtomType type9 = getAtomType("Fe.6");
                    if (isAcceptable(atom, atomContainer, type9)) {
                        return type9;
                    }
                }
            } else if ((isCharge(atom, 2))) {
                int neighbors = atomContainer.getConnectedBondsCount(atom);
                if (neighbors <= 1) {
                    IAtomType type = getAtomType("Fe.2plus");
                    if (isAcceptable(atom, atomContainer, type)) {
                        return type;
                    }
                }
            } else if ((isCharge(atom, 1))) {
                int neighbors = atomContainer.getConnectedBondsCount(atom);

                if (neighbors == 2) {
                    IAtomType type0 = getAtomType("Fe.plus");
                    if (isAcceptable(atom, atomContainer, type0)) {
                        return type0;
                    }
                }
            } else if ((isCharge(atom, 3))) {
                IAtomType type1 = getAtomType("Fe.3plus");
                if (isAcceptable(atom, atomContainer, type1)) {
                    return type1;
                }
            } else if ((isCharge(atom, -2))) {
                IAtomType type2 = getAtomType("Fe.2minus");
                if (isAcceptable(atom, atomContainer, type2)) {
                    return type2;
                }
            } else if ((isCharge(atom, -3))) {
                IAtomType type3 = getAtomType("Fe.3minus");
                if (isAcceptable(atom, atomContainer, type3)) {
                    return type3;
                }
            } else if ((isCharge(atom, -4))) {
                IAtomType type4 = getAtomType("Fe.4minus");
                if (isAcceptable(atom, atomContainer, type4)) {
                    return type4;
                }
            }
        }
        return null;
    }

    private IAtomType perceiveMercury(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (atom.getAtomicNumber() == IElement.Hg) {
            if (hasOneSingleElectron(atomContainer, atom)) {
                // no idea how to deal with this yet
                return null;
            } else if ((isCharge(atom, -1))) {
                IAtomType type = getAtomType("Hg.minus");
                if (isAcceptable(atom, atomContainer, type)) {
                    return type;
                }
            } else if ((isCharge(atom, 2))) {
                IAtomType type = getAtomType("Hg.2plus");
                if (isAcceptable(atom, atomContainer, type)) {
                    return type;
                }
            } else if ((isCharge(atom, +1))) {
                int neighbors = atomContainer.getConnectedBondsCount(atom);
                if (neighbors <= 1) {
                    IAtomType type = getAtomType("Hg.plus");
                    if (isAcceptable(atom, atomContainer, type)) {
                        return type;
                    }
                }
            } else if ((isCharge(atom, 0))) {
                int neighbors = atomContainer.getConnectedBondsCount(atom);
                if (neighbors == 2) {
                    IAtomType type = getAtomType("Hg.2");
                    if (isAcceptable(atom, atomContainer, type)) {
                        return type;
                    }
                } else if (neighbors == 1) {
                    IAtomType type = getAtomType("Hg.1");
                    if (isAcceptable(atom, atomContainer, type)) {
                        return type;
                    }
                } else if (neighbors == 0) {
                    IAtomType type = getAtomType("Hg.metallic");
                    if (isAcceptable(atom, atomContainer, type)) {
                        return type;
                    }
                }
            }
        }
        return null;
    }

    private IAtomType perceiveSulphurs(IAtomContainer atomContainer, IAtom atom,
    		                           RingSearch searcher, List<IBond> connectedBonds) throws CDKException {
        if (connectedBonds == null) connectedBonds = atomContainer.getConnectedBondsList(atom);
        IBond.Order maxBondOrder = getMaximumBondOrder(connectedBonds);
        int neighborcount = connectedBonds.size();
        if (hasOneSingleElectron(atomContainer, atom)) {
            // no idea how to deal with this yet
            return null;
        } else if (atom.getHybridization() != CDKConstants.UNSET && atom.getHybridization() == Hybridization.SP2
                && isCharge(atom, +1)) {
            if (neighborcount == 3) {
                IAtomType type = getAtomType("S.inyl.charged");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else {
                IAtomType type = getAtomType("S.plus");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if (isCharged(atom)) {

            if (isCharge(atom, -1) && neighborcount == 1) {
                IAtomType type = getAtomType("S.minus");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (isCharge(atom, +1) && neighborcount == 2) {
                IAtomType type = getAtomType("S.plus");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (isCharge(atom, +1) && neighborcount == 3) {
                IAtomType type = getAtomType("S.inyl.charged");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (isCharge(atom, +2) && neighborcount == 4) {
                IAtomType type = getAtomType("S.onyl.charged");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (isCharge(atom, -2) && neighborcount == 0) {
                IAtomType type = getAtomType("S.2minus");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if (neighborcount == 0) {
            if (isCharge(atom, 0)) {
                IAtomType type = getAtomType("S.3");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if (neighborcount == 1) {
            if (connectedBonds.get(0).getOrder() == Order.DOUBLE) {
                IAtomType type = getAtomType("S.2");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (connectedBonds.get(0).getOrder() == Order.SINGLE) {
                IAtomType type = getAtomType("S.3");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if (neighborcount == 2) {
            if (bothNeighborsAreSp2(atom, atomContainer, connectedBonds) && isRingAtom(atom, atomContainer, searcher)) {
                if (countAttachedDoubleBonds(connectedBonds, atom) == 2) {
                    IAtomType type = getAtomType("S.inyl.2");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else {
                    IAtomType type = getAtomType("S.planar3");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            } else if (countAttachedDoubleBonds(connectedBonds, atom, "O") == 2) {
                IAtomType type = getAtomType("S.oxide");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (countAttachedDoubleBonds(connectedBonds, atom) == 2) {
                IAtomType type = getAtomType("S.inyl.2");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (countAttachedDoubleBonds(connectedBonds, atom) <= 1) {
                IAtomType type = getAtomType("S.3");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (countAttachedDoubleBonds(connectedBonds, atom) == 0
                    && countAttachedSingleBonds(connectedBonds, atom) == 2) {
                IAtomType type = getAtomType("S.octahedral");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if (neighborcount == 3) {
            int doubleBondedAtoms = countAttachedDoubleBonds(connectedBonds, atom);
            if (doubleBondedAtoms == 1) {
                IAtomType type = getAtomType("S.inyl");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (doubleBondedAtoms == 3) {
                IAtomType type = getAtomType("S.trioxide");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (doubleBondedAtoms == 0) {
                IAtomType type = getAtomType("S.anyl");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if (neighborcount == 4) {
            // count the number of double bonded oxygens
            int doubleBondedOxygens = countAttachedDoubleBonds(connectedBonds, atom, "O");
            int doubleBondedNitrogens = countAttachedDoubleBonds(connectedBonds, atom, "N");
            int doubleBondedSulphurs = countAttachedDoubleBonds(connectedBonds, atom, "S");
            int countAttachedDoubleBonds = countAttachedDoubleBonds(connectedBonds, atom);

            if (doubleBondedOxygens + doubleBondedNitrogens == 2) {
                IAtomType type = getAtomType("S.onyl");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (doubleBondedSulphurs == 1 && doubleBondedOxygens == 1) {
                IAtomType type = getAtomType("S.thionyl");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (maxBondOrder == Order.SINGLE) {
                IAtomType type = getAtomType("S.anyl");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (doubleBondedOxygens == 1 && countAttachedDoubleBonds == 1) {
                IAtomType type = getAtomType("S.sp3d1");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (countAttachedDoubleBonds == 2 && maxBondOrder == Order.DOUBLE) {
                IAtomType type = getAtomType("S.sp3.4");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }

        } else if (neighborcount == 5) {

            if (maxBondOrder == Order.DOUBLE) {

                IAtomType type = getAtomType("S.sp3d1");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (maxBondOrder == Order.SINGLE) {
                IAtomType type = getAtomType("S.octahedral");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if (neighborcount == 6) {
            if (maxBondOrder == Order.SINGLE) {
                IAtomType type = getAtomType("S.octahedral");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        }
        return null;
    }

    private IAtomType perceivePhosphors(IAtomContainer atomContainer, IAtom atom, List<IBond> connectedBonds) throws CDKException {
        if (connectedBonds == null) connectedBonds = atomContainer.getConnectedBondsList(atom);
        int neighborcount = connectedBonds.size();
        IBond.Order maxBondOrder = getMaximumBondOrder(connectedBonds);
        if (countSingleElectrons(atomContainer, atom) == 3) {
            IAtomType type = getAtomType("P.se.3");
            if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
        } else if (hasOneSingleElectron(atomContainer, atom)) {
            // no idea how to deal with this yet
            return null;
        } else if (neighborcount == 0) {
            if (isCharge(atom, 0)) {
                IAtomType type = getAtomType("P.ine");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if (neighborcount == 1) {
            if (isCharge(atom, 0)) {
                IAtomType type = getAtomType("P.ide");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if (neighborcount == 3) {
            int doubleBonds = countAttachedDoubleBonds(connectedBonds, atom);
            if (isCharge(atom, 1)) {
                IAtomType type = getAtomType("P.anium");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (doubleBonds == 1) {
                IAtomType type = getAtomType("P.ate");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else {
                IAtomType type = getAtomType("P.ine");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if (neighborcount == 2) {
            if (maxBondOrder == Order.DOUBLE) {
                if (isCharge(atom, 1)) {
                    IAtomType type = getAtomType("P.sp1.plus");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else {
                    IAtomType type = getAtomType("P.irane");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            } else if (maxBondOrder == Order.SINGLE) {
                IAtomType type = getAtomType("P.ine");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if (neighborcount == 4) {
            // count the number of double bonded oxygens
            int doubleBonds = countAttachedDoubleBonds(connectedBonds, atom);
            if (isCharge(atom, 1) && doubleBonds == 0) {
                IAtomType type = getAtomType("P.ate.charged");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (doubleBonds == 1) {
                IAtomType type = getAtomType("P.ate");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if (neighborcount == 5) {
            if (isCharge(atom, 0)) {
                IAtomType type = getAtomType("P.ane");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        }
        return null;
    }

    private IAtomType perceiveHydrogens(IAtomContainer atomContainer, IAtom atom, List<IBond> connectedBonds) throws CDKException {
    	if (connectedBonds == null) connectedBonds = atomContainer.getConnectedBondsList(atom);
        int neighborcount = connectedBonds.size();
        if (hasOneSingleElectron(atomContainer, atom)) {
            if ((isCharge(atom, 0)) && neighborcount == 0) {
                IAtomType type = getAtomType("H.radical");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
            return null;
        } else if (neighborcount == 2) {
            // FIXME: bridging hydrogen as in B2H6
            return null;
        } else if (neighborcount == 1) {
            if (isCharge(atom, 0)) {
                IAtomType type = getAtomType("H");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if (neighborcount == 0) {
            if (isCharge(atom, 0)) {
                IAtomType type = getAtomType("H");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (isCharge(atom, 1)) {
                IAtomType type = getAtomType("H.plus");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (isCharge(atom, -1)) {
                IAtomType type = getAtomType("H.minus");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        }
        return null;
    }

    private IAtomType perceiveLithium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        int neighborcount = atomContainer.getConnectedBondsCount(atom);
        if (neighborcount == 1) {
            if (isCharge(atom, 0)) {
                IAtomType type = getAtomType("Li");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        } else if (neighborcount == 0) {
            if (isCharge(atom, 0)) {
                IAtomType type = getAtomType("Li.neutral");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
            if (isCharge(atom, +1)) {
                IAtomType type = getAtomType("Li.plus");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        }
        return null;
    }

    private IAtomType perceiveHalogens(IAtomContainer atomContainer, IAtom atom, List<IBond> connectedBonds) throws CDKException {

        if (connectedBonds == null) {
            connectedBonds = atomContainer.getConnectedBondsList(atom);
        }

        if (atom.getAtomicNumber() == IElement.F) {
            if (hasOneSingleElectron(atomContainer, atom)) {
                if (connectedBonds.size() == 0) {
                    if (isCharge(atom, +1)) {
                        IAtomType type = getAtomType("F.plus.radical");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    } else if (isCharge(atom, 0)) {
                        IAtomType type = getAtomType("F.radical");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    }
                } else if (connectedBonds.size() <= 1) {
                    IBond.Order maxBondOrder = getMaximumBondOrder(connectedBonds);
                    if (maxBondOrder == IBond.Order.SINGLE) {
                        IAtomType type = getAtomType("F.plus.radical");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    }
                }
                return null;
            } else if (isCharged(atom)) {
                if (isCharge(atom, -1)) {
                    IAtomType type = getAtomType("F.minus");
                    if (isAcceptable(atom, atomContainer, type)) return type;
                } else if (isCharge(atom, 1)) {
                    IBond.Order maxBondOrder = getMaximumBondOrder(connectedBonds);
                    if (maxBondOrder == IBond.Order.DOUBLE) {
                        IAtomType type = getAtomType("F.plus.sp2");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    } else if (maxBondOrder == IBond.Order.SINGLE) {
                        IAtomType type = getAtomType("F.plus.sp3");
                        if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                    }
                }
            } else if (connectedBonds.size() == 1 || connectedBonds.size() == 0) {
                IAtomType type = getAtomType("F");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if (atom.getAtomicNumber() == IElement.I) {
            return perceiveIodine(atomContainer, atom, connectedBonds);
        }
        return null;
    }

    private IAtomType perceiveArsenic(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            // no idea how to deal with this yet
            return null;
        } else if ((isCharge(atom, +1) && atomContainer
                .getConnectedBondsCount(atom) <= 4)) {
            IAtomType type = getAtomType("As.plus");
            if (isAcceptable(atom, atomContainer, type)) {
                return type;
            }
        } else if ((isCharge(atom, 0))) {
            int neighbors = atomContainer.getConnectedBondsCount(atom);
            if (neighbors == 4) {
                IAtomType type = getAtomType("As.5");
                if (isAcceptable(atom, atomContainer, type)) {
                    return type;
                }
            }
            if (neighbors == 2) {
                IBond.Order maxOrder = getMaximumBondOrder(atomContainer.getConnectedBondsList(atom));
                if (maxOrder == Order.DOUBLE) {
                    IAtomType type = getAtomType("As.2");
                    if (isAcceptable(atom, atomContainer, type)) {
                        return type;
                    }
                } else if (maxOrder == Order.SINGLE) {
                    IAtomType type = getAtomType("As.planar3");
                    if (isAcceptable(atom, atomContainer, type)) {
                        return type;
                    }
                }
            }
            IAtomType type = getAtomType("As");
            if (isAcceptable(atom, atomContainer, type)) {
                return type;
            }
        } else if ((isCharge(atom, +3))) {
            IAtomType type = getAtomType("As.3plus");
            if (isAcceptable(atom, atomContainer, type)) {
                return type;
            }
        } else if ((isCharge(atom, -1))) {
            IAtomType type = getAtomType("As.minus");
            if (isAcceptable(atom, atomContainer, type)) {
                return type;
            }
        }
        return null;
    }

    private IAtomType perceiveThorium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (atom.getAtomicNumber() == IElement.Th) {
            if (isCharge(atom, 0) && atomContainer.getConnectedBondsCount(atom) == 0) {
                IAtomType type = getAtomType("Th");
                if (isAcceptable(atom, atomContainer, type)) {
                    return type;
                }
            }
        }
        return null;
    }

    private IAtomType perceiveRubidium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            return null;
        } else if (isCharge(atom, +1)) {
            IAtomType type = getAtomType("Rb.plus");
            if (isAcceptable(atom, atomContainer, type)) {
                return type;
            }
        } else if (isCharge(atom, 0)) {
            IAtomType type = getAtomType("Rb.neutral");
            if (isAcceptable(atom, atomContainer, type)) {
                return type;
            }
        }
        return null;
    }

    private IAtomType perceiveCommonSalts(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (atom.getAtomicNumber() == IElement.Mg) {
            if (hasOneSingleElectron(atomContainer, atom)) {
                // no idea how to deal with this yet
                return null;
            } else if ((isCharge(atom, +2))) {
                IAtomType type = getAtomType("Mg.2plus");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        } else if (atom.getAtomicNumber() == IElement.Co) {
            if (hasOneSingleElectron(atomContainer, atom)) {
                // no idea how to deal with this yet
                return null;
            } else if ((isCharge(atom, +2))) {
                IAtomType type = getAtomType("Co.2plus");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if ((isCharge(atom, +3))) {
                IAtomType type = getAtomType("Co.3plus");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if ((isCharge(atom, 0))) {
                IAtomType type = getAtomType("Co.metallic");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        } else if (atom.getAtomicNumber() == IElement.W) {
            if (hasOneSingleElectron(atomContainer, atom)) {
                // no idea how to deal with this yet
                return null;
            } else if ((isCharge(atom, 0))) {
                IAtomType type = getAtomType("W.metallic");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        }
        return null;
    }

    private IAtomType perceiveCopper(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            // no idea how to deal with this yet
            return null;
        } else if ((isCharge(atom, +2))) {
            IAtomType type = getAtomType("Cu.2plus");
            if (isAcceptable(atom, atomContainer, type)) {
                return type;
            }
        } else if (isCharge(atom, 0)) {
            int neighbors = atomContainer.getConnectedBondsCount(atom);
            if (neighbors == 1) {
                IAtomType type = getAtomType("Cu.1");
                if (isAcceptable(atom, atomContainer, type)) {
                    return type;
                }
            } else {
                IAtomType type01 = getAtomType("Cu.metallic");
                if (isAcceptable(atom, atomContainer, type01)) {
                    return type01;
                }
            }
        } else if (isCharge(atom, +1)) {
            IAtomType type02 = getAtomType("Cu.plus");
            if (isAcceptable(atom, atomContainer, type02)) {
                return type02;
            }
        }
        return null;
    }

    private IAtomType perceiveBarium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            return null;
        } else if ((isCharge(atom, 2))) {
            IAtomType type = getAtomType("Ba.2plus");
            if (isAcceptable(atom, atomContainer, type)) {
                return type;
            }
        }
        return null;
    }

    private IAtomType perceiveAluminium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (isCharge(atom, 3)) {
            int connectedBondsCount = atomContainer.getConnectedBondsCount(atom);
            if (connectedBondsCount == 0) {
                IAtomType type = getAtomType("Al.3plus");
                if (isAcceptable(atom, atomContainer, type)) {
                    return type;
                }
            }
        } else if (isCharge(atom, 0)
                && atomContainer.getConnectedBondsCount(atom) == 3) {
            IAtomType type = getAtomType("Al");
            if (isAcceptable(atom, atomContainer, type)) {
                return type;
            }
        } else if (isCharge(atom, -3)
                && atomContainer.getConnectedBondsCount(atom) == 6) {
            IAtomType type = getAtomType("Al.3minus");
            if (isAcceptable(atom, atomContainer, type)) {
                return type;
            }
        }
        return null;
    }

    private IAtomType perceiveZinc(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            // no idea how to deal with this yet
            return null;
        } else if (atomContainer.getConnectedBondsCount(atom) == 0
                && (isCharge(atom, 0))) {
            IAtomType type = getAtomType("Zn.metallic");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (atomContainer.getConnectedBondsCount(atom) == 0
                && (isCharge(atom, 2))) {
            IAtomType type = getAtomType("Zn.2plus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (atomContainer.getConnectedBondsCount(atom) == 1
                && (isCharge(atom, 0))) {
            IAtomType type = getAtomType("Zn.1");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (atomContainer.getConnectedBondsCount(atom) == 2
                && (isCharge(atom, 0))) {
            IAtomType type = getAtomType("Zn");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveChromium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (isCharge(atom, 0)
                && atomContainer.getConnectedBondsCount(atom) == 6) {
            IAtomType type = getAtomType("Cr");
            if (isAcceptable(atom, atomContainer, type)) {
                return type;
            }
        } else if (isCharge(atom, 0)
                && atomContainer.getConnectedBondsCount(atom) == 4) {
            IAtomType type = getAtomType("Cr.4");
            if (isAcceptable(atom, atomContainer, type)) {
                return type;
            }
        } else if (isCharge(atom, 6)
                && atomContainer.getConnectedBondsCount(atom) == 0) {
            IAtomType type = getAtomType("Cr.6plus");
            if (isAcceptable(atom, atomContainer, type)) {
                return type;
            }
        } else if (isCharge(atom, 0)
                && atomContainer.getConnectedBondsCount(atom) == 0) {
            IAtomType type = getAtomType("Cr.neutral");
            if (isAcceptable(atom, atomContainer, type)) {
                return type;
            }
        } else if (atom.getAtomicNumber() == IElement.Cr) {
            if (isCharge(atom, 3)
                    && atomContainer.getConnectedBondsCount(atom) == 0) {
                IAtomType type = getAtomType("Cr.3plus");
                if (isAcceptable(atom, atomContainer, type)) {
                    return type;
                }
            }
        }
        return null;
    }

    private IAtomType perceiveOrganometallicCenters(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (atom.getAtomicNumber() == IElement.Po) {
            if (hasOneSingleElectron(atomContainer, atom)) {
                // no idea how to deal with this yet
                return null;
            } else if (atomContainer.getConnectedBondsCount(atom) == 2) {
                IAtomType type = getAtomType("Po");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        } else if (atom.getAtomicNumber() == IElement.Sn) {
            if (hasOneSingleElectron(atomContainer, atom)) {
                // no idea how to deal with this yet
                return null;
            } else if ((isCharge(atom, 0) && atomContainer
                    .getConnectedBondsCount(atom) <= 4)) {
                IAtomType type = getAtomType("Sn.sp3");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        } else if (atom.getAtomicNumber() == IElement.Sc) {
            if (isCharge(atom, -3)
                    && atomContainer.getConnectedBondsCount(atom) == 6) {
                IAtomType type = getAtomType("Sc.3minus");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        }
        return null;
    }

    private IAtomType perceiveNickel(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            // no idea how to deal with this yet
            return null;
        } else if ((isCharge(atom, +2))) {
            IAtomType type = getAtomType("Ni.2plus");
            if (isAcceptable(atom, atomContainer, type)) {
                return type;
            }
        } else if ((isCharge(atom, 0))
                && atomContainer.getConnectedBondsCount(atom) == 2) {
            IAtomType type = getAtomType("Ni");
            if (isAcceptable(atom, atomContainer, type)) {
                return type;
            }
        } else if ((isCharge(atom, 0))
                && atomContainer.getConnectedBondsCount(atom) == 0) {
            IAtomType type = getAtomType("Ni.metallic");
            if (isAcceptable(atom, atomContainer, type)) {
                return type;
            }
        } else if ((isCharge(atom, 1))
                && atomContainer.getConnectedBondsCount(atom) == 1) {
            IAtomType type = getAtomType("Ni.plus");
            if (isAcceptable(atom, atomContainer, type)) {
                return type;
            }
        }
        return null;
    }

    private IAtomType perceiveNobelGases(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (atom.getAtomicNumber() == IElement.He) {
            if (hasOneSingleElectron(atomContainer, atom)) {
                // no idea how to deal with this yet
                return null;
            } else if ((isCharge(atom, 0))) {
                IAtomType type = getAtomType("He");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        } else if (atom.getAtomicNumber() == IElement.Ne) {
            if (hasOneSingleElectron(atomContainer, atom)) {
                // no idea how to deal with this yet
                return null;
            } else if ((isCharge(atom, 0))) {
                IAtomType type = getAtomType("Ne");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        } else if (atom.getAtomicNumber() == IElement.Ar) {
            if (hasOneSingleElectron(atomContainer, atom)) {
                // no idea how to deal with this yet
                return null;
            } else if ((isCharge(atom, 0))) {
                IAtomType type = getAtomType("Ar");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        } else if (atom.getAtomicNumber() == IElement.Kr) {
            if (hasOneSingleElectron(atomContainer, atom)) {
                // no idea how to deal with this yet
                return null;
            } else if ((isCharge(atom, 0))) {
                IAtomType type = getAtomType("Kr");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        } else if (atom.getAtomicNumber() == IElement.Xe) {
            if (hasOneSingleElectron(atomContainer, atom)) {
                // no idea how to deal with this yet
                return null;
            } else if ((isCharge(atom, 0))) {
                if (atomContainer.getConnectedBondsCount(atom) == 0) {
                    IAtomType type = getAtomType("Xe");
                    if (isAcceptable(atom, atomContainer, type)) return type;
                } else {
                    IAtomType type = getAtomType("Xe.3");
                    if (isAcceptable(atom, atomContainer, type)) return type;
                }
            }
        } else if (atom.getAtomicNumber() == IElement.Rn) {
            if (hasOneSingleElectron(atomContainer, atom)) {
                // no idea how to deal with this yet
                return null;
            } else if ((isCharge(atom, 0))) {
                IAtomType type = getAtomType("Rn");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        }
        return null;
    }

    private IAtomType perceiveSilicon(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            // no idea how to deal with this yet
            return null;
        } else if (isCharge(atom, 0)) {
            if (atomContainer.getConnectedBondsCount(atom) == 2) {
                IAtomType type = getAtomType("Si.2");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if (atomContainer.getConnectedBondsCount(atom) == 3) {
                IAtomType type = getAtomType("Si.3");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if (atomContainer.getConnectedBondsCount(atom) == 4) {
                IAtomType type = getAtomType("Si.sp3");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        } else if (isCharge(atom, -2)) {
            IAtomType type = getAtomType("Si.2minus.6");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveManganese(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            // no idea how to deal with this yet
            return null;
        } else if ((isCharge(atom, 0))) {
            int neighbors = atomContainer.getConnectedBondsCount(atom);
            if (neighbors == 2) {
                IAtomType type02 = getAtomType("Mn.2");
                if (isAcceptable(atom, atomContainer, type02)) return type02;
            } else if (neighbors == 0) {
                IAtomType type03 = getAtomType("Mn.metallic");
                if (isAcceptable(atom, atomContainer, type03)) return type03;
            }
        } else if ((isCharge(atom, +2))) {
            IAtomType type = getAtomType("Mn.2plus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if ((isCharge(atom, +3))) {
            IAtomType type = getAtomType("Mn.3plus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveSodium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            // no idea how to deal with this yet
            return null;
        } else if ((isCharge(atom, 1))) {
            IAtomType type = getAtomType("Na.plus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if ((isCharge(atom, 0))
                && atomContainer.getConnectedBondsCount(atom) == 1) {
            IAtomType type = getAtomType("Na");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if ((isCharge(atom, 0))
                && atomContainer.getConnectedBondsCount(atom) == 0) {
            IAtomType type = getAtomType("Na.neutral");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveIodine(IAtomContainer atomContainer, IAtom atom, List<IBond> connectedBonds ) throws CDKException {
    	if (connectedBonds == null) connectedBonds = atomContainer.getConnectedBondsList(atom);
        if (hasOneSingleElectron(atomContainer, atom)) {
            if (connectedBonds.size() == 0) {
                if (isCharge(atom, +1)) {
                    IAtomType type = getAtomType("I.plus.radical");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else if (isCharge(atom, 0)) {
                    IAtomType type = getAtomType("I.radical");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            } else if (connectedBonds.size() <= 1) {
                IBond.Order maxBondOrder = getMaximumBondOrder(connectedBonds);
                if (maxBondOrder == IBond.Order.SINGLE) {
                    IAtomType type = getAtomType("I.plus.radical");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            }
            return null;
        } else if (isCharged(atom)) {
            if (isCharge(atom, -1)) {
                if (connectedBonds.size() == 0) {
                    IAtomType type = getAtomType("I.minus");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else {
                    IAtomType type = getAtomType("I.minus.5");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            } else if (isCharge(atom, 1)) {
                IBond.Order maxBondOrder = getMaximumBondOrder(connectedBonds);
                if (maxBondOrder == IBond.Order.DOUBLE) {
                    IAtomType type = getAtomType("I.plus.sp2");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else if (maxBondOrder == IBond.Order.SINGLE) {
                    IAtomType type = getAtomType("I.plus.sp3");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            }
        } else if (connectedBonds.size() == 3) {
            int doubleBondCount = countAttachedDoubleBonds(connectedBonds, atom);
            if (doubleBondCount == 2) {
                IAtomType type = getAtomType("I.5");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (isCharge(atom, 0)) {
                IAtomType type = getAtomType("I.sp3d2.3");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if (connectedBonds.size() == 2) {
            IBond.Order maxBondOrder = getMaximumBondOrder(connectedBonds);
            if (maxBondOrder == IBond.Order.DOUBLE) {
                IAtomType type = getAtomType("I.3");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if (connectedBonds.size() <= 1) {
            IAtomType type = getAtomType("I");
            if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
        }
        return null;
    }

    private IAtomType perceiveRuthenium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (isCharge(atom, 0)) {
            IAtomType type = getAtomType("Ru.6");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (isCharge(atom, -2)) {
            IAtomType type = getAtomType("Ru.2minus.6");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (isCharge(atom, -3)) {
            IAtomType type = getAtomType("Ru.3minus.6");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceivePotassium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            // no idea how to deal with this yet
            return null;
        } else if ((isCharge(atom, +1))) {
            IAtomType type = getAtomType("K.plus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (isCharge(atom, 0)) {
            int neighbors = atomContainer.getConnectedBondsCount(atom);
            if (neighbors == 1) {
                IAtomType type = getAtomType("K.neutral");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
            IAtomType type = getAtomType("K.metallic");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceivePlutonium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (isCharge(atom, 0) && atomContainer.getConnectedBondsCount(atom) == 0) {
            IAtomType type = getAtomType("Pu");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveCadmium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            // no idea how to deal with this yet
            return null;
        } else if ((isCharge(atom, +2))) {
            IAtomType type = getAtomType("Cd.2plus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if ((isCharge(atom, 0))) {
            if (atomContainer.getConnectedBondsCount(atom) == 0) {
                IAtomType type = getAtomType("Cd.metallic");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if (atomContainer.getConnectedBondsCount(atom) == 2) {
                IAtomType type = getAtomType("Cd.2");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        }
        return null;
    }

    private IAtomType perceiveIndium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (isCharge(atom, 0) && atomContainer.getConnectedBondsCount(atom) == 3) {
            IAtomType type = getAtomType("In.3");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (isCharge(atom, 3) && atomContainer.getConnectedBondsCount(atom) == 0) {
            IAtomType type = getAtomType("In.3plus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (isCharge(atom, 0) && atomContainer.getConnectedBondsCount(atom) == 1) {
            IAtomType type = getAtomType("In.1");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else {
            IAtomType type = getAtomType("In");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveChlorine(IAtomContainer atomContainer, IAtom atom, List<IBond> connectedBonds) throws CDKException {
    	if (connectedBonds == null) connectedBonds = atomContainer.getConnectedBondsList(atom);
        if (hasOneSingleElectron(atomContainer, atom)) {
            if (connectedBonds.size() > 1) {
                if (isCharge(atom, +1)) {
                    IAtomType type = getAtomType("Cl.plus.radical");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            } else if (connectedBonds.size() == 1) {
                IBond.Order maxBondOrder = atomContainer.getMaximumBondOrder(atom);
                if (maxBondOrder == IBond.Order.SINGLE) {
                    IAtomType type = getAtomType("Cl.plus.radical");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            } else if (connectedBonds.size() == 0
                    && (isCharge(atom, 0))) {
                IAtomType type = getAtomType("Cl.radical");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if (isCharge(atom, 0)) {
            int neighborcount = connectedBonds.size();
            IBond.Order maxBondOrder = getMaximumBondOrder(connectedBonds);

            if (maxBondOrder == IBond.Order.DOUBLE) {
                if (neighborcount == 2) {
                    IAtomType type = getAtomType("Cl.2");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else if (neighborcount == 3) {
                    IAtomType type = getAtomType("Cl.chlorate");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                } else if (neighborcount == 4) {
                    IAtomType type = getAtomType("Cl.perchlorate");
                    if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
                }
            } else if (neighborcount <= 1) {
                IAtomType type = getAtomType("Cl");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if ((isCharge(atom, -1))) {
            IAtomType type = getAtomType("Cl.minus");
            if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
        } else if (isCharge(atom, 1)) {
            IBond.Order maxBondOrder = getMaximumBondOrder(connectedBonds);
            if (maxBondOrder == IBond.Order.DOUBLE) {
                IAtomType type = getAtomType("Cl.plus.sp2");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (maxBondOrder == IBond.Order.SINGLE) {
                IAtomType type = getAtomType("Cl.plus.sp3");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        } else if ((isCharge(atom, +3))
                && connectedBonds.size() == 4) {
            IAtomType type = getAtomType("Cl.perchlorate.charged");
            if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
        } else {
            int doubleBonds = countAttachedDoubleBonds(connectedBonds, atom);
            if (connectedBonds.size() == 3 && doubleBonds == 2) {
                IAtomType type = getAtomType("Cl.chlorate");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            } else if (connectedBonds.size() == 4 && doubleBonds == 3) {
                IAtomType type = getAtomType("Cl.perchlorate");
                if (isAcceptable(atom, atomContainer, type, connectedBonds)) return type;
            }
        }
        return null;
    }

    private IAtomType perceiveSilver(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            return null;
        } else if ((isCharge(atom, 0))) {
            int neighbors = atomContainer.getConnectedBondsCount(atom);
            if (neighbors == 1) {
                IAtomType type = getAtomType("Ag.1");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
            IAtomType type = getAtomType("Ag.neutral");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if ((isCharge(atom, 1))) {
            IAtomType type = getAtomType("Ag.plus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveGold(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            return null;
        }
        int neighbors = atomContainer.getConnectedBondsCount(atom);
        if ((isCharge(atom, 0)) && neighbors == 1) {
            IAtomType type = getAtomType("Au.1");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveRadium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            return null;
        } else if ((isCharge(atom, 0))) {
            IAtomType type = getAtomType("Ra.neutral");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveCalcium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (atom.getAtomicNumber() == IElement.Ca) {
            if (hasOneSingleElectron(atomContainer, atom)) {
                // no idea how to deal with this yet
                return null;
            } else if ((isCharge(atom, 2) && atomContainer
                    .getConnectedBondsCount(atom) == 0)) {
                IAtomType type = getAtomType("Ca.2plus");
                if (isAcceptable(atom, atomContainer, type)) {
                    return type;
                }
            } else if ((isCharge(atom, 0) && atomContainer
                    .getConnectedBondsCount(atom) == 2)) {
                IAtomType type = getAtomType("Ca.2");
                if (isAcceptable(atom, atomContainer, type)) {
                    return type;
                }
            } else if ((isCharge(atom, 0) && atomContainer
                    .getConnectedBondsCount(atom) == 1)) {
                IAtomType type = getAtomType("Ca.1");
                if (isAcceptable(atom, atomContainer, type)) {
                    return type;
                }
            }
        }
        return null;
    }

    private IAtomType perceivePlatinum(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            // no idea how to deal with this yet
            return null;
        } else if ((isCharge(atom, +2))) {
            int neighbors = atomContainer.getConnectedBondsCount(atom);
            if (neighbors == 4) {
                IAtomType type = getAtomType("Pt.2plus.4");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else {
                IAtomType type = getAtomType("Pt.2plus");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        } else if ((isCharge(atom, 0))) {
            int neighbors = atomContainer.getConnectedBondsCount(atom);
            if (neighbors == 2) {
                IAtomType type = getAtomType("Pt.2");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if (neighbors == 4) {
                IAtomType type = getAtomType("Pt.4");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if (neighbors == 6) {
                IAtomType type = getAtomType("Pt.6");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        }
        return null;
    }

    private IAtomType perceiveAntimony(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            return null;
        } else if ((isCharge(atom, 0) && atomContainer
                .getConnectedBondsCount(atom) == 3)) {
            IAtomType type = getAtomType("Sb.3");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if ((isCharge(atom, 0) && atomContainer
                .getConnectedBondsCount(atom) == 4)) {
            IAtomType type = getAtomType("Sb.4");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveGadolinum(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (isCharge(atom, +3)
                && atomContainer.getConnectedBondsCount(atom) == 0) {
            IAtomType type = getAtomType("Gd.3plus");
            if (isAcceptable(atom, atomContainer, type)) {
                return type;
            }
        }
        return null;
    }

    private IAtomType perceiveMagnesium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            // no idea how to deal with this yet
            return null;
        } else if ((isCharge(atom, 0))) {
            int neighbors = atomContainer.getConnectedBondsCount(atom);
            if (neighbors == 4) {
                IAtomType type = getAtomType("Mg.neutral");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if (neighbors == 2) {
                IAtomType type = getAtomType("Mg.neutral.2");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if (neighbors == 1) {
                IAtomType type = getAtomType("Mg.neutral.1");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else {
                IAtomType type = getAtomType("Mg.neutral");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        } else if ((isCharge(atom, +2))) {
            IAtomType type = getAtomType("Mg.2plus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveThallium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (isCharge(atom, +1)
                && atomContainer.getConnectedBondsCount(atom) == 0) {
            IAtomType type = getAtomType("Tl.plus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (isCharge(atom, 0)
                && atomContainer.getConnectedBondsCount(atom) == 0) {
            IAtomType type = getAtomType("Tl");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (isCharge(atom, 0)
                && atomContainer.getConnectedBondsCount(atom) == 1) {
            IAtomType type = getAtomType("Tl.1");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveLead(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (isCharge(atom, 0)
                && atomContainer.getConnectedBondsCount(atom) == 0) {
            IAtomType type = getAtomType("Pb.neutral");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (isCharge(atom, 2)
                && atomContainer.getConnectedBondsCount(atom) == 0) {
            IAtomType type = getAtomType("Pb.2plus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (isCharge(atom, 0)
                && atomContainer.getConnectedBondsCount(atom) == 1) {
            IAtomType type = getAtomType("Pb.1");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveStrontium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            return null;
        } else if ((isCharge(atom, 2))) {
            IAtomType type = getAtomType("Sr.2plus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveTitanium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (isCharge(atom, -3)
                && atomContainer.getConnectedBondsCount(atom) == 6) {
            IAtomType type = getAtomType("Ti.3minus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if ((isCharge(atom, 0))
                && atomContainer.getConnectedBondsCount(atom) == 4) {
            IAtomType type = getAtomType("Ti.sp3");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if ((isCharge(atom, 0))
                && atomContainer.getConnectedBondsCount(atom) == 2) {
            IAtomType type = getAtomType("Ti.2");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveVanadium(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (isCharge(atom, -3)
                && atomContainer.getConnectedBondsCount(atom) == 6) {
            IAtomType type = getAtomType("V.3minus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (isCharge(atom, -3)
                && atomContainer.getConnectedBondsCount(atom) == 4) {
            IAtomType type = getAtomType("V.3minus.4");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private IAtomType perceiveBromine(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            if (atomContainer.getConnectedBondsCount(atom) == 0) {
                if (isCharge(atom, +1)) {
                    IAtomType type = getAtomType("Br.plus.radical");
                    if (isAcceptable(atom, atomContainer, type)) return type;
                } else if (isCharge(atom, 0)) {
                    IAtomType type = getAtomType("Br.radical");
                    if (isAcceptable(atom, atomContainer, type)) return type;
                }
            } else if (atomContainer.getConnectedBondsCount(atom) <= 1) {
                IBond.Order maxBondOrder = atomContainer.getMaximumBondOrder(atom);
                if (maxBondOrder == IBond.Order.SINGLE) {
                    IAtomType type = getAtomType("Br.plus.radical");
                    if (isAcceptable(atom, atomContainer, type)) return type;
                }
            }
            return null;
        } else if (isCharge(atom, -1)) {
            IAtomType type = getAtomType("Br.minus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (isCharge(atom, 1)) {
            IBond.Order maxBondOrder = atomContainer.getMaximumBondOrder(atom);
            if (maxBondOrder == IBond.Order.DOUBLE) {
                IAtomType type = getAtomType("Br.plus.sp2");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if (maxBondOrder == IBond.Order.SINGLE) {
                IAtomType type = getAtomType("Br.plus.sp3");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        } else if (atomContainer.getConnectedBondsCount(atom) == 1 || atomContainer.getConnectedBondsCount(atom) == 0) {
            IAtomType type = getAtomType("Br");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if (atomContainer.getConnectedBondsCount(atom) == 3) {
            IAtomType type = getAtomType("Br.3");
            if (isAcceptable(atom, atomContainer, type)) return type;
        }
        return null;
    }

    private int countAttachedDoubleBonds(List<IBond> connectedAtoms, IAtom atom, String symbol) {
        return countAttachedBonds(connectedAtoms, atom, IBond.Order.DOUBLE, symbol);
    }

    private IAtomType perceiveCobalt(IAtomContainer atomContainer, IAtom atom) throws CDKException {
        if (hasOneSingleElectron(atomContainer, atom)) {
            // no idea how to deal with this yet
            return null;
        } else if ((isCharge(atom, +2))) {
            IAtomType type = getAtomType("Co.2plus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if ((isCharge(atom, +3))) {
            IAtomType type = getAtomType("Co.3plus");
            if (isAcceptable(atom, atomContainer, type)) return type;
        } else if ((isCharge(atom, 0))) {
            int neighbors = atomContainer.getConnectedBondsCount(atom);
            if (neighbors == 2) {
                IAtomType type = getAtomType("Co.2");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if (neighbors == 4) {
                IAtomType type = getAtomType("Co.4");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if (neighbors == 6) {
                IAtomType type = getAtomType("Co.6");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if (neighbors == 1) {
                IAtomType type = getAtomType("Co.1");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else {
                IAtomType type = getAtomType("Co.metallic");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        } else if ((isCharge(atom, +1))) {
            int neighbors = atomContainer.getConnectedBondsCount(atom);
            if (neighbors == 2) {
                IAtomType type = getAtomType("Co.plus.2");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if (neighbors == 4) {
                IAtomType type = getAtomType("Co.plus.4");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if (neighbors == 1) {
                IAtomType type = getAtomType("Co.plus.1");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if (neighbors == 6) {
                IAtomType type = getAtomType("Co.plus.6");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else if (neighbors == 5) {
                IAtomType type = getAtomType("Co.plus.5");
                if (isAcceptable(atom, atomContainer, type)) return type;
            } else {
                IAtomType type = getAtomType("Co.plus");
                if (isAcceptable(atom, atomContainer, type)) return type;
            }
        }
        return null;
    }

    private int countAttachedDoubleBonds(List<IBond> connectedBonds, IAtom atom) {
        return countAttachedBonds(connectedBonds, atom, IBond.Order.DOUBLE, null);
    }

    private int countAttachedSingleBonds(List<IBond> connectedBonds, IAtom atom) {
        return countAttachedBonds(connectedBonds, atom, IBond.Order.SINGLE, null);
    }

    private boolean hasAromaticBond(List<IBond> connectedBonds) {
        for (IBond bond : connectedBonds) {
            if (bond.isAromatic()) return true;
        }
        return false;
    }

    /**
     * Count the number of doubly bonded atoms.
     *
     * @param connectedBonds bonds connected to the atom
     * @param atom the atom being looked at
     * @param order the desired bond order of the attached bonds
     * @param symbol If not null, then it only counts the double bonded atoms which
     *               match the given symbol.
     * @return the number of doubly bonded atoms
     */
    private int countAttachedBonds(List<IBond> connectedBonds, IAtom atom, IBond.Order order, String symbol) {
        // count the number of double bonded oxygens
        int neighborcount = connectedBonds.size();
        int doubleBondedAtoms = 0;
        for (int i = neighborcount - 1; i >= 0; i--) {
            IBond bond = connectedBonds.get(i);
            if (bond.getOrder() == order) {
                if (bond.getAtomCount() == 2) {
                    if (symbol != null) {
                        // if other atom is of the given element (by its symbol)
                        if (bond.getOther(atom).getSymbol().equals(symbol)) {
                            doubleBondedAtoms++;
                        }
                    } else {
                        doubleBondedAtoms++;
                    }
                }
            }
        }
        return doubleBondedAtoms;
    }

    private IAtomType getAtomType(String identifier) throws CDKException {
        IAtomType type = factory.getAtomType(identifier);
        return type;
    }

    private boolean isAcceptable(IAtom atom, IAtomContainer container, IAtomType type) {
    	return isAcceptable(atom, container, type, null);
    }

    private boolean isAcceptable(IAtom atom, IAtomContainer container, IAtomType type, List<IBond> connectedBonds) {
    	if (connectedBonds == null) connectedBonds = container.getConnectedBondsList(atom);
        if (mode == REQUIRE_EXPLICIT_HYDROGENS) {
            // make sure no implicit hydrogens were assumed
            int actualContainerCount = connectedBonds.size();
            int requiredContainerCount = type.getFormalNeighbourCount();
            if (actualContainerCount != requiredContainerCount) return false;
        } else if (atom.getImplicitHydrogenCount() != CDKConstants.UNSET) {
            // confirm correct neighbour count
            int connectedAtoms = connectedBonds.size();
            int hCount = atom.getImplicitHydrogenCount();
            int actualNeighbourCount = connectedAtoms + hCount;
            int requiredNeighbourCount = type.getFormalNeighbourCount();
            if (actualNeighbourCount > requiredNeighbourCount) return false;
        }

        // confirm correct bond orders
        IBond.Order typeOrder = type.getMaxBondOrder();
        if (typeOrder != null) {
            for (IBond bond : connectedBonds) {
                IBond.Order order = bond.getOrder();
                if (order != CDKConstants.UNSET && order != IBond.Order.UNSET) {
                    if (BondManipulator.isHigherOrder(order, typeOrder)) return false;
                } else if (bond.getFlag(IChemObject.SINGLE_OR_DOUBLE)) {
                    if (typeOrder != IBond.Order.SINGLE && typeOrder != IBond.Order.DOUBLE) return false;
                } else {
                    return false;
                }
            }
        }

        // confirm correct valency
        if (type.getValency() != CDKConstants.UNSET) {
            double valence = container.getBondOrderSum(atom);
            if (atom.getImplicitHydrogenCount() != null &&
            	atom.getImplicitHydrogenCount() != 0)
                valence += atom.getImplicitHydrogenCount();
            if (valence > type.getValency())
                return false;
        }

        // confirm correct formal charge, really would be easier if we just used an int rather than an Integer :-)
        if (!chargeEqual(atom.getFormalCharge(), type.getFormalCharge())) {
            return false;
        }

        // confirm single electron count
        if (type.getProperty(CDKConstants.SINGLE_ELECTRON_COUNT) != null) {
            int count = countSingleElectrons(container, atom);
            if (count != type.getProperty(CDKConstants.SINGLE_ELECTRON_COUNT, Integer.class))
                return false;
        }

        return true;
    }

    private boolean chargeEqual(Integer a, Integer b) {
        if (a == null)
            return b == null || b == 0;
        if (b == null)
            return a == null || a == 0;
        return a.equals(b);
    }

}
