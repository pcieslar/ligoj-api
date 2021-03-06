package org.ligoj.app.resource.project;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.ligoj.app.api.NodeStatus;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.resource.node.EventVo;
import org.ligoj.app.resource.node.NodeResource;
import org.ligoj.app.resource.node.ParameterValueResource;
import org.ligoj.app.resource.node.ParameterValueVo;
import org.ligoj.app.resource.subscription.SubscriptionVo;
import org.ligoj.bootstrap.core.DescribedBean;

/**
 * JPA {@link Project} to detailed {@link ProjectVo} converter.
 */
class ToVoConverter implements Function<Project, ProjectVo> {

	/**
	 * Subscriptions.
	 */
	private List<Object[]> subscriptionsAndParam;

	/**
	 * Subscriptions status
	 */
	private Map<Integer, EventVo> subscriptionStatus;

	/**
	 * User converter used to serialize a safe data.
	 */
	private Function<String, ? extends UserOrg> userConverter;

	/**
	 * Constructor holding the data used to convert a {@link Project} to {@link ProjectVo}.
	 * 
	 * @param userConverter
	 *            The {@link Function} used to convert internal user identifier to described user.
	 * @param subscriptionsAndParam
	 *            The subscription (index 0, type {@link Subscription}) with parameter values (index 1, type
	 *            {@link ParameterValue}).
	 * @param subscriptionStatus
	 *            The subscriptions statuses. Key is the subscription identifier.
	 */
	protected ToVoConverter(final Function<String, ? extends UserOrg> userConverter, final List<Object[]> subscriptionsAndParam,
			final Map<Integer, EventVo> subscriptionStatus) {
		this.subscriptionsAndParam = subscriptionsAndParam;
		this.subscriptionStatus = subscriptionStatus;
		this.userConverter = userConverter;
	}

	@Override
	public ProjectVo apply(final Project entity) {
		final ProjectVo vo = new ProjectVo();
		vo.copyAuditData(entity, userConverter);
		DescribedBean.copy(entity, vo);
		vo.setPkey(entity.getPkey());
		vo.setTeamLeader(userConverter.apply(entity.getTeamLeader()));

		// Build the subscriptions
		final Map<Integer, SubscriptionVo> subscriptions = new TreeMap<>();
		for (final Object[] resultSet : this.subscriptionsAndParam) {
			// Add subscription value
			final ParameterValue parameterValue = (ParameterValue) resultSet[1];
			addVo(subscriptions, (Subscription) resultSet[0]).getParameters().put(parameterValue.getParameter().getId(),
					ParameterValueResource.parseValue(parameterValue, new ParameterValueVo()));
		}

		// Merge with subscription without parameters
		entity.getSubscriptions().forEach(s -> addVo(subscriptions, s));

		// Return the subscription to order by the related node
		vo.setSubscriptions(subscriptions.values().stream().sorted(Comparator.comparing(s -> s.getNode().getId(), String::compareTo))
				.collect(Collectors.toList()));
		return vo;
	}

	/**
	 * Convert a {@link Subscription} to a {@link SubscriptionVo} with status, and put it in the target map if not
	 * existing.
	 * 
	 * @param subscriptions
	 *            The map of already converted entities.
	 * @param entity
	 *            The {@link Subscription}
	 * @return The related converted {@link SubscriptionVo} newly created or existing one.
	 */
	private SubscriptionVo addVo(final Map<Integer, SubscriptionVo> subscriptions, final Subscription entity) {
		return subscriptions.computeIfAbsent(entity.getId(), id -> {
			// Build the subscription root instance
			final SubscriptionVo vo = new SubscriptionVo();
			vo.copyAuditData(entity, userConverter);
			vo.setId(entity.getId());
			vo.setNode(NodeResource.toVo(entity.getNode()));
			vo.setParameters(new HashMap<>());
			subscriptions.put(entity.getId(), vo);

			// Add subscription status
			final EventVo lastEvent = subscriptionStatus.get(entity.getId());
			if (lastEvent != null) {
				vo.setStatus(NodeStatus.valueOf(lastEvent.getValue()));
			}
			return vo;
		});
	}

}
