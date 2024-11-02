package tukano.impl.cache;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import io.github.cdimascio.dotenv.Dotenv;

public class RedisCache {

	private static Dotenv dotenv = Dotenv.configure().load();

	private static final String RedisHostname = dotenv.get("REDIS_HOST_NAME");
	private static final String RedisKey = dotenv.get("REDIS_KEY");
	private static final int REDIS_PORT = Integer.parseInt(dotenv.get("REDIS_PORT"));
	private static final int REDIS_TIMEOUT = Integer.parseInt(dotenv.get("REDIS_TIMEOUT"));
	private static final boolean Redis_USE_TLS = Boolean.parseBoolean(dotenv.get("REDIS_USE_TLS"));
	
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
