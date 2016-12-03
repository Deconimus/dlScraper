package main;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import jdk.internal.org.objectweb.asm.Attribute;
import visionCore.util.Files;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.CopyOption;
import java.nio.file.StandardCopyOption;

import static java.nio.file.StandardCopyOption.*;

public class Main {
	
	private static final String API_KEY = "8C4835D37B726132";

	public static String abspath;
	
	private static String moviesDir;
	private static HashMap<String, Show> shows;
	private static HashMap<String, String> subNeed;
	
	public static HashMap<String, CustomScraper> customScrapers;
	
	private static List<String> seriesAdded, dlDirs;
	
	private static HashMap<String, List<Episode>> newEpisodes;
	
	private static List<FileMove> fileMoves;
	
	private static final int MAX_THREADS = Math.max(Runtime.getRuntime().availableProcessors()-1, 1);
	
	public static void main(String[] args) {
		
		setAbspath();
		
		customScrapers = new HashMap<String, CustomScraper>();
		
		File d = new File(abspath);
		File moduledir = null;
		
		for (File f : d.listFiles()) {
			
			if (f.isDirectory() && f.getName().toLowerCase().contains("scraper")) { moduledir = f; }
		}
		
		JythonFactory jf = JythonFactory.getInstance();
		
		if (moduledir != null && moduledir.exists()) {
			
			for (File f : moduledir.listFiles()) {
				String fn = f.getName().toLowerCase();
				
				if (fn.endsWith(".py")) {
					
					CustomScraper cs = (CustomScraper)jf.getJythonObject("main.CustomScraper", f.getAbsolutePath().replace("\\", "/"));
					
					if (cs != null) {
						
						customScrapers.put(cs.getAlias(), cs);
					}
				}
				
			}
		}
		
		Gronkh.load();
		GigaTop100.load();
		
		System.out.println("\ndlScraper by Deconimus\n");
		
		newEpisodes = new HashMap<String, List<Episode>>();
		seriesAdded = new ArrayList<String>();
		dlDirs = new ArrayList<String>();
		fileMoves = new ArrayList<FileMove>();
		subNeed = new HashMap<String, String>();
		
		System.out.println("Reading settings.xml");
		
		List<Element> elems = readSettingsXML();
		
		shows = new HashMap<String, Show>();
		
		for (Element elem : elems) {
			
			if (elem.getName().equalsIgnoreCase("seriesFolder")) {
				
				String seriesDir = elem.attributeValue("folder");
				if (!seriesDir.endsWith("/")) { seriesDir += "/"; }
				
				for (Iterator<Element> i = elem.elements().iterator(); i.hasNext();) {
					Element e = i.next();
					
					if (e.getName().equalsIgnoreCase("alias")) {
						
						// way too much code for this but whatever, it's robust
						
						boolean abs = false;
						
						for (Iterator<org.dom4j.Attribute> it = e.attributeIterator(); it.hasNext();) {
							org.dom4j.Attribute a = it.next();
							
							if (a.getName().trim().toLowerCase().contains("abs")) {
								
								String s = a.getValue();
								
								abs = !(s.trim().toLowerCase().equals("false") || s.trim().startsWith("0"));
							}
						}
						
						shows.put(e.attributeValue("aliasName").toLowerCase(), new Show(e.attributeValue("seriesName"), seriesDir, abs));
					}
					
				}
				
			} else if (elem.getName().equalsIgnoreCase("dlFolder")) {
				
				String dlDir = elem.attributeValue("folder");
				if (!dlDir.endsWith("/")) { dlDir += "/"; }
				
				dlDirs.add(dlDir);
				
			} else if (elem.getName().equalsIgnoreCase("moviesFolder")) {
				
				moviesDir = elem.attributeValue("folder");
			}
			
		}
		
		System.out.println("Searching for new episodes\n");
		
		for (String dlDir : dlDirs) {
			
			File dlDirFile = new File(dlDir);
			File[] files = dlDirFile.listFiles();
			
			if (files == null || files.length <= 0) { continue; }
			
			for (File file : files) {
			
				if (file.isDirectory()) {
					
					String curAlias = null;
					String str = file.getName().split(" ")[0].toLowerCase();
					
					if (shows.containsKey(file.getName().split(" ")[0].toLowerCase())) {
						
						curAlias = str;
						
						CustomScraper customScraper = getCustomScraper(curAlias);
						
						List<File> dirFiles = Files.getFilesRecursive(file.getPath(), f -> isVideoFile(f));
						List<File> dirFilesPics = Files.getFilesRecursive(file.getPath(), f -> isPicture(f));
						
						int episodesOfSeries = 0;
						int season = 0;
						
						List<String> seasonDirs = new ArrayList<String>();
						
						String folderNameLower = file.getName().toLowerCase();
						
						for (File subFile : dirFiles) {
							
							File parentFolder = null;
							
							if (!file.equals(subFile.getParentFile())) { parentFolder = subFile.getParentFile(); }
							
							int[] info = getEpisodeInfo(subFile.getName(), file, parentFolder, curAlias);
							
							if (info == null) { continue; }
							
							season = info[0];
							int episode = info[1];
							
							File showDir = getShowDir(shows.get(curAlias));
							if (showDir == null) { 
								
								showDir = new File(shows.get(curAlias).seriesPath + shows.get(curAlias).name);
								showDir.mkdirs();
							}
							
							File seasonDir = getSeasonDir(showDir, season);
							if (seasonDir == null) {
								
								seasonDir = new File(showDir.getAbsolutePath()+"/Season "+season);
								seasonDir.mkdirs();
							}
							
							if (customScraper != null) {
								
								customScraper.createShowNFO(showDir, shows.get(curAlias).name);
								
								//GoogleImages.fetchFanart(shows.get(curAlias).name, showDir);
								//GoogleImages.fetchPoster(shows.get(curAlias).name, showDir);
							}
							
							if (!seasonDirs.contains(seasonDir.getAbsolutePath())) {
								
								seasonDirs.add(seasonDir.getAbsolutePath());
							}
							
							String fileName = "S";
							if (season < 10) { fileName += "0"; }
							fileName += ""+season+"E";
							if (episode < 10) { fileName += "0"; }
							if (episode < 100 && shows.get(curAlias).absNr) { fileName += "0"; }
							fileName += ""+episode;
							
							String ext = subFile.getName().substring(subFile.getName().length()-4);
							
							String title = "";
							
							if (customScraper != null) {
								
								title = customScraper.getEpisodeTitle(subFile.getName());
								title = cleanseFileName(title);
							}
							
							if (title.length() > 0) {
								
								fileName += " - " + title;
								fileName = cleanseFileName(fileName);
								
								for (File f : dirFilesPics) {
									
									if (f.getName().toLowerCase().contains(title.toLowerCase().trim())) {
										
										fileMoves.add(new FileMove(f, new File(seasonDir.getPath()+"/"+fileName+".tbn")));
										
										break;
									}
									
								}
								
							}
							
							File episodeFile = new File(seasonDir.getPath()+"/"+fileName+ext);
							
							if (customScraper != null) {
								
								customScraper.createEpisodeNFO(info, title, fileName, seasonDir);
							}
							
							if (episodeFile.exists()) {
								
								if (Files.getSize(episodeFile) > Files.getSize(subFile)) {
									
									continue;
									
								} else {
									
									episodeFile.delete();
									
								}
								
							}
							
							fileMoves.add(new FileMove(subFile, episodeFile));
							
							String curShow = shows.get(curAlias).name;
							
							Episode newEp = new Episode(episodeFile.getAbsolutePath(), curShow, season, episode);
							
							if (newEpisodes.containsKey(curAlias)) {
								
								newEpisodes.get(curAlias).add(newEp);
								
							} else {
								
								List<Episode> episodes = new ArrayList<Episode>();
								episodes.add(newEp);
								
								newEpisodes.put(curAlias, episodes);
								
							}
							
							episodesOfSeries++;
								
						}
						
						if (episodesOfSeries > 0) {
							
							seriesAdded.add(episodesOfSeries+" of "+shows.get(curAlias).name+" in season "+season);
							
							if (folderNameLower.contains("lang=")) {
								
								String lang = "";
								
								char[] langChars = folderNameLower.substring(folderNameLower.indexOf("lang=")+5).toCharArray();
								
								for (int i = 0; i < langChars.length; i++) {
									
									if (Character.isAlphabetic(langChars[i])) {
										lang += langChars[i];
									} else { break; }
									
								}
								
								lang = lang.toLowerCase();
								if (lang.equals("en")) { lang = "eng"; }
								
								subNeed.put(seasonDirs.get(seasonDirs.size()-1), lang);
								
							}
							
						}
						
						//file.delete();
						
					} else if (file.getName().toLowerCase().startsWith("movie=") && moviesDir != null) {
						
						List<File> dirFiles = Files.getFilesRecursive(file.getPath(), f -> isVideoFile(f));
						
						String movieName = file.getName().substring("movie=".length());
						
						File movie = null;
						int movieSize = -1;
						
						// Filter samplers etc.
						for (File movieFile : dirFiles) {
							
							int movieFileSize = -1;
							
							try {
								FileInputStream fis = new FileInputStream(movieFile);
								movieFileSize = fis.available();
								movieFileSize /= 1024;
								fis.close();
							} catch (Exception e) { e.printStackTrace(); }
							
							if (movieFileSize > movieSize) {
								
								movie = movieFile;
								movieSize = movieFileSize;
								
							}
								
						}
						
						if (movie != null) {
							
							String extension = movie.getName().substring(movie.getName().length()-4);
							
							System.out.println("Moved "+movieName);
							
							fileMoves.add(new FileMove(movie, new File(moviesDir+"/"+movieName+extension)));
							
						}
						
					}
					
				}
				
			}
			
		}

		System.out.println("");
		
		int numNewEpisodes = 0;
		for (String k : newEpisodes.keySet()) {
			
			numNewEpisodes += newEpisodes.get(k).size();
		}
		
		if (numNewEpisodes == 0) {
			System.out.println("No new episodes found.\n");
		} else if (numNewEpisodes == 1) {
			System.out.println("1 new episode found.");
		} else {
			System.out.println(numNewEpisodes+" new episodes found.");
		}
		
		for (String str : seriesAdded) {
			System.out.println(str);
		}
		
		if (!fileMoves.isEmpty()) {
			
			System.out.println("\nMoving files");
			
			for (FileMove move : fileMoves) {
				
				if (move.dst.exists()) {
					
					if (Files.getSize(move.dst) > Files.getSize(move.src)) {
						
						continue;
						
					} else {
						
						move.dst.delete();
					}
					
				}
				
				Files.moveFileUsingOS(move.src, move.dst);
			}
			
		}
		
		if (seriesAdded.size() > 0) {
			
			System.out.println("Renaming episodes for database");
		}
		
		for (String showAlias : newEpisodes.keySet()) {
			
			String show = shows.get(showAlias).name;
			
			String showUrlName = show.replace(' ', '+');
			
			SAXReader r = new SAXReader();
			Document document = null;
			try { document = r.read(new URL("http://thetvdb.com/api/GetSeries.php?seriesname="+showUrlName)); }
			catch (Exception e) { continue; }
			
			String id = "";
			try { id = document.getRootElement().element("Series").element("seriesid").getText(); }
			catch (Exception | Error e) { continue; }
			
			String seriesUrl = "http://thetvdb.com/api/"+API_KEY+"/series/"+id+"/all/en.xml";
			
			try { document = r.read(new URL(seriesUrl)); }
			catch (Exception | Error e) { continue; }
			
			Element rootElem = document.getRootElement();
			
			for (Episode episode : newEpisodes.get(showAlias)) {
				
				String newName = "";
				
				for (Iterator<Element> i = rootElem.elements().iterator(); i.hasNext();) {
					Element elem = i.next();
					
					if (elem.getName().equalsIgnoreCase("episode")) {
						
						int s = Integer.parseInt(elem.element("SeasonNumber").getText());
						int e = Integer.parseInt(elem.element("EpisodeNumber").getText());
						
						int absE = -1;
						try { absE = (int)Double.parseDouble(elem.element("absolute_number").getText().trim()); } catch (Exception | Error ex) { }
						
						if ((shows.get(showAlias).absNr && absE == episode.episode) || (s == episode.season && e == episode.episode)) {
							
							newName = elem.element("EpisodeName").getText();
							
							break;
						}
						
					}
					
				}
				
				String extension = episode.dir.substring(episode.dir.lastIndexOf('.'));
				
				File newFile = new File(episode.dir.substring(0, episode.dir.lastIndexOf('.'))+" - "+cleanseFileName(newName)+""+extension);
				File epFile = new File(episode.dir);
				
				epFile.renameTo(newFile);
				
			}
			
		}
		
		int subs = 0;
		if (subNeed.size() > 0) { subs = 1; }
		
		if (subs == 1) {
			
			System.out.println("Pulling subtitles");
			
		}
		
		ExecutorService exec = Executors.newFixedThreadPool(MAX_THREADS);
		try {
			
			for (final String dir : subNeed.keySet()) {
				
				String lang = subNeed.get(dir);
				
				File seasonDir = new File(dir);
				List<File> sfiles = Files.getFilesRecursive(seasonDir.getAbsolutePath());
				
				for (final File file : sfiles) {
					
					if (isVideoFile(file)) {
						
						String name = file.getName().toLowerCase();
						name = name.substring(0, name.length()-4);
						
						boolean sub = false;
						
						for (File sfile : sfiles) {
							
							String sname = sfile.getName().toLowerCase();
							
							if (sfile != file && sname.contains(name) && sname.endsWith(".srt")) {
								
								sub = true;
								break;
							}
						}
						
						if (!sub) {
							
							exec.submit(new Callable<Void>(){
								@Override
								public Void call() throws Exception {
									
									Runtime runtime = Runtime.getRuntime();
									try {
										Process process = runtime.exec("filebot -get-subtitles \""+file.getAbsolutePath()+"\" --lang "+lang);
										try { process.waitFor(); } catch (Exception e) { e.printStackTrace(); }
									} catch (IOException e) { e.printStackTrace(); }
									
									return null;
								}
							});
							
						}
						
					}
					
				}
				
			}
			
		} finally {
			
			exec.shutdown();
			
		}
		
		try {
			exec.awaitTermination(15L, TimeUnit.MINUTES);
		} catch (Exception e1) { e1.printStackTrace(); }
		
		System.out.println("Done;\ntips_fedora();");
		
		try { Thread.sleep(1500); } catch (Exception e) { e.printStackTrace(); }
		
	}
	
