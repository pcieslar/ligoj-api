package org.ligoj.app.resource.plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.util.thread.ThreadClassLoaderScope;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.api.PluginException;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Subscription;
import org.mockito.Mockito;

/**
 * Test class of {@link PluginsClassLoader}
 */
public class PluginsClassLoaderTest {

	protected static final String USER_HOME_DIRECTORY = "target/test-classes/home-test";

	@BeforeEach
	public void cleanHome() throws IOException {
		FileUtils.deleteDirectory(
				new File(new File(USER_HOME_DIRECTORY, PluginsClassLoader.HOME_DIR_FOLDER), PluginsClassLoader.EXPORT_DIR));
	}

	@Test
	public void standardHome() throws Exception {
		PluginsClassLoader classLoader = null;
		final String oldHome = System.getProperty("user.home");
		try {
			System.setProperty("user.home", USER_HOME_DIRECTORY);
			classLoader = checkClassLoader();
		} finally {
			System.setProperty("user.home", oldHome);
			IOUtils.closeQuietly(classLoader);
		}
	}

	@Test
	public void safeMode() throws Exception {
		PluginsClassLoader classLoader = null;
		final String old = System.getProperty("app.safe.mode");
		try {
			System.setProperty("app.safe.mode", "true");
			classLoader = new PluginsClassLoader();

			Assertions.assertTrue(classLoader.isSafeMode());

			// Check the home is in the class-path
			final URL homeUrl = classLoader.getURLs()[0];
			Assertions.assertTrue(homeUrl.getFile().endsWith("/"));

			// Check the plug-in is in the class-path
			Assertions.assertEquals(1, classLoader.getURLs().length);
		} finally {
			if (old == null) {
				System.clearProperty("app.safe.mode");
			} else {
				System.setProperty("app.safe.mode", old);
			}
			IOUtils.closeQuietly(classLoader);
		}
	}

	@Test
	public void forcedHome() throws Exception {
		PluginsClassLoader classLoader = null;
		try {
			System.setProperty("ligoj.home", USER_HOME_DIRECTORY + "/.ligoj");
			classLoader = checkClassLoader();
		} finally {
			System.clearProperty("ligoj.home");
			IOUtils.closeQuietly(classLoader);
		}
	}

	@Test
	public void getInstanceNull() {
		Assertions.assertNull(PluginsClassLoader.getInstance());
	}

	@Test
	public void toExtendedVersion() {
		Assertions.assertEquals("Z0000000Z0000000Z0000000Z0000000", PluginsClassLoader.toExtendedVersion(null));
		Assertions.assertEquals("Z0000000Z0000000Z0000000Z0000000", PluginsClassLoader.toExtendedVersion(""));
		Assertions.assertEquals("Z0000001Z0000000Z0000000Z0000000", PluginsClassLoader.toExtendedVersion("1.0"));
		Assertions.assertEquals("Z0000001Z0000002Z0000003Z0000004", PluginsClassLoader.toExtendedVersion("1.2.3.4"));
		Assertions.assertEquals("Z0000012Z0000034Z0000056Z0000789", PluginsClassLoader.toExtendedVersion("12.34.56.789"));
		Assertions.assertEquals("Z0000012Z000003bZ000005AZ0000000", PluginsClassLoader.toExtendedVersion("12.3b.5A"));
	}

	@Test
	public void getInstance() {
		ThreadClassLoaderScope scope = null;
		try {
			scope = new ThreadClassLoaderScope(new URLClassLoader(new URL[0], Mockito.mock(PluginsClassLoader.class)));
			Assertions.assertNotNull(PluginsClassLoader.getInstance());
		} finally {
			IOUtils.closeQuietly(scope);
		}
	}

	@Test
	public void forcedHomeTwice() throws Exception {
		PluginsClassLoader classLoader = null;
		try {
			System.setProperty("ligoj.home", USER_HOME_DIRECTORY + "/.ligoj");
			classLoader = checkClassLoader();
			classLoader = checkClassLoader();
			Assertions.assertNotNull(classLoader.getHomeDirectory());
			Assertions.assertNotNull(classLoader.getPluginDirectory());
		} finally {
			System.clearProperty("ligoj.home");
			IOUtils.closeQuietly(classLoader);
		}
	}

