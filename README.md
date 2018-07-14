# Freetalk - a forum system for Freenet

Freetalk aims to provide a user-friendly and secure alternative to the "Freenet Message System"
(FMS) C++ daemon.

It intends to do so by:
* integrating into the Freenet web interface by being a plugin instead of being a standalone
  application with its own web interface.
* being written in Java instead of C++ to avoid remote code execution exploits.
* using the [WebOfTrust](https://github.com/xor-freenet/plugin-WebOfTrust) plugin (bundled with
  Freenet) for spam filtering instead of a single-use trust system to ensure user identities can be
  used across different Freenet plugins such as FlogHelper (bundled) and
  [Sone](https://github.com/Bombe/Sone).
  
## Usage

As of 2018 Freetalk is currently in development by [xor-freenet](https://github.com/xor-freenet) and
**NOT** intended to be used.  
It has severe performance issues which need to be fixed first.

If you use it nevertheless be aware that all messages will be deleted at some point in the future.  
That will be necessary to conduct major performance improvement changes without having to spent
months upon writing code for backwards compatibility. Sorry :)

## Compiling

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