	private static List<Element> readSettingsXML() {
		
		String path = Main.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		path = path.replace("%20", " ");
		path = path.substring(1, path.lastIndexOf('/'));
		
		File file = new File(path+"/dlScraper_settings.xml");
		
		if (!file.exists()) {
			
			File alt = new File("settings.xml");
			
			if (alt.exists()) { 
				
				alt.renameTo(file);
				
			} else {
				
				Document doc =  DocumentHelper.createDocument();
				
				Element root = doc.addElement("root");
				
				root.addElement("moviesFolder").addAttribute("folder", "path to your moviefolder");
				root.addElement("dlFolder").addAttribute("folder", "path to your downloadfolder");
				
				Element alias = root.addElement("seriesFolder").addAttribute("folder", "path to your seriesfolder").addElement("alias");
				alias.addAttribute("seriesName", "Game of Thrones");
				alias.addAttribute("aliasName", "got");
				
				return new ArrayList<Element>();
			}
			
		}
		
		SAXReader reader = new SAXReader();
		Document doc = null;
		try {
			doc = reader.read(file);
		} catch (DocumentException e) { e.printStackTrace(); }
		
		Element root = doc.getRootElement();
		ArrayList<Element> elems = new ArrayList<Element>();
		
		for (Iterator<Element> i = root.elements().iterator(); i.hasNext();) {
			Element elem = i.next();
			
			elems.add(elem);
		}
		
		return elems;
	}
	
