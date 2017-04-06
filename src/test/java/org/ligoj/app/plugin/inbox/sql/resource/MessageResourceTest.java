package org.ligoj.app.plugin.inbox.sql.resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.transaction.Transactional;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.core.UriInfo;

import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.iam.CompanyOrg;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.iam.model.CacheCompany;
import org.ligoj.app.iam.model.CacheGroup;
import org.ligoj.app.iam.model.CacheMembership;
import org.ligoj.app.iam.model.CacheUser;
import org.ligoj.app.iam.model.DelegateOrg;
import org.ligoj.app.model.DelegateNode;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.id.resource.CompanyResource;
import org.ligoj.app.plugin.id.resource.ContainerWithScopeVo;
import org.ligoj.app.plugin.id.resource.GroupResource;
import org.ligoj.app.plugin.id.resource.UserOrgResource;
import org.ligoj.app.plugin.inbox.sql.dao.MessageRepository;
import org.ligoj.app.plugin.inbox.sql.model.Message;
import org.ligoj.app.plugin.inbox.sql.model.MessageRead;
import org.ligoj.app.plugin.inbox.sql.model.MessageTargetType;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.ligoj.bootstrap.resource.system.session.SessionSettings;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * {@link MessageResource} test cases.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MessageResourceTest extends AbstractAppTest {

	@Autowired
	private MessageResource resource;
	@Autowired
	private MessageRepository repository;

	@Before
	public void prepare() throws IOException {
		persistEntities("csv",
				new Class[] { Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class,
						Message.class, DelegateNode.class, DelegateOrg.class, CacheCompany.class, CacheUser.class,
						CacheGroup.class, CacheMembership.class },
				StandardCharsets.UTF_8.name());
	}

	@Test
	public void updateOwnMessageToMe() {
		final int id = repository.findBy("target", DEFAULT_USER).getId();
		em.clear();

		final Message message2 = new Message();
		message2.setId(id);
		message2.setTarget("gfi-gstack");
		message2.setTargetType(MessageTargetType.GROUP);
		message2.setValue("new");
		mockGroup().update(message2);
		em.flush();
		em.clear();
		final Message message3 = repository.findOne(id);
		Assert.assertEquals("gfi-gstack", message3.getTarget());
		Assert.assertEquals("new", message3.getValue());
		Assert.assertEquals(MessageTargetType.GROUP, message3.getTargetType());
	}

	@Test
	public void deleteOwnMessageToMe() {
		final int id = repository.findBy("target", DEFAULT_USER).getId();
		resource.delete(id);
		em.flush();
		em.clear();
		Assert.assertNull(repository.findOne(id));
	}

	@Test
	public void deleteOwnMessageToAnother() {
		final int id = repository.findBy("target", "user1").getId();
		resource.delete(id);
		em.flush();
		em.clear();
		Assert.assertNull(repository.findOne(id));
	}

	@Test(expected = ValidationJsonException.class)
	public void deletePrivateMessage() {
		initSpringSecurityContext("any");
		final int id = repository.findBy("target", "user2").getId();
		resource.delete(id);
	}

	@Test
	public void deleteManagedNodeMessage() {
		final int id = repository.findBy("target", "service:bt").getId();
		resource.delete(id);
		em.flush();
		em.clear();
		Assert.assertNull(repository.findOne(id));
	}

	@Test
	public void deleteManagedGroupMessage() {
		final int id = repository.findBy("targetType", MessageTargetType.GROUP).getId();
		resource.delete(id);
		em.flush();
		em.clear();
		Assert.assertNull(repository.findOne(id));
	}

	@Test(expected = ValidationJsonException.class)
	public void deleteNotManagedGroupMessage() {
		initSpringSecurityContext("any");
		final int id = repository.findBy("targetType", MessageTargetType.GROUP).getId();
		resource.delete(id);
	}

	@Test
	public void deleteManagedCompanyMessage() {
		final int id = repository.findBy("targetType", MessageTargetType.COMPANY).getId();
		resource.delete(id);
		em.flush();
		em.clear();
		Assert.assertNull(repository.findOne(id));
	}

	@Test(expected = ValidationJsonException.class)
	public void deleteNotManagedCompanyMessage() {
		initSpringSecurityContext("any");
		final int id = repository.findBy("targetType", MessageTargetType.COMPANY).getId();
		resource.delete(id);
	}

	@Test
	public void deleteManagedProjectMessage() {
		final int id = repository.findBy("targetType", MessageTargetType.PROJECT).getId();
		resource.delete(id);
		em.flush();
		em.clear();
		Assert.assertNull(repository.findOne(id));
	}

	@Test(expected = ValidationJsonException.class)
	public void deleteNotManagedProjectMessage() {
		initSpringSecurityContext("any");
		final int id = repository.findBy("targetType", MessageTargetType.PROJECT).getId();
		resource.delete(id);
	}

	@Test(expected = ValidationJsonException.class)
	public void deleteNotExists() {
		resource.delete(0);
	}

	@Test(expected = ValidationJsonException.class)
	public void deleteNotVisible() {
		initSpringSecurityContext("any");
		resource.delete(repository.findBy("target", DEFAULT_USER).getId());
	}

	@Test
	public void createCompany() {
		final Message message = new Message();
		message.setTarget("gfi");
		message.setTargetType(MessageTargetType.COMPANY);
		assertMessageCreate(mockCompany(), message);
	}

	private MessageResource mockCompany() {
		final MessageResource resource = new MessageResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		resource.companyResource = Mockito.mock(CompanyResource.class);
		Mockito.when(resource.companyResource.findByIdExpected("gfi")).thenReturn(new CompanyOrg("dn", "gfi"));
		resource.afterPropertiesSet();
		return resource;
	}

	@Test(expected = ValidationJsonException.class)
	public void createNotVisibleCompany() {
		initSpringSecurityContext("any");
		final Message message = new Message();
		message.setTarget("gfi");
		message.setTargetType(MessageTargetType.COMPANY);
		message.setValue("msg");
		resource.create(message);
	}

	@Test(expected = ForbiddenException.class)
	public void createXSSScript() {
		final Message message = new Message();
		message.setTarget("alongchu");
		message.setTargetType(MessageTargetType.USER);
		message.setValue("<script>alert()</script>");
		mockUser().create(message);
	}

	private MessageResource mockUser() {
		final MessageResource resource = new MessageResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		resource.userResource = Mockito.mock(UserOrgResource.class);
		UserOrg user = new UserOrg();
		user.setId("alongchu");
		Mockito.when(resource.userResource.findById("alongchu")).thenReturn(user);
		UserOrg user2 = new UserOrg();
		user2.setId("junit");
		Mockito.when(resource.userResource.findById("junit")).thenReturn(user2);
		resource.afterPropertiesSet();
		return resource;
	}

	@Test(expected = ForbiddenException.class)
	public void createXSSScript2() {
		final Message message = new Message();
		message.setTarget("alongchu");
		message.setTargetType(MessageTargetType.USER);
		message.setValue("<a href='//google'>alert()</a>");
		mockUser().create(message);
	}

	@Test(expected = ForbiddenException.class)
	public void createXSSScript3() {
		final Message message = new Message();
		message.setTarget("alongchu");
		message.setTargetType(MessageTargetType.USER);
		message.setValue("<img src='http://google'>");
		mockUser().create(message);
	}

	@Test
	public void createUserMarkup() {
		final MessageRead messageRead = new MessageRead();
		messageRead.setId("alongchu");
		messageRead.setMessage(em.createQuery("SELECT id FROM Message WHERE targetType= :type", Integer.class)
				.setParameter("type", MessageTargetType.PROJECT).getSingleResult() + 2);
		em.persist(messageRead);
		Assert.assertEquals(0, repository.countUnread("alongchu"));

		final Message message = new Message();
		message.setTarget("alongchu");
		message.setTargetType(MessageTargetType.USER);
		message.setValue("msg <i class=\"fa fa-smile\"></i>");
		final int id = mockUser().create(message);
		Assert.assertTrue(id > 0);
		Assert.assertEquals("msg <i class=\"fa fa-smile\"></i>", repository.findOne(id).getValue());
		Assert.assertEquals(1, repository.countUnread("alongchu"));
	}

	@Test
	public void createUser() {
		final MessageResource resource = mockUser();
		final Message message = new Message();
		message.setTarget("alongchu");
		message.setTargetType(MessageTargetType.USER);
		assertMessageCreate(resource, message);
	}

	@Test(expected = ValidationJsonException.class)
	public void createNotVisibleUser() {
		initSpringSecurityContext("any");
		final Message message = new Message();
		message.setTarget("alongchu");
		message.setTargetType(MessageTargetType.USER);
		message.setValue("msg");
		resource.create(message);
	}

	@Test
	public void createGroup() {
		final Message message = new Message();
		message.setTarget("gfi-gstack");
		message.setTargetType(MessageTargetType.GROUP);
		assertMessageCreate(mockGroup(), message);
	}

	private MessageResource mockGroup() {
		final MessageResource resource = new MessageResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		resource.groupResource = Mockito.mock(GroupResource.class);
		Mockito.when(resource.groupResource.findByIdExpected("gfi-gstack"))
				.thenReturn(new GroupOrg("dn", "gfi-gstack", Collections.emptySet()));
		resource.afterPropertiesSet();
		return resource;
	}

	@Test(expected = ValidationJsonException.class)
	public void createNotVisibleGroup() {
		initSpringSecurityContext("any");
		final Message message = new Message();
		message.setTarget("gfi-gstack");
		message.setTargetType(MessageTargetType.GROUP);
		resource.create(message);
	}

	@Test
	public void createProject() {
		initSpringSecurityContext("fdaugan");
		final Message message = new Message();
		message.setTarget("gfi-gstack");
		message.setTargetType(MessageTargetType.PROJECT);
		assertMessageCreate(mockGroup(), message);
	}

	@Test(expected = ValidationJsonException.class)
	public void createNotVisibleProject() {
		initSpringSecurityContext("any");
		final Message message = new Message();
		message.setTarget("gfi-gstack");
		message.setTargetType(MessageTargetType.PROJECT);
		message.setValue("msg");
		resource.create(message);
	}

	@Test
	public void createNode() {
		final Message message = new Message();
		message.setTarget("service:build:jenkins");
		message.setTargetType(MessageTargetType.NODE);
		assertMessageCreate(resource, message);
	}

	@Test(expected = ValidationJsonException.class)
	public void createNotVisibleNode() {
		initSpringSecurityContext("any");
		final Message message = new Message();
		message.setTarget("service:build:jenkins");
		message.setTargetType(MessageTargetType.NODE);
		message.setValue("msg");
		resource.create(message);
	}

	@Test
	public void findMyEmptyNoMatchingUser() {
		initSpringSecurityContext("any");
		Assert.assertEquals(0, resource.findMy(null, newUriInfo()).getData().size());
	}

	@Test
	public void findAll() {
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.PAGE_LENGTH, "100");
		final List<MessageVo> messages = resource.findAll(null, uriInfo).getData();
		Assert.assertEquals(17, messages.size());
		Assert.assertEquals("junit", messages.get(0).getTarget());
		Assert.assertEquals("gfi", messages.get(7).getTarget());
		Assert.assertEquals("user2", messages.get(16).getTarget());
		em.flush();
		em.clear();

		// Check pagination
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.PAGE_LENGTH, "2");
		Assert.assertEquals(2, resource.findMy(null, uriInfo).getData().size());
	}

	@Test
	public void findMy() {
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.PAGE_LENGTH, "100");
		final List<MessageVo> messages = resource.findMy(null, uriInfo).getData();
		Assert.assertEquals(8, messages.size());
		Assert.assertEquals("junit", messages.get(0).getTarget());
		Assert.assertEquals("junit", messages.get(7).getTarget());
		em.flush();
		em.clear();

		// Check pagination
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.PAGE_LENGTH, "2");
		Assert.assertEquals(2, resource.findMy(null, uriInfo).getData().size());
	}

	@Test
	public void findMy2() {
		initSpringSecurityContext("fdaugan");
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.PAGE_LENGTH, "100");
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.SORT_DIRECTION, "desc");
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.SORTED_COLUMN, "1");
		uriInfo.getQueryParameters().putSingle("columns[1][data]", "id");
		final List<MessageVo> messages = resource.findMy(null, uriInfo).getData();
		Assert.assertEquals(6, messages.size());
		Assert.assertEquals("service:bt", messages.get(5).getTarget());
		Assert.assertEquals("service:build:jenkins:bpr", messages.get(4).getTarget());
		Assert.assertEquals("gfi-gstack", messages.get(3).getTarget());
		Assert.assertEquals("service:build:jenkins", messages.get(2).getTarget());
		Assert.assertEquals("gfi", messages.get(1).getTarget());
		Assert.assertEquals("fdaugan", messages.get(0).getTarget());
	}

	@Test
	public void findMyUser() {
		initSpringSecurityContext("user1");
		final List<MessageVo> messages = resource.findMy(null, newUriInfo()).getData();
		Assert.assertEquals(1, messages.size());

		final MessageVo message = messages.get(0);
		Assert.assertNotNull(message.getCreatedDate());
		Assert.assertEquals("user1", message.getTarget());
		Assert.assertEquals(MessageTargetType.USER, message.getTargetType());
		Assert.assertEquals("MessageF1", message.getValue());
		Assert.assertEquals("junit", message.getFrom().getId());
		Assert.assertEquals("user1", message.getUser().getId());
	}

	@Test
	public void findMyGroup() {
		final MessageResource resource = new MessageResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		resource.companyResource = Mockito.mock(CompanyResource.class);
		Mockito.when(resource.companyResource.findByIdExpected("gfi")).thenReturn(new CompanyOrg("dn", "gfi"));
		final ContainerWithScopeVo container1 = new ContainerWithScopeVo();
		container1.setId("gfi");
		container1.setName("gfi");
		container1.setScope("some");
		container1.setLocked(false);
		Mockito.when(resource.companyResource.findByName("gfi")).thenReturn(container1);
		resource.groupResource = Mockito.mock(GroupResource.class);
		Mockito.when(resource.groupResource.findByIdExpected("gfi-gstack"))
				.thenReturn(new GroupOrg("dn", "gfi-gstack", Collections.emptySet()));
		final ContainerWithScopeVo container2 = new ContainerWithScopeVo();
		container2.setId("gfi-gstack");
		container2.setName("gfi-gStack");
		container2.setScope("some");
		container2.setLocked(false);
		Mockito.when(resource.groupResource.findByName("gfi-gstack")).thenReturn(container2);
		resource.afterPropertiesSet();

		initSpringSecurityContext("alongchu");

		prepareUnreadPosition();
		Assert.assertEquals(3, resource.countUnread());

		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.PAGE_LENGTH, "100");
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.SORT_DIRECTION, "DESC");
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.SORTED_COLUMN, "0");
		uriInfo.getQueryParameters().putSingle("columns[0][data]", "id");

		final List<MessageVo> messages = resource.findMy(null, uriInfo).getData();
		Assert.assertEquals(6, messages.size());

		// Message for the company
		MessageVo message = messages.get(0);
		Assert.assertNotNull(message.getCreatedDate());
		Assert.assertEquals("gfi", message.getTarget());
		Assert.assertEquals(MessageTargetType.COMPANY, message.getTargetType());
		Assert.assertEquals("Message6", message.getValue());
		Assert.assertTrue(message.isUnread());
		Assert.assertEquals("junit", message.getFrom().getId());
		Assert.assertEquals("gfi", message.getCompany().getId());
		Assert.assertEquals("gfi", message.getCompany().getName());

		// Message for node, since this user is in a group linked to a project
		// subscribing to target instance
		message = messages.get(1);
		Assert.assertNotNull(message.getCreatedDate());
		Assert.assertEquals("service:build:jenkins", message.getTarget());
		Assert.assertEquals(MessageTargetType.NODE, message.getTargetType());
		Assert.assertEquals("Message5", message.getValue());
		Assert.assertTrue(message.isUnread());
		Assert.assertEquals("junit", message.getFrom().getId());
		Assert.assertEquals("service:build:jenkins", message.getNode().getId());
		Assert.assertEquals("Jenkins", message.getNode().getName());
		Assert.assertEquals("Build", message.getNode().getRefined().getName());

		// Message for group
		message = messages.get(2);
		Assert.assertNotNull(message.getCreatedDate());
		Assert.assertEquals("gfi-gstack", message.getTarget());
		Assert.assertEquals(MessageTargetType.PROJECT, message.getTargetType());
		Assert.assertEquals("Message0", message.getValue());
		Assert.assertTrue(message.isUnread());
		Assert.assertEquals("junit", message.getFrom().getId());
		Assert.assertEquals("gStack", message.getProject().getName());
		Assert.assertEquals("Stack", message.getProject().getDescription());

		// Message for node, since this user is in a group linked to a project
		// subscribing to target instance
		message = messages.get(3);
		Assert.assertNotNull(message.getCreatedDate());
		Assert.assertEquals("service:build:jenkins:bpr", message.getTarget());
		Assert.assertEquals(MessageTargetType.NODE, message.getTargetType());
		Assert.assertEquals("Message4", message.getValue());
		Assert.assertFalse(message.isUnread());
		Assert.assertEquals("junit", message.getFrom().getId());
		Assert.assertEquals("service:build:jenkins:bpr", message.getNode().getId());
		Assert.assertNull(message.getNode().getParameters());

		// Message for project, since this user is in a group linked to target
		// project
		message = messages.get(4);
		Assert.assertNotNull(message.getCreatedDate());
		Assert.assertEquals("service:bt", message.getTarget());
		Assert.assertEquals(MessageTargetType.NODE, message.getTargetType());
		Assert.assertEquals("Message3", message.getValue());
		Assert.assertFalse(message.isUnread());
		Assert.assertEquals("junit", message.getFrom().getId());
		Assert.assertEquals("service:bt", message.getNode().getId());
		Assert.assertNull(message.getNode().getParameters());

		// Message for project, since this user is in a group linked to target
		// tool
		message = messages.get(5);
		Assert.assertNotNull(message.getCreatedDate());
		Assert.assertEquals("gfi-gstack", message.getTarget());
		Assert.assertEquals(MessageTargetType.GROUP, message.getTargetType());
		Assert.assertEquals("Message2", message.getValue());
		Assert.assertFalse(message.isUnread());
		Assert.assertEquals("junit", message.getFrom().getId());
		Assert.assertEquals("gfi-gstack", message.getGroup().getId());
		Assert.assertEquals("gfi-gStack", message.getGroup().getName());
		em.flush();
		em.clear();

		// Check the second pass, there is no more unread messages
		final List<MessageVo> messagesRe = resource.findMy(null, uriInfo).getData();
		Assert.assertEquals(6, messagesRe.size());
		Assert.assertFalse(messagesRe.get(0).isUnread());
		Assert.assertEquals(0, resource.countUnread());
	}

	/**
	 * No message have been read
	 */
	@Test
	public void countUnreadEpoc() {
		initSpringSecurityContext("alongchu");
		final MessageRead messageRead = new MessageRead();
		messageRead.setId("alongchu");
		messageRead.setMessage(0);
		em.persist(messageRead);
		Assert.assertEquals(6, resource.countUnread());
	}

	/**
	 * All messages have be read.
	 */
	@Test
	public void countUnreadFuture() {
		initSpringSecurityContext("alongchu");
		final MessageRead messageRead = new MessageRead();
		messageRead.setId("alongchu");
		messageRead.setMessage(Integer.MAX_VALUE);
		em.persist(messageRead);
		Assert.assertEquals(0, resource.countUnread());
	}

	@Test(expected = ValidationJsonException.class)
	public void audienceUserRight() {
		initSpringSecurityContext("any");
		resource.audience(MessageTargetType.USER, "fdaugan");
	}

	@Test(expected = ValidationJsonException.class)
	public void audienceUserUnknown() {
		resource.audience(MessageTargetType.USER, "any");
	}

	@Test
	public void audienceUser() {
		final MessageResource resource = new MessageResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		resource.userResource = Mockito.mock(UserOrgResource.class);
		UserOrg user = new UserOrg();
		user.setId("fdaugan");
		Mockito.when(resource.userResource.findById("fdaugan")).thenReturn(user);
		resource.afterPropertiesSet();

		Assert.assertEquals(1, resource.audience(MessageTargetType.USER, "fdaugan"));
	}

	@Test
	public void audienceGroup() {
		Assert.assertEquals(1, mockGroup().audience(MessageTargetType.GROUP, "gfi-gstack"));
	}

	@Test(expected = ValidationJsonException.class)
	public void audienceGroupUnknown() {
		resource.audience(MessageTargetType.GROUP, "any");
	}

	@Test(expected = ValidationJsonException.class)
	public void audienceGroupRight() {
		initSpringSecurityContext("any");
		resource.audience(MessageTargetType.GROUP, "gfi-gstack");
	}

	@Test
	public void audienceCompany() {
		final MessageResource resource = mockCompany();

		Assert.assertEquals(7, resource.audience(MessageTargetType.COMPANY, "gfi"));
	}

	@Test(expected = ValidationJsonException.class)
	public void audienceCompanyRight() {
		initSpringSecurityContext("any");
		resource.audience(MessageTargetType.COMPANY, "gfi");
	}

	@Test(expected = ValidationJsonException.class)
	public void audienceCompanyUnknown() {
		resource.audience(MessageTargetType.COMPANY, "any");
	}

	@Test
	public void audienceProject() {
		Assert.assertEquals(2, resource.audience(MessageTargetType.PROJECT, "gfi-gstack"));
	}

	@Test
	public void audienceProjectMember() {
		initSpringSecurityContext("alongchu");
		Assert.assertEquals(2, resource.audience(MessageTargetType.PROJECT, "gfi-gstack"));
	}

	@Test(expected = ValidationJsonException.class)
	public void audienceProjectRight() {
		initSpringSecurityContext("any");
		resource.audience(MessageTargetType.PROJECT, "gfi-gstack");
	}

	@Test(expected = ValidationJsonException.class)
	public void audienceProjectUnknown() {
		initSpringSecurityContext("fdaugan");
		resource.audience(MessageTargetType.PROJECT, "any");
	}

	@Test
	public void audienceNode() {
		Assert.assertEquals(2, resource.audience(MessageTargetType.NODE, "service:build:jenkins"));
		Assert.assertEquals(2, resource.audience(MessageTargetType.NODE, "service:build:jenkins:bpr"));
		Assert.assertEquals(2, resource.audience(MessageTargetType.NODE, "service:scm"));
	}

	@Test(expected = ValidationJsonException.class)
	public void audienceNodeRight() {
		initSpringSecurityContext("any");
		resource.audience(MessageTargetType.NODE, "service:build:jenkins");
	}

	@Test(expected = ValidationJsonException.class)
	public void audienceNodeUnknown() {
		resource.audience(MessageTargetType.NODE, "service:any");
	}

	@Test
	public void countUnread() {
		initSpringSecurityContext("alongchu");
		prepareUnreadPosition();
		Assert.assertEquals(3, resource.countUnread());
		Assert.assertEquals(3, resource.countUnread());
	}

	private void prepareUnreadPosition() {
		// All messages are read until the message from 2016/08/15 that targets
		// a project
		final MessageRead messageRead = new MessageRead();
		messageRead.setId("alongchu");
		messageRead.setMessage(em.createQuery("SELECT id FROM Message WHERE targetType= :type", Integer.class)
				.setParameter("type", MessageTargetType.PROJECT).getSingleResult() - 1);
		em.persist(messageRead);
	}

	private void assertMessageCreate(final MessageResource resource, final Message message) {
		final MessageRead messageRead = new MessageRead();
		messageRead.setId("alongchu");
		messageRead.setMessage(em.createQuery("SELECT id FROM Message WHERE targetType= :type", Integer.class)
				.setParameter("type", MessageTargetType.PROJECT).getSingleResult() + 2);
		em.persist(messageRead);
		Assert.assertEquals(0, repository.countUnread("alongchu"));
		message.setValue("msg");
		final int id = resource.create(message);
		Assert.assertTrue(id > 0);
		Assert.assertEquals("msg", repository.findOne(id).getValue());
		Assert.assertEquals(1, repository.countUnread("alongchu"));
	}

	@Test
	public void getKey() {
		Assert.assertEquals("feature:inbox:sql", resource.getKey());
	}

	@Test
	public void decorate() {
		initSpringSecurityContext("alongchu");
		prepareUnreadPosition();
		SessionSettings settings = Mockito.mock(SessionSettings.class);
		Map<String, Object> userSettings = new HashMap<>();
		Mockito.when(settings.getUserSettings()).thenReturn(userSettings);
		Mockito.when(settings.getUserName()).thenReturn("alongchu");
		resource.decorate(settings);
		Assert.assertEquals(Integer.valueOf(3), userSettings.get("unreadMessages"));
	}
}
