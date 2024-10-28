package utils;

import java.util.List;

import tukano.api.Result;
import tukano.impl.cache.RedisCache;

public class CosmosDB {

	public static <T> List<T> sql(String query, Class<T> clazz) {

        Result<List<T>> res = CosmosDB_NoSQL.getInstance().query(clazz, query);

        if(res.isOK()){
            return res.value();
        } else {
            return null;
        }
	}
	
	public static <T> List<T> sql(Class<T> clazz, String fmt, Object ... args) {
		Result<List<T>> res = CosmosDB_NoSQL.getInstance().query(clazz, String.format(fmt, args));

        if(res.isOK()){
            return res.value();
        } else {
            return null;
        }
	}
	
	public static <T> Result<T> getOne(String id, Class<T> clazz) {

		var jedis = RedisCache.getCachePool().getResource();
		var key = clazz.getSimpleName() + ":" + id;
		var value = jedis.get(key);

		if(value != null){
			return Result.ok( JSON.decode(value, clazz));

		} else{
			var res = CosmosDB_NoSQL.getInstance().getOne(id, clazz);
			if(res.isOK()){
				jedis.set(key, JSON.encode(res.value()));
			}
			return res;
		}
	}
	
	public static <T> Result<?> deleteOne(String id, T obj) {

		var jedis = RedisCache.getCachePool().getResource();
		var key = obj.getClass().getSimpleName() + ":" + id;
		jedis.del(key);

		return CosmosDB_NoSQL.getInstance().deleteOne(obj);
	}
	
	public static <T> Result<T> updateOne(String id, T obj) {

		var jedis = RedisCache.getCachePool().getResource();
		var key = obj.getClass().getSimpleName() + ":" + id;

		jedis.set(key, JSON.encode(obj));

		return CosmosDB_NoSQL.getInstance().updateOne(obj);
	}
	
	public static <T> Result<T> insertOne(String id, T obj) {

		Result<T> res = Result.errorOrValue(CosmosDB_NoSQL.getInstance().insertOne(obj), obj);

		if(res.isOK()){
			var jedis = RedisCache.getCachePool().getResource();
			var key = obj.getClass().getSimpleName() + ":" + id;
			jedis.set(key, JSON.encode(obj));
		}
		
		return res;
	}
	
}