	private static File getShowDir(Show show) {
		
		File dir = new File(show.seriesPath);
		File[] files = dir.listFiles();
		
		for (File file : files) {
			
			if (file.isDirectory()) {
				
				if (file.getName().equalsIgnoreCase(show.name)) {
					
					return file;
				}
			}
		}
		
		return null;
	}
	
	private static File getSeasonDir(File showDir, int season) {
		
		File[] files = showDir.listFiles();
		
		if (files == null) { System.out.println("Dir not found."); return null; }
		
		for (File file : files) {
			
			if (file.isDirectory()) {
				
				if (file.getName().toLowerCase().contains("season") || 
					file.getName().toLowerCase().contains("staffel")) {
					
					if (file.getName().endsWith(" "+season) || file.getName().endsWith(" 0"+season)) {
						
						return file;
					}
				}
			}
		}
		
		return null;
	}
	
	private static int[] getEpisodeInfo(String name, File folder, File parentFolder, String alias) {
		
		return getEpisodeInfo(name, folder, parentFolder, alias, true);
	}
	
	private static int[] getEpisodeInfo(String name, File folder, File parentFolder, String alias, boolean first) {
		
		CustomScraper cScraper = getCustomScraper(alias);
		
		if (cScraper != null) {
			
			return cScraper.getEpisodeInfo(name);
		}
		
		String nameLC[] = null;
		
		if (parentFolder != null) {
			
			nameLC = new String[]{ name.toLowerCase(), parentFolder.getName().toLowerCase() };
			
		} else {
			
			nameLC = new String[]{ name.toLowerCase() };
		}
			
		
		char[] folderChars = folder.getName().toLowerCase().toCharArray();
		
		for (int i = 0; i < nameLC.length; i++) {
		
			nameLC[i] = nameLC[i].replace("x264", "");
			nameLC[i] = nameLC[i].replace("h.264", "");
			nameLC[i] = nameLC[i].replace("h264", "");
			
			nameLC[i] = nameLC[i].replace("x265", "");
			nameLC[i] = nameLC[i].replace("h.265", "");
			nameLC[i] = nameLC[i].replace("h265", "");
			
			nameLC[i] = nameLC[i].replace("m4a", "");
			nameLC[i] = nameLC[i].replace("ac3", "");
			nameLC[i] = nameLC[i].replace("mp3", "");
			
			nameLC[i] = nameLC[i].replace("2160p", "");
			nameLC[i] = nameLC[i].replace("1440p", "");
			nameLC[i] = nameLC[i].replace("1080p", "");
			nameLC[i] = nameLC[i].replace("720p", "");
			nameLC[i] = nameLC[i].replace("480p", "");
			nameLC[i] = nameLC[i].replace("360p", "");
			nameLC[i] = nameLC[i].replace("240p", "");
			nameLC[i] = nameLC[i].replace("144p", "");
			
			nameLC[i] = nameLC[i].replace("..", "");
		}
		
		char[][] chars = new char[nameLC.length][];
		for (int n = 0; n < chars.length; n++) {
			
			chars[n] = nameLC[n].toCharArray();
		}
		
		String showLC = shows.get(alias).name.toLowerCase();
		
		for (int n = 0; n < nameLC.length; n++) {
		
			for (int i = 0; i < chars[n].length-4; i++) {
				
				if (chars[n][i] == 's') {
					
					if (Character.isDigit(chars[n][i+1]) && Character.isDigit(chars[n][i+2])) {
						
						if ((chars[n][i+3] == 'e') && Character.isDigit(chars[n][i+4])) {
							
							String int2parse = chars[n][i+4]+"";
							
							for (int j = i+5; j < chars[n].length && Character.isDigit(chars[n][j]); j++) {
								
								int2parse += chars[n][j];
							}
							
							return new int[]{Integer.parseInt(chars[n][i+1]+""+chars[n][i+2]), Integer.parseInt(int2parse)};
							
						} else if ((chars[n][i+2] == 'e') && Character.isDigit(chars[n][i+3])) {
							
							String int2parse = chars[n][i+3]+"";
							
							for (int j = i+4; j < chars[n].length && Character.isDigit(chars[n][j]); j++) {
								
								int2parse += chars[n][j];
							}
							
							return new int[]{Integer.parseInt(chars[n][i+1]+""+chars[n][i+2]), Integer.parseInt(int2parse)};
							
						}
						
					}
				}
			}
		}
		
		int season = 1;
		
		for (int i = 0; i < folderChars.length-2; i++) {
			
			if (folderChars[i] == 's' && Character.isDigit(folderChars[i+1]) && Character.isDigit(folderChars[i+2])) {
				
				season = Integer.parseInt(folderChars[i+1]+""+folderChars[i+2]);
				break;
				
			}
			
		}
		
		//And here the nerve wrecking anime scraping begins..
		
		for (int n = 0; n < nameLC.length; n++) {
		
			for (int i = 0; i < chars[n].length-3; i++) {
				
				if (chars[n][i] == 'e' && Character.isDigit(chars[n][i+1]) && Character.isDigit(chars[n][i+2])) {
					
					String int2parse = chars[n][i+1]+""+chars[n][i+2];
					
					for (int j = i+3; j < chars[n].length-3 && Character.isDigit(chars[n][j]); j++) {
						int2parse += ""+chars[n][j];
					}
					
					return new int[]{season, Integer.parseInt(int2parse)};
					
				}
				
			}
			
		}
		
		for (int n = 0; n < nameLC.length; n++) {
		
			for (int i = 0; i < chars[n].length-3; i++) {
				
				if ((chars[n][i] == 'e' && chars[n][i+1] == 'p') && (Character.isDigit(chars[n][i+2]) || 
					(chars[n][i+2] == '.' && ((chars[n][i+3] == ' ' && Character.isDigit(chars[n][i+4])) || Character.isDigit(chars[n][i+3]))))) {
	
					String int2parse = "";
					
					boolean numbers = false;
					
					for (int j = i+2; j < chars[n].length; j++) {
						
						if (Character.isDigit(chars[n][j])) {
							
							int2parse += chars[n][j]+"";
							if (!numbers) { numbers = true; }
								
						} else if (numbers) {
							
							break;
							
						}
						
					}
					
					return new int[]{season, Integer.parseInt(int2parse)};
					
				}
				
			}
			
		}
		
		for (int n = 0; n < nameLC.length; n++) {
		
			if (nameLC[n].contains("folge") || nameLC[n].contains("episode")) {
				
				String kw = "episode";
				if (nameLC[n].contains("folge")) { kw = "folge"; }
				
				String int2parse = "";
				
				for (int i = nameLC[n].indexOf(kw) + kw.length(); i < chars[n].length; i++) {
					
					if (Character.isDigit(chars[n][i])) {
						int2parse += chars[n][i];
					} else if (int2parse.length() > 0) {
						break;
					}
					
				}
				
				if (int2parse.length() > 0) {
					return new int[]{season, Integer.parseInt(int2parse)};
				} else { return null; }
				
			}
			
		}
		
		// All hail the Regex Generator: http://regex.inginf.units.it
		
		for (int n = 0; n < nameLC.length; n++) {
		
			Pattern pattern = Pattern.compile("\\d++(?= )|12");
			Matcher matcher = pattern.matcher(nameLC[n]);
			if (matcher.find()) {
			    
				return new int[]{season, Integer.parseInt(matcher.group(0))};
				
			}
			
		}

		for (int n = 0; n < nameLC.length; n++) {
		
			if (nameLC[n].contains(alias.toLowerCase()) || nameLC[n].contains(showLC)) {
				
				char[] nc = null;
				
				if (nameLC[n].contains(alias.toLowerCase())) {
					
					String s = nameLC[n].substring(nameLC[n].indexOf(alias.toLowerCase())+alias.length()-1);
					nc = s.toCharArray();
					
				} else {
					
					String s = nameLC[n].substring(nameLC[n].indexOf(showLC)+showLC.length()-1);
					nc = s.toCharArray();
					
				}
				
				for (int i = 0; i < nc.length; i++) {
					
					String int2parse = "";
					
					if (!Character.isDigit(nc[i])) { continue; }
					
					int2parse += nc[i];
					
					for (int j = i+1; j < nc.length; j++) {
						
						if (Character.isDigit(nc[j])) {
							int2parse += nc[j];
						} else if (int2parse.length() > 0) {
							break;
						}
						
					}
					
					int ep = -1;
					
					try {
						
						ep = Integer.parseInt(int2parse);
						
					} catch (NumberFormatException e) {  }
					
					if (ep != -1) {
						
						return new int[]{season, ep};
						
					}
					
				}
				
			}
			
		}
		
		for (int n = 0; n < nameLC.length; n++) {
		
			for (int i = 0; i < chars[n].length-1; i++) {
				
				if (Character.isDigit(chars[n][i]) && (i == 0 || !Character.isDigit(chars[n][i-1])) && Integer.parseInt(chars[n][i]+"") == season) {
					
					String int2parse = "";
					
					for (int j = i+1; j < chars[n].length; j++) {
						
						if (Character.isDigit(chars[n][j])) {
							int2parse += chars[n][j];
						} else if (int2parse.length() > 0) {
							break;
						}
					}
					
					int ep = -1;
					
					try {
						
						ep = Integer.parseInt(int2parse);
						
					} catch (NumberFormatException e) {  }
					
					if (ep != -1) {
						
						return new int[]{season, ep};
						
					}
					
				}
				
			}
			
			if (Character.isDigit(chars[n][0])) {
				
				String int2parse = ""+chars[n][0];
				
				for (int i = 1; i < chars[n].length && Character.isDigit(chars[n][i]); i++) {
					
					int2parse += chars[n][i];
				}
				
				return new int[]{season, Integer.parseInt(int2parse)};
			}
			
		}
		
		for (int n = 0; n < nameLC.length; n++) {
			
			int nums = 0;
			
			for (int i = 0; i < nameLC[n].length(); i++) {
				
				if (Character.isDigit(nameLC[n].charAt(i)) && (i == 0 || !Character.isDigit(nameLC[n].charAt(i-1)))) {
					
					nums++;
				}
			}
			
			if (nums == 1) {
				
				String int2parse = "";
				
				for (int i = 0; i < nameLC[n].length(); i++) {
					
					if (Character.isDigit(nameLC[n].charAt(i))) {
						
						int2parse += nameLC[n].charAt(i);
						
					} else if (int2parse.length() > 0) {
						
						break;
					}
				}
				
				try { 
					
					int ep = Integer.parseInt(int2parse);
					
					return new int[]{ season, ep };
					
				} catch (Exception e) {}
				
			}
		}
			
		return null;
	}
	
