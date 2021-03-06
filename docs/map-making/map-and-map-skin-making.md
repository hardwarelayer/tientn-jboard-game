# Map and Map Skin Making Overview
<hr>
<br>
<h2><a id="sec_1">1.0</a> Introduction to TripleA Maps, and Map Skins</h2>
<p>
	Welcome to the Map and Map Skin making tutorial.  This will cover all points except for the actual game xml.
	Before we begin, a brief explanation is needed about skins, because actually there is no difference between 'map' and 'map skin'.
</p>
<br>
<p>
		Games made for TripleA actually consist of 2 distinct parts:
	<br>1. The Map Skin
	<br>The Map skin is all of the art files and display information needed for the map to show up on your screen.
		Nothing here ever gets sent over the network or saved to a savegame, and users are free to change these files at will.
		This can be thought of as "all files except for the games folder".
	<br>
	<br>2. The Game XML
	<br>The Game XML is a single xml file, containing the all of the information about the board setup, the game rules, how the game is played, etc.
		All of the information in this is parsed and saved as a GameData object, and all of it gets saved to savegames and sent over the network.
		Anything changed in here will affect any new games that are started with that game name (existing savegames are not affected, because all the information was parsed and saved long ago).
</p>
<br>
<p>
	The distinction is important because TripleA allows you to have multiple Map Skins for each game (as many as you want).  It also means that the game xml's, and the map skins, can be packaged and distruted separately.
	<br>(Example: many game variations or 'mod' files contain only the game xml, and the game xml points to a specific Map Skin folder, which the user must already have to be able to start the game).
</p>
<br>
<p>
	An example of this is Napoleonic Empires, which comes with an alternative skin.
	<br>Start up TripleA now to see it. Choose any of the Napoleonic Empires games, then start the game.
	Select "View" from the menu, then hold your mouse over "Map Skins" to show all available skins (if any).
	From there, click which Skin you want.  TripleA will automatically refresh the screen with your new skin active.
	<br><br><img src="https://cloud.githubusercontent.com/assets/12397753/21297559/3c5f1ace-c537-11e6-908b-3617da8d1ee0.png" alt="skin image">
	<br><br><img src="https://cloud.githubusercontent.com/assets/12397753/21297560/3c5f7a6e-c537-11e6-92ad-59dd7000a640.png" alt="skin image">
</p>
<br>
<p>
	There is NO SPECIAL DIFFERENCE between an alternative map skin, and the original map skin. Both treated the same by the engine, in that they are "everything but the game xml".
	<br>So how does TripleA know which Skin is the default (original) skin, and which are the alternative skins?
	<br>The answer is the folder's name.
	<br>The Alternative Skins are just the Original Skin's folder name, then "-" (dash), then their skin name.
	<br>(The default/original folder's name must be the same name as referenced in the game xml's "mapName" property.)
	<br>So, if you had a map called "minimap", and you made a second skin for it, you would name the folder "minimap-mySkin" or "minimap-Awesome_Skin", etc.
	<br><br><img src="https://cloud.githubusercontent.com/assets/12397753/21297562/3c609822-c537-11e6-9aec-c27fab751d45.png" alt="folder image">
</p>
<br>
<h4><a id="sec_1.1.1">1.1.1</a> Making Your First Map Skin in Under 5 Minutes</h4>
<p>
	The easiest way to make a map skin, is simply to copy another map's folder.
</p>
<br>
<p>
	So, to begin, navigate to either your triplea program folder, or your triplea user folder, then go into the "maps" folder.
	<br>Now select the game you want to copy. For the example, we will use "great_war".  Copy and then Paste the entire map folder.
	<br>Rename your folder so that it starts with the original map name, then "-", then your skin name.  We will name our folder "great_war-Cat_Infantry".
</p>
	Now, we should probably change something about our map to make it different.  So, lets replace all the infantry with pictures of the Cheezburger Cat.
	<br>Right Click and save this image as "infantry.png"
	<br><br><img src="https://cloud.githubusercontent.com/assets/12397753/21297554/3c4d1acc-c537-11e6-976e-dd65a1a2a186.png" alt="cat image">
<br>
<p>
	Now, open up your new map's folder, then open up the "units" folder, then open up the "Americans" folder.
	<br>Paste the image of your cat here.  It should overwrite the existing "infantry.png" image.
	<br>Now do the same for all the other nations.
	<br><br><img src="https://cloud.githubusercontent.com/assets/12397753/21297553/3c4ce0b6-c537-11e6-8ebd-c585b09d9318.png" alt="folder image">
</p>
<br>
<p>
	Start TripleA and load up Great War in order to test your new map skin.
	<br>After starting the game, click "View" and change your "Map Skin" to the new "Cat_Infantry" skin.
	<br>OMG, the USA better enter the war soon, or else the Western Front will run out of Cheezburgers!
	<br><br><img src="https://cloud.githubusercontent.com/assets/12397753/21297555/3c4da46a-c537-11e6-9a6b-cf83e6de4c6f.png" alt="skin image">
</p>
<br>
<p>
	Obviously this probably not how you imagined your first map, or skin, but I think it is important to point out just how easy it can be to start changing stuff.  You were probably able to do this all in less than 5 or 10 minutes.
	<br>Any time you don't like ANYTHING about a map's visuals or display stuff, you can go in and either change the original art or display files, or make a skin.
	<br>If you do a decent job of it, you could even distribute it to other players who are interested in viewing a map in a different way.
</p>
<br>
<h2><a id="sec_2">2.0</a> The Map Creator</h2>
<p>
	Lets say you want to change more than just replacing the default unit images? Lets say you want to build a map from scratch?
	<br>In this case, you will need to run all of the map maker utilities, as well as doing a little bit of preparation work. The easiest way to run all the map maker utilities, is with the Map Creator utility.
	<br>The Map Creator is nothing special.  All it does is run the utilities for you, which you could do yourself using terminal or command line.  Still, it does make things a little easier.
