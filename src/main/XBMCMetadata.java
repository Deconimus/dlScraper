package main;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

import visionCore.util.Files;

public class XBMCMetadata {

	public static void createShowNFO(File dir, String showName) {
		
		createShowNFO(dir, showName, "");
	}
	
	public static void createShowNFO(File dir, String showName, String plot) {
		
		if (!dir.exists()) { return; }
		
		File nfo = new File(dir.getPath()+"/tvshow.nfo");
		
		if (!nfo.exists()) {
			
			Document doc =  DocumentHelper.createDocument();
			
			Element root = doc.addElement("tvshow");
			
			if (showName.length() > 0) { root.addElement("title").setText(showName); }
			if (plot.length() > 0) { root.addElement("plot").setText(plot); }
			
			saveDocument(doc, nfo);
		}
		
	}
	
	public static void createShowNFO(Element tvdbXML, File out) {
		
		int id = -1;
		String aired = "", finaleAired = "", imdbID = "", language = "", network = "", 
				name = "", status = "", plot = "", runtime = "", genres = "";
		double rating = 0.0;
		
		try {
			
			name = tvdbXML.elementTextTrim("SeriesName");
			
			try { network = cleanse(tvdbXML.elementTextTrim("Network")); } catch (Exception e1) {}
			try { language = cleanse(tvdbXML.elementTextTrim("Language")); } catch (Exception e1) {}
			try { finaleAired = tvdbXML.elementTextTrim("finale_aired"); } catch (Exception e1) {}
			try { aired = tvdbXML.elementTextTrim("FirstAired"); } catch (Exception e1) {}
			try { imdbID = tvdbXML.elementTextTrim("IMDB_ID"); } catch (Exception e1) {}
			try { status = cleanse(tvdbXML.elementTextTrim("Status")); } catch (Exception e1) {}
			try { plot = cleanse(tvdbXML.elementTextTrim("Overview")); } catch (Exception e1) {}
			try { runtime = cleanse(tvdbXML.elementTextTrim("Runtime")); } catch (Exception e1) {}
			try { genres = cleanse(tvdbXML.elementTextTrim("Genre")); } catch (Exception e1) {}
			
			try { id = (int)Double.parseDouble(tvdbXML.elementTextTrim("id")); } catch (Exception e1) {}
			try { rating = Double.parseDouble(tvdbXML.elementTextTrim("Rating")); } catch (Exception e1) {}
			
		} catch (Exception e) { e.printStackTrace(); return; }
		
		Document doc =  DocumentHelper.createDocument();
		Element root = doc.addElement("tvshow");
		
		root.addElement("title").setText(name);
		root.addElement("plot").setText(plot);
		root.addElement("rating").setText(rating+"");
		root.addElement("runtime").setText(runtime);
		root.addElement("studio").setText(network);
		root.addElement("premiered").setText(aired);
		root.addElement("aired").setText(aired);
		root.addElement("status").setText(status);
		root.addElement("id").setText(id > 0 ? id+"" : "");
		
		for (String genre : genres.split("\\|")) {
			
			genre = genre.trim();
			if (genre.isEmpty()) { continue; }
			
			root.addElement("genre").setText(genre);
		}
		
		saveDocument(doc, out);
	}
	
	
	public static void createEpisodeNFO(int[] info, String title, String fileName, File seasonDir) {
		
		if (!seasonDir.exists()) { return; }
		
		File nfo = new File(seasonDir.getPath()+"/"+fileName+".nfo");
		
		if (!nfo.exists()) {
			
			Document doc =  DocumentHelper.createDocument();
			Element root = doc.addElement("episodedetails");
			
			root.addElement("title").setText(title);
			root.addElement("season").setText(info[0]+"");
			root.addElement("episode").setText(info[1]+"");
			root.addElement("thumb").setText(cleanse(fileName)+".jpg");
			
			saveDocument(doc, nfo);
		}
		
	}
	
