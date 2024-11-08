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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.checkerframework.checker.units.qual.C;
import org.glassfish.hk2.utilities.cache.Cache;
import org.hsqldb.persist.Log;

import com.fasterxml.jackson.annotation.JsonProperty;

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
	private boolean cache = Boolean.parseBoolean(dotenv.get("CACHE"));
	
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

			Result<Short> result = null;
			if (nosql) {

				result = errorOrValue(CosmosDB.insertOne(shrt), s -> s.copyWithLikes_And_Token(0));

			} else {
				result =  errorOrValue(DB.insertOne(shrt), s -> s.copyWithLikes_And_Token(0));
			}

			if (result.isOK() && cache){
				CacheForCosmos.insertOne("shorts:"+shortId, shrt);
			}

			return result;
		});
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if( shortId == null )
			return error(BAD_REQUEST);


		if(cache){

			var res = CacheForCosmos.getOne("shorts:"+shortId, Short.class, true);

			if (res.isOK()){
				Log.info(() -> format("Short found in cache %s\n", res.value().toString()));
				return res;
			}
		}

		Result<Short> result = null;

		if (nosql) {

			var query = format("SELECT VALUE count(l.shortId) FROM Likes l WHERE l.shortId = '%s'", shortId);

			var likes = CosmosDB.sql(query, Long.class);

			result = errorOrValue(CosmosDB.getOne(shortId, Short.class), shrt -> shrt.copyWithLikes_And_Token(likes.get(0)));
					
		} else {

			var query = format("SELECT count(*) FROM Likes l WHERE l.shortId = '%s'", shortId);
			var likes = DB.sql(query, Long.class);

			result = errorOrValue( getOne(shortId, Short.class), shrt -> shrt.copyWithLikes_And_Token( likes.get(0)));
		}

		if (result.isOK() && cache){ 

			CacheForCosmos.insertOne("shorts:"+shortId, result.value());
		}
		return result;
			
	}

	
	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));
		
		return errorOrResult( getShort(shortId), shrt -> {
			
			return errorOrResult( okUser( shrt.getOwnerId(), password), user -> {

				if(cache){
					CacheForCosmos.deleteOne("shorts:"+shortId);
				}

				if(nosql){

					String query = String.format("SELECT l.shortId, l.userId, l.ownerId, l.id FROM Likes l WHERE l.shortId = '%s'", shortId);
					List<Likes> likes = CosmosDB.sql(query, Likes.class);

					if(likes != null){
						Log.info(() -> "Deleting likes");
						for(Likes l : likes){
							CosmosDB.deleteOne(l);
						}
					}

					Log.info(() -> format("Deleted from DB %s", shrt.toString()));

					CosmosDB.deleteOne(shrt);
					
					return JavaBlobs.getInstance().delete(shortId, Token.get(shortId) );

				} else{

					return DB.transaction( hibernate -> {

						hibernate.remove( shrt);
						
						var query = format("DELETE FROM Likes l WHERE l.shortId = '%s'", shortId);
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

		var query1 = format("SELECT VALUE s.id FROM Short s WHERE s.ownerId = '%s'", userId);
		var query2 = format("SELECT s.id FROM Short s WHERE s.ownerId = '%s'", userId);

		return errorOrValue( okUser(userId), nosql ? CosmosDB.sql( query1, String.class) : DB.sql( query2, String.class));
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));
	
		
		return errorOrResult( okUser(userId1, password), user -> {
			
			if(nosql){
				Result<Following> f = CosmosDB.getOne(userId1+"_"+userId2, Following.class); 

				Log.info(() -> "Follow: " + f);
				if(f.isOK() && !isFollowing){
					Log.info(() -> "Deleting follow");
					Result<?> res = CosmosDB.deleteOne(f.value());
					return errorOrVoid( okUser( userId2), res);
				} 
				if(!f.isOK() && isFollowing){
					Log.info(() -> "Inserting follow");
					Following follow = new Following(userId1, userId2);
					return errorOrVoid( okUser( userId2), CosmosDB.insertOne(follow));
				}
				return errorOrVoid(okUser( userId2), Result.ok());
			} else{
				var f = new Following(userId1, userId2);

				return errorOrVoid( okUser( userId2), isFollowing ? DB.insertOne( f ) : DB.deleteOne( f ));	
			}
		});			
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		var query1 = format("SELECT VALUE f.follower FROM Following f WHERE f.followee = '%s'", userId);	
		var query2 = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);	

		return errorOrValue( okUser(userId, password), nosql ? CosmosDB.sql(query1, String.class): DB.sql(query2, String.class));
	}


	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));

		
		return errorOrResult( getShort(shortId), shrt -> {
			if(nosql){

				var l = CosmosDB.getOne(shortId+"_"+userId, Likes.class);

				return errorOrVoid( okUser( userId, password),isLiked ? CosmosDB.insertOne( new Likes(shortId, shrt.getOwnerId(), userId) ) : CosmosDB.deleteOne( l.value() ));	
			} else{
				var l = new Likes(shortId, shrt.getOwnerId(), userId);
				return errorOrVoid( okUser( userId, password), isLiked ? DB.insertOne( l ) : DB.deleteOne( l ));	
			}
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult( getShort(shortId), shrt -> {
			
			var query1 = format("SELECT VALUE l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);
			var query2 = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);					
			
			return errorOrValue( okUser( shrt.getOwnerId(), password ), nosql ? CosmosDB.sql(query1, String.class) : DB.sql(query2, String.class));
		});
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		if(nosql){

			String query1 = String.format("SELECT s.id, s.timestamp FROM Short s WHERE s.ownerId = '%s'", userId);
			List<ShortEntry> userShorts = CosmosDB.sql(query1, ShortEntry.class);

			String followeesQuery = String.format("SELECT VALUE f.followee FROM Following f WHERE f.follower = '%s'", userId);
			List<String> followeesJson = CosmosDB.sql(followeesQuery, String.class);

			List<String> followees = followeesJson.stream()
                .map(json -> json.replaceAll("\"", ""))
                .collect(Collectors.toList());

			List<ShortEntry> followeeShorts = new ArrayList<>();
			for (String followeeId : followees) {
				String query3 = String.format("SELECT s.id, s.timestamp FROM Short s WHERE s.ownerId = '%s'", followeeId);
				followeeShorts.addAll(CosmosDB.sql(query3, ShortEntry.class));
			}

			List<ShortEntry> allShorts = new ArrayList<>();
			allShorts.addAll(userShorts);
			allShorts.addAll(followeeShorts);

			return errorOrValue( okUser( userId, password), allShorts.stream()
						.sorted(Comparator.comparing(ShortEntry::getTimestamp).reversed())
						.map(ShortEntry::toJson)
						.collect(Collectors.toList()));

		}else{
			final var QUERY_FMT = """
					SELECT * FROM (
						SELECT s.shortId, s.timestamp 
						FROM Short s 
						WHERE s.ownerId = 'Cambio'
						
						UNION
						
						SELECT s.shortId, s.timestamp 
						FROM Short s, Following f 
						WHERE f.followee = s.ownerId 
						AND f.follower = 'Cambio'
					) AS results
					ORDER BY results.timestamp DESC;
					""";

			return errorOrValue( okUser( userId, password), DB.sql( format(QUERY_FMT, userId, userId), String.class));
		}		
	}
		
	public static class ShortEntry {
        @JsonProperty("id")
        private String shortId;

        @JsonProperty("timestamp")
        private String timestamp;

        public String getShortId() {
            return shortId;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public String toJson() {
            return String.format("shortId: %s, timestamp: %s", shortId, timestamp);
        }
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

		if(nosql){
			// Retrieve and delete Shorts
			var query1 = format("SELECT * FROM Short s WHERE s.ownerId = '%s'", userId);
			var shortItems = CosmosDB.sql(query1, Short.class).stream().toList();
			for (Short shortItem : shortItems) {
				CosmosDB.deleteOne(shortItem);
				if(cache){
					CacheForCosmos.deleteOne("shorts:"+shortItem.getShortId());
				}
			}
		
			// Retrieve and delete Followings
			var query2 = format("SELECT * FROM Following f WHERE f.follower = '%s' OR f.id = '%s'", userId, userId);
			var followingItems = CosmosDB.sql(query2, Following.class).stream().toList();
			for (Following following : followingItems) {
				CosmosDB.deleteOne(following);
			}
		
			// Retrieve and delete Likes
			var query3 = format("SELECT * FROM Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
			var likeItems = CosmosDB.sql(query3, Likes.class).stream().toList();
			for (Likes like : likeItems) {
				CosmosDB.deleteOne(like);
			}
			return Result.ok(); 
		}else{

			return DB.transaction( (hibernate) -> {

				if(cache){

					var query = format("SELECT s.id FROM Short s WHERE s.ownerId = '%s'", userId);
					var res = hibernate.createNativeQuery(query, String.class).getResultList();

					for(String id : res){
						CacheForCosmos.deleteOne("shorts:"+id);
					}
				}


				var query1 = format("DELETE FROM Short s WHERE s.ownerId = '%s'", userId);
				var query2 = format("DELETE FROM Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
				var query3 = format("DELETE FROM Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);	
				
				//delete shorts
				hibernate.createNativeQuery(query1, Short.class).executeUpdate();
				
				//delete follows
				hibernate.createNativeQuery(query2, Following.class).executeUpdate();
				
				//delete likes
				hibernate.createNativeQuery(query3, Likes.class).executeUpdate();
				
			});
		}
	}
	
}