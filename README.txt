Final Project - BCIT COMP 2617 - Summer 2018

An Android app to help the user find a place to look for a home in New Westminster.

Compiles dataset [School locations, bus stops, government services, playground locations, fiber internet],
gets user requested settings, then compiles and displays a heatmap.


TODO and Potential Upgrades:
-Currently, bitmap pixels are not centered on heatmap verticies.
Thus, heatmap is offset a bit to the north-east.
-Downloading, parsing datasets is a background thread, though the 
system requires it to be done before generating the heatmap without errors.
Need to adjust timings, make download delay system until completed.
-Program memory consumption is signifigant.
-Scoring subsystem speed poor. Restricted in main thread.
Should allow the scoring to push each variable to a new thread, should speed up dramatically.