</p>
<br>
<p>
	Before we start the Map Creator, lets go over what you actually need to have ready before you begin.
	<br>1. A Territory Border Outline Map (PNG).  The territories should be outlined in pure black (hex: 000000), with no anti-aliasing (smoothing), which means if look close enough it should be pixelated.
	There should not be any art, or anything, on the map. You will use this map to run all the utilities, and will also make it into your baseTiles.
	<br>2. (Optional) A second map, same size, with art and and some texture. You normally make this map semi-transparent, and use it as the reliefTiles.
	<br>3. The names of all your nations (TripleA comes with art for specific nation names, so it is advisable to use existing names if they exist. ie: Germans, not Germany).
	<br>4. The name of your map.
	<br>5. A small image of your map, normally 250 pixels wide.  Just use "Paint.net", or "MS Paint", or "Photoshop", or "GIMP", or any other picture/image editor to take your map image and shrink it to be 250 pixels wide.
	<br>
	<br>You can use any map image you want. Normally it is a political map of the earth, but it could be mars, or middle earth, or westeros, or whatever.
	<br>If you do not have a Map Image, here is a good one to use.  Feel free to save it, and then change the territory borders as you see fit.
	<br><br><img src="https://cloud.githubusercontent.com/assets/12397753/21297572/3c83eade-c537-11e6-8551-6a2cc19b4671.png" alt="map image">
</p>
<br>
<p>
	Now that you have a map and stuff, you need to make the map folder.
	<br>Go to your USER Maps Directory (not the program files directory) and create a new folder. Change the name of the folder to a simplified version of your maps name.  The folder's name will be the "mapName" property in any xml you make.
	<br>Do not make the map name too long, and do not use special characters.  (ie: "red_dawn" is a good map folder name.  "**RED DAWN** : The Inva$ion of the USA" is a bad map name, and maybe even illegal or not recognizeable by triplea.)
	<br>If you are making an alternative skin for a map that already exists, then your map name must be original/default map folder's name, then "-", then your skin's name.
</p>
<br>
<p>
	Inside of your map folder, you need at a bare minimum, the following files and folders.
	<br>You can go ahead and make the folders now.  For the files, you can move your map's territory border image there, any relief image, and the smallMap.jpeg. For the other files, they will be created later.
	<br>Folders:
</p>
<ul>
	<li>baseTiles</li>
	<li>flags</li>
	<li>units</li>
	<li>games  (*Optional* If you are making an alternative map skin, then do not make this folder!)</li>
</ul>
Files:
<ul>
	<li>centers.txt</li>
	<li>polygons.txt</li>
	<li>place.txt</li>
	<li>map.properties</li>
	<li>smallMap.jpeg</li>
	<li>your_territory_border_image.png  (*Not actually required, but you should put it here anyway*)</li>
</ul>
<br>Example of a game folder with the bare minimum:
<br><img src="https://cloud.githubusercontent.com/assets/12397753/21297561/3c60946c-c537-11e6-8c21-83c880739cd6.png" alt="map image">
<br>
<br>
<br>
<br>In addition, there are some other optional Folders and Files. TripleA will run fine without them, and we are going to ignore them for now:
<br>Folders:
		<ul>
			<li>dice</li>
			<li>misc</li>
			<li>PUs</li>
			<li>reliefTiles</li>
			<li>resources</li>
			<li>sounds</li>
			<li>territoryEffects</li>
			<li>territoryNames</li>
		</ul>
Files:
		<ul>
			<li>notifications.properties</li>
			<li>objectives.properties</li>
			<li>politicstext.properties</li>
			<li>actionstext.properties</li>
			<li>production_tabs.properties</li>
			<li>production_tabs.nationName.properties</li>
			<li>tooltips.properties</li>
			<li>blockade.txt</li>
			<li>capitols.txt</li>
			<li>comments.txt</li>
			<li>convoy.txt</li>
			<li>decorations.txt</li>
			<li>kamikaze_place.txt</li>
			<li>name_place.txt</li>
			<li>pu_place.txt</li>
			<li>territory_effects.txt</li>
			<li>vc.txt</li>
		</ul>
<p>
	<br>Example of game folder with everything possible:
	<br><br><img src="https://cloud.githubusercontent.com/assets/12397753/21297564/3c637a56-c537-11e6-95d5-6c3b6d71166d.png" alt="map image">
</p>
<br>
<p>
<h3>Now for the map creator</h3>
<p>
    To run the map creator, you can start TripleA, then click "Run Map Creator".
</p>
<p>
	Now that it is running, you will see a simple screen with some options on the left, and some options in the middle.
	<br>The first button will load this html tutorial you are reading now.
	<br>Click the second button to point the map creator to your new folder.
	<br>The third box is where you can put your unit zoom amount (normally 0.75 or 0.5).
	<br>The forth and fifth boxes are for the size of your unit images (normally 48 pixels square).
	<br>Lastly, you can specify the amount of memory in megabytes that the utilities will run at. This is only related to the utilities, and is the same thing as changing the -Xmx512m to -Xmx900m, etc.
	<br><br><img src="https://cloud.githubusercontent.com/assets/12397753/21297557/3c4f54ae-c537-11e6-837e-430ff8145fda.png" alt="map image">
</p>
<br>
<h2><a id="sec_3">3.0</a> Creating a Custom Map Territory Border Image</h2>
<p>
	Now obviously you could draw your map by hand, and a good number of maps (including the sample one at the top) are done that way.
	<br>But lets say you already have a map in mind, maybe a scan of something you drew a long time ago.  It has full art, and looks great, but it is not a "territory outline map".
	<br>Well, in this case, you will need to do a good chunck of work to create a territory outline map.
	You may have to draw the territory borders yourself, but in most cases you use the tools provided in software like Paint.NET, or GIMP, or Photoshop, to do the work for you.