	public static void createEpisodeNFO(Element tvdbXML, File out, String thumb, boolean absNr) {
		
		int season = 1, episode = -1;
		String name = "", director = "", writer = "", aired = "", plot = "", language = "";
		double rating = 0.0;
		
		try {
			
			name = tvdbXML.elementTextTrim("EpisodeName");
			
			try { director = cleanse(tvdbXML.elementTextTrim("Director")); } catch (Exception e1) {}
			try { plot = cleanse(tvdbXML.elementTextTrim("Overview")); } catch (Exception e1) {}
			try { language = cleanse(tvdbXML.elementTextTrim("Language")); } catch (Exception e1) {}
			try { writer = cleanse(tvdbXML.elementTextTrim("Writer")); } catch (Exception e1) {}
			try { aired = tvdbXML.elementTextTrim("FirstAired"); } catch (Exception e1) {}
			
			season = absNr ? 1 : (int)Double.parseDouble(tvdbXML.elementTextTrim("SeasonNumber"));
			
			if (absNr) { episode = (int)Double.parseDouble(tvdbXML.elementTextTrim("absolute_number")); } 
			else { episode = (int)Double.parseDouble(tvdbXML.elementTextTrim("EpisodeNumber")); }
			
			try { rating = Double.parseDouble(tvdbXML.elementTextTrim("Rating")); } catch (Exception e1) {}
			
			
		} catch (Exception e) { e.printStackTrace(); return; }
		
		
		Document doc =  DocumentHelper.createDocument();
		Element root = doc.addElement("episodedetails");
		
		root.addElement("title").setText(name);
		root.addElement("rating").setText(rating+"");
		root.addElement("season").setText(season+"");
		root.addElement("episode").setText(episode+"");
		root.addElement("plot").setText(plot);
		root.addElement("premiered").setText(aired);
		root.addElement("aired").setText(aired);
		root.addElement("credits").setText(writer);
		root.addElement("director").setText(director);
		
		if (thumb != null && !thumb.isEmpty()) {
			
			root.addElement("thumb").setText(cleanse(thumb));
		}
		
		saveDocument(doc, out);
		
	}
	
	
	public static void checkPosters(File showdir) {
		
		File poster = new File(showdir.getAbsolutePath()+"/folder.jpg");
		if (!poster.exists()) { return; }
		
		for (File f : showdir.listFiles()) {
			
			String nm = f.getName().toLowerCase().trim();
			
			if (f.isDirectory() && (nm.startsWith("season") || nm.startsWith("staffel"))) {
				
				int season = -1;
				
				try {
					
					String nr = "";
					for (int i = 0; i < nm.length(); i++) {
						
						if (Character.isDigit(nm.charAt(i))) { nr += nm.charAt(i); }
						else if (!nr.isEmpty()) { break; }
					}
					
					while (nr.startsWith("0") && nr.length() > 1) { nr = nr.substring(1); }
					
					season = (int)Double.parseDouble(nr);
					
				} catch (Exception e) {}
				
				if (season >= 0) {
					
					String nrStr = season+"";
					for (int i = 0, l = nrStr.length(); i < 2 - l; i++) { nrStr = "0"+nrStr; }
					
					File p = new File(showdir.getAbsolutePath()+"/season"+nrStr+"-poster.jpg");
					
					if (!p.exists()) {
						
						Files.copy(poster, p);
					}
				}
			}
		}
		
		File p = new File(showdir.getAbsolutePath()+"/season-all-poster.jpg");
		if (!p.exists()) { Files.copy(poster, p); }
		
	}
	
	
	private static void saveDocument(Document doc, File out) {
		
		try {
			
			OutputFormat format = OutputFormat.createPrettyPrint();
			format.setEncoding("UTF-8");
			
			XMLWriter xmlWriter = new XMLWriter(new FileWriter(out), format);
			xmlWriter.write(doc);
			xmlWriter.flush();
			xmlWriter.close();
			
		} catch (Exception | Error e) { e.printStackTrace(); }
	}
	
	
	private static String cleanse(String s) {
		
		/*
		for (int i = s.indexOf('&'), b = 0; i > 0; i = s.indexOf('&', b)) {
			
			b = i+1;
			
			if (!s.regionMatches(true, i, "&amp;", 0, 5) && !s.regionMatches(true, i, "&quot;", 0, 6) && 
				!s.regionMatches(true, i, "&lt;", 0, 4) && !s.regionMatches(true, i, "&gt;", 0, 4) && 
				!s.regionMatches(true, i, "&apos;", 0, 6)) {
				
				s = s.substring(0, i) + "&amp;" + s.substring(i+1);
				
				b += 4;
			}
			
		}
		
		s = s.replace("\"", "&quot;");
		s = s.replace("<", "&lt;");
		s = s.replace(">", "&gt;");
		s = s.replace("'", "&apos;");
		*/
		
		return s;
	}
	
}
