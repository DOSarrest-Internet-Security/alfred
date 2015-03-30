package com.dosarrest.alfred;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.joda.time.PeriodType;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Alfred {
	private static String host = "localhost";
	private static String port = "9200";
	private static Boolean ssl = false;
	public static int retries = 1;
	private static int timeout = 30;
	private static String style = "time";
	private static long sizeLimit = (1024*1024*1024*1024)*10;
	private static String timeUnit = "hour";
	private static String index = "_all";
	private static int expireTime = 24;
	private static Boolean delete = false;
	private static Boolean close = false;
	private static Boolean open = false;
	private static Boolean bloom = false;
	private static Boolean debloom = false;
	private static Boolean optimize = false;
	public static String[] excludes;
	public static String[] allocation;
	public static String settings = "";
	private static int max_num_segments = 2;
	private static Boolean flush = false;
	public static Boolean examples = false;
	public static Boolean run = false;
	public static Boolean debug = false;
	public static String verbosity = "info"; // debug|info|warn|error|fatal
	public static Options options = new Options();
	private static String version = "0.0.3";
	private HashMap<String,Index> indexes = new HashMap<String,Index>();
	public static HashMap<String,Node> nodes = new HashMap<String,Node>();
	
	private final static long KB_FACTOR = 1024;
	private final static long MB_FACTOR = 1024 * KB_FACTOR;
	private final static long GB_FACTOR = 1024 * MB_FACTOR;
	private final static long TB_FACTOR = 1024 * GB_FACTOR;
	
	/**
	 * Constructor for Alfred which gets loaded upon successful command line options
	 */
	public Alfred() {
		DateTime cdt = new DateTime().withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0);
		getNodes();
		if (open) {
			getClosedIndices();
		} else {
			getOpenIndices();
		}
		List<String> keys = new ArrayList<String>(indexes.keySet());
		Collections.sort(keys);
		Collections.reverse(keys);
		println("info", "Flush("+flush+") Optimize("+optimize+") Close("+close+") Open("+open+") Bloom("+bloom+") DeBloom("+debloom+") Delete("+delete+")");
		if (style.equalsIgnoreCase("time")) {
			if (timeUnit.equalsIgnoreCase("hour")) {
				DateTime expiration = cdt.minusHours(expireTime).withZone(DateTimeZone.UTC); 
				for (String index : keys) {
					Index indexInfo = indexes.get(index);
					String timeunit = (String) indexInfo.timeunit;
					if (timeunit.equalsIgnoreCase("hour")) {
						DateTime timeStamp = (DateTime) indexInfo.timestamp;
						Period per = new Period(expiration, timeStamp, PeriodType.hours());
						Period revPer = new Period(timeStamp, expiration, PeriodType.hours());
						if (timeStamp.isBefore(expiration) && per.getHours()!=0) {
							if (flush) { indexInfo.flush(revPer); }
							if (optimize) { indexInfo.optimize(revPer, Alfred.max_num_segments); }
							if (close) { indexInfo.close(revPer); }
							if (open) { indexInfo.open(revPer); }
							if (bloom) { indexInfo.bloom(revPer); }
							if (debloom) { indexInfo.debloom(revPer); }
							if (delete) { indexInfo.delete(revPer); }
							if (allocation!=null) { if (allocation.length!=0) { indexInfo.updateRouting(revPer); } }
							if (!settings.equalsIgnoreCase("")) { indexInfo.putSettings(revPer); }
						} else {
							println("general", index+" is "+per.getHours()+" hours above the cuttoff.");
						}
					}
				}
			} else if (timeUnit.equalsIgnoreCase("day")) {
				DateTime expiration = cdt.minusDays(expireTime).withZone(DateTimeZone.UTC);
				for (String index : keys) {
					Index indexInfo = indexes.get(index);
					String timeunit = (String) indexInfo.timeunit;
					if (timeunit.equalsIgnoreCase("day")) {
						DateTime timeStamp = (DateTime) indexInfo.timestamp;
						Period per = new Period(expiration, timeStamp, PeriodType.days());
						Period revPer = new Period(timeStamp, expiration, PeriodType.days());
						if (timeStamp.isBefore(expiration) && per.getDays()!=0) {
							if (flush) { indexInfo.flush(revPer); }
							if (optimize) { indexInfo.optimize(revPer, Alfred.max_num_segments); }
							if (close) { indexInfo.close(revPer); }
							if (open) { indexInfo.open(revPer); }
							if (bloom) { indexInfo.bloom(revPer); }
							if (debloom) { indexInfo.debloom(revPer); }
							if (delete) { indexInfo.delete(revPer); }
							if (allocation!=null) { if (allocation.length!=0) { indexInfo.updateRouting(revPer); } }
							if (!settings.equalsIgnoreCase("")) { indexInfo.putSettings(revPer); }
						} else {
							println("general", index+" is "+per.getDays()+" days above the cuttoff.");
						}
					}
				}
			} else {
				for (String index : keys) {
					Index indexInfo = indexes.get(index);
					String timeunit = (String) indexInfo.timeunit;
					if (timeunit.equalsIgnoreCase("none")) {
						if (flush) { indexInfo.flush(); }
						if (optimize) { indexInfo.optimize(Alfred.max_num_segments); }
						if (close) { indexInfo.close(); }
						if (open) { indexInfo.open(); }
						if (bloom) { indexInfo.bloom(); }
						if (debloom) { indexInfo.debloom(); }
						if (delete) { indexInfo.delete(); }
						if (allocation!=null) { if (allocation.length!=0) { indexInfo.updateRouting(); } }
						if (!settings.equalsIgnoreCase("")) { indexInfo.putSettings(); }
					}
				}
			}
		} else if (style.equalsIgnoreCase("size")) {
			Map<String,Long> sizes = new HashMap<String,Long>();
			for (String index : keys) {
				Index indexInfo = indexes.get(index);
				if (!sizes.containsKey(indexInfo.prefix)) {
					sizes.put(indexInfo.prefix, 0L);
				}
				Long size = sizes.get(indexInfo.prefix);
				println("info", index+" is "+byteSizeToHumanReadable(indexInfo.byteSize, false)+" bytes in size.");
				size += indexInfo.byteSize;
				sizes.put(indexInfo.prefix, size);
				if (size >= sizeLimit) {
					if (flush) { indexInfo.flush(); }
					if (optimize) { indexInfo.optimize(Alfred.max_num_segments); }
					if (close) { indexInfo.close(); }
					if (open) { indexInfo.open(); }
					if (bloom) { indexInfo.bloom(); }
					if (debloom) { indexInfo.debloom(); }
					if (delete) { indexInfo.delete(); }
					if (allocation!=null) { if (allocation.length!=0) { indexInfo.updateRouting(); } }
					if (!settings.equalsIgnoreCase("")) { indexInfo.putSettings(); }
				} else {
					long diff = Math.abs(size-sizeLimit);
					println("general", index+" is "+byteSizeToHumanReadable(diff, false)+" bytes before the cuttoff.");
				}
			}
		}
	}
	
	/**
	 * A human readable byte size to long converter
	 * @param arg0
	 * @return
	 */
	public static long parseByteSize(String arg0) {
	    int spaceNdx = arg0.indexOf(" ");
	    long ret = Long.parseLong(arg0.substring(0, spaceNdx));
	    switch (arg0.substring(spaceNdx + 1)) {
	    	case "TB":
	    		return ret * TB_FACTOR;
	        case "GB":
	            return ret * GB_FACTOR;
	        case "MB":
	            return ret * MB_FACTOR;
	        case "KB":
	            return ret * KB_FACTOR;
	    }
	    return -1;
	}
	/**
	 * Creates a human readable string representing the size used
	 * @param bytes
	 * @param si
	 * @return
	 */
	public static String byteSizeToHumanReadable(long bytes, boolean si) {
	    int unit = si ? 1000 : 1024;
	    if (bytes < unit) return bytes + " B";
	    int exp = (int) (Math.log(bytes) / Math.log(unit));
	    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
	    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}
	
	/**
	 * Get ElasticSearch Nodes
	 */
	public void getNodes() {
		try {
			String docStatsRaw = getURL("/_cluster/state/nodes");
			JsonElement docStats = new JsonParser().parse(docStatsRaw);
			JsonObject docStatsO = docStats.getAsJsonObject();
			JsonObject nodes = docStatsO.getAsJsonObject("nodes");
			Set<Entry<String, JsonElement>> nodesSet = nodes.entrySet();
			int i = 0;
			for (Entry<String, JsonElement> nodeEntry : nodesSet) {
				String id = nodeEntry.getKey();
				Alfred.nodes.put(id, new Node(id, nodeEntry.getValue()));
				i++;
			}
			println("info", i+" Nodes Loaded");
		} catch (Exception e) {
			println("fatal", "Error getting nodes");
		}
	}
	/**
	 * Get ElasticSearch Open Indices
	 */
	public void getOpenIndices() {
		try {
			String docStatsRaw = getURL("/"+Alfred.index+"/_stats/indices");
			JsonElement docStats = new JsonParser().parse(docStatsRaw);
			JsonObject docStatsO = docStats.getAsJsonObject();
			JsonObject indices = docStatsO.getAsJsonObject("indices");
			int i = 0;
			if (indices!=null) {
				Set<Entry<String, JsonElement>> indicesSet = indices.entrySet();
				for (Entry<String, JsonElement> indexEntry : indicesSet) {
					String name = indexEntry.getKey();
					Boolean hasExclude = false;
					if (excludes!=null) {
						for (String exclude : excludes) {
							if (name.contains(exclude)) {
								hasExclude = true;
							}
						}
					}
					if (hasExclude==false) {
						indexes.put(name, new Index(name, true));
						i++;
					}
				}
			}
			println("info", i+" Open Indices Loaded");
		} catch (Exception e) {
			println("fatal", "Error getting Open Indices");
		}
	}
	/**
	 * Get ElasticSearch Closed Indices
	 */
	public void getClosedIndices() {
		try {
			String docStatsRaw = getURL("/_cluster/state/blocks");
			JsonElement docStats = new JsonParser().parse(docStatsRaw);
			JsonObject docStatsO = docStats.getAsJsonObject();
			JsonObject indices = docStatsO.getAsJsonObject("blocks").getAsJsonObject("indices");
			int i = 0;
			if (indices!=null) {
				Set<Entry<String, JsonElement>> indicesSet = indices.entrySet();
				for (Entry<String, JsonElement> indexEntry : indicesSet) {
					String name = indexEntry.getKey();
					Index newIndex = new Index(name, true);
					if (newIndex.prefix.equalsIgnoreCase(index)) {
						indexes.put(name, newIndex);
						i++;
					}
				}
			}
			println("info", i+" Closed Indices Loaded");
		} catch (Exception e) {
			println("fatal", "Error getting Closed Indices");
		}
	}
	
	/**
	 * Perform a GET request
	 * @param url
	 * @return
	 * @throws Exception
	 */
	public static String getURL(String url) throws Exception {
		RequestConfig config = RequestConfig.custom()
				  .setConnectTimeout(timeout * 1000)
				  .setConnectionRequestTimeout(timeout * 1000)
				  .setSocketTimeout(timeout * 1000).build();
		CloseableHttpClient httpclient = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
        try {
            HttpGet httpGet = new HttpGet((ssl?"https":"http")+"://"+host+":"+port+url);

            println("info", "Executing request " + httpGet.getRequestLine());

            // Create a custom response handler
            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {

                public String handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 200 && status < 300) {
                        HttpEntity entity = response.getEntity();
                        return entity != null ? EntityUtils.toString(entity) : null;
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }

            };
            DateTime sdt = new DateTime();
            String responseBody = httpclient.execute(httpGet, responseHandler);
            DateTime edt = new DateTime();
            Period diff = new Period(sdt, edt);
            String msg = "Request completed in ";
            if (diff.getDays()!=0) {
            	msg += diff.getDays()+" days "+diff.getHours()+" hours "+diff.getMinutes()+" minutes "+diff.getSeconds()+" seconds";
            } else if (diff.getHours()!=0) {
            	msg += diff.getHours()+" hours "+diff.getMinutes()+" minutes "+diff.getSeconds()+" seconds";
            } else if (diff.getMinutes()!=0) {
            	msg += diff.getMinutes()+" minutes "+diff.getSeconds()+" seconds";
            } else if (diff.getSeconds()!=0) {
            	msg += diff.getSeconds()+" seconds "+diff.getMillis()+" milliseconds";
            } else if (diff.getMillis()!=0) {
            	msg += diff.getMillis()+" milliseconds";
            }
            println("debug", responseBody);
            println("info", msg);
            return responseBody;
        } finally {
            httpclient.close();
        }
	}
	/**
	 * Perform a POST request
	 * @param url
	 * @return
	 * @throws Exception
	 */
	public static String postURL(String url) throws Exception {
		RequestConfig config = RequestConfig.custom()
				  .setConnectTimeout(timeout * 1000)
				  .setConnectionRequestTimeout(timeout * 1000)
				  .setSocketTimeout(timeout * 1000).build();
		CloseableHttpClient httpclient = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
        try {
        	HttpPost httpPost = new HttpPost((ssl?"https":"http")+"://"+host+":"+port+url);

        	println("info", "Executing request " + httpPost.getRequestLine());

            // Create a custom response handler
            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
                public String handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 200 && status < 300) {
                        HttpEntity entity = response.getEntity();
                        return entity != null ? EntityUtils.toString(entity) : null;
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }
            };
            DateTime sdt = new DateTime();
            String responseBody = httpclient.execute(httpPost, responseHandler);
            DateTime edt = new DateTime();
            Period diff = new Period(sdt, edt);
            String msg = "Request completed in ";
            if (diff.getDays()!=0) {
            	msg += diff.getDays()+" days "+diff.getHours()+" hours "+diff.getMinutes()+" minutes "+diff.getSeconds()+" seconds";
            } else if (diff.getHours()!=0) {
            	msg += diff.getHours()+" hours "+diff.getMinutes()+" minutes "+diff.getSeconds()+" seconds";
            } else if (diff.getMinutes()!=0) {
            	msg += diff.getMinutes()+" minutes "+diff.getSeconds()+" seconds";
            } else if (diff.getSeconds()!=0) {
            	msg += diff.getSeconds()+" seconds "+diff.getMillis()+" milliseconds";
            } else if (diff.getMillis()!=0) {
            	msg += diff.getMillis()+" milliseconds";
            }
            println("debug", responseBody);
            println("info", msg);
            return responseBody;
        } finally {
            httpclient.close();
        }
	}
	/**
	 * Perform a DELETE request
	 * @param url
	 * @return
	 * @throws Exception
	 */
	public static String deleteURL(String url) throws Exception {
		RequestConfig config = RequestConfig.custom()
				  .setConnectTimeout(timeout * 1000)
				  .setConnectionRequestTimeout(timeout * 1000)
				  .setSocketTimeout(timeout * 1000).build();
		CloseableHttpClient httpclient = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
        try {
        	HttpDelete httpDelete = new HttpDelete((ssl?"https":"http")+"://"+host+":"+port+url);

        	println("info", "Executing request " + httpDelete.getRequestLine());

            // Create a custom response handler
            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
                public String handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 200 && status < 300) {
                        HttpEntity entity = response.getEntity();
                        return entity != null ? EntityUtils.toString(entity) : null;
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }
            };
            DateTime sdt = new DateTime();
            String responseBody = httpclient.execute(httpDelete, responseHandler);
            DateTime edt = new DateTime();
            Period diff = new Period(sdt, edt);
            String msg = "Request completed in ";
            if (diff.getDays()!=0) {
            	msg += diff.getDays()+" days "+diff.getHours()+" hours "+diff.getMinutes()+" minutes "+diff.getSeconds()+" seconds";
            } else if (diff.getHours()!=0) {
            	msg += diff.getHours()+" hours "+diff.getMinutes()+" minutes "+diff.getSeconds()+" seconds";
            } else if (diff.getMinutes()!=0) {
            	msg += diff.getMinutes()+" minutes "+diff.getSeconds()+" seconds";
            } else if (diff.getSeconds()!=0) {
            	msg += diff.getSeconds()+" seconds "+diff.getMillis()+" milliseconds";
            } else if (diff.getMillis()!=0) {
            	msg += diff.getMillis()+" milliseconds";
            }
            println("debug", responseBody);
            println("info", msg);
            return responseBody;
        } finally {
            httpclient.close();
        }
	}
	/**
	 * Perform a PUT request
	 * @param url
	 * @return
	 * @throws Exception
	 */
	public static String putURL(String url) throws Exception {
		RequestConfig config = RequestConfig.custom()
				  .setConnectTimeout(timeout * 1000)
				  .setConnectionRequestTimeout(timeout * 1000)
				  .setSocketTimeout(timeout * 1000).build();
		CloseableHttpClient httpclient = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
        try {
        	HttpPut httpPut = new HttpPut((ssl?"https":"http")+"://"+host+":"+port+url);

            println("info", "Executing Request " + httpPut.getRequestLine());

            // Create a custom response handler
            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
                public String handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 200 && status < 300) {
                        HttpEntity entity = response.getEntity();
                        return entity != null ? EntityUtils.toString(entity) : null;
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }
            };
            DateTime sdt = new DateTime();
            String responseBody = httpclient.execute(httpPut, responseHandler);
            DateTime edt = new DateTime();
            Period diff = new Period(sdt, edt);
            String msg = "Request completed in ";
            if (diff.getDays()!=0) {
            	msg += diff.getDays()+" days "+diff.getHours()+" hours "+diff.getMinutes()+" minutes "+diff.getSeconds()+" seconds";
            } else if (diff.getHours()!=0) {
            	msg += diff.getHours()+" hours "+diff.getMinutes()+" minutes "+diff.getSeconds()+" seconds";
            } else if (diff.getMinutes()!=0) {
            	msg += diff.getMinutes()+" minutes "+diff.getSeconds()+" seconds";
            } else if (diff.getSeconds()!=0) {
            	msg += diff.getSeconds()+" seconds "+diff.getMillis()+" milliseconds";
            } else if (diff.getMillis()!=0) {
            	msg += diff.getMillis()+" milliseconds";
            }
            println("debug", responseBody);
            println("info", msg);
            return responseBody;
        } finally {
            httpclient.close();
        }
	}
	/**
	 * Perform a PUT request with data
	 * @param url
	 * @param data
	 * @return
	 * @throws Exception
	 */
	public static String putURL(String url, String data) throws Exception {
		RequestConfig config = RequestConfig.custom()
				  .setConnectTimeout(timeout * 1000)
				  .setConnectionRequestTimeout(timeout * 1000)
				  .setSocketTimeout(timeout * 1000).build();
		CloseableHttpClient httpclient = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
        try {
        	HttpPut httpPut = new HttpPut((ssl?"https":"http")+"://"+host+":"+port+url);
        	httpPut.addHeader("Accept", "application/json");
            httpPut.addHeader("Content-Type", "application/json");
            StringEntity entity = new StringEntity(data, "UTF-8");
            entity.setContentType("application/json");
            httpPut.setEntity(entity);
            println("info", "Executing Request " + httpPut.getRequestLine()+" '"+data+"'");

            // Create a custom response handler
            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
                public String handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 200 && status < 300) {
                        HttpEntity entity = response.getEntity();
                        return entity != null ? EntityUtils.toString(entity) : null;
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }
            };
            DateTime sdt = new DateTime();
            String responseBody = httpclient.execute(httpPut, responseHandler);
            DateTime edt = new DateTime();
            Period diff = new Period(sdt, edt);
            String msg = "Request completed in ";
            if (diff.getDays()!=0) {
            	msg += diff.getDays()+" days "+diff.getHours()+" hours "+diff.getMinutes()+" minutes "+diff.getSeconds()+" seconds";
            } else if (diff.getHours()!=0) {
            	msg += diff.getHours()+" hours "+diff.getMinutes()+" minutes "+diff.getSeconds()+" seconds";
            } else if (diff.getMinutes()!=0) {
            	msg += diff.getMinutes()+" minutes "+diff.getSeconds()+" seconds";
            } else if (diff.getSeconds()!=0) {
            	msg += diff.getSeconds()+" seconds "+diff.getMillis()+" milliseconds";
            } else if (diff.getMillis()!=0) {
            	msg += diff.getMillis()+" milliseconds";
            }
            println("debug", responseBody);
            println("info", msg);
            return responseBody;
        } finally {
            httpclient.close();
        }
	}
	
	/**
	 * Handle printing messages to standard output
	 * @param level
	 * @param message
	 */
	public static void println(String level, String message) {
		if (debug) {
			if (verbosity.equalsIgnoreCase("debug")) {
				System.out.println(level.toUpperCase()+": "+message);
			} else if (verbosity.equalsIgnoreCase("info")) {
				if (level.equalsIgnoreCase("info") || level.equalsIgnoreCase("warn") || level.equalsIgnoreCase("error") || level.equalsIgnoreCase("fatal") || level.equalsIgnoreCase("general")) {
					System.out.println(level.toUpperCase()+": "+message);
				}
			} else if (verbosity.equalsIgnoreCase("warn")) {
				if (level.equalsIgnoreCase("warn") || level.equalsIgnoreCase("error") || level.equalsIgnoreCase("fatal") || level.equalsIgnoreCase("general")) {
					System.out.println(level.toUpperCase()+": "+message);
				}
			} else if (verbosity.equalsIgnoreCase("error")) {
				if (level.equalsIgnoreCase("error") || level.equalsIgnoreCase("fatal") || level.equalsIgnoreCase("general")) {
					System.out.println(level.toUpperCase()+": "+message);
				}
			} else if (verbosity.equalsIgnoreCase("fatal")) {
				if (level.equalsIgnoreCase("fatal") || level.equalsIgnoreCase("general")) {
					System.out.println(level.toUpperCase()+": "+message);
				}
			}
		} else {
			if (level.equalsIgnoreCase("general") || level.equalsIgnoreCase("error") || level.equalsIgnoreCase("fatal")) {
				System.out.println(level.toUpperCase()+": "+message);
			}
		}
	}
	
	/**
	 * Show examples of how to use Alfred
	 */
	public static void examples() {
		println("general", "-e24 -d    # Delete all indices older than 24 hours");
		println("general", "-e24 -fb    # Flush and disable bloom filter on all indices older than 24 hours");
		println("general", "-ssize -E\"10 GB\" -fb    # Flush and disable bloom filter on indices after 10 GB");
	}
	
	/**
	 * Check command line arguments and set internal variables
	 * @param args
	 * @return
	 */
	private static Boolean checkArgs(String[] args) {
		CommandLineParser parser = new PosixParser();
		try {
			CommandLine cmd = parser.parse( options, args);
			debug = cmd.hasOption("D");
			if (debug) {
				if (((String) cmd.getOptionValue("D")).matches("^(debug|info|warn|error|fatal)+$")) {
					verbosity = (String) cmd.getOptionValue("D");
				}
			}
			if (cmd.hasOption("style")) {
				style = (String) cmd.getOptionValue("style");
				println("debug", "Style Parameter: "+style);
			}
			if (cmd.hasOption("host")) {
				host = (String) cmd.getOptionValue("host");
				println("debug", "Host Parameter: "+host);
			}
			if (cmd.hasOption("port")) {
				port = (String) cmd.getOptionValue("port");
				println("debug", "Port Parameter: "+port);
			}
			ssl = cmd.hasOption("ssl");
			if (cmd.hasOption("i")) {
				index = (String) cmd.getOptionValue("i");
				println("debug", "Index Parameter: "+index);
			}
			if (cmd.hasOption("t")) {
				timeout = Integer.parseInt((String) cmd.getOptionValue("t"));
				println("debug", "Timeout Parameter: "+timeout);
			}
			if (cmd.hasOption("T")) {
				timeUnit = (String) cmd.getOptionValue("T");
				println("debug", "TimeUnit Parameter: "+timeUnit);
			}
			if (cmd.hasOption("e")) {
				expireTime = Integer.parseInt((String) cmd.getOptionValue("e"));
				println("debug", "ExpireTime Parameter: "+expireTime);
			}
			if (cmd.hasOption("max_num_segments")) {
				max_num_segments = Integer.parseInt((String) cmd.getOptionValue("max_num_segments"));
				println("debug", "max_num_segments Parameter: "+max_num_segments);
			}
			if (cmd.hasOption("E")) {
				sizeLimit = parseByteSize((String) cmd.getOptionValue("E"));
				println("debug", "Expire Size Parameter: "+sizeLimit);
			}
			if (cmd.hasOption("x")) {
				excludes = cmd.getOptionValues("x");
				String eString = "";
				if (excludes.length==1) {
					eString = excludes[0];
				} else if (excludes.length!=0) {
					for (String es : excludes) {
						eString += (eString.equalsIgnoreCase("")?"":",")+es;
					}
				}
				println("debug", "Exclude: "+excludes);
			}
			if (cmd.hasOption("a")) {
				allocation = cmd.getOptionValues("a");
				String eString = "";
				if (allocation.length==1) {
					eString = allocation[0];
				} else if (allocation.length!=0) {
					for (String es : allocation) {
						eString += (eString.equalsIgnoreCase("")?"":",")+es;
					}
				}
				println("debug", "Allocations: "+allocation);
			}
			delete = cmd.hasOption("delete");
			if (delete) {
				println("debug", "Delete Parameter: "+delete);
			}
			close = cmd.hasOption("close");
			if (close) {
				println("debug", "Close Parameter: "+close);
			}
			open = cmd.hasOption("open");
			if (open) {
				println("debug", "Open Parameter: "+open);
			}
			bloom = cmd.hasOption("B");
			if (bloom) {
				println("debug", "Bloom Parameter: "+bloom);
			}
			debloom = cmd.hasOption("b");
			if (debloom) {
				println("debug", "Debloom Parameter: "+debloom);
			}
			optimize = cmd.hasOption("optimize");
			if (optimize) {
				println("debug", "Optimize Parameter: "+optimize);
			}
			flush = cmd.hasOption("flush");
			if (flush) {
				println("debug", "Flush Parameter: "+flush);
			}
			if (cmd.hasOption("S")) {
				settings = (String) cmd.getOptionValue("S");
				println("debug", "Settings Parameter: "+settings);
			}
			examples = cmd.hasOption("examples");
			run = cmd.hasOption("r");
			if (run) {
				println("debug", "Run Parameter: "+run);
			}
			if (optimize && timeout<360) {
				println("warn", "Timeout value is set too low (Current: "+timeout+") (Recommended: 3600 or greater)");
			}
			if(cmd.hasOption( "h" )) {
				return false;
			}
			if (cmd.hasOption("R")) {
				retries = Integer.parseInt((String) cmd.getOptionValue("R"));
				println("debug", "Retries Parameter: "+retries);
			}
			if (retries==0) {
				println("fatal", "Retries cannot be 0");
				return false;
			} else if (retries<0) {
				println("fatal", "Retries cannot be less than 0");
				return false;
			}
		} catch (ParseException e1) {
			println("fatal", "Error parsing command line options!");
			return false;
		}
		return true;
	}
	/**
	 * Set the command line options that are available
	 */
	private static void setOptions() {
		options.addOption(null, "host", true, "ElasticSearch Host");
		options.addOption(null, "port", true, "ElasticSearch Port");
		options.addOption(null, "ssl", false, "ElasticSearch SSL");
		options.addOption("i", "index", true, "Index pattern to match (Default _all)");
		options.addOption("t", "timeout", true, "ElasticSearch Timeout (Default 30)");
		options.addOption("s", "style", true, "Clean up style (time|size) (Default time)");
		Option op1 = new Option("T", "time-unit", true, "Specify time units (hour|day|none) (Default hour)");
		options.addOption(op1);
		Option op2 = new Option("e", "expiretime", true, "Number of time units old (Default 24)");
		options.addOption(op2);
		
		Option op3 = new Option("f", "flush", false, "Flush Indexes");
		options.addOption(op3);
		Option op4 = new Option("d", "delete", false, "Delete Indexes");
		options.addOption(op4);
		Option op5 = new Option("c", "close", false, "Close Indexes");
		options.addOption(op5);
		Option op6 = new Option("O", "open", false, "Open Indexes");
		options.addOption(op6);
		Option op7 = new Option("b", "debloom", false, "Disable Bloom on Indexes");
		options.addOption(op7);
		Option op8 = new Option("B", "bloom", false, "Enable Bloom on Indexes");
		options.addOption(op8);
		Option op9 = new Option("o", "optimize", false, "Optimize Indexes");
		options.addOption(op9);
		Option op10 = new Option(null, "max_num_segments", true, "Optimize max_num_segments (Default 2)");
		options.addOption(op10);
		Option op11 = new Option("S", "settings", true, "PUT settings");
		options.addOption(op11);
		
		Option op12 = new Option("E", "expiresize", true, "Byte size limit  (Default 10 GB)");
		options.addOption(op12);
		
		Option op13 = new Option("x", "exclude", true, "Index pattern to exclude");
		op13.setArgs(Option.UNLIMITED_VALUES);
		op13.setValueSeparator(',');
		options.addOption(op13);
		
		Option op14 = new Option("a", "allocation", true, "Allocation settings (Ex. require.tag=historical,exclude.tag=realtime)");
		op14.setArgs(Option.UNLIMITED_VALUES);
		op14.setValueSeparator(',');
		options.addOption(op14);
		
		options.addOption("R", "retries", true, "Number of retries on http error (Default 1)");
		options.addOption(null, "examples", false, "Show some examples of how to use Alfred");
		options.addOption("r", "run", false, "Required to execute changes on ElasticSearch");
		options.addOption("D", "debug", true, "Display debug (debug|info|warn|error|fatal)");
		options.addOption("h", "help", false, "Help Page (Viewing Now)");
	}

	/**
	 * Check command line arguments and set internal variables
	 * @param args
	 * @return
	 */
	private static Boolean checkArgParse(Namespace args) {
		debug = (args.getString("debug")=="default"?false:true);
		if (debug) {
			if (args.getString("debug").matches("^(debug|info|warn|error|fatal)+$")) {
				verbosity = args.getString("debug");
			}
		}
		if (args.getString("style").matches("^(time|size)$")) {
			style = args.getString("style");
			println("debug", "Style Parameter: "+style);
		}
		host = args.getString("host");
		println("debug", "Host Parameter: "+host);
		port = args.getString("port");
		println("debug", "Port Parameter: "+port);
		ssl = args.getBoolean("ssl");
		index = args.getString("index");
		println("debug", "Index Parameter: "+index);
		timeout = args.getInt("timeout");
		println("debug", "Timeout Parameter: "+timeout);
		
		if (args.getString("cmd").equalsIgnoreCase("index")) {
			timeUnit = args.getString("time_unit");
			println("debug", "TimeUnit Parameter: "+timeUnit);
			expireTime = args.getInt("expire");
			println("debug", "ExpireTime Parameter: "+expireTime);
			sizeLimit = parseByteSize(args.getString("expiresize"));
			println("debug", "Expire Size Parameter: "+sizeLimit);
			excludes = args.getList("exclude").toArray(new String[0]);
			String eString = "";
			if (excludes.length==1) {
				eString = excludes[0];
			} else if (excludes.length!=0) {
				for (String es : excludes) {
					eString += (eString.equalsIgnoreCase("")?"":",")+es;
				}
			}
			println("debug", "Exclude: "+eString);
			
			if (args.getString("indexCmd").equalsIgnoreCase("show")) {
			} else if (args.getString("indexCmd").equalsIgnoreCase("flush")) {
			} else if (args.getString("indexCmd").equalsIgnoreCase("close")) {
			} else if (args.getString("indexCmd").equalsIgnoreCase("open")) {
			} else if (args.getString("indexCmd").equalsIgnoreCase("delete")) {
			} else if (args.getString("indexCmd").equalsIgnoreCase("bloom")) {
			} else if (args.getString("indexCmd").equalsIgnoreCase("debloom")) {
			} else if (args.getString("indexCmd").equalsIgnoreCase("optimize")) {
				max_num_segments = args.getInt("max_num_segments");
				println("debug", "max_num_segments Parameter: "+max_num_segments);
			} else if (args.getString("indexCmd").equalsIgnoreCase("settings")) {
			} else if (args.getString("indexCmd").equalsIgnoreCase("route")) {
				
			}
		}
		
		if (cmd.hasOption("a")) {
			allocation = cmd.getOptionValues("a");
			println("debug", "Allocations: "+allocation);
		}
		delete = cmd.hasOption("delete");
		if (delete) {
			println("debug", "Delete Parameter: "+delete);
		}
		close = cmd.hasOption("close");
		if (close) {
			println("debug", "Close Parameter: "+close);
		}
		open = cmd.hasOption("open");
		if (open) {
			println("debug", "Open Parameter: "+open);
		}
		bloom = cmd.hasOption("B");
		if (bloom) {
			println("debug", "Bloom Parameter: "+bloom);
		}
		debloom = cmd.hasOption("b");
		if (debloom) {
			println("debug", "Debloom Parameter: "+debloom);
		}
		optimize = cmd.hasOption("optimize");
		if (optimize) {
			println("debug", "Optimize Parameter: "+optimize);
		}
		flush = cmd.hasOption("flush");
		if (flush) {
			println("debug", "Flush Parameter: "+flush);
		}
		if (cmd.hasOption("S")) {
			settings = (String) cmd.getOptionValue("S");
			println("debug", "Settings Parameter: "+settings);
		}
		examples = cmd.hasOption("examples");
		run = cmd.hasOption("r");
		if (run) {
			println("debug", "Run Parameter: "+run);
		}
		if (optimize && timeout<360) {
			println("warn", "Timeout value is set too low (Current: "+timeout+") (Recommended: 3600 or greater)");
		}
		if (cmd.hasOption("R")) {
			retries = Integer.parseInt((String) cmd.getOptionValue("R"));
			println("debug", "Retries Parameter: "+retries);
		}
		if (retries==0) {
			println("fatal", "Retries cannot be 0");
			return false;
		} else if (retries<0) {
			println("fatal", "Retries cannot be less than 0");
			return false;
		}
		return true;
	}
	private static Namespace argParse(String[] args) {
		ArgumentParser parser = ArgumentParsers.newArgumentParser("alfred");
		parser.addArgument("--debug", "-D").setDefault("default").help("Display debugging (debug|info|warn|error|fatal)");
		parser.addArgument("--host").setDefault("localhost").help("ElasticSearch Host (Default: localhost)");
		parser.addArgument("--port").setDefault("9200").help("ElasticSearch Port (Default: 9200)");
		parser.addArgument("--ssl").action(Arguments.storeConst()).setConst(true).type(boolean.class).setDefault(false).help("ElasticSearch uses SSL");
		parser.addArgument("--examples").action(Arguments.storeConst()).setConst(true).type(boolean.class).setDefault(false).help("Show Alfred examples");
		parser.addArgument("--run", "-r").action(Arguments.storeConst()).setConst(true).type(boolean.class).setDefault(false).help("Execute changes");
		parser.addArgument("--timeout", "-t").setDefault(30).type(int.class).help("Set Timeout");
		parser.addArgument("--retries", "-R").setDefault(1).type(int.class).help("Number of retries on http error");
	    Subparsers subparsers = parser.addSubparsers().title("subcommands")
	            .description("valid subcommands").help("additional help")
	            .metavar("COMMAND").dest("cmd");
	    Subparser index = subparsers.addParser("index").aliases("indices", "i").help("index help");
	    index.addArgument("--index", "-i").setDefault("_all").help("Index pattern (Default _all)");
	    index.addArgument("--style", "-s").setDefault("time").help("Clean up style (time|size) (Default time)");
	    index.addArgument("--time-unit", "-T").help("Set time unit to use");
	    index.addArgument("--expire", "-e").help("Set time unit to use");
	    index.addArgument("--expiresize", "-E").help("Set expire size");
	    index.addArgument("--exclude", "-x").action(Arguments.append()).help("Index patterns to exclude");
	    index.addArgument("--flush", "-f").action(Arguments.storeConst()).setConst(true).type(boolean.class).setDefault(false).help("Flush indices");
	    index.addArgument("--close", "-c").action(Arguments.storeConst()).setConst(true).type(boolean.class).setDefault(false).help("Close indices");
	    index.addArgument("--open", "-O").action(Arguments.storeConst()).setConst(true).type(boolean.class).setDefault(false).help("Open indices");
	    index.addArgument("--delete", "-d").action(Arguments.storeConst()).setConst(true).type(boolean.class).setDefault(false).help("Delete indices");
	    index.addArgument("--bloom", "-B").action(Arguments.storeConst()).setConst(true).type(boolean.class).setDefault(false).help("Bloom indices");
	    index.addArgument("--debloom", "-b").action(Arguments.storeConst()).setConst(true).type(boolean.class).setDefault(false).help("Disable Bloom on indices");
	    Subparsers indexSubParsers = index.addSubparsers().title("subcommands").description("valid subcommands").help("additional help").metavar("COMMAND").dest("indexCmd");
	    Subparser optimizeSP = indexSubParsers.addParser("optimize").help("Optimize indices");
	    optimizeSP.addArgument("--max_num_segments", "-s").help("Set max number of segments");
	    Subparser settingsSP = indexSubParsers.addParser("settings").help("Set indices settings");
	    settingsSP.addArgument("--settings", "-s").help("Settings to set");
	    Subparser routeSP = indexSubParsers.addParser("route").help("Change indices allocation routing");
	    routeSP.addArgument("--require").action(Arguments.append()).help("Require settings");
	    routeSP.addArgument("--exclude").action(Arguments.append()).help("Exclude settings");
	    routeSP.addArgument("--include").action(Arguments.append()).help("Include settings");
	    
	    Subparser snapshot = subparsers.addParser("snapshot").aliases("s").help("snapshot help");
	    Subparsers snapshotSubParsers = snapshot.addSubparsers().title("subcommands").description("valid subcommands").help("additional help").metavar("COMMAND").dest("snapshotCmd");
	    snapshotSubParsers.addParser("show").help("Show snapshots");
	    snapshotSubParsers.addParser("create").help("Create snapshots");
	    snapshotSubParsers.addParser("restore").help("Restore snapshots");
	    try {
	    	Namespace ns = parser.parseArgs(args);
	    	System.out.println(parser.parseArgs(args));
	    	return ns;
	    } catch (ArgumentParserException e) {
	        parser.handleError(e);
	    }
	    return null;
	}
	
	/**
	 * Main function which starts Alfred
	 * @param args
	 */
	public static void main(String[] args) {	    
		Namespace ns = argParse(args);
		if (ns!=null) {
			checkArgParse(ns);
		}
		setOptions();
		if (args.length>0) {
			if (checkArgs(args)) {
				if (examples==false) {
					new Alfred();
				} else {
					examples();
				}
			} else {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("alfred", options);
				System.out.println("Alfred Version: "+version);
			}
		} else {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("alfred", options);
			System.out.println("Alfred Version: "+version);
		}
	}

}
