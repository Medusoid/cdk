/* Copyright (C) 2008  Miguel Rojasch <miguelrojasch@users.sf.net>
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
 *
 */
package org.openscience.cdk.debug;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openscience.cdk.test.interfaces.AbstractReactionSchemeTest;
import org.openscience.cdk.interfaces.IReactionScheme;

/**
 * Checks the functionality of the {@link DebugReactionScheme}.
 *
 */
class DebugReactionSchemeTest extends AbstractReactionSchemeTest {

    @BeforeAll
    static void setUp() {
        setTestObjectBuilder(DebugReactionScheme::new);
    }

    @Test
    void testDebugReactionScheme() {
        IReactionScheme scheme = new DebugReactionScheme();
        Assertions.assertNotNull(scheme);
    }
}