</p>
<br>
<p>
	I once made a map based on an existing map for a game called "ABattleMap". I will use my experience and some screenshots as an example.
	Both GIMP and Paint.net are free image manipulation programs, and I used Paint.net to get the territory lines.
	Immediately after I finished getting them done, I found the original author's source files, and found that he actually already had a territory map.
	So before you waste a ton of time, make sure that you have searched for the source files for the art you are using, because what you want might already be done.
</p>
<br>
<p>
	To start, load up the image in your drawing program.  You will be using 2 tools mostly:
	<br>1. The "magic wand", which is a selection thingy that will automatically select the current pixel and all connecting pixels of a same or similar color (you choose the tolerance).
	<br>2. The "paint bucket", which will fill in any area with a specific color, and the fill area can also be determined by tolerance.
	You can change the tolerance up at the top of the screen (Paint.net) after selecting one of these tools.  Generally you want something between 10% and 40%, so lets start with 25%.
	<br>Secondly, you MUST disable anti-aliasing.
	Thirdly, you must select the color pure black (hex code 000000).  TripleA will not recognize any lines or borders that are not pure black.
</p>
<br>
<p>
	The purpose of what we are doing, is that we are going to "fill in" all of the territory borders with black.  Then we are going to select the borders, and copy and paste the borders into a new image.
	We will end up with a territory border image that is pure black, on a white background.  You can then use this image to complete all the map utilities in the map creator.
	<br>One thing to note is that territory borders can be Any thickness, but they should all be the Same thickness. Generally 1 pixel thick territory borders look the best, but some maps use 2 pixel thick.
</p>
<br>
<p>
	1st. Some lines on the map might not be black. In my example map, the lines in France are not black, they are dark blue.  The lines in Canada look black, but they are really dark brown.
	<br>So, take the Paintbucket, and click those borders (you may need to zoom in).  That will fill in the territory bordes with black.  Remember: you are clicking the BORDER, not the middle of the territory.
	<br>IF clicking on the border ends up filling in some part of the territory with black, then your Tolerance is too high.  Undo, then reduce your tolerance, then try again.
	Now, go look around the map for any territory borders that are not yet black, and turn them black.  Keep doing this until you are sure all territory borders are black.
	<br><br><img src="https://cloud.githubusercontent.com/assets/12397753/21297549/3c3ab90e-c537-11e6-82b9-bb88ae3d41ec.png" alt="map image">
</p>
<br>
<p>
	2nd. Using the magic wand tool, click on the territory border. If the pixels connect, your selection should grab the entire territory borders for the entire map. There might be some lag while your computer calculates this.
	<br>Now copy this.  Now start a new image (same size as current one) (or start a new layer), and paste the territory borders into there.  Save both files as new files, you might need them.
	<br>Take a look around your map to make sure you are not missing anything.  You are looking for 2 main things: gabs in territory borders, and islands.
	<br>Gaps in territory borders occur when the magic wand did not grab all of the border.  Go back and grab the rest of the border and add it to your image.
	<br>Islands will never be grabbed by the magic wand, because they do not connect to the main territory borders.  You have to go back and grab each and every island by hand, now.
	<br>When you are done, turn your image "black and white". This will remove any colors on the map. After that, pump up the "contrast".  This will make sure the borders are pure black.
	<br><br><img src="https://cloud.githubusercontent.com/assets/12397753/21297551/3c3d0dd0-c537-11e6-907e-b28592cafdab.png" alt="map image">
</p>
<br>
<p>
	Your map is basically done. You can now fill in the water/sea zones with some color of blue.
	<br>Make sure the territory borders don't have lines sticking out from them, and that you are not missing any islands, or have gaps in your borders.
	<br>Congrats, you are done. Go run the map creator.
	<br><br><img src="https://cloud.githubusercontent.com/assets/12397753/21297552/3c3e84e4-c537-11e6-997c-a2a3724d2386.png" alt="map image">
</p>
<br>
<h2><a id="sec_4">4.0</a> Map Skin Utilities</h2>
<p>
	TripleA comes with several utilities for editing maps. In brief, these
	utilities will allow the user to create center points and names for each
	territory, and break the map up into 256x256 sized tiles, and do all the other work for you.
</p>
<p>
	These are the utilities that can be used:
</p>
<ol>
	<li><b>Map Properties Maker</b> : Creates the map.properties text file</li>
	<li><b>Center Picker</b> : Picks center points for each territory</li>
	<li><b>Polygon Grabber</b> : Grabs the (x,y) polygonal values of each territory</li>
	<li><b>Auto-Placement Finder</b> : Picks placement for units (automatic)</li>
	<li><b>Placement Picker</b> : Picks placement for units (manually)</li>
	<li><b>Tile Image Breaker</b> : Breaks the map into 256x256 tiles (for baseTiles and reliefTiles folders)</li>
	<li><b>Decoration Placer</b> : Creates the text files for placing custom images on the map</li>
	<li><b>Relief Image Breaker</b> : No longer used (used to make relief territory images)</li>
	<li><b>Image Shrinker</b> : Creates a mini-sized map image</li>
	<li><b>Tile Image Reconstructor</b> : Recreates an image from basetiles or relief tiles</li>
</ol>
<p>
	All images are in PNG format except for the mini-map image produced by the
	Image Shrinker utility, which is in JPEG format.
