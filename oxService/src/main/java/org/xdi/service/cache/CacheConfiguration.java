package org.xdi.service.cache;

import java.io.Serializable;

import javax.enterprise.inject.Vetoed;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

/**
 * @author yuriyz on 02/21/2017.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Vetoed
public class CacheConfiguration implements Serializable {

	private static final long serialVersionUID = 5047285980342633402L;

	private CacheProviderType cacheProviderType = CacheProviderType.IN_MEMORY;

    private MemcachedConfiguration memcachedConfiguration;

    private InMemoryConfiguration inMemoryConfiguration = new InMemoryConfiguration();

    private RedisConfiguration redisConfiguration;

    public RedisConfiguration getRedisConfiguration() {
        return redisConfiguration;
    }

    public void setRedisConfiguration(RedisConfiguration redisConfiguration) {
        this.redisConfiguration = redisConfiguration;
    }

    public CacheProviderType getCacheProviderType() {
        return cacheProviderType;
    }

    public void setCacheProviderType(CacheProviderType cacheProviderType) {
        this.cacheProviderType = cacheProviderType;
    }

    public InMemoryConfiguration getInMemoryConfiguration() {
        return inMemoryConfiguration;
    }

    public void setInMemoryConfiguration(InMemoryConfiguration inMemoryConfiguration) {
        this.inMemoryConfiguration = inMemoryConfiguration;
    }

    public MemcachedConfiguration getMemcachedConfiguration() {
        return memcachedConfiguration;
    }

    public void setMemcachedConfiguration(MemcachedConfiguration memcachedConfiguration) {
        this.memcachedConfiguration = memcachedConfiguration;
    }

    @Override
    public String toString() {
        return "CacheConfiguration{" +
                "cacheProviderType=" + cacheProviderType +
                ", memcachedConfiguration=" + memcachedConfiguration +
                ", redisConfiguration=" + redisConfiguration +
                ", inMemoryConfiguration=" + inMemoryConfiguration +
                '}';
    }
}
