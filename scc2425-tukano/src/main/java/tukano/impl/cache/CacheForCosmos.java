package tukano.impl.cache;

import java.util.List;

import tukano.api.Result;
import utils.JSON;

public class CacheForCosmos {
	
	public static <T> Result<T> getOne(String id, Class<T> clazz) {

		var jedis = RedisCache.getCachePool().getResource();
		var key = clazz.getSimpleName() + ":" + id;
		var value = jedis.get(key);

		if(value != null){
			return Result.ok( JSON.decode(value, clazz));
		} 
        return Result.error(Result.ErrorCode.NOT_FOUND);
	}
	
	public static <T> Result<?> deleteOne(String id, T obj) {

		var jedis = RedisCache.getCachePool().getResource();
		var key = obj.getClass().getSimpleName() + ":" + id;
		jedis.del(key);

		return Result.ok();
	}
	
	public static <T> Result<T> updateOne(String id, T obj) {

		var jedis = RedisCache.getCachePool().getResource();
		var key = obj.getClass().getSimpleName() + ":" + id;

		jedis.set(key, JSON.encode(obj));

		return Result.ok(obj);
	}
	
	public static <T> Result<T> insertOne(String id, T obj) {

        var jedis = RedisCache.getCachePool().getResource();
        var key = obj.getClass().getSimpleName() + ":" + id;
        jedis.set(key, JSON.encode(obj));
		
		return Result.ok(obj);
	}
}
