package org.ligoj.app.resource.node.sample;

import org.ligoj.app.resource.plugin.AbstractServicePlugin;
import org.springframework.stereotype.Component;



/**
 * The KPI Collection service.
 */
@Component
public class KpiResource extends AbstractServicePlugin {

	/**
	 * Plug-in key.
	 */
	public static final String SERVICE_URL = BASE_URL + "/kpi";

	/**
	 * Plug-in key.
	 */
	public static final String SERVICE_KEY = SERVICE_URL.replace('/', ':').substring(1);

	@Override
	public String getKey() {
		return SERVICE_KEY;
	}
}
