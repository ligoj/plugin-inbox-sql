/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.inbox.sql.resource;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import org.ligoj.app.iam.IUserRepository;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.plugin.id.resource.CompanyResource;
import org.ligoj.app.plugin.id.resource.GroupResource;
import org.ligoj.app.plugin.id.resource.UserOrgResource;
import org.ligoj.app.plugin.inbox.sql.dao.MessageReadRepository;
import org.ligoj.app.plugin.inbox.sql.dao.MessageRepository;
import org.ligoj.app.plugin.inbox.sql.model.Message;
import org.ligoj.app.plugin.inbox.sql.model.MessageRead;
import org.ligoj.app.plugin.inbox.sql.model.MessageTargetType;
import org.ligoj.app.resource.node.NodeResource;
import org.ligoj.app.resource.project.BasicProjectVo;
import org.ligoj.app.resource.project.ProjectHelper;
import org.ligoj.bootstrap.core.AuditedBean;
import org.ligoj.bootstrap.core.INamableBean;
import org.ligoj.bootstrap.core.json.PaginationJson;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.plugin.FeaturePlugin;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.security.SecurityHelper;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.ligoj.bootstrap.resource.system.session.ISessionSettingsProvider;
import org.ligoj.bootstrap.resource.system.session.SessionSettings;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link Message} resource.
 */
@Path("/message")
@Service
@Slf4j
@Transactional
@Produces(MediaType.APPLICATION_JSON)
public class MessageResource implements InitializingBean, ISessionSettingsProvider, FeaturePlugin {

	@Autowired
	private MessageRepository repository;

	@Autowired
	private SecurityHelper securityHelper;

	@Autowired
	private PaginationJson paginationJson;

	@Autowired
	private IamProvider[] iamProvider;

	@Autowired
	protected UserOrgResource userResource;

	@Autowired
	protected CompanyResource companyResource;

	@Autowired
	protected ProjectHelper projectHelper;

	@Autowired
	protected NodeResource nodeResource;

	@Autowired
	protected GroupResource groupResource;

	@Autowired
	private MessageReadRepository messageReadRepository;

	/**
	 * Configuration of checker function for a given {@link MessageTargetType}
	 */
	private final Map<MessageTargetType, Function<String, INamableBean<?>>> checker = new EnumMap<>(MessageTargetType.class);

	/**
	 * Ordered columns.
	 */
	protected static final Map<String, String> ORM_MAPPING = new HashMap<>();

	static {
		ORM_MAPPING.put("createdDate", "createdDate");
		ORM_MAPPING.put("value", "value");
		ORM_MAPPING.put("target", "target");
		ORM_MAPPING.put("targetType", "targetType");
		ORM_MAPPING.put("id", "id");
	}

	/**
	 * Delete a {@link Message} from its identifier.
	 *
	 * @param id
	 *            Message's identifier.
	 */
	@DELETE
	@Path("{id}")
	public void delete(@PathParam("id") final int id) {

		// Force the user cache to be loaded
		getUser().findAll();

		repository.findAll(securityHelper.getLogin(), null, PageRequest.of(0, 20));
		if (repository.deleteVisible(id, securityHelper.getLogin()) != 1) {
			// Message not found or not visible. Whatever, return an exception
			throw new ValidationJsonException("id", BusinessException.KEY_UNKNOWN_ID, "0", "message", "1", id);
		}
	}

	/**
	 * Update the message
	 *
	 * @param message
	 *            The message to save.
	 */
	@PUT
	public void update(final Message message) {
		saveOrUpdate(message);
	}

	/**
	 * Create a new message.
	 *
	 * @param message
	 *            The message to save.
	 * @return The identifier of created message.
	 */
	@POST
	public int create(final Message message) {
		return saveOrUpdate(message).getId();
	}

	/**
	 * Save or update a message. All properties are checked.
	 *
	 * @param message
	 *            The message to create or update.
	 * @return The current or new identifier.
	 */
	private Message saveOrUpdate(final Message message) {
		// Check the target and normalize it
		message.setTarget(checkRights(message.getTargetType(), message.getTarget()));

		// Basic XSS protection
		if (!message.getValue().replaceAll("(<\\s*script|(src|href)\\s*=\\s*['\"](//|[^'\"]+:))", "").equals(message.getValue())) {
			// XSS attempt, report it
			log.warn("XSS attempt from {} with message {}", securityHelper.getLogin(), message.getValue());
			throw new ForbiddenException();
		}

		// Target is valid, persist the message
		return repository.saveAndFlush(message);
	}

	/**
	 * Check the current user can perform an update or a creation on the given configuration.
	 *
	 * @param targetType
	 *            The message type.
	 * @param target
	 *            The target configuration : group, node, ...
	 * @return The normalized and validated target.
	 */
	private String checkRights(final MessageTargetType targetType, final String target) {
		// Force the user cache to be loaded
		getUser().findAll();

		// Check and normalize
		final INamableBean<?> targetEntity = checker.get(targetType).apply(target);
		return targetEntity instanceof BasicProjectVo ? ((BasicProjectVo) targetEntity).getPkey() : (String) targetEntity.getId();
	}

