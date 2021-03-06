package org.ligoj.app.api;

/**
 * A configurable plug-in manage some extra configuration.
 */
@FunctionalInterface
public interface ConfigurablePlugin {

	/**
	 * Return the configuration of given subscription.
	 * 
	 * @param subscription
	 *            the subscription attached to a configurable service or tool.
	 * @return the configuration of given subscription.
	 */
	Object getConfiguration(int subscription) throws Exception; // NOSONAR Every thing could happen

}