</p>
<p>
	<b>Pre-requisite !</b><br>
	<br>
	A pre-made map needs to be created for the utilities to work. The map needs
	to have black borders separating each territory. This needs to be completely
	black as in #000000 or R=0 G=0 B=0. The insides of the territories should be
	white and the oceans or water values may be colored.<br>
	<br>
	<u><strong>You should run the map utilities using a binary (not source) distribution of TripleA.</strong></u>
</p>
<h4><a id="sec_4.1.1">4.1.1</a> Map Properties Maker</h4>
<p>
	The Map Properties Maker will create the "map.properties" file for you.
	<br>This file includes various information related to how to display your map,
	such as how many pixels wide and tall your map image is, or how zoomed out your units will be.
	<br>You can add and remove new players, and click on their color to change their color.
	<br>Clicking the "Show All Options" will show additional options that can be changed (the default values are pre-selected).
</p>
<br>
<p>
	The following nations/players are already supported by TripleA, and have flags and unit art already.
	<br>If you want to use them, you must use their EXACT names, which are case sensitive.
</p>
<ul>
	<li>Americans</li>
	<li>Australians</li>
	<li>British</li>
	<li>Canadians</li>
	<li>Chinese</li>
	<li>French</li>
	<li>Germans</li>
	<li>Italians</li>
	<li>Japanese</li>
	<li>Neutral</li>
	<li>Puppet_States</li>
	<li>Russians</li>
</ul>
<br>In addition, the 'player' called "Impassible" should be listed, which even though it is not a real player, it will determine the color of impassible territories.
<br>
<p>
	Be sure to save the file when you are done.
	<br>Unfortunately you can not load a map.properties file (not yet anyway).
	However, the file is just a text file, and you can edit it by hand easily using Notepad or Notepad++ or any other text editor you have.
</p>
<br>
<p>
	Please note that unit zoom must be one of the following values.
	<br>0.75 or smaller is recommended.
</p>
<ul>
	<li>1.25</li>
	<li>1.0</li>
	<li>0.875</li>
	<li>0.8333</li>
	<li>0.75</li>
	<li>0.6666</li>
	<li>0.5625</li>
	<li>0.5</li>
</ul>
<br>
<p>
	<b>How to run Map Properties Maker</b>
</p>
<pre><code>cd bin<br>java -Xmx512m -classpath triplea.jar util/image/MapPropertiesMaker</code></pre>
<br>
<table>
	<tr>
		<td><img src="https://cloud.githubusercontent.com/assets/12397753/21297556/3c4ed1dc-c537-11e6-8f33-a0a5cf5940bd.png" alt="map properties maker"></td>
	</tr>
	<tr>
		<td>Fig 3.1.1.0</td>
	</tr>
</table>
<br>
<table>
	<tr>
		<td><img src="https://cloud.githubusercontent.com/assets/12397753/21297558/3c50ad9a-c537-11e6-98a9-954e25b19dd7.png" alt="map properties maker"></td>
	</tr>
	<tr>
		<td>Fig 3.1.2.0</td>
	</tr>
</table>
<br>
<h4><a id="sec_4.1.2">4.1.2</a> Center Picker</h4>
<p>
	Run the center picker from the <b>bin</b> directory.
	<br>The center picker creates or edits the "centers.txt" file for you.
</p>
<br>
<br>
<b>How to run Center Picker</b>
<pre><code>cd bin<br>java -Xmx512m -classpath triplea.jar util/image/CenterPicker</code></pre>
<br>
<table>
	<tr>
		<td><img src="https://cloud.githubusercontent.com/assets/12397753/21297632/5236558c-c538-11e6-888e-d94196336ea6.png" width="696" height="491" alt="center picker"></td>
	</tr>
	<tr>
		<td>Fig 4.1.2.0</td>
	</tr>
</table>
<br>
<p>
	Execution flow of the Center Picker is listed below.
</p>
<br>
<br>
<table class="table1" style="width: 850px;">
	<tr>
		<td style="background-color: #ccffff;" colspan="2"><b>Program Action</b></td>
		<td style="background-color: #ccccff;" colspan="2"><b>User Action</b></td>
	</tr>
	<tr>
		<td style="background-color: #ccffff;"><b>1</b></td>
		<td style="background-color: #ffffcc;">Show a &quot;Select Map File&quot; dialog</td>
		<td style="background-color: #ccccff;"><b>2</b></td>
		<td style="background-color: #ffffcc;">Select a map image file</td>
	</tr>
	<tr>
		<td style="background-color: #ccffff;"><b>3</b></td>
		<td style="background-color: #ffffcc;">Show a &quot;Select Polygons File&quot; dialog</td>
		<td style="background-color: #ccccff;"><b>4</b></td>
		<td style="background-color: #ffffcc;">Select a polygons.txt file or cancel and run without.</td>
	</tr>
	<tr>
		<td style="background-color: #ccffff;"><b>5</b></td>
		<td style="background-color: #ffffcc;">Show map image</td>
		<td style="background-color: #ccccff;"><b>6</b></td>
		<td style="background-color: #ffffcc;">Left click on any territory to create a center point, Right click to delete a point.</td>
	</tr>
	<tr>
		<td style="background-color: #ccffff;"><b>7</b></td>
		<td style="background-color: #ffffcc;">Show a dialog box with a default territory name</td>
		<td style="background-color: #ccccff;"><b>8</b></td>
		<td style="background-color: #ffffcc;">Put a new territory name and press &quot;OK&quot;</td>
	</tr>
	<tr>
		<td style="background-color: #ccffff;"><b>9</b></td>
		<td style="background-color: #ffffcc;">Show confirmation dialog</td>
		<td style="background-color: #ccccff;"><b>10</b></td>
		<td style="background-color: #ffffcc;">Confirm with &quot;Yes&quot;, or cancel with &quot;No&quot; or &quot;Cancel&quot;</td>
	</tr>
	<tr>
		<td style="background-color: #ccffff;"><b>11</b></td>
		<td style="background-color: #ffffcc;">Show red dot on territory</td>
		<td>&nbsp;</td>
		<td>&nbsp;</td>
	</tr>
