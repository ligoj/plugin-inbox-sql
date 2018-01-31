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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.MatcherUtil;
import org.ligoj.app.iam.CompanyOrg;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.iam.model.CacheCompany;
import org.ligoj.app.iam.model.CacheGroup;
import org.ligoj.app.iam.model.CacheMembership;
import org.ligoj.app.iam.model.CacheUser;
import org.ligoj.app.iam.model.DelegateOrg;
import org.ligoj.app.model.CacheProjectGroup;
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
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * {@link MessageResource} test cases.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class MessageResourceTest extends AbstractAppTest {

	@Autowired
	private MessageResource resource;
	@Autowired
	private MessageRepository repository;

	@BeforeEach
	public void prepare() throws IOException {
		persistEntities("csv",
				new Class[] { Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class, Message.class,
						DelegateNode.class, DelegateOrg.class, CacheCompany.class, CacheUser.class, CacheGroup.class, CacheMembership.class,
						CacheProjectGroup.class },
				StandardCharsets.UTF_8.name());
	}

	@Test
	public void updateOwnMessageToMe() {
		// Coverage only
		Assertions.assertEquals(MessageTargetType.COMPANY,
				MessageTargetType.valueOf(MessageTargetType.values()[MessageTargetType.COMPANY.ordinal()].name()));

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
		Assertions.assertEquals("gfi-gstack", message3.getTarget());
		Assertions.assertEquals("new", message3.getValue());
		Assertions.assertEquals(MessageTargetType.GROUP, message3.getTargetType());
	}

	@Test
	public void deleteOwnMessageToMe() {
		final int id = repository.findBy("target", DEFAULT_USER).getId();
		resource.delete(id);
		em.flush();
		em.clear();
		Assertions.assertNull(repository.findOne(id));
	}

	@Test
	public void deleteOwnMessageToAnother() {
		final int id = repository.findBy("target", "user1").getId();
		resource.delete(id);
		em.flush();
		em.clear();
		Assertions.assertNull(repository.findOne(id));
	}

	@Test
	public void deletePrivateMessage() {
		initSpringSecurityContext("any");
		final int id = repository.findBy("target", "user2").getId();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.delete(id);
		}), "id", "unknown-id");
	}

	@Test
	public void deleteManagedNodeMessage() {
		final int id = repository.findBy("target", "service:bt").getId();
		resource.delete(id);
		em.flush();
		em.clear();
		Assertions.assertNull(repository.findOne(id));
	}

	@Test
	public void deleteManagedGroupMessage() {
		final int id = repository.findBy("targetType", MessageTargetType.GROUP).getId();
		resource.delete(id);
		em.flush();
		em.clear();
		Assertions.assertNull(repository.findOne(id));
	}

	@Test
	public void deleteNotManagedGroupMessage() {
		initSpringSecurityContext("any");
		final int id = repository.findBy("targetType", MessageTargetType.GROUP).getId();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.delete(id);
		}), "id", "unknown-id");

	}

	@Test
	public void deleteManagedCompanyMessage() {
		final int id = repository.findBy("targetType", MessageTargetType.COMPANY).getId();
		resource.delete(id);
		em.flush();
		em.clear();
		Assertions.assertNull(repository.findOne(id));
	}

	@Test
	public void deleteNotManagedCompanyMessage() {
		initSpringSecurityContext("any");
		final int id = repository.findBy("targetType", MessageTargetType.COMPANY).getId();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.delete(id);
		}), "id", "unknown-id");

	}

	@Test
	public void deleteManagedProjectMessage() {
		final int id = repository.findBy("targetType", MessageTargetType.PROJECT).getId();
		resource.delete(id);
		em.flush();
		em.clear();
		Assertions.assertNull(repository.findOne(id));
	}

	@Test
	public void deleteNotManagedProjectMessage() {
		initSpringSecurityContext("any");
		final int id = repository.findBy("targetType", MessageTargetType.PROJECT).getId();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.delete(id);
		}), "id", "unknown-id");

	}

	@Test
	public void deleteNotExists() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.delete(0);
		}), "id", "unknown-id");

	}

	@Test
	public void deleteNotVisible() {
		initSpringSecurityContext("any");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.delete(repository.findBy("target", DEFAULT_USER).getId());
		}), "id", "unknown-id");

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

	@Test
	public void createNotVisibleCompany() {
		initSpringSecurityContext("any");
		final Message message = new Message();
		message.setTarget("gfi");
		message.setTargetType(MessageTargetType.COMPANY);
		message.setValue("msg");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.create(message);
		}), "company", "unknown-id");

	}

	@Test
	public void createXSSScript() {
		final Message message = new Message();
		message.setTarget("alongchu");
		message.setTargetType(MessageTargetType.USER);
		message.setValue("<script>alert()</script>");
		Assertions.assertThrows(ForbiddenException.class, () -> {
			mockUser().create(message);
		});
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

	@Test
	public void createXSSScript2() {
		final Message message = new Message();
		message.setTarget("alongchu");
		message.setTargetType(MessageTargetType.USER);
		message.setValue("<a href='//google'>alert()</a>");
		Assertions.assertThrows(ForbiddenException.class, () -> {
			mockUser().create(message);
		});
	}

	@Test
	public void createXSSScript3() {
		final Message message = new Message();
		message.setTarget("alongchu");
		message.setTargetType(MessageTargetType.USER);
		message.setValue("<img src='http://google'>");
		Assertions.assertThrows(ForbiddenException.class, () -> {
			mockUser().create(message);
		});

	}

	@Test
	public void createUserMarkup() {
		final MessageRead messageRead = new MessageRead();
		messageRead.setId("alongchu");
		messageRead.setMessage(em.createQuery("SELECT id FROM Message WHERE targetType= :type", Integer.class)
				.setParameter("type", MessageTargetType.PROJECT).getSingleResult() + 2);
		em.persist(messageRead);
		Assertions.assertEquals(0, repository.countUnread("alongchu"));

		final Message message = new Message();
		message.setTarget("alongchu");
		message.setTargetType(MessageTargetType.USER);
		message.setValue("msg <i class=\"fa fa-smile\"></i>");
		final int id = mockUser().create(message);
		Assertions.assertTrue(id > 0);
		Assertions.assertEquals("msg <i class=\"fa fa-smile\"></i>", repository.findOne(id).getValue());
		Assertions.assertEquals(1, repository.countUnread("alongchu"));
	}

	@Test
	public void createUser() {
		final MessageResource resource = mockUser();
		final Message message = new Message();
		message.setTarget("alongchu");
		message.setTargetType(MessageTargetType.USER);
		assertMessageCreate(resource, message);
	}

	@Test
	public void createNotVisibleUser() {
		initSpringSecurityContext("any");
		final Message message = new Message();
		message.setTarget("alongchu");
		message.setTargetType(MessageTargetType.USER);
		message.setValue("msg");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.create(message);
		}), "id", "unknown-id");

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

	@Test
	public void createNotVisibleGroup() {
		initSpringSecurityContext("any");
		final Message message = new Message();
		message.setTarget("gfi-gstack");
		message.setTargetType(MessageTargetType.GROUP);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.create(message);
		}), "group", "unknown-id");

	}

	@Test
	public void createProject() {
		initSpringSecurityContext("fdaugan");
		final Message message = new Message();
		message.setTarget("gfi-gstack");
		message.setTargetType(MessageTargetType.PROJECT);
		assertMessageCreate(mockGroup(), message);
	}

	@Test
	public void createNotVisibleProject() {
		initSpringSecurityContext("any");
		final Message message = new Message();
		message.setTarget("gfi-gstack");
		message.setTargetType(MessageTargetType.PROJECT);
		message.setValue("msg");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.create(message);
		}), "pkey", "unknown-id");
	}

	@Test
	public void createNode() {
		final Message message = new Message();
		message.setTarget("service:build:jenkins");
		message.setTargetType(MessageTargetType.NODE);
		assertMessageCreate(resource, message);
	}

	@Test
	public void createNotVisibleNode() {
		initSpringSecurityContext("any");
		final Message message = new Message();
		message.setTarget("service:build:jenkins");
		message.setTargetType(MessageTargetType.NODE);
		message.setValue("msg");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.create(message);
		}), "id", "unknown-id");

	}

	@Test
	public void findMyEmptyNoMatchingUser() {
		initSpringSecurityContext("any");
		Assertions.assertEquals(0, resource.findMy(newUriInfo()).getData().size());
	}

	@Test
	public void findAll() {
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.PAGE_LENGTH, "100");
		final List<MessageVo> messages = resource.findAll(uriInfo).getData();
		Assertions.assertEquals(17, messages.size());
		Assertions.assertEquals("junit", messages.get(0).getTarget());
		Assertions.assertEquals("gfi", messages.get(7).getTarget());
		Assertions.assertEquals("user2", messages.get(16).getTarget());
		em.flush();
		em.clear();

		// Check pagination
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.PAGE_LENGTH, "2");
		Assertions.assertEquals(2, resource.findMy(uriInfo).getData().size());
	}

	@Test
	public void findMy() {
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.PAGE_LENGTH, "100");
		final List<MessageVo> messages = resource.findMy(uriInfo).getData();
		Assertions.assertEquals(8, messages.size());
		Assertions.assertEquals("junit", messages.get(0).getTarget());
		Assertions.assertEquals("junit", messages.get(7).getTarget());
		em.flush();
		em.clear();

		// Check pagination
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.PAGE_LENGTH, "2");
		Assertions.assertEquals(2, resource.findMy(uriInfo).getData().size());
	}

	@Test
	public void findMy2() {
		initSpringSecurityContext("fdaugan");
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.PAGE_LENGTH, "100");
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.SORT_DIRECTION, "desc");
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.SORTED_COLUMN, "1");
		uriInfo.getQueryParameters().putSingle("columns[1][data]", "id");
		final List<MessageVo> messages = resource.findMy(uriInfo).getData();
		Assertions.assertEquals(6, messages.size());
		Assertions.assertEquals("service:bt", messages.get(5).getTarget());
		Assertions.assertEquals("service:build:jenkins:bpr", messages.get(4).getTarget());
		Assertions.assertEquals("gfi-gstack", messages.get(3).getTarget());
		Assertions.assertEquals("service:build:jenkins", messages.get(2).getTarget());
		Assertions.assertEquals("gfi", messages.get(1).getTarget());
		Assertions.assertEquals("fdaugan", messages.get(0).getTarget());
	}

	@Test
	public void findMyUser() {
		initSpringSecurityContext("user1");
		final List<MessageVo> messages = resource.findMy(newUriInfo()).getData();
		Assertions.assertEquals(1, messages.size());

		final MessageVo message = messages.get(0);
		Assertions.assertNotNull(message.getCreatedDate());
		Assertions.assertEquals("user1", message.getTarget());
		Assertions.assertEquals(MessageTargetType.USER, message.getTargetType());
		Assertions.assertEquals("MessageF1", message.getValue());
		Assertions.assertEquals("junit", message.getFrom().getId());
		Assertions.assertEquals("user1", message.getUser().getId());
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
		Assertions.assertEquals(3, resource.countUnread());

		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.PAGE_LENGTH, "100");
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.SORT_DIRECTION, "DESC");
		uriInfo.getQueryParameters().putSingle(DataTableAttributes.SORTED_COLUMN, "0");
		uriInfo.getQueryParameters().putSingle("columns[0][data]", "id");

		final List<MessageVo> messages = resource.findMy(uriInfo).getData();
		Assertions.assertEquals(6, messages.size());

		// Message for the company
		MessageVo message = messages.get(0);
		Assertions.assertNotNull(message.getCreatedDate());
		Assertions.assertEquals("gfi", message.getTarget());
		Assertions.assertEquals(MessageTargetType.COMPANY, message.getTargetType());
		Assertions.assertEquals("Message6", message.getValue());
		Assertions.assertTrue(message.isUnread());
		Assertions.assertEquals("junit", message.getFrom().getId());
		Assertions.assertEquals("gfi", message.getCompany().getId());
		Assertions.assertEquals("gfi", message.getCompany().getName());

		// Message for node, since this user is in a group linked to a project
		// subscribing to target instance
		message = messages.get(1);
		Assertions.assertNotNull(message.getCreatedDate());
		Assertions.assertEquals("service:build:jenkins", message.getTarget());
		Assertions.assertEquals(MessageTargetType.NODE, message.getTargetType());
		Assertions.assertEquals("Message5", message.getValue());
		Assertions.assertTrue(message.isUnread());
		Assertions.assertEquals("junit", message.getFrom().getId());
		Assertions.assertEquals("service:build:jenkins", message.getNode().getId());
		Assertions.assertEquals("Jenkins", message.getNode().getName());
		Assertions.assertEquals("Build", message.getNode().getRefined().getName());

		// Message for group
		message = messages.get(2);
		Assertions.assertNotNull(message.getCreatedDate());
		Assertions.assertEquals("gfi-gstack", message.getTarget());
		Assertions.assertEquals(MessageTargetType.PROJECT, message.getTargetType());
		Assertions.assertEquals("Message0", message.getValue());
		Assertions.assertTrue(message.isUnread());
		Assertions.assertEquals("junit", message.getFrom().getId());
		Assertions.assertEquals("gStack", message.getProject().getName());
		Assertions.assertEquals("Stack", message.getProject().getDescription());

		// Message for node, since this user is in a group linked to a project
		// subscribing to target instance
		message = messages.get(3);
		Assertions.assertNotNull(message.getCreatedDate());
		Assertions.assertEquals("service:build:jenkins:bpr", message.getTarget());
		Assertions.assertEquals(MessageTargetType.NODE, message.getTargetType());
		Assertions.assertEquals("Message4", message.getValue());
		Assertions.assertFalse(message.isUnread());
		Assertions.assertEquals("junit", message.getFrom().getId());
		Assertions.assertEquals("service:build:jenkins:bpr", message.getNode().getId());
		Assertions.assertNull(message.getNode().getParameters());

		// Message for project, since this user is in a group linked to target
		// project
		message = messages.get(4);
		Assertions.assertNotNull(message.getCreatedDate());
		Assertions.assertEquals("service:bt", message.getTarget());
		Assertions.assertEquals(MessageTargetType.NODE, message.getTargetType());
		Assertions.assertEquals("Message3", message.getValue());
		Assertions.assertFalse(message.isUnread());
		Assertions.assertEquals("junit", message.getFrom().getId());
		Assertions.assertEquals("service:bt", message.getNode().getId());
		Assertions.assertNull(message.getNode().getParameters());

		// Message for project, since this user is in a group linked to target
		// tool
		message = messages.get(5);
		Assertions.assertNotNull(message.getCreatedDate());
		Assertions.assertEquals("gfi-gstack", message.getTarget());
		Assertions.assertEquals(MessageTargetType.GROUP, message.getTargetType());
		Assertions.assertEquals("Message2", message.getValue());
		Assertions.assertFalse(message.isUnread());
		Assertions.assertEquals("junit", message.getFrom().getId());
		Assertions.assertEquals("gfi-gstack", message.getGroup().getId());
		Assertions.assertEquals("gfi-gStack", message.getGroup().getName());
		em.flush();
		em.clear();

		// Check the second pass, there is no more unread messages
		final List<MessageVo> messagesRe = resource.findMy(uriInfo).getData();
		Assertions.assertEquals(6, messagesRe.size());
		Assertions.assertFalse(messagesRe.get(0).isUnread());
		Assertions.assertEquals(0, resource.countUnread());
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
		Assertions.assertEquals(6, resource.countUnread());
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
		Assertions.assertEquals(0, resource.countUnread());
	}

	@Test
	public void audienceUserRight() {
		initSpringSecurityContext("any");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.audience(MessageTargetType.USER, "fdaugan");
		}), "id", "unknown-id");

	}

	@Test
	public void audienceUserUnknown() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.audience(MessageTargetType.USER, "any");
		}), "id", "unknown-id");

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

		Assertions.assertEquals(1, resource.audience(MessageTargetType.USER, "fdaugan"));
	}

	@Test
	public void audienceGroup() {
		Assertions.assertEquals(1, mockGroup().audience(MessageTargetType.GROUP, "gfi-gstack"));
	}

	@Test
	public void audienceGroupUnknown() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.audience(MessageTargetType.GROUP, "any");
		}), "group", "unknown-id");

	}

	@Test
	public void audienceGroupRight() {
		initSpringSecurityContext("any");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.audience(MessageTargetType.GROUP, "gfi-gstack");
		}), "group", "unknown-id");

	}

	@Test
	public void audienceCompany() {
		final MessageResource resource = mockCompany();

		Assertions.assertEquals(7, resource.audience(MessageTargetType.COMPANY, "gfi"));
	}

	@Test
	public void audienceCompanyRight() {
		initSpringSecurityContext("any");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.audience(MessageTargetType.COMPANY, "gfi");
		}), "company", "unknown-id");

	}

	@Test
	public void audienceCompanyUnknown() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.audience(MessageTargetType.COMPANY, "any");
		}), "company", "unknown-id");

	}

	@Test
	public void audienceProject() {
		Assertions.assertEquals(2, resource.audience(MessageTargetType.PROJECT, "gfi-gstack"));
	}

	@Test
	public void audienceProjectMember() {
		initSpringSecurityContext("alongchu");
		Assertions.assertEquals(2, resource.audience(MessageTargetType.PROJECT, "gfi-gstack"));
	}

	@Test
	public void audienceProjectRight() {
		initSpringSecurityContext("any");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.audience(MessageTargetType.PROJECT, "gfi-gstack");
		}), "pkey", "unknown-id");
	}

	@Test
	public void audienceProjectUnknown() {
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.audience(MessageTargetType.PROJECT, "any");
		}), "pkey", "unknown-id");
	}

	@Test
	public void audienceNode() {
		Assertions.assertEquals(2, resource.audience(MessageTargetType.NODE, "service:build:jenkins"));
		Assertions.assertEquals(2, resource.audience(MessageTargetType.NODE, "service:build:jenkins:bpr"));
		Assertions.assertEquals(2, resource.audience(MessageTargetType.NODE, "service:scm"));
	}

	@Test
	public void audienceNodeRight() {
		initSpringSecurityContext("any");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.audience(MessageTargetType.NODE, "service:build:jenkins");
		}), "id", "unknown-id");
	}

	@Test
	public void audienceNodeUnknown() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.audience(MessageTargetType.NODE, "service:any");
		}), "id", "unknown-id");
	}

	@Test
	public void countUnread() {
		initSpringSecurityContext("alongchu");
		prepareUnreadPosition();
		Assertions.assertEquals(3, resource.countUnread());
		Assertions.assertEquals(3, resource.countUnread());
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
		Assertions.assertEquals(0, repository.countUnread("alongchu"));
		message.setValue("msg");
		final int id = resource.create(message);
		Assertions.assertTrue(id > 0);
		Assertions.assertEquals("msg", repository.findOne(id).getValue());
		Assertions.assertEquals(1, repository.countUnread("alongchu"));
	}

	@Test
	public void getKey() {
		Assertions.assertEquals("feature:inbox:sql", resource.getKey());
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
		Assertions.assertEquals(Integer.valueOf(3), userSettings.get("unreadMessages"));
	}
}
