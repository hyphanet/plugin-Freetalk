# Freetalk - a forum system for Freenet [![Build status of branch master on xor-freenet's repository](https://travis-ci.org/xor-freenet/plugin-Freetalk.svg?branch=master "Build status of branch master on xor-freenet's repository")](https://travis-ci.org/xor-freenet/plugin-Freetalk/builds)

Freetalk (FT) aims to provide a user-friendly and secure alternative to the "Freenet Message System"
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

These instructions have been written for the Eclipse package `Eclipse IDE for Java Developers` of
version `2018-12` for `Linux 64-bit`, which you can get
[here](https://www.eclipse.org/downloads/packages/release/2018-12/r).

1. Import the fred project into Eclipse: `File / Import... / Gradle / Existing Gradle Project`.
2. Configure the project to use Gradle version `4.10.3` at
   `Right click the project / Properties / Gradle`.  
   Enable `Automatic project Synchronization` there as well.
3. Enable Eclipse's `Gradle executions` and `Gradle tasks` views at `Window / Show view / Other...`.
4. In the `Gradle Tasks` view, right click `fred` and select `Run Default Gradle Tasks`.  
   Wait for Gradle to finish. You can see its output and error messages in the `Console` view.
5. Once the above step is finished, the green `Run` button in the main toolbar will show a run
   configuration for fred in its dropdown menu.  
   Open the UI to edit it at `Run / Run Configurations...` and there set:  
   * `Gradle Tasks / Gradle tasks: jar copyRuntimeLibs`  
      The latter ensures Gradle copies all dependency JARs of Freenet to a single directory which
      FT will use.  
     **TODO**: Prefix with `clean` task once it doesn't break `Version.class` anymore.
   * `Arguments / Program Arguments: -x test` optionally to skip running the fred unit tests at
      every build.
6. Re-run fred's Gradle with the above run configuration via `Run / <configuration name>`.
7. Import the FT project as type `General / Existing Projects into Workspace` - that type is what
   to use here because the FT repository already contains an Eclipse project configuration.
8. Ensure a Gradle run configuration for FT is created by running the default tasks like you did
   for fred.  
   Set its Gradle tasks to `jar`, or `clean jar` if you want to ensure the JAR is always fully
   rebuilt. Not fully rebuilding may cause e.g. deleted classes to persist in the JAR, though
   I have not tested if this still applies to a build system as modern as Gradle.

**Notice**: Building using `Project / Build project` or `Project / Build Automatically` or the
toolbar buttons does not seem to trigger Gradle with the said Eclipse version!  
It seems that this only triggers Eclipse's internal Java builder which is used to empower Eclipse's
own features.  
As a consequence, manually run Gradle using the aforementioned `Run` button in case you need the
FT JAR as output, e.g. for the following [debugging](#debugging) section.  
Running the unit tests is also done by that, or by Eclipse's own UI for running tests, especially to
debug failing tests with its debugger.

**Notice**: Should Eclipse show errors about missing JARs such as `db4o.jar` and say they prevent it
from building: Notice that the JARs likely have in fact been created by the fred/FT Gradle
builders on the filesystem already, so you can fix Eclipse to notice them by:
1. `Right click the project / Gradle / Refresh Gradle Project`.
2. `Project / Build Project` to manually start a build. Automatic building might have to be disabled
   in the same menu.

## Debugging

* Set up Eclipse as explained in the [compiling](#compiling) section.
* Run fred's class `freenet.node.NodeStarter` using the Eclipse debugger.
* Browse to Freenet's [Plugins page](http://127.0.0.1:8888/plugins/).
* Load the `WebOfTrust` plugin.
* Use the `Load Plugin` box to load `PARENT_DIRECTORY/plugin-Freetalk/dist/Freetalk.jar`.
* After the plugin is loaded, Freetalk will be accessible at the `Forums` menu.
