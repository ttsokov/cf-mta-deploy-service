package com.sap.cloud.lm.sl.cf.core.cf.v1;

import static com.sap.cloud.lm.sl.mta.util.PropertiesUtil.getPropertyValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.cloudfoundry.client.lib.domain.DockerInfo;
import org.cloudfoundry.client.lib.domain.Staging;

import com.sap.cloud.lm.sl.cf.client.lib.domain.CloudApplicationExtended;
import com.sap.cloud.lm.sl.cf.client.lib.domain.CloudTask;
import com.sap.cloud.lm.sl.cf.client.lib.domain.ServiceKeyToInject;
import com.sap.cloud.lm.sl.cf.core.cf.DeploymentMode;
import com.sap.cloud.lm.sl.cf.core.cf.HandlerFactory;
import com.sap.cloud.lm.sl.cf.core.helpers.XsPlaceholderResolver;
import com.sap.cloud.lm.sl.cf.core.helpers.v1.PropertiesAccessor;
import com.sap.cloud.lm.sl.cf.core.message.Messages;
import com.sap.cloud.lm.sl.cf.core.model.DeployedMta;
import com.sap.cloud.lm.sl.cf.core.model.DeployedMtaModule;
import com.sap.cloud.lm.sl.cf.core.model.SupportedParameters;
import com.sap.cloud.lm.sl.cf.core.parser.DockerInfoParser;
import com.sap.cloud.lm.sl.cf.core.parser.MemoryParametersParser;
import com.sap.cloud.lm.sl.cf.core.parser.ParametersParser;
import com.sap.cloud.lm.sl.cf.core.parser.StagingParametersParser;
import com.sap.cloud.lm.sl.cf.core.parser.TaskParametersParser;
import com.sap.cloud.lm.sl.cf.core.util.CloudModelBuilderUtil;
import com.sap.cloud.lm.sl.cf.core.util.UserMessageLogger;
import com.sap.cloud.lm.sl.common.ContentException;
import com.sap.cloud.lm.sl.common.util.ListUtil;
import com.sap.cloud.lm.sl.mta.builders.v1.PropertiesChainBuilder;
import com.sap.cloud.lm.sl.mta.handlers.v1.DescriptorHandler;
import com.sap.cloud.lm.sl.mta.model.SystemParameters;
import com.sap.cloud.lm.sl.mta.model.v1.DeploymentDescriptor;
import com.sap.cloud.lm.sl.mta.model.v1.Module;
import com.sap.cloud.lm.sl.mta.model.v1.Resource;
import com.sap.cloud.lm.sl.mta.util.PropertiesUtil;

public class ApplicationsCloudModelBuilder {

    public static final String DEPENDENCY_TYPE_SOFT = "soft";
    public static final String DEPENDENCY_TYPE_HARD = "hard";

    private static final int MTA_MAJOR_VERSION = 1;

    protected DescriptorHandler handler;
    protected PropertiesChainBuilder propertiesChainBuilder;
    protected PropertiesAccessor propertiesAccessor;
    protected DeploymentDescriptor deploymentDescriptor;

    protected CloudModelConfiguration configuration;
    protected ApplicationUrisCloudModelBuilder urisCloudModelBuilder;
    protected ApplicationEnvironmentCloudModelBuilder applicationEnvCloudModelBuilder;
    protected CloudServiceNameMapper cloudServiceNameMapper;
    protected XsPlaceholderResolver xsPlaceholderResolver;
    protected DeployedMta deployedMta;
    protected UserMessageLogger userMessageLogger;

