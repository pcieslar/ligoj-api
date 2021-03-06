package org.ligoj.app.iam.model;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * Cache object of a company.<br>
 * "id" corresponds to the normalized name.<br>
 * "name" corresponds to the real name, not normalized.<br>
 * "description" corresponds to the normalized "Distinguished Name".
 */
@Entity
@Table(name = "LIGOJ_CACHE_COMPANY", uniqueConstraints= @UniqueConstraint(columnNames="name"))
public class CacheCompany extends CacheContainer {

	// Only a template class implementation

}
