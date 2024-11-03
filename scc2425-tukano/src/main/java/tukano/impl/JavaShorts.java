package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.CONFLICT;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static tukano.api.Result.ErrorCode.NOT_FOUND;
import static utils.DB.getOne;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.checkerframework.checker.units.qual.C;
import org.glassfish.hk2.utilities.cache.Cache;

import io.github.cdimascio.dotenv.Dotenv;
import tukano.api.Blobs;
import tukano.api.Result;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.cache.CacheForCosmos;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import utils.DB;
import utils.CosmosDB;

public class JavaShorts implements Shorts {

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());
	
	private static Shorts instance;

	private static Dotenv dotenv = Dotenv.load();

	private boolean nosql = Boolean.parseBoolean(dotenv.get("NOSQL"));
	
	synchronized public static Shorts getInstance() {
		if( instance == null )
			instance = new JavaShorts();
		return instance;
	}
	
	private JavaShorts() {}
	
	
	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		return errorOrResult( okUser(userId, password), user -> {
			
			var shortId = format("%s+%s", userId, UUID.randomUUID());
			var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId); 
			var shrt = new Short(shortId, userId, blobUrl);

			if (nosql) {
				var result = errorOrValue(CosmosDB.insertOne(shrt), s -> s.copyWithLikes_And_Token(0));

				if (result.isOK()){
					CacheForCosmos.insertOne("shorts:"+shortId, result.value());
					CosmosDB.insertOne(new Likes(shortId, userId));
				}
				return result;
			} else {
				return errorOrValue(DB.insertOne(shrt), s -> s.copyWithLikes_And_Token(0));
			}
		});
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if( shortId == null )
			return error(BAD_REQUEST);
			
		//var query = format("SELECT count(*) FROM Likes l WHERE l.shortId = '%s'", shortId); // doesn't work for NoSQL
		var query = format("SELECT VALUE count(l.shortId) FROM Likes l WHERE l.shortId = '%s'", shortId);
		var likes = CosmosDB.sql(query, Long.class);

		if (nosql) {

			final var res = CacheForCosmos.getOne("shorts:"+shortId, Short.class);

			if (res.isOK()){
				Log.info(() -> format("Short found in cache %s\n", res.value().toString()));
				return res;
			}
			else {

				var result = errorOrValue(CosmosDB.getOne(shortId, Short.class), shrt -> shrt.copyWithLikes_And_Token(likes.get(0)));
				if (result.isOK()) {
					CacheForCosmos.insertOne(shortId, result.value());
				}
				return result;
			}
		} else {
			return errorOrValue( getOne(shortId, Short.class), shrt -> shrt.copyWithLikes_And_Token( likes.get(0)));
		}
			
	}

	
	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));
		
		return errorOrResult( getShort(shortId), shrt -> {
			
			return errorOrResult( okUser( shrt.getOwnerId(), password), user -> {

				if(nosql){

					var like = CosmosDB.getOne(shortId, Likes.class).value();
					CosmosDB.deleteOne(like);
					// String query = String.format("SELECT l.shortId, l.userId, l.ownerId, l.id FROM Likes l WHERE l.shortId = '%s'", shortId);
					// List<Likes> likes = CosmosDB.sql(query, Likes.class);

					// if(likes != null){
					// 	Log.info(() -> "Deleting likes");
					// 	for(Likes l : likes){
					// 		CosmosDB.deleteOne(l);
					// 	}
					// }

					Log.info(() -> "Deleting from cache");

					CacheForCosmos.deleteOne("short:"+shortId);

					Log.info(() -> format("Deleted from DB %s", shrt.toString()));

					CosmosDB.deleteOne(shrt);

					// var query = format("DELETE Likes l WHERE l.shortId = '%s'", shortId);

					// CosmosDB.sql(query, Likes.class);
					
					// CacheForCosmos.deleteOne("short:"+shortId);
					
					// CosmosDB.deleteOne(shrt);
					
					//Problem with token
					return JavaBlobs.getInstance().delete(shortId, Token.get(shortId) );

				} else{

					return DB.transaction( hibernate -> {

						hibernate.remove( shrt);
						
						var query = format("DELETE Likes l WHERE l.shortId = '%s'", shortId);
						hibernate.createNativeQuery( query, Likes.class).executeUpdate();
						
						JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get() );
					});
				}
			});	
		});
	}

	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		var query = format("SELECT VALUE s.id FROM Short s WHERE s.ownerId = '%s'", userId);
		return errorOrValue( okUser(userId), nosql ? CosmosDB.sql( query, String.class) : DB.sql( query, String.class));
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));
	
		
		return errorOrResult( okUser(userId1, password), user -> {
			var f = new Following(userId1, userId2);
			if(nosql){
				return errorOrVoid( okUser( userId2), isFollowing ? CosmosDB.insertOne( f ) : CosmosDB.deleteOne( f ));	
			} else{
				return errorOrVoid( okUser( userId2), isFollowing ? DB.insertOne( f ) : DB.deleteOne( f ));	
			}
		});			
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		var query = format("SELECT VALUE f.follower FROM Following f WHERE f.followee = '%s'", userId);		
		return errorOrValue( okUser(userId, password), nosql ? CosmosDB.sql(query, String.class) : DB.sql(query, String.class));
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));

		
		return errorOrResult( getShort(shortId), shrt -> {
			var l = CosmosDB.getOne(shortId, Likes.class);
			if(nosql){

				Likes like = l.value();
				List<String> userIds = like.getUserIds();
				if(isLiked){
					if(!userIds.contains(userId)){
						userIds.add(userId);
					} else{
						return error(CONFLICT);
					}
				} else{
					if(userIds.contains(userId)){
						userIds.remove(userId);
					} else{
						return error(NOT_FOUND);
					}
				}

				like.setUserIds(userIds);
				return errorOrVoid( okUser( userId, password), CosmosDB.updateOne(like));	
			} else{
				return errorOrVoid( okUser( userId, password), isLiked ? DB.insertOne( l ) : DB.deleteOne( l ));	
			}
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult( getShort(shortId), shrt -> {
			
			var query = format("SELECT VALUE l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);					
			
			return errorOrValue( okUser( shrt.getOwnerId(), password ), nosql ? CosmosDB.getOne(shortId, Likes.class).value().getUserIds() : DB.sql(query, String.class));
		});
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		final var QUERY_FMT = """
				SELECT s.shortId, s.timestamp FROM Short s WHERE	s.ownerId = '%s'				
				UNION			
				SELECT s.shortId, s.timestamp FROM Short s, Following f 
					WHERE 
						f.followee = s.ownerId AND f.follower = '%s' 
				ORDER BY s.timestamp DESC""";

		return errorOrValue( okUser( userId, password), DB.sql( format(QUERY_FMT, userId, userId), String.class));		
	}
		
	protected Result<User> okUser( String userId, String pwd) {
		return JavaUsers.getInstance().getUser(userId, pwd);
	}
	
	private Result<Void> okUser( String userId ) {
		var res = okUser( userId, "");
		if( res.error() == FORBIDDEN )
			return ok();
		else
			return error( res.error() );
	}
	
	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

		if( ! Token.isValid( token, userId ) )
			return error(FORBIDDEN);
		
		var query1 = format("DELETE Short s WHERE s.ownerId = '%s'", userId);
		var query2 = format("DELETE Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
		var query3 = format("DELETE Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);		

		if(nosql){
			CosmosDB.sql(query1, Short.class);
			CosmosDB.sql(query2, Following.class);
			CosmosDB.sql(query3, Likes.class);
			return Result.ok();
		}else{

			return DB.transaction( (hibernate) -> {
							
				//delete shorts
				hibernate.createQuery(query1, Short.class).executeUpdate();
				
				//delete follows
				hibernate.createQuery(query2, Following.class).executeUpdate();
				
				//delete likes
				hibernate.createQuery(query3, Likes.class).executeUpdate();
				
			});
		}
	}
	
}