	/**
	 * Return all messages by criteria the given user could have written. The main difference with the function
	 * {@link #findMy(UriInfo)} is that the returned messages includes the one the given user is not
	 * involved, or targeted.
	 * For sample with this function, a user can see all messages from a group because this group is visible by this
	 * user.
	 * But with the other function {@link #findMy(UriInfo)} these messages will be returned because this user is
	 * not member of this group.
	 *
	 * @param uriInfo
	 *            filter data.
	 * @return Related messages, already read or not. Also there is an indicator on the message specifying the "new"
	 *         state.
	 */
	@GET
	public TableItem<MessageVo> findAll(@Context final UriInfo uriInfo) {
		return findAllProvider(uriInfo, (user, pageRequest) -> repository.findAll(user, DataTableAttributes.getSearch(uriInfo), pageRequest));
	}

	/**
	 * Return the amount of users targeted by the given configuration.
	 *
	 * @param targetType
	 *            The message type.
	 * @param target
	 *            The target configuration : group, node, ...
	 * @return The amount of users targeted by the given configuration.
	 */
	@GET
	@Path("audience/{targetType}/{target}")
	public long audience(@PathParam("targetType") final MessageTargetType targetType, @PathParam("target") final String target) {
		return repository.audience(targetType.name(), checkRights(targetType, target));
	}

	/**
	 * Return messages related to current user. Also update at the same time the cursor indicating the read messages.
	 *
	 * @param uriInfo
	 *            filter data.
	 * @return Related messages, already read or not. Also there is an indicator on the message specifying the "new"
	 *         state.
	 */
	@GET
	@Path("my")
	public TableItem<MessageVo> findMy(@Context final UriInfo uriInfo) {
		return findAllProvider(uriInfo, (user, pageRequest) -> repository.findMy(user, DataTableAttributes.getSearch(uriInfo), pageRequest));
	}

	/**
	 * Return messages related to current user. Also update at the same time the cursor indicating the read messages.
	 *
	 * @param uriInfo
	 *            filter data.
	 * @param function
	 *            Function providing the messages from a request and a user.
	 * @return Related messages, already read or not. Also there is an indicator on the message specifying the "new"
	 *         state.
	 */
	private TableItem<MessageVo> findAllProvider(final UriInfo uriInfo, final BiFunction<String, PageRequest, Page<Message>> function) {

		// Force the user cache to be loaded
		getUser().findAll();

		// Then query the messages
		final TableItem<MessageVo> messages = paginationJson.applyPagination(uriInfo,
				function.apply(securityHelper.getLogin(), paginationJson.getPageRequest(uriInfo, ORM_MAPPING, Collections.singleton("id"))), m -> {
					final MessageVo vo = new MessageVo();
					AuditedBean.copyAuditData(m, vo);
					vo.setId(m.getId());
					vo.setValue(m.getValue());
					vo.setTargetType(m.getTargetType());
					vo.setTarget(m.getTarget());

					// Get the details of the target
					fillTarget(m, vo);

					// Attach user information of the source of the message
					vo.setFrom(getUser().toUser(m.getCreatedBy()));
					return vo;
				});

		// Then update the read messages indicator
		final MessageRead messageRead = Optional.ofNullable(messageReadRepository.findOne(securityHelper.getLogin())).orElseGet(() -> {
			// First access
			final MessageRead m = new MessageRead();
			m.setId(securityHelper.getLogin());
			return m;
		});
		messageRead.setMessage(Integer.max(messages.getData().stream().filter(m -> m.getId() > messageRead.getMessage()).map(m -> {
			// Then update the unread state of new messages
			m.setUnread(true);
			return m.getId();
		}).max(Comparator.naturalOrder()).orElse(0), messageRead.getMessage()));

		// Persist the state even if the user might has not read/seen the message
		messageReadRepository.save(messageRead);
		return messages;
	}

	/**
	 * Complete the target object depending on the target type of the given message.
	 */
	private void fillTarget(final Message message, final MessageVo vo) {
		switch (message.getTargetType()) {
		case PROJECT:
			vo.setProject(projectHelper.findByPKey(message.getTarget()));
			break;
		case COMPANY:
			vo.setCompany(companyResource.findByName(message.getTarget()));
			break;
		case GROUP:
			vo.setGroup(groupResource.findByName(message.getTarget()));
			break;
		case NODE:
			vo.setNode(nodeResource.findByIdInternal(message.getTarget()));
			break;
		case USER:
		default:
			vo.setUser(getUser().toUser(message.getTarget()));
		}
	}

	/**
	 * Return amount of unread messages related to current user.
	 *
	 * @return Amount of unread messages related to current user.
	 */
	@GET
	@Path("count")
	public int countUnread() {

		// Force the user cache to be loaded
		getUser().findAll();

		return repository.countUnread(securityHelper.getLogin());
	}

	@Override
	public void afterPropertiesSet() {
		checker.put(MessageTargetType.COMPANY, companyResource::findByIdExpected);
		checker.put(MessageTargetType.GROUP, groupResource::findByIdExpected);
		checker.put(MessageTargetType.PROJECT, projectHelper::findByPKey);
		checker.put(MessageTargetType.NODE, nodeResource::findById);
		checker.put(MessageTargetType.USER, userResource::findById);
	}

	/**
	 * User repository provider.
	 *
	 * @return User repository provider.
	 */
	private IUserRepository getUser() {
		return iamProvider[0].getConfiguration().getUserRepository();
	}

	@Override
	public void decorate(final SessionSettings settings) {
		// Add the unread messages counter
		settings.getUserSettings().put("unreadMessages", repository.countUnread(settings.getUserName()));
	}

	@Override
	public String getKey() {
		return "feature:inbox:sql";
	}
}
