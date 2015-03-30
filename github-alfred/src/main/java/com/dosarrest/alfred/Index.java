package com.dosarrest.alfred;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.ClientProtocolException;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Index {
	public String name;
	public String prefix;
	public String prefixseparator;
	public String separator;
	public String year;
	public String month;
	public String day;
	public String hour;
	public String timeunit = "none";
	public DateTime timestamp;
	public int shardCount = 0;
	public int replicaCount = 0;
	public long byteSize = 0;
	public List<Node> nodes = new ArrayList<Node>();
	public Boolean open;
	public Boolean bloom = true;
	public JsonObject settings;
	
	public Index(String name, Boolean open) {
		this.name = name;
		this.open = open;
		if (open) { this.getStatus(); this.getSettings(); }
		Matcher m = Pattern.compile("^((?<Name>[a-zA-Z0-9\\.\\-_]+)(?<PrefixSeparator>(_|-)+)(?<Year>[0-9]{4})(?<Separator>(\\.|_|-))(?<Month>[0-9]{2})(\\.|_|-)(?<Day>[0-9]{2})(\\.|_|-)?(?<Hour>[0-9]{2})?)$").matcher(name);
		if (m.matches()) {
			if (m.group("Separator")!=null) {
				this.prefix = m.group("Name");
				this.prefixseparator = m.group("Separator");
				this.separator = m.group("Separator");
				this.year = m.group("Year");
				this.month = m.group("Month");
				this.day = m.group("Day");
				if (m.group("Hour")!=null) {
					this.hour = m.group("Hour");
					this.timeunit = "hour";
					DateTime indexTime = new DateTime().withZone(DateTimeZone.UTC).withYear(Integer.parseInt(m.group("Year"))).withMonthOfYear(Integer.parseInt(m.group("Month"))).withDayOfMonth(Integer.parseInt(m.group("Day"))).withHourOfDay(Integer.parseInt(m.group("Hour"))).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0);
					this.timestamp = indexTime;
				} else {
					this.timeunit = "day";
					DateTime indexTime = new DateTime().withZone(DateTimeZone.UTC).withYear(Integer.parseInt(m.group("Year"))).withMonthOfYear(Integer.parseInt(m.group("Month"))).withDayOfMonth(Integer.parseInt(m.group("Day"))).withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0);
					this.timestamp = indexTime;
				}
			} else {
				this.timeunit = "none";
			}
		}
	}
	/**
	 * Get Index Status
	 */
	private void getStatus() {
		try {
			String docStatsRaw = Alfred.getURL("/"+this.name+"/_status");
			JsonElement docStats = new JsonParser().parse(docStatsRaw);
			JsonObject docStatsO = docStats.getAsJsonObject();
			JsonObject indices = docStatsO.getAsJsonObject("indices");
			byteSize = indices.get(name).getAsJsonObject().get("index").getAsJsonObject().get("size_in_bytes").getAsLong();
			JsonObject shards = indices.get(name).getAsJsonObject().get("shards").getAsJsonObject();
			
			Set<Entry<String, JsonElement>> shardsSet = shards.entrySet();
			for (Entry<String, JsonElement> shardEntry : shardsSet) {
				JsonElement shardInfo = shardEntry.getValue();
				String nodeID = shardInfo.getAsJsonArray()
						.get(0)
						.getAsJsonObject()
						.get("routing")
						.getAsJsonObject()
						.get("node").getAsString();
				if (Alfred.nodes.containsKey(nodeID)) {
					nodes.add(Alfred.nodes.get(nodeID));
					Alfred.println("debug", "NodeID: "+nodeID+", Name:"+Alfred.nodes.get(nodeID).name);
				}
			}
		} catch (Exception e) {
			Alfred.println("fatal", "Count not get status for "+this.name);
		}
	}
	/**
	 * Get Index Settings
	 */
	private void getSettings() {
		try {
			String docStatsRaw = Alfred.getURL("/"+this.name+"/_settings");
			JsonElement docStats = new JsonParser().parse(docStatsRaw);
			JsonObject docStatsO = docStats.getAsJsonObject();
			try {
				Boolean bloomValue = docStatsO.getAsJsonObject(this.name)
						.getAsJsonObject("settings")
						.getAsJsonObject("index")
						.getAsJsonObject("codec")
						.getAsJsonObject("bloom")
						.get("load").getAsBoolean();
				if (bloomValue==false) {
					this.bloom = false;
				}
				this.settings = docStatsO.getAsJsonObject(this.name).getAsJsonObject("settings");
			} catch (NullPointerException e) {
				this.bloom = true;
			}
			this.shardCount = docStatsO.getAsJsonObject(this.name)
					.getAsJsonObject("settings")
					.getAsJsonObject("index")
					.get("number_of_shards").getAsInt();
			this.replicaCount = docStatsO.getAsJsonObject(this.name)
					.getAsJsonObject("settings")
					.getAsJsonObject("index")
					.get("number_of_replicas").getAsInt();
		} catch (Exception e) {
			Alfred.println("debug", "Count not get settings for "+this.name);
		}
	}
	/**
	 * Flush time indexes
	 * @param per
	 */
	public void flush(Period per) {
		if (this.open==true) {
			if (Alfred.run) {
				try {
					Alfred.postURL("/"+this.name+"/_flush");
				} catch (Exception e) {
		        	Alfred.println("error", "Client Error in request: "+e.getMessage());
		        	if (Alfred.retries>1) {
		        		int r = 1;
		        		while (r!=Alfred.retries) {
		        			try {
		        				Alfred.postURL("/"+this.name+"/_flush");
		        				r = Alfred.retries;
		        			} catch (Exception ce) {
		        				Alfred.println("error", "Client Error in request: "+ce.getMessage());
		        				r++;
		        			}
		        		}
		        	}
				}
			} else {
				if (this.timeunit.equalsIgnoreCase("hour")) {
					String expireTime = (per.getYears()!=0?" "+Math.abs(per.getYears())+" year(s)":"")+(per.getDays()!=0?" "+Math.abs(per.getDays())+" day(s)":"")+Math.abs(per.getHours())+" hour(s)";
					Alfred.println("general", "Index "+this.name+" would have been flushed for being "+expireTime+" older than expiry time.");
				} else if (this.timeunit.equalsIgnoreCase("day")) {
					String expireTime = Math.abs(per.getDays())+" days";
					Alfred.println("general", "Index "+this.name+" would have been flushed for being "+expireTime+" older than expiry time.");
				} else {
					Alfred.println("general", "Index "+this.name+" would have been flushed due to no time indexing.");
				}
			}
		}
	}
	/**
	 * Flush non-time indexes
	 */
	public void flush() {
		if (this.open==true) {
			if (Alfred.run) {
				try {
					Alfred.postURL("/"+this.name+"/_flush");
				} catch (Exception e) {
		        	Alfred.println("error", "Client Error in request: "+e.getMessage());
		        	if (Alfred.retries>1) {
		        		int r = 1;
		        		while (r!=Alfred.retries) {
		        			try {
		        				Alfred.postURL("/"+this.name+"/_flush");
		        				r = Alfred.retries;
		        			} catch (Exception ce) {
		        				Alfred.println("error", "Client Error in request: "+ce.getMessage());
		        				r++;
		        			}
		        		}
		        	}
				}
			} else {
				Alfred.println("general", "Index "+this.name+" would have been flushed.");
			}
		}
	}
	/**
	 * Close time indexes
	 * @param per
	 */
	public void close(Period per) {
		if (this.open==false) {
			Alfred.println("error", "Index "+this.name+" is already closed.");
		} else {
			if (Alfred.run) {
				try {
					Alfred.postURL("/"+this.name+"/_close");
				} catch (Exception e) {
		        	Alfred.println("error", "Client Error in request: "+e.getMessage());
		        	if (Alfred.retries>1) {
		        		int r = 1;
		        		while (r!=Alfred.retries) {
		        			try {
		        				Alfred.postURL("/"+this.name+"/_close");
		        				r = Alfred.retries;
		        			} catch (Exception ce) {
		        				Alfred.println("error", "Client Error in request: "+ce.getMessage());
		        				r++;
		        			}
		        		}
		        	}
				}
			} else {
				if (this.timeunit.equalsIgnoreCase("hour")) {
					String expireTime = (per.getYears()!=0?" "+Math.abs(per.getYears())+" year(s)":"")+(per.getDays()!=0?" "+Math.abs(per.getDays())+" day(s)":"")+Math.abs(per.getHours())+" hour(s)";
					Alfred.println("general", "Index "+this.name+" would have been closed for being "+expireTime+" older than expiry time.");
				} else if (this.timeunit.equalsIgnoreCase("day")) {
					String expireTime = Math.abs(per.getDays())+" days";
					Alfred.println("general", "Index "+this.name+" would have been closed for being "+expireTime+" older than expiry time.");
				} else {
					Alfred.println("general", "Index "+this.name+" would have been closed due to no time indexing.");
				}
			}
		}
	}
	/**
	 * Close non-time indexes
	 */
	public void close() {
		if (this.open==false) {
			Alfred.println("error", "Index "+this.name+" is already closed.");
		} else {
			if (Alfred.run) {
				try {
					Alfred.postURL("/"+this.name+"/_close");
				} catch (Exception e) {
		        	Alfred.println("error", "Client Error in request: "+e.getMessage());
		        	if (Alfred.retries>1) {
		        		int r = 1;
		        		while (r!=Alfred.retries) {
		        			try {
		        				Alfred.postURL("/"+this.name+"/_close");
		        				r = Alfred.retries;
		        			} catch (Exception ce) {
		        				Alfred.println("error", "Client Error in request: "+ce.getMessage());
		        				r++;
		        			}
		        		}
		        	}
				}
			} else {
				Alfred.println("general", "Index "+this.name+" would have been closed.");
			}
		}
	}
	/**
	 * Open time indexes
	 * @param per
	 */
	public void open(Period per) {
		if (this.open==true) {
			Alfred.println("error", "Index "+this.name+" is already open.");
		} else {
			if (Alfred.run) {
				try {
					Alfred.postURL("/"+this.name+"/_open");
				} catch (Exception e) {
		        	Alfred.println("error", "Client Error in request: "+e.getMessage());
		        	if (Alfred.retries>1) {
		        		int r = 1;
		        		while (r!=Alfred.retries) {
		        			try {
		        				Alfred.postURL("/"+this.name+"/_open");
		        				r = Alfred.retries;
		        			} catch (Exception ce) {
		        				Alfred.println("error", "Client Error in request: "+ce.getMessage());
		        				r++;
		        			}
		        		}
		        	}
				}
			} else {
				if (this.timeunit.equalsIgnoreCase("hour")) {
					String expireTime = (per.getYears()!=0?" "+Math.abs(per.getYears())+" year(s)":"")+(per.getDays()!=0?" "+Math.abs(per.getDays())+" day(s)":"")+Math.abs(per.getHours())+" hour(s)";
					Alfred.println("general", "Index "+this.name+" would have been opened for being "+expireTime+" older than expiry time.");
				} else if (this.timeunit.equalsIgnoreCase("day")) {
					String expireTime = Math.abs(per.getDays())+" days";
					Alfred.println("general", "Index "+this.name+" would have been opened for being "+expireTime+" older than expiry time.");
				} else {
					Alfred.println("general", "Index "+this.name+" would have been opened.");
				}
			}
		}
	}
	/**
	 * Open non-time indexes
	 */
	public void open() {
		if (this.open==true) {
			Alfred.println("error", "Index "+this.name+" is already open.");
		} else {
			if (Alfred.run) {
				try {
					Alfred.postURL("/"+this.name+"/_open");
				} catch (Exception e) {
		        	Alfred.println("error", "Client Error in request: "+e.getMessage());
		        	if (Alfred.retries>1) {
		        		int r = 1;
		        		while (r!=Alfred.retries) {
		        			try {
		        				Alfred.postURL("/"+this.name+"/_open");
		        				r = Alfred.retries;
		        			} catch (Exception ce) {
		        				Alfred.println("error", "Client Error in request: "+ce.getMessage());
		        				r++;
		        			}
		        		}
		        	}
				}
			} else {
				Alfred.println("general", "Index "+this.name+" would have been opened.");
			}
		}
	}
	/**
	 * Delete time indexes
	 * @param per
	 */
	public void delete(Period per) {
		if (this.open==true) {
			if (Alfred.run) {
				try {
					Alfred.deleteURL("/"+this.name+"/");
				} catch (Exception e) {
		        	Alfred.println("error", "Client Error in request: "+e.getMessage());
		        	if (Alfred.retries>1) {
		        		int r = 1;
		        		while (r!=Alfred.retries) {
		        			try {
		        				Alfred.deleteURL("/"+this.name+"/");
		        				r = Alfred.retries;
		        			} catch (Exception ce) {
		        				Alfred.println("error", "Client Error in request: "+ce.getMessage());
		        				r++;
		        			}
		        		}
		        	}
				}
			} else {
				if (this.timeunit.equalsIgnoreCase("hour")) {
					String expireTime = (per.getYears()!=0?" "+Math.abs(per.getYears())+" year(s)":"")+(per.getDays()!=0?" "+Math.abs(per.getDays())+" day(s)":"")+Math.abs(per.getHours())+" hour(s)";
					Alfred.println("general", "Index "+this.name+" would have been deleted for being "+expireTime+" older than expiry time.");
				} else if (this.timeunit.equalsIgnoreCase("day")) {
					String expireTime = Math.abs(per.getDays())+" days";
					Alfred.println("general", "Index "+this.name+" would have been deleted for being "+expireTime+" older than expiry time.");
				} else {
					Alfred.println("general", "Index "+this.name+" would have been deleted.");
				}
			}
		}
	}
	/**
	 * Delete non-time indexes
	 */
	public void delete() {
		if (this.open==true) {
			if (Alfred.run) {
				try {
					Alfred.deleteURL("/"+this.name+"/");
				} catch (Exception e) {
		        	Alfred.println("error", "Client Error in request: "+e.getMessage());
		        	if (Alfred.retries>1) {
		        		int r = 1;
		        		while (r!=Alfred.retries) {
		        			try {
		        				Alfred.deleteURL("/"+this.name+"/");
		        				r = Alfred.retries;
		        			} catch (Exception ce) {
		        				Alfred.println("error", "Client Error in request: "+ce.getMessage());
		        				r++;
		        			}
		        		}
		        	}
				}
			} else {
				Alfred.println("general", "Index "+this.name+" would have been deleted.");
			}
		}
	}
	/**
	 * Bloom time indexes
	 * @param per
	 */
	public void bloom(Period per) {
		if (this.open==true) {
			if (this.bloom==true) {
				Alfred.println("error", "Bloom filter has already been enabled for "+this.name);
			} else {
				if (Alfred.run) {
					try {
						Alfred.putURL("/"+this.name+"/_settings?index.codec.bloom.load=true");
					} catch (Exception e) {
			        	Alfred.println("error", "Client Error in request: "+e.getMessage());
			        	if (Alfred.retries>1) {
			        		int r = 1;
			        		while (r!=Alfred.retries) {
			        			try {
			        				Alfred.putURL("/"+this.name+"/_settings?index.codec.bloom.load=true");
			        				r = Alfred.retries;
			        			} catch (Exception ce) {
			        				Alfred.println("error", "Client Error in request: "+ce.getMessage());
			        				r++;
			        			}
			        		}
			        	}
					}
				} else {
					if (this.timeunit.equalsIgnoreCase("hour")) {
						String expireTime = (per.getYears()!=0?" "+Math.abs(per.getYears())+" year(s)":"")+(per.getDays()!=0?" "+Math.abs(per.getDays())+" day(s)":"")+Math.abs(per.getHours())+" hour(s)";
						Alfred.println("general", "Index "+this.name+" would have had bloom filter enabled for being "+expireTime+" older than expiry time.");
					} else if (this.timeunit.equalsIgnoreCase("day")) {
						String expireTime = Math.abs(per.getDays())+" days";
						Alfred.println("general", "Index "+this.name+" would have had bloom filter enabled for being "+expireTime+" older than expiry time.");
					} else {
						Alfred.println("general", "Index "+this.name+" would have had bloom filter enabled.");
					}
				}
			}
		}
	}
	/**
	 * Bloom non-time indexes
	 */
	public void bloom() {
		if (this.open==true) {
			if (this.bloom==true) {
				Alfred.println("error", "Bloom filter has already been enabled for "+this.name);
			} else {
				if (Alfred.run) {
					try {
						Alfred.putURL("/"+this.name+"/_settings?index.codec.bloom.load=true");
					} catch (Exception e) {
			        	Alfred.println("error", "Client Error in request: "+e.getMessage());
			        	if (Alfred.retries>1) {
			        		int r = 1;
			        		while (r!=Alfred.retries) {
			        			try {
			        				Alfred.putURL("/"+this.name+"/_settings?index.codec.bloom.load=true");
			        				r = Alfred.retries;
			        			} catch (Exception ce) {
			        				Alfred.println("error", "Client Error in request: "+ce.getMessage());
			        				r++;
			        			}
			        		}
			        	}
					}
				} else {
					Alfred.println("general", "Index "+this.name+" would have had bloom filter enabled.");
				}
			}
		}
	}
	/**
	 * Debloom time indexes
	 * @param per
	 */
	public void debloom(Period per) {
		if (this.open==true) {
			if (this.bloom==false) {
				Alfred.println("error", "Bloom filter has already been disabled for "+this.name);
			} else {
				if (Alfred.run) {
					try {
						Alfred.putURL("/"+this.name+"/_settings?index.codec.bloom.load=false");
					} catch (Exception e) {
			        	Alfred.println("error", "Client Error in request: "+e.getMessage());
			        	if (Alfred.retries>1) {
			        		int r = 1;
			        		while (r!=Alfred.retries) {
			        			try {
			        				Alfred.putURL("/"+this.name+"/_settings?index.codec.bloom.load=false");
			        				r = Alfred.retries;
			        			} catch (Exception ce) {
			        				Alfred.println("error", "Client Error in request: "+ce.getMessage());
			        				r++;
			        			}
			        		}
			        	}
					}
				} else {
					if (this.timeunit.equalsIgnoreCase("hour")) {
						String expireTime = (per.getYears()!=0?" "+Math.abs(per.getYears())+" year(s)":"")+(per.getDays()!=0?" "+Math.abs(per.getDays())+" day(s)":"")+Math.abs(per.getHours())+" hour(s)";
						Alfred.println("general", "Index "+this.name+" would have had bloom filter disabled for being "+expireTime+" older than expiry time.");
					} else if (this.timeunit.equalsIgnoreCase("day")) {
						String expireTime = Math.abs(per.getDays())+" days";
						Alfred.println("general", "Index "+this.name+" would have had bloom filter disabled for being "+expireTime+" older than expiry time.");
					} else {
						Alfred.println("general", "Index "+this.name+" would have had bloom filter disabled.");
					}
				}
			}
		}
	}
	/**
	 * Debloom non-time indexes
	 */
	public void debloom() {
		if (this.open==true) {
			if (this.bloom==false) {
				Alfred.println("error", "Bloom filter has already been disabled for "+this.name);
			} else {
				if (Alfred.run) {
					try {
						Alfred.putURL("/"+this.name+"/_settings?index.codec.bloom.load=false");
					} catch (Exception e) {
			        	Alfred.println("error", "Client Error in request: "+e.getMessage());
			        	if (Alfred.retries>1) {
			        		int r = 1;
			        		while (r!=Alfred.retries) {
			        			try {
			        				Alfred.putURL("/"+this.name+"/_settings?index.codec.bloom.load=false");
			        				r = Alfred.retries;
			        			} catch (Exception ce) {
			        				Alfred.println("error", "Client Error in request: "+ce.getMessage());
			        				r++;
			        			}
			        		}
			        	}
					}
				} else {
					Alfred.println("general", "Index "+this.name+" would have had bloom filter disabled.");
				}
			}
		}
	}
	/**
	 * Optimize time indexes
	 * @param per
	 * @param segments
	 */
	public void optimize(Period per, int segments) {
		if (this.open==true) {
			if (segments==this.shardCount) {
				Alfred.println("error", "Shard count matches optimize max_num_segments for "+this.name);
			} else {
				if (Alfred.run) {
					try {
						Alfred.postURL("/"+this.name+"/_optimize?max_num_segments="+segments);
					} catch (Exception e) {
			        	Alfred.println("error", "Client Error in request: "+e.getMessage());
			        	if (Alfred.retries>1) {
			        		int r = 1;
			        		while (r!=Alfred.retries) {
			        			try {
			        				Alfred.postURL("/"+this.name+"/_optimize?max_num_segments="+segments);
			        				r = Alfred.retries;
			        			} catch (Exception ce) {
			        				Alfred.println("error", "Client Error in request: "+ce.getMessage());
			        				r++;
			        			}
			        		}
			        	}
					}
				} else {
					if (this.timeunit.equalsIgnoreCase("hour")) {
						String expireTime = (per.getYears()!=0?" "+Math.abs(per.getYears())+" year(s)":"")+(per.getDays()!=0?" "+Math.abs(per.getDays())+" day(s)":"")+Math.abs(per.getHours())+" hour(s)";
						Alfred.println("general", "Index "+this.name+" would have been optimized for being "+expireTime+" older than expiry time.");
					} else if (this.timeunit.equalsIgnoreCase("day")) {
						String expireTime = Math.abs(per.getDays())+" days";
						Alfred.println("general", "Index "+this.name+" would have been optimized for being "+expireTime+" older than expiry time.");
					} else {
						Alfred.println("general", "Index "+this.name+" would have been optimized.");
					}
				}
			}
		}
	}
	/**
	 * Optimize non-time indexes
	 * @param segments
	 */
	public void optimize(int segments) {
		if (this.open==true) {
			if (segments==this.shardCount) {
				Alfred.println("error", "Shard count matches optimize max_num_segments for "+this.name);
			} else {
				if (Alfred.run) {
					try {
						Alfred.postURL("/"+this.name+"/_optimize?max_num_segments="+segments);
					} catch (Exception e) {
			        	Alfred.println("error", "Client Error in request: "+e.getMessage());
			        	if (Alfred.retries>1) {
			        		int r = 1;
			        		while (r!=Alfred.retries) {
			        			try {
			        				Alfred.postURL("/"+this.name+"/_optimize?max_num_segments="+segments);
			        				r = Alfred.retries;
			        			} catch (Exception ce) {
			        				Alfred.println("error", "Client Error in request: "+ce.getMessage());
			        				r++;
			        			}
			        		}
			        	}
					}
				} else {
					Alfred.println("general", "Index "+this.name+" would have been optimized.");
				}
			}
		}
	}
	/**
	 * Put settings for time indexes
	 * @param per
	 */
	public void putSettings(Period per) {
		if (this.open==true) {
//			if (checkSettingsChange()) {
//				Alfred.println("general", "Index "+this.name+" would have had settings changed.");
//			}
			if (Alfred.run) {
				try {
					Alfred.putURL("/"+this.name+"/_settings", Alfred.settings);
				} catch (Exception e) {
		        	Alfred.println("error", "Client Error in request: "+e.getMessage());
		        	if (Alfred.retries>1) {
		        		int r = 1;
		        		while (r!=Alfred.retries) {
		        			try {
		        				Alfred.putURL("/"+this.name+"/_settings", Alfred.settings);
		        				r = Alfred.retries;
		        			} catch (Exception ce) {
		        				Alfred.println("error", "Client Error in request: "+ce.getMessage());
		        				r++;
		        			}
		        		}
		        	}
				}
			} else {
				if (this.timeunit.equalsIgnoreCase("hour")) {
					String expireTime = (per.getYears()!=0?" "+Math.abs(per.getYears())+" year(s)":"")+(per.getDays()!=0?" "+Math.abs(per.getDays())+" day(s)":"")+Math.abs(per.getHours())+" hour(s)";
					Alfred.println("general", "Index "+this.name+" would have had settings changed for being "+expireTime+" older than expiry time.");
				} else if (this.timeunit.equalsIgnoreCase("day")) {
					String expireTime = Math.abs(per.getDays())+" days";
					Alfred.println("general", "Index "+this.name+" would have had settings changed for being "+expireTime+" older than expiry time.");
				} else {
					Alfred.println("general", "Index "+this.name+" would have had settings changed.");
				}
			}
		}
	}
	/**
	 * Put settings for non-time indexes
	 */
	public void putSettings() {
		if (this.open==true) {
//			if (checkSettingsChange()) {
//				Alfred.println("general", "Index "+this.name+" would have had settings changed.");
//			}
			if (Alfred.run) {
				try {
					Alfred.putURL("/"+this.name+"/_settings", Alfred.settings);
				} catch (Exception e) {
		        	Alfred.println("error", "Client Error in request: "+e.getMessage());
		        	if (Alfred.retries>1) {
		        		int r = 1;
		        		while (r!=Alfred.retries) {
		        			try {
		        				Alfred.putURL("/"+this.name+"/_settings", Alfred.settings);
		        				r = Alfred.retries;
		        			} catch (Exception ce) {
		        				Alfred.println("error", "Client Error in request: "+ce.getMessage());
		        				r++;
		        			}
		        		}
		        	}
				}
			} else {
				Alfred.println("general", "Index "+this.name+" would have had settings changed.");
			}
		}
	}
	/**
	 * Check settings for changes
	 */
	public Boolean checkSettingsChange() {
		Boolean foundChange = false;
		JsonElement newSettings = new JsonParser().parse(Alfred.settings).getAsJsonObject();
		Set<Entry<String, JsonElement>> ns = newSettings.getAsJsonObject().entrySet();
		try {
			for (Entry<String, JsonElement> entry : ns) {
				if (entry.getKey().contains(".")) {
					String[] sections = entry.getKey().split("\\.");
					JsonElement checkSection = this.settings;
					for (String section : sections) {
						checkSection = checkSection.getAsJsonObject().get(section);
					}
					if (!entry.getValue().equals(checkSection)) {
						foundChange = true;
					}
				} else {
					JsonObject checkSection = this.settings.getAsJsonObject();
					for (Entry<String, JsonElement> section : checkSection.entrySet()) {
						if (section.getValue().isJsonObject()) {
							checkSection = checkSection.getAsJsonObject(section.getKey());
						}
					}
					if (!entry.getValue().equals(checkSection)) {
						foundChange = true;
					}
				}
			}
		} catch (NullPointerException exp) {
			foundChange = true;
		}
		return foundChange;
	}
	/**
	 * Check routing settings
	 */
	public Boolean checkRouting() {
		Boolean check = false;
		for (String route : Alfred.allocation) {
			try {
				String[] sRoute = route.split("=");
				String routeValue = sRoute[1];
				String[] routeKey = sRoute[0].split("\\.");
				String routeType = routeKey[0];
				String routeItem = routeKey[1];
				JsonObject allocationSettings = settings.getAsJsonObject("index").getAsJsonObject("routing").getAsJsonObject("allocation");
				if (allocationSettings.has(routeType)) {
					if (allocationSettings.getAsJsonObject(routeType).has(routeItem)) {
						if (!allocationSettings.getAsJsonObject(routeType).get(routeItem).getAsString().equalsIgnoreCase(routeValue)) {
							check = true;
						}
					} else {
						check = true;
					}
				} else {
					check = true;
				}
			} catch (Exception e) {
				Alfred.println("error", "Check Routing Exception: "+e.getMessage());
			}
		}
		return check;
	}
	/**
	 * Change routing settings
	 */
	public void updateRouting() {
		if (this.open==true) {
			if (Alfred.run) {
				if (checkRouting()) {
					try {
						Alfred.putURL("/"+this.name+"/_settings", Alfred.settings);
					} catch (Exception e) {
			        	Alfred.println("error", "Client Error in request: "+e.getMessage());
			        	if (Alfred.retries>1) {
			        		int r = 1;
			        		while (r!=Alfred.retries) {
			        			try {
			        				Alfred.putURL("/"+this.name+"/_settings", Alfred.settings);
			        				r = Alfred.retries;
			        			} catch (Exception ce) {
			        				Alfred.println("error", "Client Error in request: "+ce.getMessage());
			        				r++;
			        			}
			        		}
			        	}
					}
				}
			} else {
				if (checkRouting()) {
					Alfred.println("general", "Index "+this.name+" would have had routing changed");
				}
			}
		}
	}
	/**
	 * Change routing settings
	 */
	public void updateRouting(Period per) {
		if (this.open==true) {
			if (Alfred.run) {
				if (checkRouting()) {
					try {
						Alfred.putURL("/"+this.name+"/_settings", Alfred.settings);
					} catch (Exception e) {
			        	Alfred.println("error", "Client Error in request: "+e.getMessage());
			        	if (Alfred.retries>1) {
			        		int r = 1;
			        		while (r!=Alfred.retries) {
			        			try {
			        				Alfred.putURL("/"+this.name+"/_settings", Alfred.settings);
			        				r = Alfred.retries;
			        			} catch (Exception ce) {
			        				Alfred.println("error", "Client Error in request: "+ce.getMessage());
			        				r++;
			        			}
			        		}
			        	}
					}
				}
			} else {
				if (checkRouting()) {
					if (this.timeunit.equalsIgnoreCase("hour")) {
						String expireTime = (per.getYears()!=0?" "+Math.abs(per.getYears())+" year(s)":"")+(per.getDays()!=0?" "+Math.abs(per.getDays())+" day(s)":"")+Math.abs(per.getHours())+" hour(s)";
						Alfred.println("general", "Index "+this.name+" would have had routing changed for being "+expireTime+" older than expiry time.");
					} else if (this.timeunit.equalsIgnoreCase("day")) {
						String expireTime = Math.abs(per.getDays())+" days";
						Alfred.println("general", "Index "+this.name+" would have had routing changed for being "+expireTime+" older than expiry time.");
					} else {
						Alfred.println("general", "Index "+this.name+" would have had routing changed.");
					}
				}
			}
		}
	}
}
