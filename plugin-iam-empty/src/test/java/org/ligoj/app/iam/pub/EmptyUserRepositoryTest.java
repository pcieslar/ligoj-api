package org.ligoj.app.iam.pub;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.iam.empty.EmptyIamProvider;
import org.ligoj.app.iam.empty.EmptyUserRepository;

/**
 * Test class of {@link EmptyUserRepository}
 */
public class EmptyUserRepositoryTest {

	@Test
	public void findAll() {
		new EmptyUserRepository().setPassword(null, null);
		Assertions.assertTrue(new EmptyUserRepository().findAll().isEmpty());
	}

	@Test
	public void findAll2() {
		Assertions.assertTrue(new EmptyUserRepository().findAll(null, null, null, null).getContent().isEmpty());
	}

	@Test
	public void findAllBy() {
		Assertions.assertTrue(new EmptyUserRepository().findAllBy("any", "any").isEmpty());
	}

	@Test
	public void authenticate() {
		Assertions.assertTrue(new EmptyUserRepository().authenticate("any", "any"));
	}

	@Test
	public void findByIdNoCache() {
		Assertions.assertEquals("some", new EmptyUserRepository().findByIdNoCache("some").getId());
	}

	@Test
	public void getToken() {
		Assertions.assertEquals("some", new EmptyUserRepository().getToken("some"));
	}

	@Test
	public void getCompanyRepository() {
		Assertions.assertNotNull(new EmptyIamProvider().getConfiguration().getUserRepository().getCompanyRepository());
	}

	@Test
	public void getPeopleInternalBaseDn() {
		Assertions.assertEquals("", new EmptyUserRepository().getPeopleInternalBaseDn());
	}

	@Test
	public void updateUser() {
		new EmptyUserRepository().updateUser(null);
	}

	@Test
	public void move() {
		new EmptyUserRepository().move(null, null);
	}

	@Test
	public void restore() {
		new EmptyUserRepository().restore(null);
	}

	@Test
	public void unlock() {
		new EmptyUserRepository().unlock(null);
	}

	@Test
	public void isolate() {
		new EmptyUserRepository().isolate(null, null);
	}

	@Test
	public void lock() {
		new EmptyUserRepository().lock(null, null);
	}

	@Test
	public void delete() {
		new EmptyUserRepository().delete(null);
	}

	@Test
	public void updateMembership() {
		new EmptyUserRepository().updateMembership(null, null);
	}

	@Test
	public void create() {
		final UserOrg entry = new UserOrg();
		Assertions.assertSame(entry, new EmptyUserRepository().create(entry));
	}

	@Test
	public void toDn() {
		Assertions.assertEquals("", new EmptyUserRepository().toDn(null));
	}
}
