package tukano.impl.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Likes {
		
	@JsonProperty("id")
	String shortId;
	
	public String getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(String ownerId) {
		this.ownerId = ownerId;
	}

	String ownerId;

	List<String> userIds;
	
	public Likes() {}

	public Likes(String shortId, String ownerId) {
		this.shortId = shortId;
		this.ownerId = ownerId;
		this.userIds = new ArrayList<>();
	}


	public List<String> getUserIds() {
		return userIds;
	}

	public void setUserIds(List<String> userIds) {
		this.userIds = userIds;
	}

	public String getShortId() {
		return shortId;
	}

	public void setShortId(String shortId) {
		this.shortId = shortId;
	}

	@Override
	public String toString() {
		return "Likes [shortId=" + shortId + ", ownerId=" + ownerId + "]";
	}

	@Override
	public int hashCode() {
		return Objects.hash(ownerId, shortId);
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
		return Objects.equals(ownerId, other.ownerId) && Objects.equals(shortId, other.shortId);
	}
	
	
}
