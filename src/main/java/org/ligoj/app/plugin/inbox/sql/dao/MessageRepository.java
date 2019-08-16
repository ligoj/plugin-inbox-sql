/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.inbox.sql.dao;

import org.ligoj.app.dao.ProjectRepository;
import org.ligoj.app.iam.dao.DelegateOrgRepository;
import org.ligoj.app.plugin.inbox.sql.model.Message;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link Message} repository
 */
public interface MessageRepository extends RestRepository<Message, Integer> {

	/**
	 * Base query to find related messages of a user.
	 */
	String MY_MESSAGES = "FROM Message m WHERE (targetType IS NULL                           "
			+ "  OR (targetType = org.ligoj.app.plugin.inbox.sql.model.MessageTargetType.USER    AND target = :user)"
			+ "  OR (targetType = org.ligoj.app.plugin.inbox.sql.model.MessageTargetType.GROUP   AND ingroup(:user,m.target,m.target) IS TRUE)"
			+ "  OR (targetType = org.ligoj.app.plugin.inbox.sql.model.MessageTargetType.COMPANY AND incompany(:user,m.target,m.target) IS TRUE)"
			+ "  OR (targetType = org.ligoj.app.plugin.inbox.sql.model.MessageTargetType.PROJECT AND inprojectkey(:user,m.target,:user,m.target) IS TRUE)"
			+ "  OR (targetType = org.ligoj.app.plugin.inbox.sql.model.MessageTargetType.NODE AND EXISTS(SELECT 1    FROM Subscription s INNER JOIN s.project p INNER JOIN s.node n000 WHERE"
			+ "     (n000.id = m.target OR n000.id LIKE CONCAT(m.target, ':%')) AND inproject(:user,p,:user,p) IS TRUE)))";

	/**
	 * Base query to find messages a user can see, even if there are not targeting him/her. User can also see his/her
	 * messages sent directly to another user.
	 */
	String VISIBLE_MESSAGES = "FROM Message m WHERE (targetType IS NULL                           "
			+ "  OR (targetType = org.ligoj.app.plugin.inbox.sql.model.MessageTargetType.USER    AND createdBy = :user)"
			+ "  OR (targetType = org.ligoj.app.plugin.inbox.sql.model.MessageTargetType.GROUP   AND EXISTS(SELECT 1 FROM CacheGroup c WHERE c.id = m.target"
			+ "       AND EXISTS(SELECT 1 FROM DelegateOrg d WHERE (d.type=org.ligoj.app.iam.model.DelegateType.TREE OR d.type=org.ligoj.app.iam.model.DelegateType.GROUP)"
			+ "           AND c.description LIKE CONCAT('%,', d.dn) AND " + DelegateOrgRepository.ASSIGNED_DELEGATE + ")))"
			+ "  OR (targetType = org.ligoj.app.plugin.inbox.sql.model.MessageTargetType.COMPANY AND EXISTS(SELECT 1 FROM CacheCompany c WHERE c.id = m.target"
			+ "       AND EXISTS(SELECT 1 FROM DelegateOrg d WHERE (d.type=org.ligoj.app.iam.model.DelegateType.TREE OR d.type=org.ligoj.app.iam.model.DelegateType.COMPANY)"
			+ "           AND c.description LIKE CONCAT('%,', d.dn) AND " + DelegateOrgRepository.ASSIGNED_DELEGATE + ")))"
			+ "  OR (targetType = org.ligoj.app.plugin.inbox.sql.model.MessageTargetType.PROJECT AND EXISTS(SELECT 1 FROM Project p LEFT JOIN p.cacheGroups AS cpg LEFT JOIN cpg.group AS cg   WHERE p.pkey = m.target AND "
			+ ProjectRepository.VISIBLE_PROJECTS + "))"
			+ "  OR (targetType = org.ligoj.app.plugin.inbox.sql.model.MessageTargetType.NODE AND EXISTS(SELECT 1    FROM Node n WHERE n.id = m.target"
			+ "       AND EXISTS(SELECT 1 FROM DelegateNode d WHERE " + DelegateOrgRepository.ASSIGNED_DELEGATE
			+ " AND (n.id LIKE CONCAT(d.name, ':%') OR n.id = d.id)))))";


