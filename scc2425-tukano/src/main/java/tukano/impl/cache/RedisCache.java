package tukano.impl.cache;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisCache {
	private static final String RedisHostname = System.getenv("REDIS_HOST_NAME");
	private static final String RedisKey = System.getenv("REDIS_KEY");
	private static final int REDIS_PORT = Integer.parseInt(System.getenv("REDIS_PORT"));
	private static final int REDIS_TIMEOUT = Integer.parseInt(System.getenv("REDIS_TIMEOUT"));
	private static final boolean Redis_USE_TLS = Boolean.parseBoolean(System.getenv("REDIS_USE_TLS"));
	
	private static JedisPool instance;
	
	public synchronized static JedisPool getCachePool() {
		if( instance != null)
			return instance;
		
		var poolConfig = new JedisPoolConfig();
		poolConfig.setMaxTotal(128);
		poolConfig.setMaxIdle(128);
		poolConfig.setMinIdle(16);
		poolConfig.setTestOnBorrow(true);
		poolConfig.setTestOnReturn(true);
		poolConfig.setTestWhileIdle(true);
		poolConfig.setNumTestsPerEvictionRun(3);
		poolConfig.setBlockWhenExhausted(true);
		instance = new JedisPool(poolConfig, RedisHostname, REDIS_PORT, REDIS_TIMEOUT, RedisKey, Redis_USE_TLS);
		return instance;
	}
}
