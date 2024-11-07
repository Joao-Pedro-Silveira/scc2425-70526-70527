package tukano.impl.cache;

import io.github.cdimascio.dotenv.Dotenv;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisCache {

	private static Dotenv dotenv = Dotenv.load();
	
	private static final String RedisHostname = dotenv.get("REDIS_HOST_NAME");
	private static final String RedisKey = dotenv.get("REDIS_KEY");
	private static final int REDIS_PORT = 6380;
	private static final int REDIS_TIMEOUT = 1000;
	private static final boolean Redis_USE_TLS = dotenv.get("REDIS_USE_TLS").equals("true");
	private static final boolean cache = Boolean.parseBoolean(dotenv.get("CACHE"));

	
	private static JedisPool instance;
	
	public synchronized static JedisPool getCachePool() {
		if(cache){
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
		return null;
	}
}