    public ApplicationsCloudModelBuilder(DeploymentDescriptor deploymentDescriptor, CloudModelConfiguration configuration,
        DeployedMta deployedMta, SystemParameters systemParameters, XsPlaceholderResolver xsPlaceholderResolver, String deployId) {
        HandlerFactory handlerFactory = createHandlerFactory();
        this.handler = handlerFactory.getDescriptorHandler();
        this.propertiesChainBuilder = createPropertiesChainBuilder(deploymentDescriptor);
        this.propertiesAccessor = handlerFactory.getPropertiesAccessor();
        this.deploymentDescriptor = deploymentDescriptor;
        this.configuration = configuration;
        this.urisCloudModelBuilder = new ApplicationUrisCloudModelBuilder(configuration.isPortBasedRouting(), systemParameters,
            propertiesAccessor);
        this.applicationEnvCloudModelBuilder = createApplicationEnvironmentCloudModelBuilder(configuration, deploymentDescriptor,
            xsPlaceholderResolver, handler, propertiesAccessor, deployId);
        this.cloudServiceNameMapper = new CloudServiceNameMapper(configuration, propertiesAccessor, deploymentDescriptor);
        this.xsPlaceholderResolver = xsPlaceholderResolver;
        this.deployedMta = deployedMta;
    }

    public ApplicationsCloudModelBuilder(DeploymentDescriptor deploymentDescriptor, CloudModelConfiguration configuration,
        DeployedMta deployedMta, SystemParameters systemParameters, XsPlaceholderResolver xsPlaceholderResolver, String deployId,
        UserMessageLogger userMessageLogger) {
        this(deploymentDescriptor, configuration, deployedMta, systemParameters, xsPlaceholderResolver, deployId);
        this.userMessageLogger = userMessageLogger;
    }

    protected HandlerFactory createHandlerFactory() {
        return new HandlerFactory(MTA_MAJOR_VERSION);
    }

    protected PropertiesChainBuilder createPropertiesChainBuilder(DeploymentDescriptor deploymentDescriptor) {
        return new PropertiesChainBuilder(deploymentDescriptor);
    }

    protected ApplicationEnvironmentCloudModelBuilder createApplicationEnvironmentCloudModelBuilder(CloudModelConfiguration configuration,
        DeploymentDescriptor deploymentDescriptor, XsPlaceholderResolver xsPlaceholderResolver, DescriptorHandler handler,
        PropertiesAccessor propertiesAccessor, String deployId) {
        return new ApplicationEnvironmentCloudModelBuilder(configuration, deploymentDescriptor, xsPlaceholderResolver, handler,
            propertiesAccessor, deployId);
    }

    public List<CloudApplicationExtended> build(Set<String> mtaModulesInArchive, Set<String> allMtaModules, Set<String> deployedModules) {
        initializeModulesDependecyTypes(deploymentDescriptor);
        List<CloudApplicationExtended> apps = resolveModules(mtaModulesInArchive, deployedModules);
        Set<String> unresolvedModules = getUnresolvedModules(apps, deployedModules, allMtaModules);
        if (!unresolvedModules.isEmpty()) {
            throw new ContentException(Messages.UNRESOLVED_MTA_MODULES, unresolvedModules);
        }

        return apps;
    }

    private List<CloudApplicationExtended> resolveModules(Set<String> mtaModulesInArchive, Set<String> deployedModules) {
        return handler
            .getModulesForDeployment(deploymentDescriptor, SupportedParameters.ENABLE_PARALLEL_DEPLOYMENTS,
                SupportedParameters.DEPENDENCY_TYPE, DEPENDENCY_TYPE_HARD)
            .stream()
            .filter(module -> shouldDeployModule(module, mtaModulesInArchive, deployedModules))
            .map(this::getApplication)
            .collect(Collectors.toList());
    }

    private boolean shouldDeployModule(Module module, Set<String> mtaModulesInArchive, Set<String> deployedModules) {
        if (isDockerModule(module)) {

            return true;
        }
        if (!isModulePresentInArchive(module, mtaModulesInArchive) || module.getType() == null) {
            if (isModuleDeployed(module, deployedModules)) {
                printMTAModuleNotFoundWarning(module.getName());
            }
            return false;
        }

        return true;
    }

    private boolean isDockerModule(Module module) {
        Map<String, Object> moduleParameters = propertiesAccessor.getParameters(module);

        return moduleParameters.containsKey(SupportedParameters.DOCKER);
    }

