package com.sap.cloud.lm.sl.cf.core.helpers.v2;

import static com.sap.cloud.lm.sl.cf.core.util.NameUtil.getIndexedName;
import static com.sap.cloud.lm.sl.common.util.MapUtil.merge;

import java.util.Map;

import com.sap.cloud.lm.sl.cf.core.dao.ConfigurationEntryDao;
import com.sap.cloud.lm.sl.cf.core.model.ConfigurationEntry;
import com.sap.cloud.lm.sl.cf.core.util.ApplicationConfiguration;
import com.sap.cloud.lm.sl.common.util.JsonUtil;
import com.sap.cloud.lm.sl.mta.model.v2.Resource;

public class ConfigurationReferenceResolver extends com.sap.cloud.lm.sl.cf.core.helpers.v1.ConfigurationReferenceResolver {

    public ConfigurationReferenceResolver(ConfigurationEntryDao dao, ApplicationConfiguration configuration) {
        super(dao, configuration);
    }

    @Override
    protected Resource asResource(ConfigurationEntry entry, com.sap.cloud.lm.sl.mta.model.v1.Resource resource, int index,
        int entriesCount) {
        return asResource(entry, (Resource) resource, index, entriesCount);
    }

    private Resource asResource(ConfigurationEntry entry, Resource resource, int index, int entriesCount) {
        String indexedResourceName = getIndexedName(resource.getName(), index, entriesCount, RESOURCE_INDEX_DELIMITER);
        Map<String, Object> properties = mergeProperties(resource, entry);
        Map<String, Object> parameters = removeConfigurationParameters(resource.getParameters());
        Resource.Builder builder = getResourceBuilder();
        builder.setName(indexedResourceName);
        builder.setDescription(resource.getDescription());
        builder.setProperties(properties);
        builder.setParameters(parameters);
        return builder.build();
    }

    @Override
    protected Resource.Builder getResourceBuilder() {
        return new Resource.Builder();
    }

    @Override
    protected Map<String, Object> mergeProperties(com.sap.cloud.lm.sl.mta.model.v1.Resource resource,
        ConfigurationEntry configurationEntry) {
        return merge(JsonUtil.convertJsonToMap(configurationEntry.getContent()), resource.getProperties());
    }

}
