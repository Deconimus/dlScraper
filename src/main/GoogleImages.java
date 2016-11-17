package main;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import com.mortennobel.imagescaling.ResampleOp;

import visionCore.util.Files;

public class GoogleImages {

	public static final String API_KEY = "AIzaSyCBpXUOZyBD-3ImaPy9dI343iMmn8ekM_Y";
	
	public static final float POSTER_RATIO = 0.68f;
	public static final int POSTER_WIDTH = 680, POSTER_HEIGHT = 1000;
	
	private static List<JsonObject> getResults(String query) {
		return getResults(query, 32);
	}
	
	private static List<JsonObject> getResults(String query, int size) {
		
		if (size < 1) { return null; }
		
		query = query.trim();
		query = query.replace(' ', '+');
		
		List<JsonObject> results = new ArrayList<JsonObject>();
		
		for (int i = 0; i < Math.max(1, size / 8); i++) {
			
			try {
				
				URLConnection connection = null;
				InputStream is = null;
				
				String url = "https://www.googleapis.com/customsearch/v1?start="+(i*8)+"&num=8&q="+query+"&fileType=jpg&imgSize=large"+"&key="+API_KEY;
				
				connection = new URL(url).openConnection();
				is = connection.getInputStream();
				
				JsonReader rdr = Json.createReader(is);
				JsonObject obj = rdr.readObject().getJsonObject("responseData");
				
				for (JsonObject result : obj.getJsonArray("results").getValuesAs(JsonObject.class)) {
					
					results.add(result);
					
					if (results.size() >= size) { break; }
					
				}
				
			} catch (Exception | Error e) { e.printStackTrace(); }
			
		}
		
		if (results == null || results.size() == 0) {
			return null;
		}
		
		return results;
	}
	
	private static BufferedImage getImage(List<JsonObject> results) {
		
		for (JsonObject result : results) {
			
			BufferedImage img = getImage(result);
			if (img != null) { return img; }
			
		}
		
		return null;
	}
	
	private static BufferedImage getImage(JsonObject result) {
		
		String url = result.getString("url");
		
		BufferedImage img = null;
		
		try {
			img = ImageIO.read(new URL(url));
		} catch (Exception e) { }
		
		return img;
	}
	
	private static void saveImages(List<JsonObject> results, File outDir) {
		saveImages(results, outDir, results.size());
	}
	
	private static void saveImages(List<JsonObject> results, File outDir, int num) {
		
		int i = 0;
		for (JsonObject result : results) {
			
			if (saveImage(result, new File(outDir.getPath()+"/"+i+".jpg"))) {
				
				i++;
				
				if (i >= num) { break; }
				
			}
			
		}
		
	}
	
	private static void saveImage(List<JsonObject> results, File out) {
		
		for (JsonObject result : results) {
			
			if (saveImage(result, out)) { break; }
			
		}
		
	}
	
	private static boolean saveImage(JsonObject result, File out) {
		
		BufferedImage img = getImage(result);
		if (img == null) { return false; }
		
		try {
			ImageIO.write(img, "jpg", out);
		} catch (Exception e) { return false; }
		
		return true;
	}
	
	public static void fetchFanart(String showName, File showDir) {
		
		if (showName.length() < 1 || !showDir.exists()) { return; }
		
		File fanart = new File(showDir.getPath()+"/fanart.jpg");
		if (fanart.exists()) { return; }
		
		System.out.println("Fetching fanart for "+showName);
		
		List<JsonObject> results = getResults(showName+" wallpaper imagesize:1920x1080");
		if (results == null) { return; }
		
		saveImage(results, fanart);
		
		File extraFanartDir = new File(showDir.getPath()+"/"+"extrafanart");
		if (!extraFanartDir.exists()) { extraFanartDir.mkdirs(); }
		saveImages(results, extraFanartDir, 8);
		
	}
	
	public static void fetchPoster(String showName, File showDir) {
		fetchPoster(showName, showDir, "poster");
	}
	