    private boolean isModulePresentInArchive(Module module, Set<String> modulesInArchive) {
        return modulesInArchive.contains(module.getName());
    }

    private boolean isModuleDeployed(Module module, Set<String> deployedModules) {
        return deployedModules.contains(module.getName());
    }

    private Set<String> getUnresolvedModules(List<CloudApplicationExtended> apps, Set<String> deployedModules, Set<String> allMtaModules) {
        Set<String> resolvedModules = apps.stream()
            .map(CloudApplicationExtended::getModuleName)
            .collect(Collectors.toSet());
        return SetUtils.difference(allMtaModules, SetUtils.union(resolvedModules, deployedModules))
            .toSet();
    }

    public DeploymentMode getDeploymentMode() {
        return DeploymentMode.SEQUENTIAL;
    }

    private void printMTAModuleNotFoundWarning(String moduleName) {
        if (userMessageLogger != null) {
            userMessageLogger.warn(Messages.NOT_DESCRIBED_MODULE, moduleName);
        }
    }

    protected void initializeModulesDependecyTypes(DeploymentDescriptor deploymentDescriptor) {
        for (Module module : deploymentDescriptor.getModules1()) {
            String dependencyType = getDependencyType(module);
            Map<String, Object> moduleProperties = propertiesAccessor.getParameters(module);
            moduleProperties.put(SupportedParameters.DEPENDENCY_TYPE, dependencyType);
            propertiesAccessor.setParameters(module, moduleProperties);
        }
    }

    protected String getDependencyType(Module module) {
        return (String) propertiesAccessor.getParameters(module)
            .getOrDefault(SupportedParameters.DEPENDENCY_TYPE, DEPENDENCY_TYPE_SOFT);
    }

    protected CloudApplicationExtended getApplication(Module module) {
        List<Map<String, Object>> propertiesList = propertiesChainBuilder.buildModuleChain(module.getName());
        Staging staging = parseParameters(propertiesList, new StagingParametersParser());
        int diskQuota = parseParameters(propertiesList, new MemoryParametersParser(SupportedParameters.DISK_QUOTA, "0"));
        int memory = parseParameters(propertiesList, new MemoryParametersParser(SupportedParameters.MEMORY, "0"));
        DockerInfo dockerInfo = parseParameters(propertiesList, new DockerInfoParser());
        int instances = (Integer) getPropertyValue(propertiesList, SupportedParameters.INSTANCES, 0);
        DeployedMtaModule deployedModule = findDeployedModule(deployedMta, module);
        List<String> uris = urisCloudModelBuilder.getApplicationUris(module, propertiesList, deployedModule);
        List<String> idleUris = urisCloudModelBuilder.getIdleApplicationUris(module, propertiesList);
        List<String> resolvedUris = xsPlaceholderResolver.resolve(uris);
        List<String> resolvedIdleUris = xsPlaceholderResolver.resolve(idleUris);
        List<String> allServices = getAllApplicationServices(module);
        List<ServiceKeyToInject> serviceKeysToInject = getServicesKeysToInject(module);
        Map<Object, Object> env = applicationEnvCloudModelBuilder.build(module, getApplicationServices(module));
        List<CloudTask> tasks = getTasks(propertiesList);
        return createCloudApplication(getApplicationName(module), module.getName(), staging, diskQuota, memory, instances, resolvedUris,
            resolvedIdleUris, allServices, serviceKeysToInject, env, tasks, dockerInfo);
    }

    protected DeployedMtaModule findDeployedModule(DeployedMta deployedMta, Module module) {
        return deployedMta == null ? null : deployedMta.findDeployedModule(module.getName());
    }

    protected String getApplicationName(Module module) {
        return (String) propertiesAccessor.getParameters(module)
            .get(SupportedParameters.APP_NAME);
    }

    protected <R> R parseParameters(List<Map<String, Object>> parametersList, ParametersParser<R> parser) {
        return parser.parse(parametersList);
    }

    protected List<String> getAllApplicationServices(Module module) {
        return getApplicationServices(module, this::allServicesRule);
    }

