package org.ligoj.app.resource.subscription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DELETE;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Persistable;
import org.springframework.stereotype.Service;

import org.ligoj.bootstrap.core.DescribedBean;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.security.SecurityHelper;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.ligoj.app.api.ConfigurablePlugin;
import org.ligoj.app.api.ConfigurationVo;
import org.ligoj.app.api.NodeVo;
import org.ligoj.app.api.ServicePlugin;
import org.ligoj.app.api.SubscriptionMode;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.dao.EventRepository;
import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.dao.ProjectRepository;
import org.ligoj.app.dao.SubscriptionRepository;
import org.ligoj.app.model.EventType;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.app.resource.node.EventResource;
import org.ligoj.app.resource.node.EventVo;
import org.ligoj.app.resource.node.NodeResource;
import org.ligoj.app.resource.node.ParameterValueEditionVo;
import org.ligoj.app.resource.node.ParameterValueResource;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link Subscription} resource.
 */
@Path("/subscription")
@Service
@Transactional
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class SubscriptionResource {

	@Autowired
	private SubscriptionRepository repository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private NodeRepository nodeRepository;

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private ParameterValueResource parameterValueResource;

	@Autowired
	private ServicePluginLocator servicePluginLocator;

	@Autowired
	private EventResource eventResource;

	@Autowired
	private SecurityHelper securityHelper;

	@Autowired
	private NodeResource nodeResource;

	/**
	 * {@link SubscriptionEditionVo} to JPA entity transformer.
	 * 
	 * @param vo
	 *            The object to convert.
	 * @return The mapped entity.
	 */
	public static Subscription toEntity(final SubscriptionEditionVo vo) {
		final Subscription entity = new Subscription();
		final Node node = new Node();
		node.setId(vo.getNode());
		entity.setNode(node);
		final Project project = new Project();
		project.setId(vo.getProject());
		entity.setProject(project);
		entity.setId(vo.getId());
		return entity;
	}

	/**
	 * Return non secured parameters values related to the subscription.The attached project is validated against the
	 * current user to check it is visible. Secured parameters (even the encrypted ones) are not returned. The
	 * visibility of this subscription is checked.
	 * 
	 * @param id
	 *            The subscription identifier.
	 * @return secured associated parameters values. Key of returned map is the identifier of
	 *         {@link org.ligoj.app.model.Parameter}
	 */
	@GET
	@Path("{id:\\d+}")
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public Map<String, String> getNonSecuredParameters(@PathParam("id") final int id) {
		return parameterValueResource.getNonSecuredSubscriptionParameters(checkVisibleSubscription(id).getId());
	}

	/**
	 * Return tools specific configuration. Only non secured parameters are returned.
	 * 
	 * @param id
	 *            The subscription identifier.
	 * @return tools specific configuration.
	 */
	@GET
	@Path("{id:\\d+}/configuration")
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public ConfigurationVo getConfiguration(@PathParam("id") final int id) throws Exception {

		// Copy subscription details
		final Subscription entity = checkVisibleSubscription(id);
		final ConfigurationVo vo = new ConfigurationVo();
		vo.setNode(NodeResource.toVo(entity.getNode()));
		vo.setParameters(getNonSecuredParameters(id));
		vo.setSubscription(id);
		vo.setProject(DescribedBean.clone(entity.getProject()));

		// Get specific configuration
		final ConfigurablePlugin servicePlugin = servicePluginLocator.getResource(vo.getNode().getId(), ConfigurablePlugin.class);
		if (servicePlugin != null) {
			// Specific configuration is available
			vo.setConfiguration(servicePlugin.getConfiguration(id));
		}
		return vo;
	}

	/**
	 * Return all parameters values related to the subscription. The attached project is validated against the current
	 * user to check it is visible. Beware, these parameters must not be returned to user, since clear encrypted
	 * parameters are present.
	 * 
	 * @param id
	 *            The subscription identifier.
	 * @return all associated parameters values. Key of returned map is the identifier of
	 *         {@link org.ligoj.app.model.Parameter}
	 */
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public Map<String, String> getParameters(final int id) {
		checkVisibleSubscription(id);
		return getParametersNoCheck(id);
	}

	/**
	 * Return all parameters values related to the subscription. The visibility of attached project is not checked in
	 * this case. Secured (encrypted) parameters are decrypted.
	 * 
	 * @param id
	 *            The subscription identifier.
	 * @return all associated parameters values. Key of returned map is the identifier of
	 *         {@link org.ligoj.app.model.Parameter}
	 */
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public Map<String, String> getParametersNoCheck(final int id) {
		return parameterValueResource.getSubscriptionParameters(id);
	}

	/**
	 * Create subscription.
	 * 
	 * @param vo
	 *            the subscription.
	 * @return the created {@link Subscription}.
	 */
	@POST
	public int create(final SubscriptionEditionVo vo) throws Exception {

		// Validate entities
		final Project project = checkVisibleProject(vo.getProject());
		checkManagedProject(vo.getProject());
		checkManagedNodeForSubscription(vo.getNode());

		final List<Parameter> acceptedParameters = nodeRepository.getOrphanParameters(vo.getNode(), vo.getMode(), securityHelper.getLogin());

		// Check all mandatory parameters for the current subscription mode
		vo.setParameters(ObjectUtils.defaultIfNull(vo.getParameters(), new ArrayList<>()));
		checkMandatoryParameters(vo.getParameters(), acceptedParameters, vo.getMode());

		// Check there is no override
		checkOverrides(acceptedParameters.stream().map(Parameter::getId).collect(Collectors.toList()),
				vo.getParameters().stream().map(ParameterValueEditionVo::getParameter).collect(Collectors.toList()));

		// Create subscription and parameters that would be removed in case of roll-back because of invalid parameters
		final Subscription entity = toEntity(vo);

		// Expose the real entity for plug-in since we have loaded it
		entity.setProject(project);

		// Save this subscription in the transaction
		repository.saveAndFlush(entity);
		parameterValueResource.create(vo.getParameters(), (ParameterValue value) -> value.setSubscription(entity));

		// Delegate the creation --> exception can appear there, causing to roll-back the previous persists
		ServicePlugin plugin = servicePluginLocator.getResource(vo.getNode());
		while (plugin != null) {
			if (vo.getMode() == SubscriptionMode.CREATE) {
				plugin.create(entity.getId());
			} else {
				plugin.link(entity.getId());
			}
			// Also call the parent
			plugin = servicePluginLocator.getResource(servicePluginLocator.getParent(plugin.getKey()));
		}

		// Check again the parameters in the final state
		checkMandatoryParameters(vo.getParameters(), acceptedParameters, SubscriptionMode.CREATE);
		log.info("Subscription of project {} to service {}", vo.getProject(), vo.getNode());

		return entity.getId();
	}

	/**
	 * Check the current user can subscribe a project to the given visible node.
	 * 
	 * @param node
	 *            The node to subscribe.
	 */
	private void checkManagedNodeForSubscription(final String node) {
		Optional.ofNullable(nodeRepository.findOneForSubscription(node, securityHelper.getLogin()))
				.orElseThrow(() -> new ValidationJsonException("id", BusinessException.KEY_UNKNOW_ID, "0", "node", "1", node));
	}

	/**
	 * Check the given parameters do not overrides a valued parameter.
	 */
	private void checkOverrides(final List<String> acceptedParameters, final List<String> parameters) {
		final Collection<String> overrides = org.apache.commons.collections4.CollectionUtils.removeAll(parameters, acceptedParameters);
		if (!overrides.isEmpty()) {
			// A non acceptable parameter. An attempt to override a secured data?
			throw ValidationJsonException.newValidationJsonException("not-accepted-parameter", overrides.iterator().next());
		}
	}

	/**
	 * Check mandatory parameters are provided.
	 */
	protected void checkMandatoryParameters(final List<ParameterValueEditionVo> parameters, final List<Parameter> acceptedParameters,
			final SubscriptionMode mode) {
		// Check each mandatory parameter for the current mode
		acceptedParameters.stream().filter(parameter -> (parameter.getMode() == mode || parameter.getMode() == null) && parameter.isMandatory())
				.forEach(parameter -> checkMandatoryParameter(parameters, parameter));
	}

	/**
	 * Check mandatory parameter is provided.
	 */
	private void checkMandatoryParameter(final Collection<ParameterValueEditionVo> parameters, final Persistable<String> parameter) {
		// Have to find this parameter
		if (parameters.stream().noneMatch(value -> value.getParameter().equals(parameter.getId()))) {
			// Missing mandatory parameter
			throw ValidationJsonException.newValidationJsonException(NotNull.class.getSimpleName(), parameter.getId());
		}
	}

	/**
	 * Delete entity and cascaded associations : parameters, events then subscription. Note that remote data are not
	 * deleted. Links are just destroyed.
	 * 
	 * @param id
	 *            the entity identifier.
	 */
	@Path("{id:\\d+}")
	@DELETE
	public void delete(@PathParam("id") final int id) throws Exception {
		// Deletion without remote deletion
		delete(id, false);
	}

	/**
	 * Delete entity and cascaded associations : parameters, events then subscription.
	 * 
	 * @param id
	 *            the entity identifier.
	 * @param deleteRemoteData
	 *            When <code>true</code>, remote data will be also destroyed.
	 */
	@Path("{id:\\d+}/{deleteRemoteData}")
	@DELETE
	public void delete(@PathParam("id") final int id, @PathParam("deleteRemoteData") final boolean deleteRemoteData) throws Exception {
		final Subscription entity = checkVisibleSubscription(id);
		checkManagedProject(entity.getProject().getId());

		// Delete the events
		eventRepository.deleteAllBy("subscription", entity);

		// Delegate the deletion
		ServicePlugin plugin = servicePluginLocator.getResource(entity.getNode().getId());
		while (plugin != null) {
			plugin.delete(id, deleteRemoteData);
			plugin = servicePluginLocator.getResource(servicePluginLocator.getParent(plugin.getKey()));
		}
		parameterValueResource.deleteBySubscription(id);
		repository.delete(entity);
	}

	/**
	 * Check the associated project is managed for current user. Currently, a managed project is a project where
	 * subscription can be managed.
	 */
	private void checkManagedProject(final int project) {
		if (null == projectRepository.isManageSubscription(project, securityHelper.getLogin())) {
			// Not managed associated project
			log.warn("Attempt to manage a project '" + project + "' out of scope");
			throw new ForbiddenException();
		}
	}

	/**
	 * Check the associated project is visible for current user.
	 * 
	 * @param id
	 *            Project's identifier.
	 * @return the loaded project.
	 */
	private Project checkVisibleProject(final int id) {
		final Project project = projectRepository.findOneVisible(id, securityHelper.getLogin());
		if (project == null) {
			// Associated project is not visible
			throw new EntityNotFoundException(String.valueOf(id));
		}
		return project;
	}

	/**
	 * Check the given subscription is visible.
	 * 
	 * @param id
	 *            Subscription identifier.
	 * @return the loaded subscription.
	 */
	public Subscription checkVisibleSubscription(final int id) {
		final Subscription entity = repository.findOneExpected(id);
		checkVisibleProject(entity.getProject().getId());
		return entity;
	}

	/**
	 * Return all subscriptions and related nodes. Very light data are returned there since a lot of subscriptions be
	 * there. Parameters values are not fetch.
	 * 
	 * @return Status of each subscription of given project.
	 */
	@GET
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public SubscriptionListVo findAll() {

		// First, list visible projects having at least one subscription
		final List<Object[]> projects = projectRepository.findAllHavingSubscription(securityHelper.getLogin());

		/*
		 * Then, return involved subscriptions relating theses projects. SQL "IN" is not used, because of size
		 * limitations. Structure : project.id, service.id
		 */
		final List<Object[]> subscriptions = repository.findAllLight(securityHelper.getLogin());

		/*
		 * Then, fetch all nodes. SQL "IN" is not used, because of size limitations. They will be filtered against
		 * subscriptions associated to a visible project.
		 */
		final Map<String, NodeVo> nodes = nodeResource.findAll();

		// Fill the target structure
		final SubscriptionListVo result = new SubscriptionListVo();

		// Fill the projects
		final Map<Integer, SubscribingProjectVo> projectsMap = new HashMap<>();
		result.setProjects(projects.stream().map(rs -> {

			// Build the project
			final SubscribingProjectVo project = new SubscribingProjectVo();
			project.setId((Integer) rs[0]);
			project.setName((String) rs[1]);
			project.setPkey((String) rs[2]);

			// Also save it for indexed search
			projectsMap.put(project.getId(), project);
			return project;
		}).collect(Collectors.toList()));

		// Prepare the subscriptions container with project name ordering
		result.setSubscriptions(new TreeSet<>((o1, o2) -> (projectsMap.get(o1.getProject()).getName() + "," + o1.getId())
				.compareToIgnoreCase(projectsMap.get(o2.getProject()).getName() + "," + o2.getId())));

		// Fill the node with associated projects
		final Map<String, SubscribedNodeVo> filteredNodes = new TreeMap<>();
		subscriptions.forEach(rs -> {
			// Build the subscription data
			final SubscriptionLightVo vo = new SubscriptionLightVo();
			vo.setId((Integer) rs[0]);
			vo.setProject((Integer) rs[1]);
			vo.setNode((String) rs[2]);
			result.getSubscriptions().add(vo);

			// Add the related node
			final NodeVo node = nodes.get(vo.getNode());
			addNodeAsNeeded(filteredNodes, nodes, node);
		});
		result.setNodes(filteredNodes.values());
		return result;
	}

	/**
	 * Add a node to the filtered nodes, and also add recursively the parent.
	 */
	private void addNodeAsNeeded(final Map<String, SubscribedNodeVo> filteredNodes, final Map<String, NodeVo> allNodes, final NodeVo node) {
		if (!filteredNodes.containsKey(node.getId())) {

			// Build the node wrapper
			final SubscribedNodeVo subscribedNode = new SubscribedNodeVo();
			DescribedBean.copy(node, subscribedNode);
			subscribedNode.setTag(node.getTag());
			subscribedNode.setTagUiClasses(node.getTagUiClasses());
			filteredNodes.put(node.getId(), subscribedNode);

			// Now check the parent exists or not and add it to the target filtered nodes
			if (node.getRefined() != null) {

				// Completed the previous link
				subscribedNode.setRefined(node.getRefined().getId());

				// Add the parent too (as needed
				addNodeAsNeeded(filteredNodes, allNodes, allNodes.get(subscribedNode.getRefined()));
			}
		}
	}

	/**
	 * Retrieve the last known status of subscriptions of given project .
	 * 
	 * @param project
	 *            project identifier
	 * @return Status of each subscription of given project.
	 */
	@Path("status/{project:\\d+}")
	@GET
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public Map<Integer, EventVo> getStatusByProject(@PathParam("project") final int project) {
		return eventRepository.findLastEvents(project).stream().map(EventResource::toVo)
				.collect(Collectors.toMap(EventVo::getSubscription, Function.identity()));
	}

	/**
	 * Get fresh status of given subscription. This fresh status is also stored in the data base. The project must be
	 * visible to current user.
	 * 
	 * @param id
	 *            Node identifier
	 * @return Status of each subscription of given project.
	 */
	@Path("status/{id:\\d+}/refresh")
	@GET
	public SubscriptionStatusWithData refreshStatus(@PathParam("id") final int id) {
		return refreshSubscription(checkVisibleSubscription(id));
	}

	/**
	 * Get fresh status of a set of subscriptions. This a loop shortcut of the per-subscription call.
	 * 
	 * @param ids
	 *            Node identifiers
	 * @return Status of each subscription of given project. Order is not guaranted.
	 * @see #refreshStatus(int)
	 */
	@Path("status/refresh")
	@GET
	public Map<Integer, SubscriptionStatusWithData> refreshStatuses(@QueryParam("id") final Set<Integer> ids) {
		return ids.stream().map(this::refreshStatus).collect(Collectors.toMap(SubscriptionStatusWithData::getId, Function.identity()));
	}

	/**
	 * Refresh given subscriptions and return their status.
	 */
	private SubscriptionStatusWithData refreshSubscription(final Subscription subscription) {
		final String toolKey = subscription.getNode().getId();
		final Map<String, String> parameters = getParameters(subscription.getId());
		final SubscriptionStatusWithData statusWithData = nodeResource.checkSubscriptionStatus(toolKey, parameters);
		statusWithData.setId(subscription.getId());
		statusWithData.setProject(subscription.getProject().getId());
		statusWithData.setParameters(parameterValueResource.getNonSecuredSubscriptionParameters(subscription.getId()));

		// Update the last event with fresh data
		eventResource.registerEvent(subscription, EventType.STATUS, statusWithData.getStatus().name());

		// Return the fresh statuses
		return statusWithData;
	}
}