</table>
<br>
<p>
	After creating center points for all the territories, proceed to save
	them. The center picker will ask for a directory to save the center points.
	These center points will be needed for other map utilities later on, and for
	TripleA it self to run the game.
</p>
<h4><a id="sec_4.1.3">4.1.3</a> Polygon Grabber</h4>
<p>
	Run the polygon grabber from the <b>bin</b> directory.
	<br>The polygon grabber creates or edits the "polygons.txt" file for you.
	<br>
</p>
<b>How to run Polygon Grabber</b>
<pre><code>cd bin<br>java -Xmx512m -classpath triplea.jar util/image/PolygonGrabber</code></pre>
<br>
<table>
	<tr>
		<td><img src="https://cloud.githubusercontent.com/assets/12397753/21297566/3c71ac5c-c537-11e6-85e0-76418d4d005c.png" width="598" height="524" alt="polygon grabber"></td>
	</tr>
	<tr>
		<td>Fig 4.1.3.0</td>
	</tr>
</table>
<br>
<p>
	Execution flow of the Polygon Grabber is listed below.
</p>
<br>
<br>
<table class="table1">
	<tr>
		<td style="background-color: #ccffff;" colspan="2"><b>Program Action</b></td>
		<td style="background-color: #ccccff;" colspan="2"><b>User Action</b></td>
	</tr>
	<tr>
		<td style="background-color: #ccffff;"><b>1</b></td>
		<td style="background-color: #ffffcc;">Show a &quot;Select Map File&quot; dialog</td>
		<td style="background-color: #ccccff;"><b>2</b></td>
		<td style="background-color: #ffffcc;">Select a map image file</td>
	</tr>
	<tr>
		<td style="background-color: #ccffff;"><b>3</b></td>
		<td style="background-color: #ffffcc;">Show a &quot;Select Centers File&quot; dialog</td>
		<td style="background-color: #ccccff;"><b>4</b></td>
		<td style="background-color: #ffffcc;">Select a centers.txt file</td>
	</tr>
	<tr>
		<td style="background-color: #ccffff;"><b>5</b></td>
		<td style="background-color: #ffffcc;">Show map image</td>
		<td style="background-color: #ccccff;"><b>6</b></td>
		<td style="background-color: #ffffcc;">Left click on any territory to select it</td>
	</tr>
	<tr>
		<td style="background-color: #ccffff;"><b>7</b></td>
		<td style="background-color: #ffffcc;">Selected territory is highlighted in red</td>
		<td style="background-color: #ccccff;"><b>8</b></td>
		<td style="background-color: #ffffcc;">Right click on highlighted territory (hold shift for more)</td>
	</tr>
	<tr>
		<td style="background-color: #ccffff;"><b>9</b></td>
		<td style="background-color: #ffffcc;">Show name option dialog</td>
		<td style="background-color: #ccccff;"><b>10</b></td>
		<td style="background-color: #ffffcc;">Confirm if territory name is correct</td>
	</tr>
	<tr>
		<td style="background-color: #ccffff;"><b>11</b></td>
		<td style="background-color: #ffffcc;">Highlight selected territory in yellow</td>
		<td style="background-color: #ccccff;"><b>12</b></td>
		<td style="background-color: #ffffcc;">Go to next territory</td>
	</tr>
</table>
<br>
<p>
	The polygon grabber utility comes with a special &quot;Island Mode&quot; feature.
	It has been known that when dealing with many islands in one sea zone causes a visual
	problem. Doing the sea zone first will cover up any islands inside. This will leave
	the user unaware if the islands have been selected or not. The &quot;Island Mode&quot;
	feature helps over come this by out-lining selected territories in red and not filling
	them in with yellow, as is the default. Look at figures 4.1.3.1 and 4.1.3.2 for examples
	of &quot;Island Mode&quot; at work.
</p>
<table>
	<tr>
		<td><img src="https://cloud.githubusercontent.com/assets/12397753/21297570/3c763cb8-c537-11e6-9032-ceca7862ce60.png" width="598" height="524" alt="island mode 1"></td>
	</tr>
	<tr>
		<td>Fig 4.1.3.1</td>
	</tr>
</table>
<br>
<table>
	<tr>
		<td><img src="https://cloud.githubusercontent.com/assets/12397753/21297565/3c71a2b6-c537-11e6-84e4-a53b3abd7e22.png" width="598" height="524" alt="island mode 2"></td>
	</tr>
	<tr>
		<td>Fig 4.1.3.2</td>
	</tr>
</table>
<br>
<p>
	Notice how in figure 4.1.3.1 one of Sardinia's islands was covered up when
	the sea zone was done first. Island mode helped show the covered up island
	and allowed us to select it.
</p>
<p>
	When done, save the polygon points to disk.
</p>
<h4><a id="sec_4.1.4">4.1.4</a> Auto-Placement Finder</h4>
<p>
	The auto-placement finder can be used before the placement picker.
	It automates the placement picking procedure and picks what it chooses
	to be the optimal placement points.
	<br>The Auto-Placement Finder creates the "place.txt" file for you.
	<br>It is best to use the auto placement picker first, then check the placements in the normal (manual) placement picker.
</p>
<p>
	There are some pre-requisites that need to be fulfilled before running
	the auto-placement finder:
</p>
<ol>
	<li>The centers.txt and polygons.txt files exist</li>
	<li>The above text files need to be in their finalized map directory</li>
	<li>Your map's folder must be in the maps directory where triplea can find it: users/yourname/triplea/maps/</li>
