package org.ligoj.app.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test class of {@link NodeIdValidator}
 */
public class NodeIdValidatorTest {

	@Test
	public void invalid() {
		Assertions.assertFalse(new NodeIdValidator().isValid("service", null));
		Assertions.assertFalse(new NodeIdValidator().isValid("service:", null));
		Assertions.assertFalse(new NodeIdValidator().isValid("service:a:", null));
		Assertions.assertFalse(new NodeIdValidator().isValid("service:a-:", null));
		Assertions.assertFalse(new NodeIdValidator().isValid("service:a--b:", null));
		Assertions.assertFalse(new NodeIdValidator().isValid("service:a:B", null));
		Assertions.assertFalse(new NodeIdValidator().isValid("1:a:b", null));
		Assertions.assertFalse(new NodeIdValidator().isValid("service:a:bé", null));
		Assertions.assertFalse(new NodeIdValidator().isValid("service:a:b_c", null));
		Assertions.assertFalse(new NodeIdValidator().isValid("", null));
		Assertions.assertFalse(new NodeIdValidator().isValid(null, null));
		Assertions.assertFalse(new NodeIdValidator().isValid(" ", null));
	}

	@Test
	public void valid() {
		// only there for coverage
		new NodeIdValidator().initialize(null);

		// Real tests
		Assertions.assertTrue(new NodeIdValidator().isValid("service:vm:vcloud:some-deep-name", null));
		Assertions.assertTrue(new NodeIdValidator().isValid("service:e", null));
		Assertions.assertTrue(new NodeIdValidator().isValid("service:a:b", null));
		Assertions.assertTrue(new NodeIdValidator().isValid("service:a:b-c-d", null));
		Assertions.assertTrue(new NodeIdValidator().isValid("service:a:b:c:1", null));
	}

}