    protected List<String> getApplicationServices(Module module) {
        return getApplicationServices(module, this::filterExistingServicesRule);
    }

    protected List<String> getApplicationServices(Module module, Predicate<ResourceAndResourceType> filterRule) {
        List<String> services = new ArrayList<>();
        for (String dependencyName : module.getRequiredDependencies1()) {
            ResourceAndResourceType resourceAndResourceType = getApplicationService(dependencyName);
            if (resourceAndResourceType != null && filterRule.test(resourceAndResourceType)) {
                CollectionUtils.addIgnoreNull(services, cloudServiceNameMapper.mapServiceName(resourceAndResourceType.getResource(),
                    resourceAndResourceType.getResourceType()));
            }
        }
        return ListUtil.removeDuplicates(services);
    }

    protected boolean allServicesRule(ResourceAndResourceType resourceAndResourceType) {
        return true;
    }

    protected boolean filterExistingServicesRule(ResourceAndResourceType resourceAndResourceType) {
        return !isExistingService(resourceAndResourceType.getResourceType());
    }

    private boolean isExistingService(ResourceType resourceType) {
        return resourceType.equals(ResourceType.EXISTING_SERVICE);
    }

    protected ResourceAndResourceType getApplicationService(String dependencyName) {
        Resource resource = getResource(dependencyName);
        if (resource != null && CloudModelBuilderUtil.isService(resource, propertiesAccessor)) {
            ResourceType serviceType = CloudModelBuilderUtil.getResourceType(resource.getProperties());
            return new ResourceAndResourceType(resource, serviceType);
        }
        return null;
    }

    protected List<ServiceKeyToInject> getServicesKeysToInject(Module module) {
        List<ServiceKeyToInject> serviceKeysToInject = new ArrayList<>();
        for (String dependencyName : module.getRequiredDependencies1()) {
            ServiceKeyToInject serviceKeyToInject = getServiceKeyToInject(dependencyName);
            CollectionUtils.addIgnoreNull(serviceKeysToInject, serviceKeyToInject);
        }
        return serviceKeysToInject;
    }

    protected ServiceKeyToInject getServiceKeyToInject(String dependencyName) {
        Resource resource = getResource(dependencyName);
        if (resource != null && CloudModelBuilderUtil.isServiceKey(resource, propertiesAccessor)) {
            Map<String, Object> resourceParameters = propertiesAccessor.getParameters(resource);
            String serviceName = PropertiesUtil.getRequiredParameter(resourceParameters, SupportedParameters.SERVICE_NAME);
            String serviceKeyName = (String) resourceParameters.getOrDefault(SupportedParameters.SERVICE_KEY_NAME, resource.getName());
            return new ServiceKeyToInject(serviceKeyName, serviceName, serviceKeyName);
        }
        return null;
    }

    protected Resource getResource(String dependencyName) {
        return handler.findDependency(deploymentDescriptor, dependencyName)._1;
    }

    protected List<CloudTask> getTasks(List<Map<String, Object>> propertiesList) {
        return parseParameters(propertiesList, new TaskParametersParser(SupportedParameters.TASKS, configuration.isPrettyPrinting()));
    }

    protected static CloudApplicationExtended createCloudApplication(String name, String moduleName, Staging staging, int diskQuota,
        int memory, int instances, List<String> uris, List<String> idleUris, List<String> services,
        List<ServiceKeyToInject> serviceKeysToInject, Map<Object, Object> env, List<CloudTask> tasks, DockerInfo dockerInfo) {
        CloudApplicationExtended app = new CloudApplicationExtended(null, name);
        app.setModuleName(moduleName);
        app.setStaging(staging);
        app.setDiskQuota(diskQuota);
        app.setMemory(memory);
        app.setInstances(instances);
        app.setUris(uris);
        app.setIdleUris(idleUris);
        app.setServices(services);
        app.setServiceKeysToInject(serviceKeysToInject);
        app.setEnv(env);
        app.setTasks(tasks);
        app.setDockerInfo(dockerInfo);
        return app;
    }

}
