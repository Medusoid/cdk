/* Copyright (C) 2007-2008  Egon Willighagen <egonw@users.sf.net>
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
package org.openscience.cdk.atomtype;

import org.openscience.cdk.config.AtomTypeFactory;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.test.atomtype.AbstractAtomTypeTest;

/**
 * Helper class that all atom type matcher test classes must implement.
 * It keeps track of the atom types which have been tested, to ensure
 * that all atom types are tested.
 *
 * @cdk.bug    1890702
 */
abstract class AbstractCDKAtomTypeTest extends AbstractAtomTypeTest {

    private final static String            ATOMTYPE_LIST = "cdk-atom-types.owl";

    final static AtomTypeFactory factory       = AtomTypeFactory
                                                                 .getInstance("org/openscience/cdk/dict/data/"
                                                                         + ATOMTYPE_LIST,
                                                                         SilentChemObjectBuilder.getInstance());

    @Override
    public String getAtomTypeListName() {
        return ATOMTYPE_LIST;
    }

    @Override
    public AtomTypeFactory getFactory() {
        return factory;
    }

    @Override
    public IAtomTypeMatcher getAtomTypeMatcher(IChemObjectBuilder builder) {
        return CDKAtomTypeMatcher.getInstance(builder);
    }
}