	public static boolean isVideoFile(File file) {
		
		String name = file.getName().toLowerCase();
		
		return name.endsWith(".mkv") || name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".wmv") ||
				name.endsWith(".3gp") || name.endsWith(".mov");
	}
	
	public static boolean isPicture(File file) {
		
		String name = file.getName().toLowerCase().trim();
		
		return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
	}
	
	public static class Episode {
		
		public String dir, show;
		public int season, episode;
		
		public Episode(String dir, String show, int season, int episode) {
			
			this.dir = dir;
			this.show = show;
			
			this.season = season;
			this.episode = episode;
			
		}
		
	}
	
	public static class Show {
		
		public String name, seriesPath;
		
		/** absolute episode numbering */
		public boolean absNr;
		
		public Show(String name, String seriesPath, boolean absNr) {
			
			this.name = name;
			this.seriesPath = seriesPath;
			this.absNr = absNr;
		}
		
	}
	
	public static class FileMove {
		
		public File src, dst;
		
		public FileMove(File src, File dst) {
			
			this.src = src;
			this.dst = dst;
			
		}
		
	}
	
	public static CustomScraper getCustomScraper(String alias) {
		
		if (alias.contains("_")) {
			
			String prefix = alias.toLowerCase().substring(0, alias.indexOf('_'));
			
			if (customScrapers.containsKey(prefix)) {
				
				return customScrapers.get(prefix);
			}
			
		}
		
		return null;
	}
	
	public static String cleanseFileName(String fileName) {
		
		fileName = fileName.replace(":", " -");
		fileName = fileName.replace("?", "");
		fileName = fileName.replace("\\", "");
		fileName = fileName.replace("/", " ");
		fileName = fileName.replace("*", "");
		fileName = fileName.replace("|", "-");
		fileName = fileName.replace("<", "[");
		fileName = fileName.replace(">", "]");
		
		return fileName;
	}
	
	private static void setAbspath() {
		
		try {
			
			abspath = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath().replace("\\", "/");
			
			if (abspath.endsWith("/bin")) {
				
				abspath = abspath.substring(0, abspath.indexOf("/bin"));
			}
			
			if (abspath.endsWith(".jar")) {
				
				abspath = new File(abspath).getParentFile().getAbsolutePath();
			}
			
		} catch (Exception e) { e.printStackTrace(); }
	}
	
}
