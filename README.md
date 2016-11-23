# dlScraper
**A 1-click tool that organizes downloaded tv shows and movies for a media center like XBMC (or Kodi if you will).**

The premise of dlScraper is that you want to store your tv shows or movies in a structured manner that is especially readable for media centers and want to fill these folders with files you downloaded from the internet. It does the dreary handwork of moving and renaming the files you downloaded with just one click.

## How to use

### Requirements

The only requirements are a functional JVM and few megabytes of diskspace.
You can extract the release into any folder you like and run the jar-file or the bat if you are on a windows machine.

### Set-Up

#### The config xml

After you extracted the release zip you will see that there is a file called 'dlScraper_settings.xml'.
That is basically the config file where you define which folders you want to use and to where you download which show.

(There are also examples given in the xml itself)

In that file you list your download directories like

```
<dlFolder folder="D:/downloads" />
<dlFolder folder="E:/downloads" />
<dlFolder folder="//remote/downloads" />
etc..
```

Movie folders follow the same principle.

That basically also appeals to tvshows folders, but you define the tvshows you want to have inside these folder inside the folders xml brackets:

```
<seriesFolder folder="D:/Shows">
  
  <alias seriesName="Game of Thrones" aliasName="got" />
  
  <alias seriesName="South Park" aliasName="sp" />
  
  <alias seriesName="Homeland" aliasName="hml" />
  <alias seriesName="Homeland" aliasName="hland" />
  
  etc...
  
</seriesFolder>
```

As you can see the "alias" brackets define the seriesName and its alias. The alias will be later of use for the downloads.
You can make as much alias-entries for a single show as you desire.


##### Absolute Episode Numbering

If you want to have absolute episode numbering (conventionally used for long running anime such as Naruto) instead of TVDBs default season based numbering enabled for a show just add the "absNumbering" attribute to the show's entry and set it to "1" or "true":

```
<alias seriesName="Naruto Shippuuden" aliasName="shippuuden" absNumbering="1" />
```

#### Folders

##### Downloads

You basically want to have one or more folders in which you download your files into (these folders should be listed in the settings xml).
At runtime the tool will scan these folders for new video-files that are in subfolders that start with tvshow aliases (that you defined in the xml) or that are named like "movie=[title]".

In the case of tv-shows the season and episode numbers are looked up from the file names, which works in allmost every case as long as the file-namings are not too nonsensical or bold. 
If the show exists in the online database *thetvdb.com* the episode names will be looked up there as well.

##### TV-Shows

dlScraper will fill your tvshow folders with the following structure:

```
parent tvshows folder
  tvshow xyz
    Season 1
      S01E01 - Title
      S01E02 - Title
      ...
    Season 2
      S02E01 - Title
      ...
  tvshow uvw
   Season 1
    ...
  ...
```

### Example: TV Shows

You want to download a season of your favorite tvshow and let dlScraper do it's magic afterwards.

What you now want to do is to set up an entry of this tvshow in dlScrapers settings file where you define the shows name and it's alias. 
You then download the season into a folder named "[alias]"- You can also do dlScraper a favor by putting the season number into the folders name like "[alias] s01" f.i.
After this you just have to run dlScraper and let it move and rename your files.

### Example: Movies

Even simpler than tv shows are movies.
You just have to name the folder where you download your movie into in the form of "movie=[movietitle]" ("movie=Indie Game The Movie" f.i.).

### Custom Scrapers

If you wan't to use this tool to organize a TV Show or a Webseries, that isn't added to TVDB, you can write a simple scraper in python and add it to the "scrapers" directory. There is an explaining example as well as a functional scraper for the YouTube channel "TimeToDrei"'s Let's Plays.

The show's alias must also feature the scrapers alias as a prefix.

#### Example:

TimeToTrei.py defines it's alias as "t23":

```
def getAlias(self):
		
		return "t23"
```

So the shows this scraper should organize are labeled with "t23_" as a prefix:

```
<alias seriesName="999 - Nine Hours. Nine Persons. Nine Doors" aliasName="t23_999" />
```
