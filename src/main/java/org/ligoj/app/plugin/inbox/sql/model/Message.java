/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.inbox.sql.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.hibernate.validator.constraints.Length;
import org.ligoj.bootstrap.core.model.AbstractAudited;

import lombok.Getter;
import lombok.Setter;

/**
 * A message to target audience.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_MESSAGE")
public class Message extends AbstractAudited<Integer> {

	/**
	 * Type of target (group, user, ...). When <code>null</code> the target is everybody.
	 * 
	 * @see MessageTargetType
	 */
	@Enumerated(EnumType.STRING)
	@NotNull
	@Column(length = 10)
	private MessageTargetType targetType;

	/**
	 * Optional related target : user, group, node, ...
	 */
	@NotNull
	private String target;

	/**
	 * Value of the message.
	 */
	@Length(max = 500)
	@NotNull
	@NotBlank
	private String value;
}
