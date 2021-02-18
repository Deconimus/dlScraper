package main;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import visionCore.dataStructures.tuples.Triplet;
import visionCore.util.Files;
import visionCore.util.Web;

public class Main {
	
	public static final String API_KEY = "8C4835D37B726132";
	
	
	public static final int MODE_DEFAULT = 0, MODE_FIX = 1, MODE_REFRESH = 2;
	public static int mode = MODE_DEFAULT;
	public static String modeArg = null;
	
	
	public static String abspath;
	
	private static String moviesDir;
	private static HashMap<String, Show> shows;
	private static HashMap<String, String> subNeed;
	
	private static String curShowsFolder = "", curAnimeFolder = "";
	
	public static HashMap<String, CustomScraper> customScrapers;
	public static HashMap<String, String> loadableCustomScrapers;
	
	private static List<String> dlDirs;
	
	private static HashMap<String, List<Episode>> newEpisodes;
	
	private static List<FileMove> fileMoves;
	
	private static final int MAX_THREADS = Math.max(Runtime.getRuntime().availableProcessors()-1, 1);
	
	
	public static String default_lang = "en";
	public static boolean default_local = false;
	
	
	public static void main(String[] args) {
		
		setAbspath();
		parseArgs(args);
		
		System.out.println("\ndlScraper by Deconimus\n");
		
		newEpisodes = new HashMap<String, List<Episode>>();
		dlDirs = new ArrayList<String>();
		fileMoves = new ArrayList<FileMove>();
		subNeed = new HashMap<String, String>();
		
		System.out.println("Reading settings.xml");
		
		shows = new HashMap<String, Show>();
		readSettingsXML();
		
		if (shows.isEmpty()) {
			
			System.out.println("\nNo shows specified or no settings-file found.");
			return;
		}
		
		
		if (mode == MODE_DEFAULT) {
			
			
			System.out.println("Loading custom scrapers");
			
			customScrapers = new HashMap<String, CustomScraper>();
			loadableCustomScrapers = new HashMap<String, String>();
			loadCustomScrapers();
		
			
			System.out.println("Searching for new episodes\n");
			findNewEpisodes();
			
	
			System.out.println("");
			printNewEpisodesInfo();
			
			
			if (!fileMoves.isEmpty()) {
				
				System.out.println("Moving files");
				
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
			
			
			if (!newEpisodes.isEmpty()) { System.out.println("Renaming episodes for database"); }
			renameEpisodes();
			
			
			System.out.println("Done;\ntips_fedora();");
			
			
		} else if (mode == MODE_FIX || mode == MODE_REFRESH) {
			
			System.out.println();
			
			List<Show> sh = new ArrayList<Show>();
			
			if (modeArg == null) {
				
				if (mode == MODE_FIX) { System.out.println("Fixing metadata in library."); }
				else { System.out.println("Refreshing metadata in library."); }
				
				sh.addAll(shows.values());
				
			} else {
				
				Show show = shows.get(modeArg.toLowerCase().trim());
				if (show != null) { sh.add(show); }
				
				if (sh.isEmpty()) {
					
					for (Show s : shows.values()) {
						
						if (s.name.toLowerCase().equals(modeArg)) { sh.add(s); break; }
					}
				}
			}
			
			if (sh.isEmpty()) {
				
				System.out.println("Show \""+modeArg+"\" not found.");
				return;
			}
			
			if (sh.size() == 1) {
			
				if (mode == MODE_FIX) { System.out.println("Fixing metadata for \""+sh.get(0).name+"\""); }
				else { System.out.println("Refreshing metadata for \""+sh.get(0).name+"\""); }
			}
			
			
			for (Show show : sh) {
				
				File showDir = new File(show.seriesPath.replace("\\", "/")+show.name);
				if (showDir == null || !showDir.exists()) { continue; }
				
				show.load();
				
				if (!show.local) { continue; }
				
				File tmpfile = new File(showDir.getAbsolutePath()+"/tmp.zip");
				Web.downloadFile(show.url, tmpfile);
				
				ZipEntry infoEntry = null;
				ZipFile zipFile = null;
				
				try {
					
					zipFile = new ZipFile(tmpfile);
					
					infoEntry = zipFile.getEntry(show.lang+".xml");
					if (infoEntry == null) { infoEntry = zipFile.getEntry("en.xml"); }
					
				} catch (Exception e) { }
				
				if (infoEntry == null) {
					
					if (zipFile != null) { try { zipFile.close(); } catch (Exception e) {} }
					tmpfile.delete();
					continue;
				}
				
				SAXReader r = new SAXReader();
				Document document = null;
				try { document = r.read(zipFile.getInputStream(infoEntry)); }
				catch (Exception | Error e) { tmpfile.delete(); continue; }
				
				Element rootElem = document.getRootElement();
				Element showElem = rootElem.elements().stream().filter(e -> e.getName().equalsIgnoreCase("series")).findAny().get();
				
				show.updateMedia(showElem, zipFile);
				
				List<Triplet<Integer, Integer, File>> eps = new ArrayList<Triplet<Integer, Integer, File>>();
				
				for (File d : showDir.listFiles()) {
					
					String nm = d.getName().toLowerCase();
					if (!d.isDirectory() || (!nm.startsWith("season") && !nm.startsWith("staffel"))) { continue; }
					
					for (File f : d.listFiles()) {
						if (!isVideoFile(f)) { continue; }
						
						String n = f.getName().substring(0, f.getName().lastIndexOf('.'));
						String p = d.getAbsolutePath().replace('\\', '/')+"/"+n;
						
						if (mode == MODE_REFRESH || (!(new File(p+".nfo").exists()) || (!(new File(p+".jpg").exists()) && !(new File(p+".tbn").exists())))) {
							
							n = n.toLowerCase();
							
							int episode = Integer.parseInt(n.substring(n.indexOf('e')+1, n.indexOf('-')).trim());
							int season = Integer.parseInt(n.substring(n.indexOf('s')+1, n.indexOf('e')));
							
							eps.add(new Triplet<Integer, Integer, File>(season, episode, f));
						}
					}
				}
				
				//elements = elements.stream().filter(e -> e.getName().equalsIgnoreCase("episode")).collect(Collectors.toCollection(ArrayList<Element>::new));
				
				for (Triplet<Integer, Integer, File> ep : eps) {
				
					Element elem = getEpisodeElem(rootElem, ep.x, ep.y, show.absNr);
					if (elem == null) { continue; }
					
					show.saveEpisodeMetadata(ep.z, elem);
				}
				
				tmpfile.delete();
			}
			
			
		}
		
	}
	
	
	private static void loadCustomScrapers() {
		
		File d = new File(abspath);
		File moduledir = null;
		
		for (File f : d.listFiles()) {
			
			if (f.isDirectory() && f.getName().toLowerCase().contains("scraper")) { moduledir = f; }
		}
		
		if (moduledir != null && moduledir.exists()) {
			
			for (File f : moduledir.listFiles()) {
				String fn = f.getName().toLowerCase();
				if (!fn.endsWith(".py")) { continue; }
				
				loadableCustomScrapers.put(fn.substring(0, fn.length()-3), f.getAbsolutePath().replace("\\", "/"));
			}
		}
		
		Gronkh.load();
		GigaTop100.load();
	}
	
	
	private static void readSettingsXML() {
		
		String path = Main.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		path = path.replace("%20", " ");
		path = path.substring(1, path.lastIndexOf('/'));
		
		File file = new File(path+"/dlScraper_settings.xml");
		
		if (!file.exists()) {
			
			File alt = new File("settings.xml");
			
			if (alt.exists()) { 
				
				alt.renameTo(file);
				
			} else { return; }
		}
		
		SAXReader reader = new SAXReader();
		Document doc = null;
		try {
			doc = reader.read(file);
		} catch (DocumentException e) { e.printStackTrace(); }
		
		Element root = doc.getRootElement();
		
		
		for (Element elem : root.elements()) {
			
			if (elem.getName().equalsIgnoreCase("lang")) {
				
				String lang = elem.getText().trim().toLowerCase();
				if (!lang.isEmpty()) { default_lang = lang; }
				
			} else if (elem.getName().equalsIgnoreCase("local")) {
				
				try { default_local = parseBoolean(elem.getText()); } catch (Exception e) {}
			}
		}
		
		for (Element elem : root.elements()) {
			
			if (elem.getName().equalsIgnoreCase("seriesFolder")) {
				
				String seriesDir = elem.attributeValue("folder");
				if (!seriesDir.endsWith("/")) { seriesDir += "/"; }
				
				for (Iterator<Element> i = elem.elements().iterator(); i.hasNext();) {
					Element e = i.next();
					
					if (e.getName().equalsIgnoreCase("alias")) {
						
						boolean abs = false;
						int id = -1;
						String lang = default_lang;
						boolean local = default_local;
						
						for (Iterator<org.dom4j.Attribute> it = e.attributeIterator(); it.hasNext();) {
							org.dom4j.Attribute a = it.next();
							
							String nm = a.getName().trim().toLowerCase();
							
							if (nm.contains("abs")) {
								
								try { abs = parseBoolean(a.getValue()); } catch (Exception ex) {}
								
							} else if (nm.contains("local")) {
								
								try { local = parseBoolean(a.getValue()); } catch (Exception ex) {}
								
							} else if (nm.contains("lang")) {
								
								String v = a.getValue();
								lang = (v != null && !v.trim().isEmpty()) ? v : lang;
								
							} else if (nm.contains("id")) {
								
								try { id = parseId(a.getValue()); } catch (Exception ex) {}
							}
						}
						
						shows.put(e.attributeValue("aliasName").toLowerCase(), new Show(e.attributeValue("seriesName"), seriesDir, abs, id, lang, local));
					}
					
				}
				
			} else if (elem.getName().equalsIgnoreCase("dlFolder")) {
				
				String dlDir = elem.attributeValue("folder");
				if (!dlDir.endsWith("/")) { dlDir += "/"; }
				
				dlDirs.add(dlDir);
				
			} else if (elem.getName().equalsIgnoreCase("moviesFolder")) {
				
				moviesDir = elem.attributeValue("folder");
				moviesDir = moviesDir.replace("\\", "/");
				if (!moviesDir.endsWith("/")) { moviesDir += "/"; }
				
			} else if (elem.getName().equalsIgnoreCase("curSeriesFolder") || elem.getName().equalsIgnoreCase("curShowsFolder") ) {
				
				curShowsFolder = elem.attributeValue("folder");
				curShowsFolder = curShowsFolder.replace("\\", "/");
				if (!curShowsFolder.endsWith("/")) { curShowsFolder += "/"; }
				
			} else if (elem.getName().equalsIgnoreCase("curAnimeFolder")) {
				
				curAnimeFolder = elem.attributeValue("folder");
				curAnimeFolder = curAnimeFolder.replace("\\", "/");
				if (!curAnimeFolder.endsWith("/")) { curAnimeFolder += "/"; }
			}
		}
		
	}
	
	
	private static void findNewEpisodes() {
		
		for (String dlDir : dlDirs) {
			
			File dlDirFile = new File(dlDir);
			File[] files = dlDirFile.listFiles();
			
			if (files == null || files.length <= 0) { continue; }
			
			for (File file : files) {
			
				if (file.isDirectory()) {
					
					String curAlias = null;
					String str = file.getName().split(" ")[0].toLowerCase();
					
					if ((!curAnimeFolder.isEmpty() || !curShowsFolder.isEmpty()) && (str.startsWith("series=") || str.startsWith("show=") || str.startsWith("anime="))) {
						
						String seriesName = file.getName().substring(file.getName().indexOf("=")+1);
						String seriesPath;
						
						if (!curShowsFolder.isEmpty() && (!str.startsWith("anime=") || curAnimeFolder.isEmpty()))
							seriesPath = curShowsFolder;
						else
							seriesPath = curAnimeFolder;
						
						str = "" + System.currentTimeMillis() + "" + System.nanoTime(); // temporary alias token
						
						shows.put(str, new Show(seriesName, seriesPath, false, -1, default_lang, default_local));
					}
					
					if (shows.containsKey(str)) {
						
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
										
										fileMoves.add(new FileMove(f, new File(seasonDir.getPath()+"/"+fileName+".jpg")));
										
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
	}
	
	
	private static void printNewEpisodesInfo() {
		
		int numNewEpisodes = 0;
		for (String k : newEpisodes.keySet()) {
			
			numNewEpisodes += newEpisodes.get(k).size();
		}
		
		if (numNewEpisodes <= 0) { System.out.println("No new episodes found.\n"); } 
		else { System.out.println("New Episodes:\n"); } 
		
		int newEpisodesShowsNum = newEpisodes.keySet().size();
		
		for (String k : newEpisodes.keySet()) {
			List<Episode> cur = newEpisodes.get(k);
			
			int num = cur.size();
			
			if (numNewEpisodes <= 6 || (newEpisodesShowsNum == 1 && num <= 6)) {
				
				for (Episode ep : cur) {
					
					String s = ep.season+"";
					for (int i = 0, l = s.length(); i < 2-l; i++) { s = "0"+s; }
					
					String e = ep.episode+"";
					for (int i = 0, l = e.length(); i < 2-l; i++) { e = "0"+e; }
					
					System.out.println(shows.get(k).name+" - S"+s+"E"+e);
				}
				
			} else {
				
				System.out.println(num+" new episode"+(num > 1 ? "s" : "")+" of \""+shows.get(k).name+"\"");
			}
		}
		
		System.out.println("");
	}
	
	
	private static void renameEpisodes() {
		
		for (String showAlias : newEpisodes.keySet()) {
			
			Show show = shows.get(showAlias);
			show.load();
			
			File tmpfile = new File(show.seriesPath.replace("\\", "/")+show.name+"/tmp.zip");
			Web.downloadFile(show.url, tmpfile);
			
			
			ZipEntry infoEntry = null;
			ZipFile zipFile = null;
			
			try {
				
				zipFile = new ZipFile(tmpfile);
				
				infoEntry = zipFile.getEntry(show.lang+".xml");
				if (infoEntry == null) { infoEntry = zipFile.getEntry("en.xml"); }
				
			} catch (Exception e) { }
			
			if (infoEntry == null) {
				
				if (zipFile != null) { try { zipFile.close(); } catch (Exception e) {} }
				tmpfile.delete();
				continue;
			}
			
			SAXReader r = new SAXReader();
			Document document = null;
			try { document = r.read(zipFile.getInputStream(infoEntry)); }
			catch (Exception | Error e) { tmpfile.delete(); continue; }
			
			Element rootElem = document.getRootElement();
			
			Element showElement = rootElem.elements().stream().filter(e -> e.getName().equalsIgnoreCase("series")).findAny().get();
			
			for (Episode episode : newEpisodes.get(showAlias)) {
				
				Element episodeElement = getEpisodeElem(rootElem, episode.season, episode.episode, show.absNr);
				if (episodeElement == null) { continue; }
				
				String newName = episodeElement.elementTextTrim("EpisodeName");
				
				String extension = episode.dir.substring(episode.dir.lastIndexOf('.'));
				
				File newFile = new File(episode.dir.substring(0, episode.dir.lastIndexOf('.'))+" - "+cleanseFileName(newName)+""+extension);
				File epFile = new File(episode.dir);
				
				epFile.renameTo(newFile);
				
				if (show.local) {
					
					show.saveEpisodeMetadata(newFile, episodeElement);
				}
			}
			
			if (show.local) {
				
				show.saveNFO(showElement);
				show.updateMedia(showElement, zipFile);
			}
			
			if (zipFile != null) { try { zipFile.close(); } catch (Exception e) {} }
			tmpfile.delete();
			
		}
		
		if (!newEpisodes.isEmpty()) { System.out.println(""); }
	}
	
	
	private static Element getEpisodeElem(Element rootElem, int season, int episode, boolean absNr) {
		
		for (Iterator<Element> i = rootElem.elementIterator(); i.hasNext();) {
			Element elem = i.next();
			
			if (elem.getName().equalsIgnoreCase("episode")) {
				
				int s = Integer.parseInt(elem.elementTextTrim("SeasonNumber"));
				int e = Integer.parseInt(elem.elementTextTrim("EpisodeNumber"));
				
				int absE = -1;
				try { absE = (int)Double.parseDouble(elem.element("absolute_number").getText().trim()); } catch (Exception | Error ex) { }
				
				if ((absNr && absE == episode) || (s == season && e == episode)) {
					
					return elem;
				}
			}
		}
		return null;
	}
	
	
	private static File getShowDir(Show show) {
		
		File dir = new File(show.seriesPath);
		File[] files = dir.listFiles();
		
		if (files == null) { return null; }
		
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
			
			if (loadableCustomScrapers.containsKey(prefix)) {
				
				CustomScraper cs = (CustomScraper)JythonFactory.getJythonObject("main.CustomScraper", loadableCustomScrapers.get(prefix));
				
				if (cs != null) {
					
					customScrapers.put(prefix, cs);
					
					return cs;
				}
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
	
	private static void parseArgs(String[] args) {
		
		List<String> tmp = new ArrayList<String>(args.length);
		for (String a : args) { tmp.add(a.trim().toLowerCase()); }
		args = tmp.toArray(new String[0]);
		
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			String nextArg = (i < args.length-1) ? args[i+1] : null;
			
			if (arg.equals("--fix-all")) {
				
				mode = MODE_FIX;
				
			} else if (arg.equals("--refresh-all")) {
				
				mode = MODE_REFRESH;
				
			} else if (nextArg != null) {
				
				if (arg.equals("-f") || arg.equals("--fix")) {
					
					mode = MODE_FIX;
					modeArg = nextArg;
					
				} else if (arg.equals("-r") || arg.equals("--refresh")) {
					
					mode = MODE_REFRESH;
					modeArg = nextArg;
					
				}
			}
			
		}
		
	}
	
	
	public static boolean parseBoolean(String str) throws Exception {
		
		if (str != null) {
			
			str = str.trim().toLowerCase();
			
			if (str.equals("0") || str.equals("false") || str.equals("no")) { return false; } 
			if (str.equals("1") || str.equals("true") || str.equals("yes")) { return true; }
		}
		
		throw new Exception();
	}
	
	public static int parseId(String str) throws Exception {
		
		if (str != null && !str.trim().isEmpty()) {
			
			str = str.trim();
			int num = -1;
			try { num = Integer.parseInt(str); } catch (Exception ex) { }
			if (num >= 0) { return num; }
		}
		
		throw new Exception();
	}
	
}