	public static void fetchPoster(String showName, File showDir, String keyword) {
		
		if (showName.length() < 1 || !showDir.exists()) { return; }
		
		File poster = new File(showDir.getPath()+"/folder.jpg");
		
		List<File> seasonPosters = new ArrayList<File>();
		
		for (File f : showDir.listFiles()) {
			if (!f.isDirectory()) { continue; }
			
			if (f.getName().toLowerCase().startsWith("season")) {
				
				int num = Integer.parseInt(f.getName().toLowerCase().substring(7));
				String s = "";
				
				if (num < 10) { s += "0"; }
				s += num+"";
				
				seasonPosters.add(new File(showDir.getPath()+"/season"+s+"-poster.jpg"));
				
			}
			
		}
		
		seasonPosters.add(new File(showDir.getPath()+"/season-all-poster.jpg"));
		
		if (poster.exists()) {
			
			for (File f : seasonPosters) {
				
				if (!f.exists()) {
					
					Files.copyFileUsingOS(poster, f);
					
				}
				
			}
			
			return;
		}
		
		System.out.println("Fetching posters for "+showName);
		
		List<JsonObject> results = getResults(showName+" "+keyword);
		if (results == null) { return; }
		
		Comparator<JsonObject> comp = new Comparator<JsonObject>(){

			@Override
			public int compare(JsonObject obj0, JsonObject obj1) {
				
				float ratio0 = (float)Integer.parseInt(obj0.getString("width")) / (float)Integer.parseInt(obj0.getString("height"));
				float ratio1 = (float)Integer.parseInt(obj1.getString("width")) / (float)Integer.parseInt(obj1.getString("height"));
				
				if (Math.abs(ratio0 - POSTER_RATIO) < Math.abs(ratio1 - POSTER_RATIO)) {
					
					return -1;
					
				} else if (Math.abs(ratio0 - POSTER_RATIO) > Math.abs(ratio1 - POSTER_RATIO)) {
					
					return 1;
					
				}
				
				return 0;
			}
			
		};
		
		Collections.sort(results, comp);
		
		BufferedImage img = getImage(results);
		
		float ratio = (float)img.getWidth() / (float)img.getHeight();
		
		BufferedImage tmp = new BufferedImage(POSTER_WIDTH, POSTER_HEIGHT, BufferedImage.TYPE_INT_RGB);
		Graphics tmpG = tmp.getGraphics();
		
		if (ratio > POSTER_RATIO) {
			
			float scale = (float)POSTER_HEIGHT / (float)img.getHeight();
			
			int newWidth = (int)(img.getHeight() * scale);
			int shift = (POSTER_WIDTH - newWidth) / 2;
			
			ResampleOp resample = new ResampleOp(newWidth, POSTER_HEIGHT);
			BufferedImage buffImg = resample.filter((BufferedImage)(img), null);
			
			tmpG.drawImage(buffImg, shift, 0, newWidth, POSTER_HEIGHT, null);
			
		} else {
			
			float scale = (float)POSTER_WIDTH / (float)img.getWidth();
			
			int newHeight = (int)(img.getHeight() * scale);
			int shift = (POSTER_HEIGHT - newHeight) / 2;
			
			ResampleOp resample = new ResampleOp(POSTER_WIDTH, newHeight);
			BufferedImage buffImg = resample.filter((BufferedImage)(img), null);
			
			tmpG.drawImage(buffImg, shift, 0, POSTER_WIDTH, newHeight, null);
			
		}
		
		if (!poster.exists()) {
			
			try {
				ImageIO.write(tmp, "jpg", poster);
			} catch (Exception | Error e) {}
			
		}
		
		for (File seasonPoster : seasonPosters) {
		
			if (!seasonPoster.exists()) {
			
				try {
					ImageIO.write(tmp, "jpg", seasonPoster);
				} catch (Exception | Error e) {}
				
			}
			
		}
		
	}
	
}
