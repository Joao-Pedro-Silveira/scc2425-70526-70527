package utils;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.hsqldb.persist.Log;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;

import tukano.api.Result;
import tukano.api.Result.ErrorCode;

import tukano.api.User;
import tukano.api.UserDAO;

import tukano.api.Short;
import tukano.api.ShortDAO;
import tukano.impl.JavaUsers;
import tukano.impl.data.Following;
import tukano.impl.data.FollowingDAO;

import tukano.impl.data.Likes;
import tukano.impl.data.LikesDAO;

public class CosmosDB_NoSQL {
    private static final String CONNECTION_URL = "https://cosmos70526.documents.azure.com:443/"; // replace with your own
	private static final String DB_KEY = "jQ2068Hg2C5QBaBolnkZkrbkxjwX7FhqVcCUlW5qdWmCg51bmJxZ05LUCfvu234fM3tYTjnUMa43ACDbnQ5YMA==";
	private static final String DB_NAME = "cosmosdb70526";

    private static final String CONTAINER_USER = "users";
    private static final String CONTAINER_SHORT = "shorts";
    private static final String CONTAINER_FOLLOW = "follows";
    private static final String CONTAINER_LIKE = "likes";

	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());
	
	private static CosmosDB_NoSQL instance;

	public static synchronized CosmosDB_NoSQL getInstance() {
		if( instance != null)
			return instance;

		CosmosClient client = new CosmosClientBuilder()
		         .endpoint(CONNECTION_URL)
		         .key(DB_KEY)
		         //.directMode()
		         .gatewayMode()		
		         // replace by .directMode() for better performance
		         .consistencyLevel(ConsistencyLevel.SESSION)
		         .connectionSharingAcrossClientsEnabled(true)
		         .contentResponseOnWriteEnabled(true)
		         .buildClient();
		instance = new CosmosDB_NoSQL( client);
		return instance;
		
	}
	
	private CosmosClient client;
	private CosmosDatabase db;
    private CosmosContainer container;
	
	public CosmosDB_NoSQL(CosmosClient client) {
		this.client = client;
	}
	
	private synchronized void init(String containerName) {
		if (db == null) {
			db = client.getDatabase(DB_NAME);
		}
		container = db.getContainer(containerName);
		Log.info(()->("Container: " + container.getId()));
	}

	public void close() {
		client.close();
	}
	
	public <T> Result<T> getOne(String id, Class<T> clazz) {
        String containerName = ChooseContainer(clazz);
		return tryCatch( () -> container.readItem(id, new PartitionKey(id), clazz).getItem(), containerName);
	}
	
	public <T> Result<?> deleteOne(T obj) {
        String containerName = ChooseContainer(obj.getClass());
		return tryCatch( () -> container.deleteItem(obj, new CosmosItemRequestOptions()).getItem(), containerName);
	}
	
	public <T> Result<T> updateOne(T obj) {
        String containerName = ChooseContainer(obj.getClass());
		return tryCatch( () -> container.upsertItem(obj).getItem(), containerName);
	}
	
	public <T> Result<T> insertOne( T obj) {
        String containerName = ChooseContainer(obj.getClass());
		var res = tryCatch( () -> container.createItem(obj).getItem(), containerName);
		Log.info(()->("InsertOne: " + res.error()));
		return res;
	}
	
	public <T> Result<List<T>> query(Class<T> clazz, String queryStr) {
        String containerName = ChooseContainer(clazz);
		return tryCatch(() -> {
			var res = container.queryItems(queryStr, new CosmosQueryRequestOptions(), clazz);
			return res.stream().toList();
		}, containerName);
	}

	private String ChooseContainer(Class<?> clazz) {
        if (clazz == User.class || clazz == UserDAO.class) {
            return CONTAINER_USER;
        } else if (clazz == Short.class || clazz == ShortDAO.class) {
            return CONTAINER_SHORT;
        } else if (clazz == Following.class || clazz == FollowingDAO.class) {
            return CONTAINER_FOLLOW;
        } else if (clazz == Likes.class || clazz == LikesDAO.class) {
            return CONTAINER_LIKE;
        } else {
            return null;
        }
    }
	
	<T> Result<T> tryCatch( Supplier<T> supplierFunc, String containerName) {
		try {
			init(containerName);
			return Result.ok(supplierFunc.get());			
		} catch( CosmosException ce ) {
			Log.info(()->("CosmosDB_NoSQL:CosmosE " + ce.getStatusCode() + " " + ce.getMessage()));
			//ce.printStackTrace();
			return Result.error ( errorCodeFromStatus(ce.getStatusCode() ));		
		} catch( Exception x ) {
			Log.info(()->("CosmosDB_NoSQL:Exece " + x.getMessage()));
			x.printStackTrace();
			return Result.error( ErrorCode.INTERNAL_ERROR);						
		}
	}
	
	static Result.ErrorCode errorCodeFromStatus( int status ) {
		return switch( status ) {
		case 200 -> ErrorCode.OK;
		case 404 -> ErrorCode.NOT_FOUND;
		case 409 -> ErrorCode.CONFLICT;
		default -> ErrorCode.INTERNAL_ERROR;
		};
	}
}