</ol>
<p>
	Run the placement picker from the <b>bin</b> directory. When run
	it will ask for the map name.  The map name IS the exact name of the map folder.
</p>
<br>
<br>
<b>How to run Auto-Placement Finder</b>
<pre><code>cd bin<br>java -Xmx512m -classpath triplea.jar util/image/AutoPlacementFinder</code></pre>
<br>
<table>
	<tr>
		<td><img src="https://cloud.githubusercontent.com/assets/12397753/21297548/3c3a7160-c537-11e6-8a44-ede6885d2f6a.png" width="560" height="333" alt="auto-placement finder"></td>
	</tr>
	<tr>
		<td>Fig 4.1.4.0</td>
	</tr>
</table>
<br>
<p>
	When the auto-placement finder is done creating the placement points, it will
	prompt a dialog in order to save the placement points to disk.
</p>
<h4><a id="sec_4.1.5">4.1.5</a> Placement Picker</h4>
<p>
	Run the placement picker from the <b>bin</b> directory.
	<br>The Auto-Placement Finder creates or edits the "place.txt" file for you.
</p>
<br>
<br>
<b>How to run Placement Picker</b>
<pre><code>cd bin<br>java -Xmx512m -classpath triplea.jar util/image/PlacementPicker</code></pre>
<br>
<p>
	Placement Picker commands are as follows:
</p>
<table class="table1">
	<tr>
		<td style="background-color: #ccffff;"><b>Command</b></td>
		<td style="background-color: #ccccff;"><b>Result</b></td>
	</tr>
	<tr>
		<td style="background-color: #ffffcc;">Left Mouse Button</td>
		<td style="background-color: #ffffcc;">Start in this territory</td>
	</tr>
	<tr>
		<td style="background-color: #ffffcc;">CTRL/SHIFT +  Left Mouse Button</td>
		<td style="background-color: #ffffcc;">Add a placement point to the list</td>
	</tr>
	<tr>
		<td style="background-color: #ffffcc;">Right Mouse Button</td>
		<td style="background-color: #ffffcc;">Remove last placement point</td>
	</tr>
	<tr>
		<td style="background-color: #ffffcc;">CTRL/SHIFT + Right Mouse Button</td>
		<td style="background-color: #ffffcc;">Save placement points for that territory</td>
	</tr>
</table>
<br>
<p>
	Figure 4.1.5.0 shows an example of U.K. with its placements done.
</p>
<table>
	<tr>
		<td><img src="https://cloud.githubusercontent.com/assets/12397753/21297563/3c629794-c537-11e6-909a-a05ee0416f77.png" width="594" height="520" alt="placement picker"></td>
	</tr>
	<tr>
		<td>Fig 4.1.5.0</td>
	</tr>
</table>
<br>
<p>
	When done save the placement points to disk.
</p>
<h4><a id="sec_4.1.6">4.1.6</a> Tile Image Breaker</h4>
<p>
	This utility will break up the original large map into tiles of size 256x256 so that
	it can be used by TripleA. There are not any special prerequisites to use this
	utility other than running it and choosing the correct map for it to break up.
	<br>Use this utility to fill the "baseTiles" folder with your "territory border image".
	<br>You can also use this to fill the "reliefTiles" folder with a semi-transparent art or textured map image.
</p>
<b>How to run Tile Image Breaker</b>
<pre><code>cd bin<br>java -Xmx512m -classpath triplea.jar util/image/TileImageBreaker</code></pre>
<br>
<table>
	<tr>
		<td><img src="https://cloud.githubusercontent.com/assets/12397753/21297571/3c835448-c537-11e6-89bf-8966a2a5fa8b.png" width="553" height="263" alt="tile image breaker"></td>
	</tr>
	<tr>
		<td>Fig 4.1.6.0</td>
	</tr>
</table>
<br>
<table class="table1">
	<tr>
		<td style="background-color: #ccffff;" colspan="2"><b>Program Action</b></td>
		<td style="background-color: #ccccff;" colspan="2"><b>User Action</b></td>
	</tr>
	<tr>
		<td style="background-color: #ccffff;"><b>1</b></td>
		<td style="background-color: #ffffcc;">Show a &quot;Folder Save Location&quot; dialog</td>
		<td style="background-color: #ccccff;"><b>2</b></td>
		<td style="background-color: #ffffcc;">Select a directory to save the tiles</td>
	</tr>
	<tr>
		<td style="background-color: #ccffff;"><b>3</b></td>
		<td style="background-color: #ffffcc;">Show a &quot;Select Map File&quot; dialog</td>
		<td style="background-color: #ccccff;"><b>4</b></td>
		<td style="background-color: #ffffcc;">Select a map image</td>
	</tr>
	<tr>
		<td style="background-color: #ccffff;"><b>5</b></td>
		<td style="background-color: #ffffcc;">Process map and break into tiles</td>
		<td style="background-color: #ccccff;"><b>6</b></td>
		<td style="background-color: #ffffcc;">Wait until finished</td>
	</tr>
</table>
<br>
<p>
	Once the Tile Image Breaker is done, all the tiles will be saved
	in the directory that has been selected at the start.
</p>
<h4><a id="sec_4.1.7">4.1.7</a> Decoration Placer</h4>
<p>
	The decoration placer makes putting custom images on your map easy.
	<br>Essentially you choose which image you are placing, then you load that image or set of images onto the map.
	<br>The images if named exactly equal to a territory name, will be automatically placed at the center of that territory.
	<br>Otherwise, all other images will be placed at the upper left corner of the map.
	<br>One by one, you click each image, drag it to where you want it to be, then click again to put it there.
</p>
<br>
<p>
	The decoration placer covers the following folders and files:
	<br>Folders:
