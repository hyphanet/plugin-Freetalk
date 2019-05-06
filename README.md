# Freetalk - a forum system for Freenet

Freetalk aims to provide a user-friendly and secure alternative to the "Freenet Message System"
(FMS) C++ daemon.

It intends to do so by:
* integrating into the Freenet web interface by being a plugin instead of being a standalone
  application with its own web interface.
* being written in Java instead of C++ to avoid remote code execution exploits.
* using the [WebOfTrust](https://github.com/freenet/plugin-WebOfTrust) plugin (bundled with Freenet)
  for spam filtering instead of a single-use trust system to ensure user identities can be
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

### Dependencies

Clone the [fred](https://github.com/freenet/fred) and plugin-Freetalk repositories into the same
parent directory.  
Compile fred using its instructions.

### Compiling by command line

```bash
ant clean
ant
# If you get errors about missing classes check build.xml for whether the JAR locations are correct.
```

The output `Freetalk.jar` will be in the `dist` directory.  
You can load it on the `Plugins` page of the Freenet web interface.  
Make sure to load the `WebOfTrust` plugin as well.

### Compiling with Eclipse

* Import the project configurations which fred and Freetalk ship in Eclipse.  
  **NOTICE:** As of 2018-07 fred currently does not ship one, you can use an old release for now.
  The newest which still includes the project can be obtained with:  
  	`git checkout build01480`  
  Be aware that its build instructions will be different compared to newer releases, so check the
  `README.md` after the above command.
* Since build01480 does not automatically download its dependencies, get them from an existing
  Freenet installation:
  * Put `freenet-ext.jar` in `fred/lib/freenet`
  * Put `bcprov.jar` (from e.g. `bcprov-jdk15on-149.jar`, name may vary) in `fred/lib`.
* If necessary fix the build paths for your Eclipse projects so they refer to the correct JAR paths.
* Disable automatic building in Eclipse's `Project` menu as the Ant builders take quite a bit of time to execute.

Now building should work using the `Project` menu or toolbar buttons.

## Debugging

Run fred's class `freenet.node.NodeStarter` using the Eclipse debugger.  

## License

GNU General Public License, version 2, or at your option any later version.