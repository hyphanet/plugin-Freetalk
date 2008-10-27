/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.FMSPlugin;

import java.util.Set;

import freenet.support.Executor;

/**
 * @author saces, xor
 *
 */
public abstract class FMSIdentityManager {

	private final Executor mExecutor;

	public FMSIdentityManager(Executor newExecutor) {
		mExecutor = newExecutor;

	}

}
