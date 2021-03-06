package com.sap.cloud.lm.sl.cf.core.helpers.v1;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.sap.cloud.lm.sl.cf.core.cf.v1.ResourceType;
import com.sap.cloud.lm.sl.common.util.Callable;
import com.sap.cloud.lm.sl.common.util.TestUtil;
import com.sap.cloud.lm.sl.common.util.TestUtil.Expectation;
import com.sap.cloud.lm.sl.mta.handlers.v1.ConfigurationParser;
import com.sap.cloud.lm.sl.mta.handlers.v1.DescriptorParser;
import com.sap.cloud.lm.sl.mta.model.v1.DeploymentDescriptor;
import com.sap.cloud.lm.sl.mta.model.v1.Platform;

@RunWith(Parameterized.class)
public class UserProvidedResourceResolverTest {

    protected static final String USER_PROVIDED_SERVICE_TYPE = ResourceType.USER_PROVIDED_SERVICE.toString();

    private String descriptorLocation;
    private String platformLocation;
    private Expectation expectation;

    protected UserProvidedResourceResolver resolver;

    @Parameters
    public static Collection<Object[]> getParameters() {
        return Arrays.asList(new Object[][] {
// @formatter:off
            {
                "mtad-07.yaml", "/mta/cf-platform.json", new Expectation(Expectation.Type.RESOURCE, "mtad-07.yaml.json"),
            },
            {
                "mtad-08.yaml", "/mta/cf-platform.json", new Expectation(Expectation.Type.RESOURCE, "mtad-08.yaml.json"),
            },
            {
                "mtad-09.yaml", "/mta/cf-platform.json", new Expectation(Expectation.Type.RESOURCE, "mtad-09.yaml.json"),
            },
// @formatter:on
        });
    }

    public UserProvidedResourceResolverTest(String descriptorLocation, String platformLocation, Expectation expectation) {
        this.descriptorLocation = descriptorLocation;
        this.platformLocation = platformLocation;
        this.expectation = expectation;
    }

    @Before
    public void setUp() throws Exception {
        DescriptorParser descriptorParser = getDescriptorParser();
        ConfigurationParser configurationParser = getConfigurationParser();

        InputStream descriptorYaml = getClass().getResourceAsStream(descriptorLocation);
        DeploymentDescriptor descriptor = descriptorParser.parseDeploymentDescriptorYaml(descriptorYaml);

        InputStream platformJson = getClass().getResourceAsStream(platformLocation);
        Platform platform = configurationParser.parsePlatformJson(platformJson);

        resolver = getUserProidedResourceResolver(descriptor, platform);
    }

    @Test
    public void testResolve() {
        TestUtil.test(new Callable<DeploymentDescriptor>() {

            @Override
            public DeploymentDescriptor call() throws Exception {
                return resolver.resolve();
            }
        }, expectation, getClass());
    }

    protected UserProvidedResourceResolver getUserProidedResourceResolver(DeploymentDescriptor descriptor, Platform platform) {
        return new UserProvidedResourceResolver(getResourceTypeFinder(), descriptor, platform);
    }

    protected ResourceTypeFinder getResourceTypeFinder() {
        return new ResourceTypeFinder(USER_PROVIDED_SERVICE_TYPE);
    }

    protected ConfigurationParser getConfigurationParser() {
        return new ConfigurationParser();
    }

    protected DescriptorParser getDescriptorParser() {
        return new DescriptorParser();
    }
}
