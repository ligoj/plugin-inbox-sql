/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.inbox.sql.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.ligoj.bootstrap.core.model.AbstractBusinessEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * A simple entity holding the last read message by a user. Identifier ({@link #getId()} is the login.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_MESSAGE_USER_READ")
public class MessageRead extends AbstractBusinessEntity<String> {

	/**
	 * Identifier of the last read message. It's not a foreign key to allow message deletion without updating this
	 * value.
	 */
	private int message;
}
