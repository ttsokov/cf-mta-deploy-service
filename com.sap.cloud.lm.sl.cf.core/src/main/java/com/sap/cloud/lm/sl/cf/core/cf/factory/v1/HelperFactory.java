package com.sap.cloud.lm.sl.cf.core.cf.factory.v1;

import static com.sap.cloud.lm.sl.common.util.CommonUtil.cast;

import java.util.List;
import java.util.function.BiFunction;

import com.sap.cloud.lm.sl.cf.core.cf.factory.HelperFactoryConstructor;
import com.sap.cloud.lm.sl.cf.core.cf.v1.ApplicationsCloudModelBuilder;
import com.sap.cloud.lm.sl.cf.core.cf.v1.CloudModelConfiguration;
import com.sap.cloud.lm.sl.cf.core.cf.v1.ServiceKeysCloudModelBuilder;
import com.sap.cloud.lm.sl.cf.core.cf.v1.ServicesCloudModelBuilder;
import com.sap.cloud.lm.sl.cf.core.dao.ConfigurationEntryDao;
import com.sap.cloud.lm.sl.cf.core.helpers.XsPlaceholderResolver;
import com.sap.cloud.lm.sl.cf.core.helpers.v1.ApplicationColorAppender;
import com.sap.cloud.lm.sl.cf.core.helpers.v1.ConfigurationFilterParser;
import com.sap.cloud.lm.sl.cf.core.helpers.v1.ConfigurationReferencesResolver;
import com.sap.cloud.lm.sl.cf.core.helpers.v1.ConfigurationSubscriptionFactory;
import com.sap.cloud.lm.sl.cf.core.helpers.v1.PropertiesAccessor;
import com.sap.cloud.lm.sl.cf.core.helpers.v1.ResourceTypeFinder;
import com.sap.cloud.lm.sl.cf.core.helpers.v1.UserProvidedResourceResolver;
import com.sap.cloud.lm.sl.cf.core.model.ApplicationColor;
import com.sap.cloud.lm.sl.cf.core.model.CloudTarget;
import com.sap.cloud.lm.sl.cf.core.model.DeployedMta;
import com.sap.cloud.lm.sl.cf.core.util.ApplicationConfiguration;
import com.sap.cloud.lm.sl.cf.core.util.UserMessageLogger;
import com.sap.cloud.lm.sl.cf.core.validators.parameters.ParameterValidator;
import com.sap.cloud.lm.sl.cf.core.validators.parameters.v1.DescriptorParametersValidator;
import com.sap.cloud.lm.sl.mta.builders.v1.PropertiesChainBuilder;
import com.sap.cloud.lm.sl.mta.handlers.v1.DescriptorHandler;
import com.sap.cloud.lm.sl.mta.mergers.v1.PlatformMerger;
import com.sap.cloud.lm.sl.mta.model.SystemParameters;
import com.sap.cloud.lm.sl.mta.model.v1.DeploymentDescriptor;
import com.sap.cloud.lm.sl.mta.model.v1.Platform;

public class HelperFactory implements HelperFactoryConstructor {

    protected DescriptorHandler descriptorHandler;

    public HelperFactory(DescriptorHandler descriptorHandler) {
        this.descriptorHandler = descriptorHandler;
    }

    protected DescriptorHandler getHandler() {
        return cast(this.descriptorHandler);
    }

    @Override
    public ApplicationsCloudModelBuilder getApplicationsCloudModelBuilder(DeploymentDescriptor deploymentDescriptor,
        CloudModelConfiguration configuration, DeployedMta deployedMta, SystemParameters systemParameters,
        XsPlaceholderResolver xsPlaceholderResolver, String deployId) {
        return new ApplicationsCloudModelBuilder(deploymentDescriptor, configuration, deployedMta, systemParameters, xsPlaceholderResolver,
            deployId);
    }

