This is the Freetalk message board plugin for Freenet. It requires the WebOfTrust plugin.

How to get this thing to work?

* Install Eclipse (Luna)
* Make sure you have the git plugin (already included in Luna)
* Clone the fred, plugin-WebOfTrust and plugin-Freetalk repositories somewhere on your hard drive. Disregard all references to "staging", those are left over from an old system. If you want to do development make sure to clone them first in github.
 * https://github.com/freenet/plugin-Freetalk
 * https://github.com/freenet/fred
 * https://github.com/freenet/plugin-WebOfTrust
* Import these git repositories in Eclipse
* Add libraries (tip: get these from your existing freenet install)
 * Put freenet-ext.jar in fred/lib/freenet 
 * put bcprov.jar in fred/lib 
* (maybe not needed) Fix the build paths for your eclipse projects so they refer to the correct freenet-ext and bcprov jars
* Have fun in eclipse and then run "ant" from the fred, plugin-WebOfTrust and plugin-Freetalk directories
* The jars will end up in the "dist" subdirectory.
* Import Freetalk.jar into Freenet at the plugins page
