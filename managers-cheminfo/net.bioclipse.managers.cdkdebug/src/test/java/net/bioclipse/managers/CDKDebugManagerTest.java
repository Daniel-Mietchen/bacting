/* Copyright (c) 2020  Egon Willighagen <egon.willighagen@gmail.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contact: http://www.bioclipse.net/
 */
package net.bioclipse.managers;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.bioclipse.cdk.domain.ICDKMolecule;
import net.bioclipse.core.business.BioclipseException;
import net.bioclipse.core.domain.IMolecule;
import net.bioclipse.managers.cdkdebug.CDKDebugManager;

public class CDKDebugManagerTest {

	static CDKManager cdk;
	static CDKDebugManager cdx;
	static String workspaceRoot;

	@BeforeAll
	static void setupManager() throws Exception {
		workspaceRoot = Files.createTempDirectory("cdxtestws").toString();
		cdx = new CDKDebugManager(workspaceRoot);
		cdk = new CDKManager(workspaceRoot);
	}

	@Test
	public void testDOIs() {
		List<String> dois = cdx.doi();
		assertNotNull(dois);
		assertSame(3, dois.size());
	}

	@Test
	public void testManagerName() {
		assertSame("cdx", cdx.getManagerName());
	}

	@Test
	public void testPerceiveCDKAtomTypes() throws Exception {
		IMolecule mol = cdk.fromSMILES("COC");
		String types = cdx.perceiveCDKAtomTypes(mol);
		assertNotNull(types);
		assertTrue(types.contains("C.sp3"));
	}

	@Test
	public void testPerceiveCDKAtomTypes_FakeIMolecule() throws Exception {
		IMolecule mol = new IMolecule() {
			public <T> T getAdapter(Class<T> adapter) { return null; }
			public void setResource(IResource resource) {}
			public String getUID() { return null; }
			public IResource getResource() { return null; }
			public String toSMILES() throws BioclipseException { return null; }
			public String toCML() throws BioclipseException { return null; }
			public List<IMolecule> getConformers() { return null; }
		};
		assertThrows(NullPointerException.class, () -> {
			cdx.perceiveCDKAtomTypes(mol);
		});
	}

	@Test
	public void testPerceiveCDKAtomTypes_FakeIMolecule2() throws Exception {
		IMolecule mol = new IMolecule() {
			public <T> T getAdapter(Class<T> adapter) { return null; }
			public void setResource(IResource resource) {}
			public String getUID() { return null; }
			public IResource getResource() { return null; }
			public String toSMILES() throws BioclipseException { throw new BioclipseException("No, I'm fake."); }
			public String toCML() throws BioclipseException { return null; }
			public List<IMolecule> getConformers() { return null; }
		};
		InvocationTargetException exception = assertThrows(InvocationTargetException.class, () -> {
			cdx.perceiveCDKAtomTypes(mol);
		});
		assertTrue(exception.getMessage().contains("Error while creating a ICDKMolecule"));
	}

    @Test
    public void testDiff() throws Exception {
        ICDKMolecule mol1 = (ICDKMolecule)cdk.fromSMILES("C");
        ICDKMolecule mol2 = (ICDKMolecule)cdk.fromSMILES("C");
        cdx.diff(mol1, mol2);
    }

}