	@Test
	public void toFile() throws Exception {
		PluginsClassLoader classLoader = null;
		try {
			System.setProperty("ligoj.home", USER_HOME_DIRECTORY + "/.ligoj");
			final File file = new File(USER_HOME_DIRECTORY, ".ligoj/service-id/ldap/server1/42/foo/bar.log");
			final File subscriptionParent = new File(USER_HOME_DIRECTORY, ".ligoj/service-id");
			FileUtils.deleteQuietly(subscriptionParent);
			Assertions.assertFalse(subscriptionParent.exists());
			Assertions.assertFalse(file.exists());
			classLoader = checkClassLoader();
			final Subscription subscription = newSubscription();
			final File cfile = classLoader.toFile(subscription, "foo", "bar.log");
			Assertions.assertTrue(subscriptionParent.exists());
			Assertions.assertTrue(cfile.getParentFile().exists());
			Assertions.assertTrue(file.getParentFile().exists());
			Assertions.assertFalse(file.exists());
		} finally {
			System.clearProperty("ligoj.home");
			IOUtils.closeQuietly(classLoader);
		}
	}

	private Subscription newSubscription() {
		Subscription subscription = new Subscription();
		Node node = new Node();
		node.setId("service:id:ldap:server1");
		Node tool = new Node();
		tool.setId("service:id:ldap");
		Node service = new Node();
		service.setId("service:id");
		tool.setRefined(service);
		node.setRefined(tool);
		subscription.setNode(node);
		subscription.setId(42);
		return subscription;
	}

	@Test
	public void copyFailed() {
		final AtomicReference<PluginsClassLoader> refError = new AtomicReference<>();
		try {
			System.setProperty("ligoj.home", USER_HOME_DIRECTORY + "/.ligoj");
			Assertions.assertThrows(PluginException.class, () -> {
				final PluginsClassLoader classLoader = new PluginsClassLoader() {
					@Override
					protected void copy(final Path from, final Path dest) throws IOException {
						throw new IOException();
					}
				};
				refError.set(classLoader);
				classLoader.copyExportedResources("any", null, null);
			});
		} finally {
			IOUtils.closeQuietly(refError.get());
			System.clearProperty("ligoj.home");
		}
	}

	private PluginsClassLoader checkClassLoader() throws IOException {
		final PluginsClassLoader classLoader = new PluginsClassLoader();
		Assertions.assertEquals(3, classLoader.getURLs().length);

		// Check the home is in the class-path
		final URL homeUrl = classLoader.getURLs()[0];
		Assertions.assertTrue(homeUrl.getFile().endsWith("/"));

		// Check the plug-in is in the class-path
		final URL pluginTestUrl = classLoader.getURLs()[1];
		Assertions.assertTrue(pluginTestUrl.getFile().endsWith("plugin-foo-1.0.1.jar"));

		// Check the JAR is readable
		final InputStream pluginTestUrlStream = pluginTestUrl.openStream();
		Assertions.assertNotNull(pluginTestUrlStream);
		IOUtils.closeQuietly(pluginTestUrlStream);

		// Check the content of the plug-in is resolvable from the class loader
		IOUtils.toString(classLoader.getResourceAsStream("home-test/.ligoj/plugins/plugin-foo-1.0.1.jar"), StandardCharsets.UTF_8.name());
		Assertions.assertEquals("FOO", IOUtils.toString(classLoader.getResourceAsStream("plugin-foo.txt"), StandardCharsets.UTF_8.name()));

		final File export = new File(USER_HOME_DIRECTORY + "/.ligoj/export");
		Assertions.assertTrue(export.exists());
		Assertions.assertTrue(export.isDirectory());
		Assertions.assertTrue(new File(export, "export.txt").exists());
		Assertions.assertTrue(new File(export, "export.txt").isFile());
		Assertions.assertEquals("EXPORT", FileUtils.readFileToString(new File(export, "export.txt"), StandardCharsets.UTF_8.name()));
		return classLoader;
	}
}