</p>
<ul>
	<li>flags</li>
	<li>misc</li>
	<li>PUs</li>
	<li>territoryEffects</li>
	<li>territoryNames</li>
</ul>
Files:
<ul>
	<li>blockade.txt</li>
	<li>capitols.txt</li>
	<li>comments.txt</li>
	<li>convoy.txt</li>
	<li>decorations.txt</li>
	<li>kamikaze_place.txt</li>
	<li>name_place.txt</li>
	<li>pu_place.txt</li>
	<li>territory_effects.txt</li>
	<li>vc.txt</li>
</ul>
<br>
<p>
	You can use the "edit options" to highlight all images in a red square, or to have the image name directly above the image.
	<br>When done, be sure to save the file.
</p>
<br>
<p>
	<b>How to run Decoration Placer</b>
</p>
<pre><code>cd bin<br>java -Xmx512m -classpath triplea.jar util/image/DecorationPlacer</code></pre>
<br>
<table>
	<tr>
		<td><img src="https://cloud.githubusercontent.com/assets/12397753/21297547/3c3aa842-c537-11e6-8f91-d2a2ab3b0279.png" alt="decoration placer"></td>
	</tr>
	<tr>
		<td>Fig 3.1.7.0</td>
	</tr>
</table>
<br>
<h4><a id="sec_4.1.8">4.1.8</a> Relief Image Breaker</h4>
<p>
	The relief image breaker will take your map and create a textured image for each territory.
	<br>TripleA no longer uses these Relief Territory images, and instead uses "Relief Tiles" (which you make using the "Tile Image Breaker").
</p>
<h4><a id="sec_4.1.9">4.1.9</a> Image Shrinker</h4>
<p>
	This utility will create a copy of the original map, but shrunk down
	to a custom scale. TripleA uses this small scale map as a &quot;mini-map&quot;.
	<br>You should save this image as "smallMap.jpeg" and put it into your map's folder.
	<br>You do not have to use this program, you can use ANY image manipulation or drawing program you want in order to make your smallMap.jpeg.
	<br>It is advisable to keep the smallMap's width between 200 and 300 pixels.
</p>
<p>
	<b>How to run Image Shrinker</b>
</p>
<br>
<pre><code>cd bin<br>java -Xmx512m -classpath triplea.jar util/image/ImageShrinker</code></pre>
<table class="table1">
	<tr>
		<td style="background-color: #ccffff;" colspan="2"><b>Program Action</b></td>
		<td style="background-color: #ccccff;" colspan="2"><b>User Action</b></td>
	</tr>
	<tr>
		<td style="background-color: #ccffff;"><b>1</b></td>
		<td style="background-color: #ffffcc;">Show a &quot;Select Map File&quot; dialog</td>
		<td style="background-color: #ccccff;"><b>2</b></td>
		<td style="background-color: #ffffcc;">Select a map</td>
	</tr>
	<tr>
		<td style="background-color: #ccffff;"><b>3</b></td>
		<td style="background-color: #ffffcc;">Show a &quot;Scale Input&quot; dialog</td>
		<td style="background-color: #ccccff;"><b>4</b></td>
		<td style="background-color: #ffffcc;">Enter a floating point scale value (ie. 0.1)</td>
	</tr>
	<tr>
		<td style="background-color: #ccffff;"><b>5</b></td>
		<td style="background-color: #ffffcc;">Image saved as <b>smallMap.jpeg</b> in current directory</td>
		<td style="background-color: #ccccff;">&nbsp;</td>
		<td>&nbsp;</td>
	</tr>
</table>
<h4><a id="sec_4.1.10">4.1.10</a> Tile Image Reconstructor</h4>
<p>
	This utility will recreate an image from the basetiles or relief tiles.
</p>
<b>Tile Image Reconstructor</b>
<pre><code>cd bin<br>java -Xmx512m -classpath triplea.jar util/image/TileImageReconstructor</code></pre>
<br>
<h2><a id="sec_5">5.0</a> Filling in the Other Folders and Files</h2>
<p>
	So what about all those other files and folders?
	<br>Here we can explain what everything else is for.
</p>
<br>
<p>So far you have used the map utilities to create the following files, and fill the following folders:</p>
<br>Folders:
<ul>
	<li>baseTiles</li>
	<li>reliefTiles</li>
</ul>
<p>Files:</p>
<ul>
	<li>centers.txt</li>
	<li>polygons.txt</li>
	<li>place.txt</li>
	<li>map.properties</li>
	<li>smallMap.jpeg</li>
</ul>
<p>You may have also used the decoration placer to create the following placement text files:</p>
<ul>
	<li>blockade.txt</li>
	<li>capitols.txt</li>
	<li>comments.txt</li>
	<li>convoy.txt</li>
	<li>decorations.txt</li>
	<li>kamikaze_place.txt</li>
	<li>name_place.txt</li>
	<li>pu_place.txt</li>
	<li>territory_effects.txt</li>
	<li>vc.txt</li>
</ul>
<br>
<p>
	This leaves the following folders:
	<br>Folders:
</p>
<ul>
	<li>flags</li>
	<li>units</li>
	<li>games</li>
	<li>dice</li>
	<li>misc</li>
	<li>PUs</li>
	<li>resources</li>
	<li>sounds</li>
	<li>territoryEffects</li>
	<li>territoryNames</li>
	<li>doc</li>
</ul>
<p>Files:</p>
<ul>
	<li>notifications.properties</li>
	<li>politicstext.properties</li>
	<li>actionstext.properties</li>
	<li>production_tabs.properties</li>
	<li>production_tabs.nationName.properties</li>
	<li>tooltips.properties</li>
	<li>objectives.properties</li>
	<li>sounds.properties</li>
</ul>
<br>
<p>
	The "games" folder holds the game xml's, which contain all the information about the board setup, the rules, etc.  How to make the xml is not covered here.
