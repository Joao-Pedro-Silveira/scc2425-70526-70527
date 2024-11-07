package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import io.github.cdimascio.dotenv.Dotenv;
import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import utils.DB;
import utils.CosmosDB;
import tukano.impl.cache.CacheForCosmos;
import tukano.impl.data.Following;


public class JavaUsers implements Users {
	
	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

	private static Users instance;
	private static Dotenv dotenv = Dotenv.load();

	private boolean nosql = Boolean.parseBoolean(dotenv.get("NOSQL"));
	private boolean cache = Boolean.parseBoolean(dotenv.get("CACHE"));
	
	synchronized public static Users getInstance() {
		if( instance == null )
			instance = new JavaUsers();
		return instance;
	}
	
	private JavaUsers() {
	}
	
	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if( badUserInfo( user ) )
			return error(BAD_REQUEST);

		Result<User> res;

		if(nosql){
			Log.info(() -> "Using CosmosDB");
			res = CosmosDB.insertOne(user);
		} 
		else {
			Log.info(() -> "Using SQL DB");
			res = DB.insertOne(user);
		}

		if(res.isOK() && cache){
			Log.info(() -> "Inserting into cache");
			CacheForCosmos.insertOne("users:"+user.getUserId(), user);
			//DB.insertOne(new Following(user.getUserId()));
		}
		Log.info(() -> "Returning result");
		return errorOrValue(res, user.getUserId());
		
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info( () -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);
		
		if(cache){
			var res = CacheForCosmos.getOne("users:"+userId, User.class, true);
			if(res.isOK()){

				Log.info(() -> "User found in cache ");

				return validatedUserOrError(res, pwd);
			}
		}

		Result<User> dbres;
		if(nosql){
			dbres = CosmosDB.getOne(userId, User.class);
		} else {
			dbres = DB.getOne( userId, User.class);
		}

		if(dbres.isOK() && cache){
			Log.info(() -> "User found in DB");
			CacheForCosmos.insertOne(userId, dbres.value());
		}

		return validatedUserOrError(dbres, pwd);
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
				return error(BAD_REQUEST);
		if (nosql) {
			return errorOrResult( validatedUserOrError(CosmosDB.getOne(userId, User.class), pwd), user -> {
				var res = CosmosDB.updateOne(user.updateFrom(other));
				if(res.isOK() && cache){
					CacheForCosmos.updateOne("users:"+userId, res.value());
				}
				return res;
			});
		} else {
			return errorOrResult( validatedUserOrError(DB.getOne(userId, User.class), pwd), user -> {
				var res = DB.updateOne( user.updateFrom(other));
				if(res.isOK() && cache){
					CacheForCosmos.updateOne("users:"+userId, res.value());
				}
				return res;
			});
		}
	}

	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null )
			return error(BAD_REQUEST);

		if(nosql){
			return errorOrResult( validatedUserOrError(CosmosDB.getOne(userId, User.class), pwd), user -> {
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));

				CosmosDB.deleteOne(user);
				if(cache)
					CacheForCosmos.deleteOne("users:"+userId);
				return ok(user);
			});
		} else {
			return errorOrResult( validatedUserOrError(DB.getOne( userId, User.class), pwd), user -> {

				// Delete user shorts and related info asynchronously in a separate thread
				Executors.defaultThreadFactory().newThread( () -> {
					JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
					JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
				}).start();

				if(cache)
					CacheForCosmos.deleteOne("users:"+userId);
				
				return DB.deleteOne( user);
			});
		}
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info( () -> format("searchUsers : patterns = %s\n", pattern));

		if(nosql){
			var query = format("SELECT u.pwd, u.email, u.displayName, u.id FROM users u WHERE UPPER(u.id) LIKE '%%%s%%'", pattern.toUpperCase());
			var hits = CosmosDB.sql(query, User.class)
					.stream()
					.map(User::copyWithoutPassword)
					.toList();
			
			return ok(hits);
		} else {
			var query = format("SELECT * FROM users u WHERE UPPER(u.userId) LIKE '%%%s%%'", pattern.toUpperCase());
			var hits = DB.sql(query, User.class)
					.stream()
					.map(User::copyWithoutPassword)
					.toList();

			return ok(hits);
		}
	}

	
	private Result<User> validatedUserOrError( Result<User> res, String pwd ) {
		if( res.isOK())
			return res.value().getPwd().equals( pwd ) ? res : error(FORBIDDEN);
		else
			return res;
	}
	
	private boolean badUserInfo( User user) {
		return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
	}
	
	private boolean badUpdateUserInfo( String userId, String pwd, User info) {
		return (userId == null || pwd == null || info.getUserId() != null && ! userId.equals( info.getUserId()));
	}
}