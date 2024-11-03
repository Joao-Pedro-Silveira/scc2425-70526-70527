package tukano.impl.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;

@Entity
public class Following{
	
	@JsonProperty("id")
	String followee;

	List<String> followers;

	Following() {}

	public Following(String followee) {
		super();
		this.followee = followee;
		followers = new ArrayList<>(); 
	}


	public List<String> getFollowers() {
		return followers;
	}

	public void setFollowers(List<String> followers) {
		this.followers = followers;
	}

	public String getFollowee() {
		return followee;
	}

	public void setFollowee(String followee) {
		this.followee = followee;
	}

	@Override
	public int hashCode() {
		return Objects.hash(followee);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Following other = (Following) obj;
		return Objects.equals(followee, other.followee);
	}

	@Override
	public String toString() {
		return "Following [followee=" + followee +", followers=" + followers + "]";
	}
	
	
}