</p>
<br>
<p>
	The "flags" folder is required (if you are using non-standard nations), and you need to fill it with 3 images for each nation you have.
	<br>Lets say one of your nations is called "Germans".  In this case you need a middle sized flag named "Germans.png" which should be 32x32 pixels.
	You also need a small version called "Germans_small.png" which is 12x12 pixels.  And lastly you need a large version called "Germans_large.png" (which shows up on the map at the capital), which can be anywhere from 32x32 to 50x50 to 100x100 or whatever you want.
	<br>In addition, if you are using kamikaze zones then you need a "Germans_fade.png" flag (32x32 to 40x40 to 50x50).  And if you have convoy zones or convoy route, you may optionally add a "Germans_convoy.png" (32x32 to 40x40 to 50x50).
</p>
<br>
<p>
	The "units" folder is also required (if you have any non-standard nations, and/or any non-standard units), and you need to fill it with 1 folder for each nation.
	The folders must be the exact names of your nations (case sensitive), and each folder is filled with every unit in the game (again, case sensitive).
	<br>Pay attention to the case sensitivity, because "infantry.png" is different from "Infantry.png" (also use .png, not .PNG).
	<br>If you have any technology in your map, you need to include some _variations of each unit, such as _jp for jet power, etc. See the list below:
	<br>Anything I list as "_something" means that you must have the units exact name in front of it, example: fighter_jp
</p>
<ul>
	<li>if the unit is called "aaGun" then you need units called "rockets", "rockets_r", and "aaGun_r"</li>
	<li>if the unit has "isAA" or "isAAfor...", but is not exactly named "aaGun", then you need units called "_rockets" and "_r" and "_rockets_r"</li>
	<li>if the unit has "isRocket" or "isAA", but is not exactly named "aaGun", then you need "_rockets"</li>
	<li>if the unit has "isAA" or "isAAforCombatOnly" and/or "isAAforBombingThisUnitOnly", then you need "_r"</li>
	<li>if the unit has "isAir", but not "isStrategicBomber", then you need "_lr", and "_jp", and "_lr_jp"</li>
	<li>if the unit has "isAir", and is "isStrategicBomber", then you need "_lr", and "_hb", and "_lr_hb"</li>
	<li>if the unit has "isSub", then you need "_ss"</li>
	<li>if the unit has "isFactory" or "canProduceUnits", or is called "factory", then you need "_it", and "_it_hit"</li>
	<li>if a unit can be damaged (by being a factory, or by having "canBeDamaged", or by having more than one hitpoint) then you will need "_hit"</li>
	<li>if a unit can be disabled, then you will need "_disabled"</li>
</ul>
<br>
<p>
	The "dice" folder contains dice images to use. You must have 3 for each die possibility (Example using '1' as die result): "1.png", "1_hit.png", and "1_ignored.png"
</p>
<br>
<p>
	The "misc" folder contains the "blockade.png" (for blockade zones), the "cursor.gif" (custom cursor image, 32x32 gif), and the "vc.png" (victory city image).
	<br>In addition, you can fill the misc folder with ANY images you want.  You can then place these images all over the map, anywhere you want, using the decoration placer and the decorations.txt file.
</p>
<br>
<p>
	The "PUs" folder contains images for each territory production value. Generally numbers 1-20 are enough. Example: "2.png"
</p>
<br>
<p>
	The "resources" folder is not implemented yet, but eventually it will be used to display non-standard territory resources on the map.
</p>
<br>
<p>
	The "sounds" folder can be filled with folders for each sound that the game allows.
	Each folder can then be filled with multiple sound files, which will be played at that sound point.
	Only ".wav" sound files are supported.
	<br>To see what sound folders are allowed, check the "assets" folder inside of the triplea program folder.
	<br>You can also customize the folders by adding "_nationName" for nation specific sounds.
</p>
<br>
<p>
	The "territoryEffects" folder contains territory effect images, which can be shown on the map in any territory that has that effect (which is determined in the game xml).
	Since territories can have more than 1 effect, you can choose multiple placement points for multiple images, using the decoration placer utility.
</p>
<br>
<p>
	The "territoryNames" folder contains png images of the territory names, exactly named after the territories in the game (spaces included).  So "Eastern USA" becomes "Eastern USA.png".
	The placement points for the images is choosen using the decoration placer utility.
</p>
<br>
<p>
	The ".properties" files are just text files, and can be edited using Notepad or any other text editor.
	<br>These normally relate directly to something in the game xml.
	<br><br>The "notifications.properties" file has keys and values for notifications shown by the xml (popup messages).
	<br>The "objectives.properties" file has special information for displaying national objectives and triggers/conditions in an objectives tab for the user.
	<br>The "politicstext.properties" file has keys and values for political action text and button texts.
	<br>The "actionstext.properties" file has keys and values for user action text and button texts.
	<br>The "production_tabs.properties" and "production_tabs.nationName.properties" allow for customizing the production window in the game, including the tabs and ordering of the units.
	<br>The "tooltips.properties" allows for the customizing of the tooltips for units and territories.
	<br>The "sounds.properties" allows you to specify which era of sounds (ww2, preindustrial, classical, future) you are using, and also set up custom sound paths.
</p>
<br>
<p>
	The "doc" folder can hold help documents and stuff. It also has the "images" folder inside of it.
	<br>The "doc/images/" folder contains all images that you plan on using inside your game notes and inside any triggerred notifications.
</p>
<br>
<br>
<h2><a id="sec_10">10.0</a> Credits &amp;Acknowledgements</h2>
<ul>
	<li><b>Mark Christopher Duncan (Veqryn)</b> : The Map Creator, and this Tutorial</li>
</ul>
<br>

