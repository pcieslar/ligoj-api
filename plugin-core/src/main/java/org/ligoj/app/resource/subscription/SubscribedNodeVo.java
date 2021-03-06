package org.ligoj.app.resource.subscription;

import org.ligoj.app.api.AbstractNodeVo;
import lombok.Getter;
import lombok.Setter;

/**
 * A subscribed node.
 */
@Getter
@Setter
public class SubscribedNodeVo extends AbstractNodeVo {

	/**
	 * Instance of tool proving the expected service.
	 */
	private String refined;
}