	/**
	 * Base query to find related project to a user "u.id".
	 */
	String HIS_PROJECTS = "(inproject2(u.id,p,u.id,p) IS TRUE)";

	/**
	 * Return all messages where the given user is involved and by criteria.
	 * 
	 * @param user
	 *            The user requesting the messages.
	 * @param criteria
	 *            Optional text to filter the messages.
	 * @param page
	 *            The ordering and page data.
	 * @return The related messages
	 */
	@Query(MY_MESSAGES + " AND (targetType LIKE(CONCAT(CONCAT('%',:criteria),'%'))"
			+ "                 OR target LIKE(CONCAT(CONCAT('%',:criteria),'%')) OR value LIKE(CONCAT(CONCAT('%',:criteria),'%')))")
	Page<Message> findMy(String user, String criteria, Pageable page);

	/**
	 * Return all messages the given user could have written, and by criteria. The main difference with the function
	 * {@link #findMy(String, String, Pageable)} is the messages returned includes the one the given user is not
	 * involved, or targeted.
	 * For sample, with this function, a user can see all messages from a group because this group is visible by this
	 * user.
	 * But with the other function {@link #findMy(String, String, Pageable)} these messages will be returned
	 * because this user is not member of this group.
	 * 
	 * @param user
	 *            The user requesting the messages.
	 * @param criteria
	 *            Optional text to filter the messages.
	 * @param page
	 *            The ordering and page data.
	 * @return The related messages
	 */
	@Query(VISIBLE_MESSAGES + " AND (targetType LIKE(CONCAT(CONCAT('%',:criteria),'%'))"
			+ "                 OR target LIKE(CONCAT(CONCAT('%',:criteria),'%')) OR value LIKE(CONCAT(CONCAT('%',:criteria),'%')))")
	Page<Message> findAll(String user, String criteria, Pageable page);

	/**
	 * Return the amount of unread messages since the last time this user has read them.
	 * 
	 * @param user
	 *            The user requesting the counter.
	 * @return the amount of unread messages since the last time this user has read them.
	 */
	@Query("SELECT COUNT(m.id) " + MY_MESSAGES
			+ " AND m.id > (SELECT CASE mr.message WHEN NULL THEN 0 ELSE mr.message END FROM MessageRead  mr WHERE mr.id = :user)")
	int countUnread(String user);

	/**
	 * Return the amount of users targeted by the given configuration.
	 * 
	 * @param targetType
	 *            The message type.
	 * @param target
	 *            The target configuration : group, node, ...
	 * @return The amount of users targeted by the given configuration.
	 */
	@Query("SELECT COUNT(u.id) FROM CacheUser u WHERE :targetType IS NULL                             "
			+ "  OR (:targetType = 'USER'     AND :target = u.id)"
			+ "  OR (:targetType = 'GROUP'    AND ingroup2(u.id,:target,:target) IS TRUE)                     "
			+ "  OR (:targetType = 'COMPANY'  AND incompany2(u.id,:target,:target) IS TRUE)                   "
			+ "  OR (:targetType = 'PROJECT'  AND inprojectkey2(u.id,:target,u.id,:target) IS TRUE)   "
			+ "  OR (:targetType = 'NODE'     AND EXISTS(SELECT 1 FROM Subscription s INNER JOIN s.project p INNER JOIN s.node n000 WHERE "
			+ HIS_PROJECTS + " AND (n000.id = :target OR n000.id LIKE CONCAT(:target, ':%'))))")
	int audience(String targetType, String target);

	/**
	 * Delete the message matching to the given identifier if this message is visible to a specified user.
	 * 
	 * @param id
	 *            The message identifier.
	 * @param user
	 *            The user requesting the deletion.
	 * @return The amount of delete messages. Should be either <code>1</code> either <code>0</code>.
	 */
	@Modifying
	@Query("DELETE " + VISIBLE_MESSAGES + " AND m.id = :id")
	int deleteVisible(int id, String user);
}
