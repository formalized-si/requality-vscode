/*******************************************************************************
 * Copyright (c) 2009, 2011 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.unitesk.requality.vscode.ui.compare.patch;

import com.unitesk.requality.vscode.ui.compare.patch.core.FilePatch2;
import com.unitesk.requality.vscode.ui.compare.patch.api.IFilePatchResult;
import com.unitesk.requality.vscode.ui.compare.patch.api.PatchConfiguration;
import com.unitesk.requality.vscode.ui.resources.IStorage;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

public class FilePatch extends FilePatch2 {

	public FilePatch(IPath oldPath, long oldDate, IPath newPath,
			long newDate) {
		super(oldPath, oldDate, newPath, newDate);
	}

	/** The patch applied to the contents of a storage (IFilePatch.apply). */
	public IFilePatchResult apply(IStorage content,
			PatchConfiguration configuration, IProgressMonitor monitor) {
		return apply(content != null ? Utilities.getReaderCreator(content)
				: null, configuration, monitor);
	}

	@Override
	protected FilePatch2 create(IPath oldPath, long oldDate, IPath newPath,
			long newDate) {
		return new FilePatch(oldPath, oldDate, newPath, newDate);
	}

}
