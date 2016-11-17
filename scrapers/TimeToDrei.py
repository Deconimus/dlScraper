from main import CustomScraper

class TimeToDrei(CustomScraper):

	
	def __init__(self):
		
		print("kek")

		
	def getAlias():
		
		return "t23";

		
	def getEpisodeInfo(fileName):
		
		fileName = fileName[fileName.indexof("#")+1:]
		
		int2parse = ""+fileName[0]
		
		for i in range(0, len(fileName)):
			
			s = int2parse + fileName[i]
			
			if (s.isdigit()):
				
				int2parse = s
				
			else:
				
				break
				
		episode = 0
		
		if (int2parse.isdigit()):
			
			episode = int(int2parse)
			
		return [1, episode]

		
	def getEpisodeTitle(fileName):
		
		title = fileName[:fileName.rindex("-")]
		title = title[:title.rindex("-")]
		title = title[:title.rindex("-")]
		
		title = title.replace("\"", "").replace("_", "")
		title = title.strip()
		title = Main.cleanseFileName(title)
		
		return title