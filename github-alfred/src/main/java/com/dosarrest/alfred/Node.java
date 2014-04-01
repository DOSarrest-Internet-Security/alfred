package com.dosarrest.alfred;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class Node {
	public String id;
	public String name;
	public Boolean master = false;
	public Boolean data = false;
	public Boolean client = false;

	public Node(String id, JsonElement data) {
		this.id = id;
		JsonObject dataO = data.getAsJsonObject();
		this.name = dataO.get("name").getAsString();
		if (dataO.get("attributes").getAsJsonObject().has("master")) {
			if (dataO.get("attributes").getAsJsonObject().get("master").getAsString().equalsIgnoreCase("true")) {
				this.master = true;
			}
		}
		if (dataO.get("attributes").getAsJsonObject().has("data")) {
			if (dataO.get("attributes").getAsJsonObject().get("data").getAsString().equalsIgnoreCase("true")) {
				this.data = true;
			}
		}
		if (dataO.get("attributes").getAsJsonObject().has("client")) {
			if (dataO.get("attributes").getAsJsonObject().get("client").getAsString().equalsIgnoreCase("true")) {
				this.client = true;
			}
		}
	}

}
