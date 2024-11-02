package utils;

import java.util.List;

import tukano.api.Result;

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

		return CosmosDB_NoSQL.getInstance().getOne(id, clazz);
	}
	
	public static <T> Result<?> deleteOne(T obj) {

		return CosmosDB_NoSQL.getInstance().deleteOne(obj);
	}
	
	public static <T> Result<T> updateOne(T obj) {

		return CosmosDB_NoSQL.getInstance().updateOne(obj);
	}
	
	public static <T> Result<T> insertOne(T obj) {

		return Result.errorOrValue(CosmosDB_NoSQL.getInstance().insertOne(obj), obj);
	}
	
}