    @Override
    public ApplicationsCloudModelBuilder getApplicationsCloudModelBuilder(DeploymentDescriptor deploymentDescriptor,
        CloudModelConfiguration configuration, DeployedMta deployedMta, SystemParameters systemParameters,
        XsPlaceholderResolver xsPlaceholderResolver, String deployId, UserMessageLogger userMessageLogger) {
        return new ApplicationsCloudModelBuilder(deploymentDescriptor, configuration, deployedMta, systemParameters, xsPlaceholderResolver,
            deployId, userMessageLogger);
    }

    @Override
    public ServicesCloudModelBuilder getServicesCloudModelBuilder(DeploymentDescriptor deploymentDescriptor,
        PropertiesAccessor propertiesAccessor, CloudModelConfiguration configuration) {
        return new ServicesCloudModelBuilder(deploymentDescriptor, propertiesAccessor, configuration);
    }

    @Override
    public ServicesCloudModelBuilder getServicesCloudModelBuilder(DeploymentDescriptor deploymentDescriptor,
        PropertiesAccessor propertiesAccessor, CloudModelConfiguration configuration, UserMessageLogger userMessageLogger) {
        return new ServicesCloudModelBuilder(deploymentDescriptor, propertiesAccessor, configuration, userMessageLogger);
    }

    @Override
    public ServiceKeysCloudModelBuilder getServiceKeysCloudModelBuilder(DeploymentDescriptor deploymentDescriptor,
        PropertiesAccessor propertiesAccessor) {
        return new ServiceKeysCloudModelBuilder(deploymentDescriptor, propertiesAccessor);
    }

    @Override
    public ConfigurationReferencesResolver getConfigurationReferencesResolver(DeploymentDescriptor deploymentDescriptor, Platform platform,
        BiFunction<String, String, String> spaceIdSupplier, ConfigurationEntryDao dao, CloudTarget cloudTarget,
        ApplicationConfiguration configuration) {
        return new ConfigurationReferencesResolver(dao,
            new ConfigurationFilterParser(cloudTarget, new PropertiesChainBuilder(deploymentDescriptor, platform)), spaceIdSupplier,
            cloudTarget, configuration);
    }

    @Override
    public ConfigurationReferencesResolver getConfigurationReferencesResolver(ConfigurationEntryDao dao,
        ConfigurationFilterParser filterParser, CloudTarget cloudTarget, ApplicationConfiguration configuration) {
        return new ConfigurationReferencesResolver(dao, filterParser, null, cloudTarget, configuration);
    }

    @Override
    public DescriptorParametersValidator getDescriptorParametersValidator(DeploymentDescriptor descriptor,
        List<ParameterValidator> parameterValidators) {
        return new DescriptorParametersValidator(descriptor, parameterValidators);
    }

    @Override
    public DescriptorParametersValidator getDescriptorParametersValidator(DeploymentDescriptor descriptor,
        List<ParameterValidator> parameterValidators, boolean doNotCorrect) {
        return new DescriptorParametersValidator(descriptor, parameterValidators, doNotCorrect);
    }

    @Override
    public ApplicationColorAppender getApplicationColorAppender(ApplicationColor deployedMtaColor, ApplicationColor applicationType) {
        return new ApplicationColorAppender(deployedMtaColor, applicationType);
    }

    @Override
    public ResourceTypeFinder getResourceTypeFinder(String resourceType) {
        return new ResourceTypeFinder(resourceType);
    }

    @Override
    public PlatformMerger getPlatformMerger(Platform platform) {
        return new PlatformMerger(platform, getHandler());
    }

    @Override
    public UserProvidedResourceResolver getUserProvidedResourceResolver(ResourceTypeFinder resourceHelper, DeploymentDescriptor descriptor,
        Platform platform) {
        return new UserProvidedResourceResolver(resourceHelper, descriptor, platform);
    }

    @Override
    public PropertiesAccessor getPropertiesAccessor() {
        return new PropertiesAccessor();
    }

    @Override
    public ConfigurationSubscriptionFactory getConfigurationSubscriptionFactory() {
        return new ConfigurationSubscriptionFactory();
    }

}
