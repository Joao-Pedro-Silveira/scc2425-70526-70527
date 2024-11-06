package tukano.impl.data;


import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
@Entity
public class Likes {
	
	@JsonProperty("shortId")
	String shortId;
	
	public String getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(String ownerId) {
		this.ownerId = ownerId;
	}

	String ownerId;
	
	@Id
	@JsonProperty("id")
	private String id;

	@JsonProperty("userId")
	String userId;
	
	public Likes() {}

	public Likes(String shortId, String ownerId, String userId) {
		this.shortId = shortId;
		this.ownerId = ownerId;
		this.userId = userId;
		this.id = makeId(shortId, userId);
	}

	public String makeId(String sh, String us) {
		return sh +"_"+ us;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserIds(String userId) {
		this.userId = userId;
	}

	public String getShortId() {
		return shortId;
	}

	public void setShortId(String shortId) {
		this.shortId = shortId;
	}

	@Override
	public String toString() {
		return "Likes [shortId=" + shortId + ", ownerId=" + ownerId + ", userId=" + userId + "]";
	}

	@Override
	public int hashCode() {
		return Objects.hash(ownerId, shortId, userId);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Likes other = (Likes) obj;
		return Objects.equals(ownerId, other.ownerId) && Objects.equals(shortId, other.shortId) && Objects.equals(userId, other.userId);
	}
	
	
}
